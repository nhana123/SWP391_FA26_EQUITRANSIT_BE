/*
 * NotificationWriter
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Retryable after-commit projection from durable audit facts.
 */
package com.equitransit.backend.service;


import com.equitransit.backend.repository.ActivityRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


/** Independent projection failures cannot roll back a committed producer. */
@Service
public class NotificationWriter {

    private final ActivityRepository repository;

    /** @param repository set-based idempotent outbox adapter */
    public NotificationWriter(ActivityRepository repository) {
        this.repository = repository;
    }

    /** Projects at most100 audit events in one independent transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 15)
    public void dispatch() {
        if (repository.ready()) {
            repository.lock("EquiTransit:notification-projection");
            repository.update("project", Map.of());
        }
    }

    /** @return at most20 exclusive, retryable email leases */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public List<Long> claimMail() {
        if (!repository.ready()) {
            return List.of();
        }
        return repository.rows("claimMail", Map.of()).stream()
                .map(row -> ((Number) row.get("NotificationID")).longValue())
                .toList();
    }

    /**
     * @param id claimed mail
     * @return current opted-in recipient, rechecked before delivery
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public String recipient(long id) {
        var rows = repository.rows("mail", Map.of("id", id));

        return rows.isEmpty() ? null : rows.getFirst().get("Email").toString();
    }

    /**
     * @param id leased notification
     * @param success sent or suppressed; failure keeps a backoff lease
     * @param suppressed recipient disabled/opted out since enqueue
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void completeMail(long id, boolean success, boolean suppressed) {
        if (success) {
            repository.update("mailDone", Map.of("id", id, "state",
                    suppressed ? "SUPPRESSED" : "SENT"));
        } else {
            repository.update("mailFail", Map.of("id", id));
        }
    }
}
