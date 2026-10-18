package com.equitransit.backend.repository.departure;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Append F11 audit facts using the existing baseline audit schema. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class DepartureAuditRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc shared transaction-aware template */
    public DepartureAuditRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param order parent @param user authenticated UserID @param plan plan
     * @param action trusted action @param operation retry key @param now UTC */
    public void append(long order, long user, long plan, String action,
            UUID operation, OffsetDateTime now) {
        String snapshot = "{\"planId\":" + plan + ",\"operationId\":\""
                + operation + "\"}";
        jdbc.update("INSERT INTO dbo.AuditEvent (UserID,EntityType,EntityID,"
                + "ActionCode,ChangeSnapshot,OccurredAt) VALUES "
                + "(:user,'DEPARTURE',:order,:action,:snapshot,:now)",
                Map.of("user", user, "order", order, "action", "F11_" + action,
                        "snapshot", snapshot, "now", now.withOffsetSameInstant(
                                ZoneOffset.UTC).toLocalDateTime()));
    }
}
