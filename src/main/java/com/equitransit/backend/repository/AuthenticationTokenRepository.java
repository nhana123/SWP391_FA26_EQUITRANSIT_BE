/*
 * AuthenticationTokenRepository
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Persist one-time token hashes.
 */
package com.equitransit.backend.repository;


import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.equitransit.backend.entity.AuthenticationToken;


/** Stores hashed one-time verification and recovery tokens. */
public interface AuthenticationTokenRepository
        extends JpaRepository<AuthenticationToken, Long> {

    /**
     * Looks up a token by its hash.
     * @param hash the token hash
     * @return the token, if present
     */
    Optional<AuthenticationToken> findByTokenHash(String hash);

    /**
     * Locks the token to prevent concurrent consumption.
     * @param id the token identifier
     * @return the locked token
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AuthenticationToken t where t.tokenId = :id")
    Optional<AuthenticationToken> findLockedById(@Param("id") Long id);

    /**
     * Reads the owner before acquiring locks in a consistent order.
     * @param hash the submitted token hash
     * @return the owning account identifier
     */
    @Query("select t.user.userId from AuthenticationToken t "
            + "where t.tokenHash = :hash")
    Optional<Long> findOwnerByHash(@Param("hash") String hash);

    /**
     * Locks a token after its owner has been locked.
     * @param hash the submitted token hash
     * @return the freshly loaded token
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AuthenticationToken t where t.tokenHash = :hash")
    Optional<AuthenticationToken> findLockedByHash(@Param("hash") String hash);

    /**
     * Invalidates previous outstanding tokens of the same purpose.
     * @param userId the account identifier
     * @param purpose the token purpose
     * @return the number of invalidated tokens
     */
    @Modifying
    @Query("delete from AuthenticationToken t "
            + "where t.user.userId = :userId and t.purpose = :purpose "
            + "and t.usedAt is null")
    int invalidate(@Param("userId") Long userId,
            @Param("purpose") String purpose);

    /**
     * Determines whether this flow previously issued a verification token.
     * @param userId the account identifier
     * @param purpose the token purpose
     * @return whether a token record exists
     */
    boolean existsByUserUserIdAndPurpose(Long userId, String purpose);

    /**
     * Determines whether verification already succeeded in this flow.
     * @param userId the account identifier
     * @param purpose the token purpose
     * @return whether a consumed token exists
     */
    boolean existsByUserUserIdAndPurposeAndUsedAtIsNotNull(
            Long userId, String purpose);
}
