package com.equitransit.backend.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Existing audit rows store issuance metadata alongside the real ledger. */
@Repository
public class ManagerDepositRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    /** @param jdbc transactional SQL @param json typed metadata */
    public ManagerDepositRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** @param order scope @param revision current contract
     * @return last issuance for this exact revision */
    public Optional<JsonNode> latest(long order, long revision) {
        var rows = jdbc.query("SELECT TOP(1) ChangeSnapshot FROM "
                + "dbo.AuditEvent WHERE EntityType='ORDER_DEPOSIT' "
                + "AND EntityID=? AND JSON_VALUE(ChangeSnapshot,"
                + "'$.revisionId')=? ORDER BY EventID DESC",
                (r, i) -> decode(r.getString(1)), order,
                Long.toString(revision));
        return rows.stream().findFirst();
    }

    /** @param revision current contract @return preferred current quote */
    public Long quote(long revision) {
        List<Long> rows = jdbc.query("SELECT TOP(1) QuoteID FROM dbo.Quote "
                + "WITH (UPDLOCK,HOLDLOCK) WHERE RevisionID=? AND QuoteStatus "
                + "IN ('ACCEPTED','ISSUED','APPROVED','DRAFT') ORDER BY "
                + "CASE WHEN QuoteStatus='ACCEPTED' THEN 0 ELSE 1 END,"
                + "QuoteID DESC", (r, i) -> r.getLong(1), revision);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** @param quote contract @return deposit request ID if already opened */
    public Long payment(long quote) {
        List<Long> rows = jdbc.query("SELECT PaymentRequestID FROM "
                + "dbo.PaymentRequest WHERE QuoteID=? AND PaymentStage="
                + "'DEPOSIT' ORDER BY PaymentRequestID", (r, i) -> r.getLong(1),
                quote);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** @param order parent @param user actor @param payload metadata */
    public void issued(long order, long user, String payload) {
        jdbc.update("INSERT dbo.AuditEvent(UserID,EntityType,EntityID,"
                + "ActionCode,ChangeSnapshot) VALUES (?, 'ORDER_DEPOSIT', ?, "
                + "'DEPOSIT_REQUESTED', ?)", user, order, payload);
    }

    /** @param order locked scope @return owning customer */
    public long customer(long order) {
        return jdbc.queryForObject("SELECT CustomerID FROM dbo.TransportOrder "
                + "WHERE OrderID=?", Long.class, order);
    }

    /** @param quote accepted contract; caller owns ledger lock */
    public void open(long quote) {
        jdbc.update("INSERT dbo.PaymentRequest(QuoteID,ScheduleID,"
                + "PaymentNumber,PaymentStage,AmountDue,Currency,DueAt,Status) "
                + "SELECT s.QuoteID,s.ScheduleID,CONCAT('PAY-',s.ScheduleID),"
                + "s.PaymentStage,s.AmountDue,s.Currency,s.DueAt,'OPEN' FROM "
                + "dbo.QuotePaymentSchedule s WHERE s.QuoteID=? AND "
                + "s.PaymentStage='DEPOSIT' AND NOT EXISTS(SELECT 1 FROM "
                + "dbo.PaymentRequest p WHERE p.ScheduleID=s.ScheduleID)",
                quote);
    }

    private JsonNode decode(String text) {
        try {
            return json.readTree(text);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("Invalid deposit metadata.");
        }
    }
}
