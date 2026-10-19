/*
 * RefreshSessionService
 * Author: EquiTransit development team
 * Date: 2026-10-20
 * Copyright: EquiTransit project contributors.
 * Purpose: Rotate opaque cookie credentials under account/family locks.
 */
package com.equitransit.backend.service;

import com.equitransit.backend.dto.request.LoginRequest;
import com.equitransit.backend.dto.response.LoginResponse;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.RefreshTokenRepository;
import com.equitransit.backend.repository.RefreshTokenRepository.Account;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Expected rejection commits any family revocation before returning 401. */
@Service
public class RefreshSessionService {
    /** Private transport to the controller; never serialized as a body. */
    public record Session(LoginResponse response, @JsonIgnore String raw) {
        @Override
        public String toString() {
            return "Session[REDACTED]";
        }
    }

    /** Safe error code only; no credential is attached to the exception. */
    public static class Failure extends RuntimeException {
        private final String code;

        /** @param code stable public rejection code */
        public Failure(String code) {
            super("Refresh authentication failed.");
            this.code = code;
        }

        /** @return safe public rejection code */
        public String code() {
            return code;
        }
    }

    private final RefreshTokenRepository repository;
    private final PasswordEncoder passwords;
    private final JwtTokenService jwt;
    private final Clock clock;
    private final long ttl;
    private final String dummy;

    /** @param repository JDBC adapter @param passwords credential verifier
     * @param jwt access issuer @param clock UTC @param ttl refresh lifetime */
    public RefreshSessionService(RefreshTokenRepository repository,
            PasswordEncoder passwords, JwtTokenService jwt, Clock clock,
            @Value("${app.jwt.refresh-ttl-seconds:28800}") long ttl) {
        this.repository = repository;
        this.passwords = passwords;
        this.jwt = jwt;
        this.clock = clock;
        this.ttl = ttl;
        dummy = passwords.encode(TokenTools.randomToken());
        if (ttl < 1) {
            throw new IllegalArgumentException("Invalid refresh lifetime.");
        }
    }

    /** @param request credentials @return access body and private cookie */
    @Transactional(timeout = 15)
    public Session login(LoginRequest request) {
        requireReady();
        var account = repository.userId(request.getEmail().trim()
                .toLowerCase(Locale.ROOT)).flatMap(repository::account)
                .orElse(null);
        String hash = account == null ? dummy : account.passwordHash();
        boolean matches = request.getPassword().getBytes(
                java.nio.charset.StandardCharsets.UTF_8).length <= 72
                && passwords.matches(request.getPassword(), hash);
        if (!matches || account == null || !active(account)) {
            throw new OrderException(HttpStatus.UNAUTHORIZED,
                    "LOGIN_FAILED", "Invalid credentials or account.");
        }
        return issue(account, UUID.randomUUID(), null);
    }

    /** @param raw untrusted cookie @param role optional tab role
     * @return replacement credential; reuse permanently revokes the family */
    @Transactional(timeout = 15, noRollbackFor = Failure.class)
    public Session refresh(String raw, String role) {
        requireReady();
        if (raw == null || raw.isBlank()) {
            throw new Failure("REFRESH_TOKEN_MISSING");
        }
        String hash = fingerprint(raw);
        var routed = repository.find(hash).orElseThrow(() ->
                new Failure("REFRESH_TOKEN_INVALID"));
        var account = repository.account(routed.userId()).orElseThrow(() ->
                new Failure("REFRESH_TOKEN_INVALID"));
        repository.lockFamily(routed.family());
        var token = repository.find(hash).orElseThrow(() ->
                new Failure("REFRESH_TOKEN_INVALID"));
        if (role != null && !role.equals(account.role())) {
            throw new Failure("REFRESH_ROLE_MISMATCH");
        }
        if (token.revokedAt() != null) {
            if ("ROTATED".equals(token.reason())) {
                repository.revokeFamily(token.family(), "REUSE_DETECTED",
                        now());
                throw new Failure("REFRESH_TOKEN_REUSED");
            }
            throw new Failure("REFRESH_TOKEN_REVOKED");
        }
        if (!token.expiresAt().isAfter(now())) {
            throw new Failure("REFRESH_TOKEN_EXPIRED");
        }
        if (!active(account)) {
            repository.revokeFamily(token.family(), "ACCOUNT_UNAVAILABLE",
                    now());
            throw new Failure("ACCOUNT_INACTIVE_OR_LOCKED");
        }
        if (!TokenTools.hash(account.passwordHash())
                .equals(token.credentialHash())) {
            repository.revokeFamily(token.family(), "PASSWORD_CHANGED",
                    now());
            throw new Failure("CREDENTIALS_CHANGED");
        }
        return issue(account, token.family(), token.id());
    }

    /** @param raw optional cookie; a malformed value is still logged out */
    @Transactional(timeout = 15)
    public void logout(String raw) {
        if (raw == null || !raw.matches("^[A-Za-z0-9_-]{43}$")) {
            return;
        }
        requireReady();
        repository.find(TokenTools.hash(raw)).ifPresent(token -> {
            repository.account(token.userId());
            repository.lockFamily(token.family());
            repository.revokeFamily(token.family(), "LOGOUT", now());
        });
    }

    /** Caller already locks the account; joins reset/F15 transaction.
     * @param user account @param reason PASSWORD_CHANGED/STAFF_DEACTIVATED */
    public void revokeUser(long user, String reason) {
        requireReady();
        repository.revokeUser(user, reason, now());
    }

    private boolean active(Account account) {
        return "ACTIVE".equals(account.status())
                && ("CUSTOMER".equals(account.role())
                        || repository.activeStaff(account.id()));
    }

    private Session issue(Account account, UUID family, Long previous) {
        String raw = TokenTools.randomToken();
        var now = now();
        long next = repository.insert(family, account.id(),
                TokenTools.hash(raw), TokenTools.hash(account.passwordHash()),
                now, now.plusSeconds(ttl));
        if (previous != null) {
            repository.rotate(previous, next, now);
        }
        return new Session(jwt.issue(account.id(), account.passwordHash(),
                account.role()), raw);
    }

    private String fingerprint(String raw) {
        if (!raw.matches("^[A-Za-z0-9_-]{43}$")) {
            throw new Failure("REFRESH_TOKEN_INVALID");
        }
        return TokenTools.hash(raw);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private void requireReady() {
        if (!repository.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_SCHEMA_PENDING", "Deploy auth migration 017.");
        }
    }
}
