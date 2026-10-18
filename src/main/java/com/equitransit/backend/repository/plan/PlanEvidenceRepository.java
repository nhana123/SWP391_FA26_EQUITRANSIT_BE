package com.equitransit.backend.repository.plan;

import com.equitransit.backend.exception.order.OrderException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** F10 consumes actual BE1 quote evidence and appends baseline audit facts. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PlanEvidenceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc transaction-aware application datasource */
    public PlanEvidenceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param order locked parent @param revision exact current revision
     * @param quote accepted quote @param now server time */
    public void requireAccepted(long order, long revision, long quote,
            OffsetDateTime now) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.Quote q WITH (UPDLOCK,HOLDLOCK) "
                + "JOIN dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                + "JOIN dbo.TransportOrder o ON o.OrderID=r.OrderID "
                + "WHERE o.OrderID=:order AND r.RevisionID=:rev "
                + "AND o.CurrentRevisionID=r.RevisionID AND q.QuoteID=:quote "
                + "AND q.QuoteStatus='ACCEPTED' AND q.AcceptedAt<=:now "
                + "AND q.TotalQuotedAmount IS NOT NULL "
                + "AND q.TotalQuotedAmount>0",
                new MapSqlParameterSource("order", order)
                        .addValue("rev", revision).addValue("quote", quote)
                        .addValue("now", now.withOffsetSameInstant(
                                ZoneOffset.UTC).toLocalDateTime()),
                Integer.class);

        if (count == null || count != 1) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "ACCEPTED_QUOTE_REQUIRED",
                    "Select an accepted quote for this order and revision.");
        }
    }

    /** @param order authorized parent @param user authenticated UserID
     * @param plan resulting plan @param action trusted action code
     * @param operation successful retry key @param now server time */
    public void append(long order, long user, long plan, String action,
            UUID operation, OffsetDateTime now) {
        String snapshot = "{\"planId\":" + plan + ",\"operationId\":\""
                + operation + "\"}";

        jdbc.update("INSERT INTO dbo.AuditEvent (UserID,EntityType,EntityID,"
                + "ActionCode,ChangeSnapshot,OccurredAt) VALUES "
                + "(:user,'OPERATIONAL_PLAN',:order,:action,:snapshot,:now)",
                Map.of("user", user, "order", order,
                        "action", "F10_" + action, "snapshot", snapshot,
                        "now", now.withOffsetSameInstant(ZoneOffset.UTC)
                                .withNano(0).toLocalDateTime()));
    }
}
