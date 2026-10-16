/*
 * AuditWriter
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Append trusted order facts with redacted bounded metadata.
 */
package com.equitransit.backend.service;


import com.equitransit.backend.entity.activity.ActivityCode;
import com.equitransit.backend.repository.ActivityRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.exception.OrderException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


/** Internal facade: producer owns business authorization and parent lock. */
@Service
public class AuditWriter {

    private final ActivityRepository repository;
    private final CurrentActorService actors;
    private final AuditRedactor redactor;

    /**
     * @param repository insert-only audit adapter
     * @param actors live actor UserID, never client-supplied StaffID
     * @param redactor metadata privacy policy
     */
    public AuditWriter(ActivityRepository repository,
            CurrentActorService actors, AuditRedactor redactor) {
        this.repository = repository;
        this.actors = actors;
        this.redactor = redactor;
    }

    /**
     * @param order authorized and locked order
     * @param code verified fact; this method does not clear/depart/pay an order
     * @param operation stable producer UUID
     * @param snapshot bounded metadata, sensitive/free text is removed
     * @return original event identity on retry; never edits an existing event
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long append(long order, ActivityCode code, UUID operation,
            JsonNode snapshot) {
        if (!repository.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ACTIVITY_SCHEMA_PENDING", "Deploy activity migration008.");
        }
        long user = actors.current().userId();
        String key = TokenTools.hash(user + ":" + order + ":" + code
                + ":" + operation);

        repository.lock("EquiTransit:audit:" + key);
        var args = Map.of("key", key, "user", user, "order", order,
                "code", code.name(), "snapshot", "{\"schemaVersion\":1,"
                        + "\"metadata\":" + redactor.redact(snapshot) + "}");
        var prior = repository.rows("auditKey", args);

        if (!prior.isEmpty()) {
            return ((Number) prior.getFirst().get("EventID")).longValue();
        }
        return repository.scalar("auditAppend", args).longValueExact();
    }
}
