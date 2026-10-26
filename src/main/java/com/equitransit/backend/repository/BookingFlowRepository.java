package com.equitransit.backend.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parent-locked commercial automation; no new ledger or alternate statuses. */
@Repository
public class BookingFlowRepository {
    private final JdbcTemplate jdbc;

    /** @param jdbc transaction-aware SQL */
    public BookingFlowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param order parent @return customer owning the submitted contract */
    public long customer(long order) {
        return jdbc.queryForObject("SELECT CustomerID FROM dbo.TransportOrder "
                + "WHERE OrderID=?", Long.class, order);
    }
    /** @param revision current revision @return preferred current quote ID */
    public Long quote(long revision) {
        List<Long> ids = jdbc.query("SELECT TOP(1) QuoteID FROM dbo.Quote "
                + "WITH (UPDLOCK,HOLDLOCK) WHERE RevisionID=? "
                + "ORDER BY CASE WHEN QuoteStatus='ACCEPTED' THEN 0 "
                + "ELSE 1 END,QuoteID DESC", (r, i) -> r.getLong(1), revision);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /** @param quote accepted quote; parent/quote/ledger locks are retained */
    public void requestDeposit(long quote) {
        jdbc.update("INSERT dbo.PaymentRequest(QuoteID,ScheduleID,"
                + "PaymentNumber,PaymentStage,AmountDue,Currency,DueAt,Status) "
                + "SELECT s.QuoteID,s.ScheduleID,CONCAT('PAY-',s.ScheduleID),"
                + "s.PaymentStage,s.AmountDue,s.Currency,s.DueAt,'OPEN' "
                + "FROM dbo.QuotePaymentSchedule s JOIN dbo.Quote q ON "
                + "q.QuoteID=s.QuoteID WHERE q.QuoteID=? "
                + "AND q.QuoteStatus='ACCEPTED' AND s.PaymentStage='DEPOSIT' "
                + "AND s.AmountDue>0 AND NOT EXISTS(SELECT 1 FROM "
                + "dbo.PaymentRequest p WHERE p.ScheduleID=s.ScheduleID)",
                quote);
    }

    /** @param order scope @param revision current revision
     * @return whether this is the fixed-road automatic contract */
    public boolean autoRoad(long order, long revision) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Quote q "
                + "JOIN dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                + "WHERE r.OrderID=? AND r.RevisionID=? AND "
                + "q.QuoteStatus='ACCEPTED' AND q.PreparedBy IS NULL "
                + "AND q.IssuedBy IS NULL AND JSON_VALUE("
                + "q.PricingSnapshotJSON,'$.request.termsVersion')="
                + "'ROAD-AUTO-V1'", Integer.class, order, revision) == 1;
    }
}
