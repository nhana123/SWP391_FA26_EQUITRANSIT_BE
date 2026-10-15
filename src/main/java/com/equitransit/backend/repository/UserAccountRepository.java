/*
 * UserAccountRepository
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Persist login accounts and lock token changes.
 */
package com.equitransit.backend.repository;


import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.equitransit.backend.entity.UserAccount;


/** Reads and persists login accounts. */
public interface UserAccountRepository
        extends JpaRepository<UserAccount, Long> {

    /**
     * Finds an account without depending on database collation.
     * @param email the normalized email
     * @return the matching account
     */
    Optional<UserAccount> findByEmailIgnoreCase(String email);

    /**
     * Locks an account while consuming or issuing a token.
     * @param id the account identifier
     * @return the locked account
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.userId = :id")
    Optional<UserAccount> findLockedById(@Param("id") Long id);

    /**
     * Reads an identifier without caching a mutable account entity.
     * @param email the submitted email
     * @return the matching account identifier
     */
    @Query("select u.userId from UserAccount u "
            + "where lower(u.email) = lower(:email)")
    Optional<Long> findIdByEmail(@Param("email") String email);
}
