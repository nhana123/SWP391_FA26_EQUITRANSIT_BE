package com.equitransit.backend.security;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Per-account throttle locks never include raw email addresses. */
public interface AuthRequestBudgetRepository
        extends JpaRepository<AuthRequestBudget, String> {
    /** @param key fingerprint @return locked budget */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AuthRequestBudget b where b.key=:key")
    Optional<AuthRequestBudget> locked(@Param("key") String key);

    /** @param now cutoff @return expired anonymous buckets deleted */
    @Modifying
    @Query("delete from AuthRequestBudget b where b.until<:now")
    int purge(@Param("now") LocalDateTime now);
}
