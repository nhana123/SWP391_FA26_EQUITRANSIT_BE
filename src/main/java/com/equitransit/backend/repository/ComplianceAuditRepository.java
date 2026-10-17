package com.equitransit.backend.repository;


import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;


/** Appends real F08 audit records in the caller's transaction. */
@Repository
public class ComplianceAuditRepository extends ComplianceJdbcSupport {

    /** @param jdbc the transaction-aware SQL template */
    public ComplianceAuditRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /** @param order the authorized order
     * @param user the live authenticated user
     * @param action the service-owned action code
     * @param entity the resulting row key
     */
    public void append(long order, long user, String action, long entity) {
        jdbc.update("INSERT INTO dbo.AuditEvent"
                + " (UserID,EntityType,EntityID,ActionCode,ChangeSnapshot,"
                + "OccurredAt) VALUES (:user,'COMPLIANCE',:order,:action,"
                + ":snapshot,:now)", args("user", user).addValue("order", order)
                        .addValue("action", action)
                        .addValue("snapshot", "{\"entityId\":" + entity + "}")
                        .addValue("now", now()));
    }
}
