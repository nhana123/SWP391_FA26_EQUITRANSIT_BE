/*
 * AssessmentAuditRepository
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Append transactional assessment audit and resolve command retries.
 */
package com.equitransit.backend.repository;


import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


/** No update/delete operations; audit writes share the parent transaction. */
@Repository
public class AssessmentAuditRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc the shared transaction-aware SQL template */
    public AssessmentAuditRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Looks up the authenticated actor's successful command under parent lock.
     * @param order locked order key
     * @param user live authenticated account key
     * @param operation client retry UUID
     * @return versioned server-produced audit JSON
     */
    public Optional<String> operation(long order, long user, UUID operation) {
        return jdbc.query("SELECT ChangeSnapshot FROM dbo.AuditEvent"
                + " WHERE EntityType='ORDER_ASSESSMENT' AND EntityID=:order"
                + " AND UserID=:user AND JSON_VALUE(ChangeSnapshot,"
                + "'$.operationId')=:operation",
                new MapSqlParameterSource("order", order)
                        .addValue("user", user)
                        .addValue("operation", operation.toString()),
                (row, index) -> row.getString("ChangeSnapshot"))
                .stream().findFirst();
    }

    /**
     * Records actor, action and typed completion result without secrets.
     * @param order locked order key
     * @param user trusted account key, not StaffID
     * @param action service-defined action code
     * @param snapshot versioned typed audit JSON
     */
    public void append(long order, long user, String action, String snapshot) {
        jdbc.update("INSERT dbo.AuditEvent"
                + " (UserID,EntityType,EntityID,ActionCode,ChangeSnapshot)"
                + " VALUES (:user,'ORDER_ASSESSMENT',:order,:action,:snapshot)",
                new MapSqlParameterSource("user", user)
                        .addValue("order", order).addValue("action", action)
                        .addValue("snapshot", snapshot));
    }
}
