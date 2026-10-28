package com.equitransit.backend.mail;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Portable JPA queries; lease ownership is checked under a row lock. */
public interface AccountMailRepository
        extends JpaRepository<AccountMail, UUID> {
    /** @param now UTC @param page bounded batch @return candidate keys only */
    @Query("select m.id from AccountMail m where "
            + "(m.status='PENDING' and m.availableAt<=:now) or "
            + "(m.status='PROCESSING' and m.leaseUntil<=:now) "
            + "order by m.createdAt,m.id")
    List<UUID> candidates(@Param("now") LocalDateTime now, Pageable page);

    /** @param id key @return freshly locked row, without a cached candidate */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from AccountMail m where m.id=:id")
    Optional<AccountMail> locked(@Param("id") UUID id);

    /** @return non-sensitive delivery totals for operational monitoring */
    @Query("select m.status,count(m) from AccountMail m group by m.status")
    List<Object[]> counts();

    /** @return counts of safe current failure reasons, without provider text */
    @Query("select m.errorCode,count(m) from AccountMail m "
            + "where m.errorCode is not null group by m.errorCode")
    List<Object[]> errorCounts();

    /** @param now deadline @return number of expired queued payloads erased */
    @Modifying
    @Query("update AccountMail m set m.status='EXPIRED',m.payload=null,"
            + "m.completedAt=:now,m.leaseUntil=null,m.leaseToken=null "
            + "where m.expiresAt<=:now and m.status in ('PENDING',"
            + "'PROCESSING') and (m.leaseUntil is null or m.leaseUntil<=:now)")
    int expire(@Param("now") LocalDateTime now);

    /** @param before retention cutoff @return deleted terminal metadata */
    @Modifying
    @Query("delete from AccountMail m where m.completedAt<:before "
            + "and m.status not in ('PENDING','PROCESSING')")
    int purge(@Param("before") LocalDateTime before);
}
