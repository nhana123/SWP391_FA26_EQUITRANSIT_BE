/*
 * RefreshTokenRepository
 * Author: EquiTransit development team
 * Date: 2026-10-20
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized refresh persistence and account-first locks.
 */
package com.equitransit.backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** All writes participate in the caller's transaction. */
@Repository
public class RefreshTokenRepository {
    /** Live authentication state; credential hash is never a response. */
    public record Account(long id, String passwordHash, String role,
            String status) { }

    /** Only irreversible fingerprints are stored. */
    public record Token(long id, UUID family, long userId,
            String credentialHash, LocalDateTime expiresAt,
            LocalDateTime revokedAt, String reason) { }

    private final JdbcTemplate jdbc;

    /** @param source transaction-aware application data source */
    public RefreshTokenRepository(DataSource source) {
        jdbc = new JdbcTemplate(source);
    }

    /** @return whether every required refresh column is deployed */
    public boolean ready() {
        try {
            jdbc.queryForList("SELECT TokenID,FamilyID,UserID,TokenHash,"
                    + "CredentialHash,IssuedAt,ExpiresAt,RevokedAt,"
                    + "RevokeReason,ReplacedByTokenID FROM "
                    + "dbo.RefreshTokenSession WHERE 1=0");
            return true;
        } catch (DataAccessException error) {
            return false;
        }
    }

    /** @param email normalized identifier @return account key only */
    public Optional<Long> userId(String email) {
        return jdbc.query("SELECT UserID FROM dbo.[User] "
                + "WHERE LOWER(Email)=?", (r, i) -> r.getLong(1), email)
                .stream().findFirst();
    }

    /** @param id account key @return account under update lock */
    public Optional<Account> account(long id) {
        return jdbc.query("SELECT u.UserID,u.PasswordHash,u.Status,"
                + "r.RoleCode FROM dbo.[User] u WITH (UPDLOCK,HOLDLOCK) "
                + "JOIN dbo.Role r ON r.RoleID=u.RoleID WHERE u.UserID=?",
                (r, i) -> new Account(r.getLong(1), r.getString(2),
                        r.getString(4), r.getString(3)), id)
                .stream().findFirst();
    }

    /** @param id internal user @return active live staff profile exists */
    public boolean activeStaff(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.StaffProfile "
                + "WITH (HOLDLOCK) WHERE UserID=? AND StaffStatus='ACTIVE'",
                Integer.class, id) == 1;
    }

    /** @param hash submitted fingerprint @return minimal routing record */
    public Optional<Token> find(String hash) {
        return jdbc.query("SELECT TokenID,FamilyID,UserID,CredentialHash,"
                + "ExpiresAt,RevokedAt,RevokeReason FROM "
                + "dbo.RefreshTokenSession WHERE TokenHash=?", (r, i) ->
                        new Token(r.getLong(1), UUID.fromString(
                                r.getString(2)), r.getLong(3), r.getString(4),
                                r.getTimestamp(5).toLocalDateTime(),
                                r.getTimestamp(6) == null ? null
                                        : r.getTimestamp(6).toLocalDateTime(),
                                r.getString(7)), hash).stream().findFirst();
    }

    /** @param family session lineage, locked after its owning account */
    public void lockFamily(UUID family) {
        jdbc.queryForList("SELECT TokenID FROM dbo.RefreshTokenSession "
                + "WITH (UPDLOCK,HOLDLOCK) WHERE FamilyID=?",
                family.toString());
    }

    /** @param family lineage @param user owner @param hash opaque hash
     * @param credential password fingerprint @param now issuance UTC
     * @param expiry expiry UTC @return inserted token key */
    public long insert(UUID family, long user, String hash, String credential,
            LocalDateTime now, LocalDateTime expiry) {
        jdbc.update("INSERT INTO dbo.RefreshTokenSession(FamilyID,UserID,"
                + "TokenHash,CredentialHash,IssuedAt,ExpiresAt) "
                + "VALUES (?,?,?,?,?,?)", family.toString(), user, hash,
                credential, now, expiry);
        return jdbc.queryForObject("SELECT TokenID FROM "
                + "dbo.RefreshTokenSession WHERE TokenHash=?", Long.class,
                hash);
    }

    /** @param id old key @param next replacement @param now UTC revocation */
    public void rotate(long id, long next, LocalDateTime now) {
        jdbc.update("UPDATE dbo.RefreshTokenSession SET RevokedAt=?,"
                + "RevokeReason='ROTATED',ReplacedByTokenID=? WHERE "
                + "TokenID=? AND RevokedAt IS NULL", now, next, id);
    }

    /** @param family lineage @param reason safe code @param now UTC */
    public void revokeFamily(UUID family, String reason, LocalDateTime now) {
        jdbc.update("UPDATE dbo.RefreshTokenSession SET RevokedAt=?,"
                + "RevokeReason=? WHERE FamilyID=? AND RevokedAt IS NULL",
                now, reason, family.toString());
    }

    /** @param user account @param reason safe code @param now UTC */
    public void revokeUser(long user, String reason, LocalDateTime now) {
        jdbc.update("UPDATE dbo.RefreshTokenSession SET RevokedAt=?,"
                + "RevokeReason=? WHERE UserID=? AND RevokedAt IS NULL",
                now, reason, user);
    }
}
