/*
 * ServiceSelectionAuditRepository
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Persist atomic service confirmation receipts without price claims.
 */
package com.equitransit.backend.repository;


import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


/** Parent-order lock serializes operation lookup and append; no delete API. */
@Repository
public class ServiceSelectionAuditRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc shared transaction-aware SQL adapter */
    public ServiceSelectionAuditRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Looks up only the authenticated owner's completed selection operation.
     * @param order locked owned order
     * @param user trusted account key
     * @param operation retry UUID
     * @return versioned server-produced receipt JSON
     */
    public Optional<String> operation(long order, long user, UUID operation) {
        return jdbc.query("SELECT ChangeSnapshot FROM dbo.AuditEvent"
                + " WHERE EntityType='SERVICE_SELECTION' AND EntityID=:order"
                + " AND UserID=:user AND JSON_VALUE(ChangeSnapshot,"
                + "'$.operationId')=:operation",
                new MapSqlParameterSource("order", order)
                        .addValue("user", user)
                        .addValue("operation", operation.toString()),
                (row, index) -> row.getString("ChangeSnapshot"))
                .stream().findFirst();
    }

    /**
     * Appends an atomic typed confirmation result under the order transaction.
     * @param order owned locked order
     * @param user trusted account key
     * @param snapshot server-produced JSON without credentials
     */
    public void append(long order, long user, String snapshot) {
        jdbc.update("INSERT dbo.AuditEvent"
                + " (UserID,EntityType,EntityID,ActionCode,ChangeSnapshot)"
                + " VALUES (:user,'SERVICE_SELECTION',:order,"
                + "'CONFIRM_SERVICES',:snapshot)",
                new MapSqlParameterSource("user", user)
                        .addValue("order", order).addValue("snapshot",
                snapshot));
    }
}
