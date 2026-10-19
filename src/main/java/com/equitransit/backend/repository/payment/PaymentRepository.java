/*
 * PaymentRepository
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized payment ledger persistence.
 */
package com.equitransit.backend.repository.payment;


import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import javax.sql.DataSource;
import com.equitransit.backend.exception.order.OrderException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


/** Internal rows never cross the public DTO boundary. */
@Repository
public class PaymentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param source shared transaction-aware data source */
    public PaymentRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return whether deployment supports F07 */
    public boolean ready() {
        String sql = "SELECT CASE WHEN (SELECT COUNT(*) FROM sys.tables WHERE "
                + "schema_id=SCHEMA_ID('dbo') AND name IN ('PaymentProof', "
                + "'PaymentOperation', 'PaymentRequest', 'PaymentReport', "
                + "'PaymentTransaction', 'PaymentAllocation', 'Refund', "
                + "'Quote', 'QuotePaymentSchedule'))=9 AND (SELECT COUNT(*) "
                + "FROM sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.PaymentProof') AND name IN "
                + "('FileKey', 'OrderID', 'UserID', 'MediaType', "
                + "'ContentBytes', 'CreatedAt'))=6 AND (SELECT COUNT(*) "
                + "FROM sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.PaymentOperation') AND name IN "
                + "('OrderID', 'UserID', 'OperationID', 'RequestHash', "
                + "'ReceiptJSON'))=5 AND (SELECT COUNT(*) FROM sys.columns "
                + "WHERE object_id=OBJECT_ID('dbo.PaymentTransaction') AND "
                + "name IN ('ReversalReason', 'ReversedAt', 'ReversedBy', "
                + "'ReversalProofFileKey'))=4 AND EXISTS(SELECT 1 FROM "
                + "sys.indexes i WHERE "
                + "i.object_id=OBJECT_ID('dbo.PaymentRequest') AND "
                + "i.name='UQ_PaymentRequest_Schedule' AND i.is_unique=1 "
                + "AND i.is_disabled=0 AND i.has_filter=1 AND (SELECT "
                + "COUNT(*) FROM sys.index_columns c WHERE "
                + "c.object_id=i.object_id AND c.index_id=i.index_id AND "
                + "c.key_ordinal>0)=1 AND (SELECT COUNT(*) FROM "
                + "sys.index_columns c WHERE c.object_id=i.object_id AND "
                + "c.index_id=i.index_id AND c.key_ordinal>0 AND "
                + "COL_NAME(c.object_id, c.column_id) IN ('ScheduleID'))=1) "
                + "AND EXISTS(SELECT 1 FROM sys.indexes i WHERE "
                + "i.object_id=OBJECT_ID('dbo.Refund') AND "
                + "i.name='UQ_Refund_BankReference' AND i.is_unique=1 AND "
                + "i.is_disabled=0 AND i.has_filter=1 AND (SELECT COUNT(*) "
                + "FROM sys.index_columns c WHERE c.object_id=i.object_id "
                + "AND c.index_id=i.index_id AND c.key_ordinal>0)=1 AND "
                + "(SELECT COUNT(*) FROM sys.index_columns c WHERE "
                + "c.object_id=i.object_id AND c.index_id=i.index_id AND "
                + "c.key_ordinal>0 AND COL_NAME(c.object_id, c.column_id) "
                + "IN ('BankRefundReference'))=1) AND EXISTS(SELECT 1 FROM "
                + "sys.indexes i WHERE "
                + "i.object_id=OBJECT_ID('dbo.PaymentTransaction') AND "
                + "i.name='UQ_PaymentTransaction_BankReference' AND "
                + "i.is_unique=1 AND i.is_disabled=0 AND i.has_filter=0 AND "
                + "(SELECT COUNT(*) FROM sys.index_columns c WHERE "
                + "c.object_id=i.object_id AND c.index_id=i.index_id AND "
                + "c.key_ordinal>0)=2 AND (SELECT COUNT(*) FROM "
                + "sys.index_columns c WHERE c.object_id=i.object_id AND "
                + "c.index_id=i.index_id AND c.key_ordinal>0 AND "
                + "COL_NAME(c.object_id, c.column_id) IN "
                + "('RecipientAccount', 'BankTransactionReference'))=2) AND "
                + "EXISTS(SELECT 1 FROM sys.foreign_keys f WHERE "
                + "f.parent_object_id=OBJECT_ID('dbo.PaymentRequest') AND "
                + "f.referenced_object_id=OBJECT_ID("
                + "'dbo.QuotePaymentSchedule') "
                + "AND f.name='FK_Figma_PaymentRequest_ScheduleID' AND "
                + "f.is_disabled=0 AND f.is_not_trusted=0 AND (SELECT "
                + "COUNT(*) FROM sys.foreign_key_columns c WHERE "
                + "c.constraint_object_id=f.object_id)=2 AND (SELECT "
                + "COUNT(*) FROM sys.foreign_key_columns c WHERE "
                + "c.constraint_object_id=f.object_id AND "
                + "COL_NAME(c.parent_object_id, c.parent_column_id) = "
                + "COL_NAME(c.referenced_object_id, c.referenced_column_id) "
                + "AND COL_NAME(c.parent_object_id, c.parent_column_id) IN "
                + "('ScheduleID', 'QuoteID'))=2) AND EXISTS(SELECT 1 FROM "
                + "sys.check_constraints WHERE "
                + "parent_object_id=OBJECT_ID('dbo.PaymentProof') AND "
                + "name='CK_PaymentProof_Size' AND is_disabled=0 AND "
                + "is_not_trusted=0) AND EXISTS(SELECT 1 FROM "
                + "sys.check_constraints WHERE "
                + "parent_object_id=OBJECT_ID('dbo.PaymentProof') AND "
                + "name='CK_PaymentProof_Mime' AND is_disabled=0 AND "
                + "is_not_trusted=0) THEN 1 ELSE 0 END";

        return Integer.valueOf(1).equals(jdbc.getJdbcTemplate()
                .queryForObject(sql, Integer.class));
    }

    /** Serializes cross-order allocation of the same bank transaction. */
    public void lockLedger() {
        long result = jdbc.getJdbcTemplate().queryForObject("DECLARE @r "
                    + "INT; EXEC @r=sys.sp_getapplock"
                + " @Resource='EquiTransit:payment-ledger',"
                + "@LockMode='Exclusive',@LockOwner='Transaction',"
                + "@LockTimeout=5000; SELECT @r", Long.class);

        if (result < 0) {
            throw new OrderException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_LEDGER_BUSY", "Retry the same operation later.");
        }
    }

    /**
     * @param sql server-owned parameterized statement
     * @param args bound parameters
     * @return selected internal rows
     */
    public List<Map<String, Object>> rows(String sql, Map<String, ?> args) {
        return jdbc.queryForList(statement(sql), args);
    }

    /**
     * @param sql server-owned scalar statement
     * @param args bound parameters
     * @return exact amount, zero for an empty aggregate
     */
    public BigDecimal amount(String sql, Map<String, ?> args) {
        var value = jdbc.queryForObject(statement(sql), args, BigDecimal.class);

        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * @param sql server-owned scalar statement
     * @param args bound parameters
     * @return scalar identity or count
     */
    public long number(String sql, Map<String, ?> args) {
        Long value = jdbc.queryForObject(statement(sql), args, Long.class);

        return value == null ? 0 : value;
    }

    /**
     * @param sql server-owned statement
     * @param args bound parameters
     * @return affected row count
     */
    public int update(String sql, Map<String, ?> args) {
        return jdbc.update(statement(sql), args);
    }
    private String statement(String key) {
        return switch (key) {
            case "quote" ->
                    "SELECT q.*,r.OrderID FROM dbo.Quote q WITH "
                    + "(UPDLOCK,HOLDLOCK) JOIN dbo.OrderRevision r ON "
                    + "r.RevisionID=q.RevisionID WHERE r.OrderID=:order "
                    + "AND q.QuoteStatus='ACCEPTED'";
            case "schedule" ->
                    "SELECT * FROM dbo.QuotePaymentSchedule WHERE "
                    + "QuoteID=:quote ORDER BY SequenceNo";
            case "request" ->
                    "SELECT p.* FROM dbo.PaymentRequest p JOIN "
                    + "dbo.Quote q ON q.QuoteID=p.QuoteID JOIN "
                    + "dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                    + "WHERE r.OrderID=:order AND p.PaymentRequestID=:id";
            case "requests" ->
                    "SELECT p.* FROM dbo.PaymentRequest p JOIN "
                    + "dbo.Quote q ON q.QuoteID=p.QuoteID JOIN "
                    + "dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                    + "WHERE r.OrderID=:order ORDER BY p.PaymentRequestID "
                    + "OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
            case "reports" ->
                    "SELECT x.* FROM dbo.PaymentReport x JOIN "
                    + "dbo.PaymentRequest p ON "
                    + "p.PaymentRequestID=x.PaymentRequestID JOIN "
                    + "dbo.Quote q ON q.QuoteID=p.QuoteID JOIN "
                    + "dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                    + "WHERE r.OrderID=:order ORDER BY x.ReportID OFFSET "
                    + ":offset ROWS FETCH NEXT :size ROWS ONLY";
            case "allocations" ->
                    "SELECT a.* FROM dbo.PaymentAllocation a JOIN "
                    + "dbo.PaymentRequest p ON "
                    + "p.PaymentRequestID=a.PaymentRequestID JOIN "
                    + "dbo.Quote q ON q.QuoteID=p.QuoteID JOIN "
                    + "dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                    + "WHERE r.OrderID=:order ORDER BY a.AllocationID "
                    + "OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
            case "allocation", "allocationForUpdate" ->
                    "SELECT a.* FROM dbo.PaymentAllocation a"
                    + ("allocationForUpdate".equals(key)
                            ? " WITH (UPDLOCK,HOLDLOCK)" : "")
                    + " JOIN "
                    + "dbo.PaymentRequest p ON "
                    + "p.PaymentRequestID=a.PaymentRequestID JOIN "
                    + "dbo.Quote q ON q.QuoteID=p.QuoteID JOIN "
                    + "dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                    + "WHERE r.OrderID=:order AND a.AllocationID=:id";
            case "refunds" ->
                    "SELECT f.* FROM dbo.Refund f JOIN "
                    + "dbo.PaymentAllocation a ON "
                    + "a.AllocationID=f.AllocationID JOIN "
                    + "dbo.PaymentRequest p ON "
                    + "p.PaymentRequestID=a.PaymentRequestID JOIN "
                    + "dbo.Quote q ON q.QuoteID=p.QuoteID JOIN "
                    + "dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                    + "WHERE r.OrderID=:order ORDER BY f.RefundID OFFSET "
                    + ":offset ROWS FETCH NEXT :size ROWS ONLY";
            case "refund" ->
                    "SELECT f.* FROM dbo.Refund f JOIN "
                    + "dbo.PaymentAllocation a ON "
                    + "a.AllocationID=f.AllocationID JOIN "
                    + "dbo.PaymentRequest p ON "
                    + "p.PaymentRequestID=a.PaymentRequestID JOIN "
                    + "dbo.Quote q ON q.QuoteID=p.QuoteID JOIN "
                    + "dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                    + "WHERE r.OrderID=:order AND f.RefundID=:id";
            case "bank" ->
                    "SELECT * FROM dbo.PaymentTransaction WITH (UPDLOCK,"
                    + "HOLDLOCK) WHERE TransactionID=:id";
            case "replay" ->
                    "SELECT RequestHash,ReceiptJSON FROM "
                    + "dbo.PaymentOperation WHERE OrderID=:order AND "
                    + "UserID=:user AND OperationID=:op";
            case "proof" ->
                    "SELECT FileKey,OrderID,UserID,MediaType FROM "
                    + "dbo.PaymentProof WHERE "
                    + "OrderID=:order AND FileKey=:key";
            case "newRequest" ->
                    "INSERT dbo.PaymentRequest (QuoteID,ScheduleID,"
                    + "PaymentNumber,PaymentStage,AmountDue,Currency,"
                    + "DueAt,Status) OUTPUT INSERTED.PaymentRequestID "
                    + "SELECT QuoteID,ScheduleID,CONCAT('PAY-',"
                    + "ScheduleID),PaymentStage,AmountDue,Currency,DueAt,"
                    + "'OPEN' FROM dbo.QuotePaymentSchedule WHERE "
                    + "ScheduleID=:schedule AND QuoteID=:quote AND "
                    + "Currency='USD' AND AmountDue>0";
            case "report" ->
                    "INSERT dbo.PaymentReport (PaymentRequestID,"
                    + "ReportedAmount,BankReference,ProofFileKey,"
                    + "TransferredAt,ReportedCurrency,Status) OUTPUT "
                    + "INSERTED.ReportID VALUES (:request,:amount,"
                    + ":reference,:proof,:at,:currency,'SUBMITTED')";
            case "receipt" ->
                    "INSERT dbo.PaymentTransaction (ReconciledBy,"
                    + "RecipientAccount,BankTransactionReference,"
                    + "AmountReceived,Currency,PostedAt,ReconciledAt,"
                    + "Status,VerificationSource,EvidenceFileKey,"
                    + "InternalNotes) OUTPUT INSERTED.TransactionID "
                    + "VALUES (:staff,:account,:reference,:amount,"
                    + ":currency,:at,DATEADD(second,DATEDIFF(second,"
                    + "'20000101',SYSUTCDATETIME()),CAST('20000101' AS "
                    + "datetime2)),'RECONCILED',:source,:proof,:notes)";
            case "allocate" ->
                    "INSERT dbo.PaymentAllocation (TransactionID,"
                    + "PaymentRequestID,AllocatedAmount) OUTPUT "
                    + "INSERTED.AllocationID VALUES (:transaction,"
                    + ":request,:amount)";
            case "refundRequest" ->
                    "INSERT dbo.Refund (AllocationID,Amount,Reason,"
                    + "Status,RequestedBy,RequestedAt) OUTPUT "
                    + "INSERTED.RefundID VALUES (:allocation,:amount,"
                    + ":reason,'REQUESTED',:user,SYSUTCDATETIME())";
            case "proofBytes" ->
                    "SELECT ContentBytes FROM dbo.PaymentProof WHERE "
                    + "OrderID=:order AND FileKey=:key";
            case "proofInsert" ->
                    "INSERT dbo.PaymentProof (FileKey,OrderID,UserID,"
                    + "MediaType,ContentBytes) VALUES (:key,:order,:user,"
                    + ":mime,:bytes)";
            case "operation" ->
                    "INSERT dbo.PaymentOperation (OrderID,UserID,"
                    + "OperationID,RequestHash,ReceiptJSON) VALUES "
                    + "(:order,:user,:op,:hash,:receipt)";
            case "audit" ->
                    "INSERT dbo.AuditEvent (UserID,EntityType,EntityID,"
                    + "ActionCode,ChangeSnapshot) VALUES (:user,"
                    + "'ORDER_PAYMENT',:order,:action,:receipt)";
            case "bump" ->
                    "UPDATE dbo.TransportOrder SET "
                    + "OrderStatus=OrderStatus WHERE OrderID=:order";
            case "refundApprove" ->
                    "UPDATE dbo.Refund SET Status='APPROVED',"
                    + "ApprovedBy=:staff,ApprovedAt=DATEADD(second,"
                    + "DATEDIFF(second,'20000101',SYSUTCDATETIME()),"
                    + "CAST('20000101' AS datetime2)) WHERE RefundID=:id";
            case "refundReject" ->
                    "UPDATE dbo.Refund SET Status='REJECTED',"
                    + "ApprovedBy=:staff,ApprovedAt=DATEADD(second,"
                    + "DATEDIFF(second,'20000101',SYSUTCDATETIME()),"
                    + "CAST('20000101' AS datetime2)) WHERE RefundID=:id";
            case "refundComplete" ->
                    "UPDATE dbo.Refund SET Status='COMPLETED',"
                    + "BankRefundReference=:reference,ProofFileKey=:proof,"
                    + "RefundedAt=:at WHERE RefundID=:id";
            case "reverse" ->
                    "UPDATE dbo.PaymentTransaction SET "
                    + "Status='REVERSED',ReversalReason=:reason,"
                    + "ReversedBy=:staff,ReversedAt=DATEADD(second,"
                    + "DATEDIFF(second,'20000101',SYSUTCDATETIME()),"
                    + "CAST('20000101' AS datetime2)),"
                    + "ReversalProofFileKey=:proof WHERE TransactionID=:id";
            case "rejectReport" ->
                    "UPDATE dbo.PaymentReport SET Status='REJECTED' "
                    + "WHERE Status='SUBMITTED' AND ReportID=:id AND "
                    + "PaymentRequestID IN (SELECT p.PaymentRequestID "
                    + "FROM dbo.PaymentRequest p JOIN dbo.Quote q ON "
                    + "q.QuoteID=p.QuoteID JOIN dbo.OrderRevision r ON "
                    + "r.RevisionID=q.RevisionID WHERE r.OrderID=:order)";
            case "bankDuplicate" ->
                    "SELECT COUNT(*) FROM dbo.PaymentTransaction WITH "
                    + "(UPDLOCK,HOLDLOCK) WHERE RecipientAccount=:account "
                    + "AND BankTransactionReference=:reference";
            case "requestDuplicate" ->
                    "SELECT COUNT(*) FROM dbo.PaymentRequest WHERE "
                    + "ScheduleID=:schedule";
            case "refundDuplicate" ->
                    "SELECT COUNT(*) FROM dbo.Refund WHERE "
                    + "BankRefundReference=:reference";
            case "usedBank" ->
                    "SELECT COALESCE(SUM(AllocatedAmount),0) FROM "
                    + "dbo.PaymentAllocation WHERE TransactionID=:id";
            case "usedRequest" ->
                    "SELECT "
                    + "COALESCE(SUM(a.AllocatedAmount-COALESCE(f.Refunded,"
                    + "0)),0) FROM dbo.PaymentAllocation a JOIN "
                    + "dbo.PaymentTransaction t ON "
                    + "t.TransactionID=a.TransactionID OUTER APPLY "
                    + "(SELECT SUM(Amount) Refunded FROM dbo.Refund WHERE "
                    + "AllocationID=a.AllocationID AND "
                    + "Status='COMPLETED') f WHERE a.PaymentRequestID=:id "
                    + "AND t.Status='RECONCILED' AND t.ReconciledAt IS "
                    + "NOT NULL";
            case "refundReserved" ->
                    "SELECT COALESCE(SUM(Amount),0) FROM dbo.Refund "
                    + "WHERE AllocationID=:id AND Status IN ('REQUESTED',"
                    + "'APPROVED','COMPLETED')";
            case "bankRefunded" ->
                    "SELECT COALESCE(SUM(f.Amount),0) FROM dbo.Refund f "
                    + "JOIN dbo.PaymentAllocation a ON "
                    + "a.AllocationID=f.AllocationID WHERE "
                    + "a.TransactionID=:id AND f.Status IN ('REQUESTED',"
                    + "'APPROVED','COMPLETED')";
            case "bankBound" ->
                    "SELECT COUNT(*) FROM dbo.PaymentTransaction t "
                    + "WHERE t.TransactionID=:id AND (EXISTS (SELECT 1 "
                    + "FROM dbo.PaymentProof pr WHERE CONVERT(varchar(36),"
                    + "pr.FileKey)=t.EvidenceFileKey AND "
                    + "pr.OrderID=:order) OR EXISTS (SELECT 1 FROM "
                    + "dbo.PaymentAllocation a JOIN dbo.PaymentRequest p "
                    + "ON p.PaymentRequestID=a.PaymentRequestID JOIN "
                    + "dbo.Quote q ON q.QuoteID=p.QuoteID JOIN "
                    + "dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                    + "WHERE a.TransactionID=t.TransactionID AND "
                    + "r.OrderID=:order))";
            case "balances" ->
                    "SELECT s.ScheduleID,s.AmountDue,s.Currency,s.DueAt,"
                    + "COALESCE(b.PaidAmount,0) PaidAmount FROM "
                    + "dbo.QuotePaymentSchedule s OUTER APPLY (SELECT "
                    + "SUM(a.AllocatedAmount-COALESCE(f.Refunded,0)) "
                    + "PaidAmount FROM dbo.PaymentAllocation a JOIN "
                    + "dbo.PaymentRequest p ON "
                    + "p.PaymentRequestID=a.PaymentRequestID JOIN "
                    + "dbo.PaymentTransaction t ON "
                    + "t.TransactionID=a.TransactionID OUTER APPLY "
                    + "(SELECT SUM(Amount) Refunded FROM dbo.Refund WHERE "
                    + "AllocationID=a.AllocationID AND Status='COMPLETED' "
                    + "AND RefundedAt<=:asOf) f WHERE "
                    + "p.ScheduleID=s.ScheduleID AND a.AllocatedAt<=:asOf AND "
                    + "(t.Status='RECONCILED' OR (t.Status='REVERSED' AND "
                    + "t.ReversedAt>:asOf)) AND t.PostedAt<=:asOf AND "
                    + "t.ReconciledAt<=:asOf AND t.Currency=s.Currency "
                    + "AND p.Currency=s.Currency) b WHERE "
                    + "s.QuoteID=:quote ORDER BY s.SequenceNo";
            case "pendingRefunds" ->
                    "SELECT COUNT(*) FROM dbo.Refund f JOIN "
                    + "dbo.PaymentAllocation a ON "
                    + "a.AllocationID=f.AllocationID JOIN "
                    + "dbo.PaymentRequest p ON "
                    + "p.PaymentRequestID=a.PaymentRequestID WHERE "
                    + "p.QuoteID=:quote AND f.Status IN ('REQUESTED',"
                    + "'APPROVED')";
            default -> throw new IllegalArgumentException("Unknown query.");
        };
    }
}
