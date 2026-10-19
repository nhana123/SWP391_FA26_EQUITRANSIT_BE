/*
 * OrderCommitmentRepository
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Read commitments and invalidate unaccepted preparation atomically.
 */
package com.equitransit.backend.repository.order;


import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


/** Small adapter behind the cross-flow policy; no approval is fabricated. */
@Repository
public class OrderCommitmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the persisted readiness adapter.
     * @param jdbc the transaction-aware SQL template
     */
    public OrderCommitmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Locks finance/plan evidence while the parent order lock is held.
     * Unknown operational states fail closed rather than allowing a rewrite.
     * @param order the locked order key
     * @return whether any commitment protects this order
     */
    public boolean protectedOrder(long order) {
        String sql = "SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.Quote q"
                + " WITH (UPDLOCK,HOLDLOCK) JOIN dbo.OrderRevision r"
                + " ON q.RevisionID=r.RevisionID WHERE r.OrderID=:id"
                + " AND (q.AcceptedAt IS NOT NULL OR q.QuoteStatus='ACCEPTED'))"
                + " OR EXISTS (SELECT 1 FROM dbo.TransportPlan p"
                + " WITH (UPDLOCK,HOLDLOCK) WHERE p.OrderID=:id"
                + " AND (p.AcceptedQuoteID IS NOT NULL"
                + " OR p.DepartureAuthorizedAt IS NOT NULL"
                + " OR p.PlanStatus NOT IN ('DRAFT','PROPOSED',"
                + "'PENDING','PLANNED')))"
                + " OR EXISTS (SELECT 1 FROM dbo.TripLeg l"
                + " WITH (UPDLOCK,HOLDLOCK) JOIN dbo.TransportPlan p"
                + " ON l.PlanID=p.PlanID WHERE p.OrderID=:id"
                + " AND l.ActualDeparture IS NOT NULL)"
                + " THEN 1 ELSE 0 END";
        Integer protectedFlag = jdbc.queryForObject(sql,
                new MapSqlParameterSource("id", order), Integer.class);

        return protectedFlag != null && protectedFlag == 1;
    }

    /**
     * Makes stale preparation unusable without erasing historical evidence.
     * Called only after the service refuses accepted/authorized orders.
     * @param order the locked order key
     */
    public void invalidatePreparation(long order) {
        MapSqlParameterSource args = new MapSqlParameterSource("id", order);

        jdbc.update("UPDATE q SET QuoteStatus='SUPERSEDED' FROM dbo.Quote q"
                + " JOIN dbo.OrderRevision r ON q.RevisionID=r.RevisionID"
                + " WHERE r.OrderID=:id AND q.AcceptedAt IS NULL"
                + " AND q.QuoteStatus IN ('DRAFT','APPROVED','ISSUED')", args);
        jdbc.update("UPDATE dbo.TransportPlan SET "
                + "FinancialGateStatus='PENDING',"
                + " OperationalGateStatus='PENDING',DocGateStatus='PENDING',"
                + " GatesCheckedAt=NULL WHERE OrderID=:id"
                + " AND AcceptedQuoteID IS NULL AND DepartureAuthorizedAt"
                + " IS NULL", args);
    }
}
