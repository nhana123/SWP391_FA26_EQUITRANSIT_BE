/*
 * ActivityRepository
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.repository;


import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


/** Set-based scoped reads and insert-only audit/deduplicated projection. */
@Repository
public class ActivityRepository {

    private static final String SCOPE = "(:role='MANAGER' OR"
            + " (:role='CUSTOMER' AND o.CustomerID=:customer) OR"
            + " (:role IN ('SPECIALIST','COORDINATOR') AND EXISTS"
            + " (SELECT 1 FROM dbo.OrderAssignment x WHERE x.OrderID=o.OrderID"
            + " AND x.StaffID=:staff AND x.AssignmentRole=:role"
            + " AND x.EndedAt IS NULL)) OR (:role='DRIVER' AND EXISTS"
            + " (SELECT 1 FROM dbo.TripLeg l JOIN dbo.TripAssignment x"
            + " ON x.LegID=l.LegID WHERE l.PlanID=o.CurrentPlanID"
            + " AND x.StaffID=:staff AND x.RoleInTrip='DRIVER'"
            + " AND x.Status='ACTIVE' AND (x.AssignedFrom IS NULL OR"
            + " x.AssignedFrom<=SYSUTCDATETIME()) AND (x.AssignedTo IS NULL"
            + " OR x.AssignedTo>=SYSUTCDATETIME()))))";

    private final NamedParameterJdbcTemplate jdbc;

    /** @param source shared transactional connection pool */
    public ActivityRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return whether the F16 schema was explicitly deployed */
    public boolean ready() {
        String sql = "SELECT CASE WHEN (SELECT COUNT(*) FROM sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.AuditEvent') AND name IN "
                + "('EventKey'))=1 AND (SELECT COUNT(*) FROM sys.columns "
                + "WHERE object_id=OBJECT_ID('dbo.Notification') AND name "
                + "IN ('SourceEventID', 'EmailState', 'EmailLeaseUntil', "
                + "'EmailAttempts'))=4 AND "
                + "OBJECT_ID('dbo.NotificationProjection', 'U') IS NOT NULL "
                + "AND (SELECT COUNT(*) FROM sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.NotificationProjection') AND "
                + "name IN ('EventID', 'ProjectedAt'))=2 AND EXISTS(SELECT "
                + "1 FROM sys.indexes i WHERE "
                + "i.object_id=OBJECT_ID('dbo.AuditEvent') AND "
                + "i.name='UQ_AuditEvent_EventKey' AND i.is_unique=1 AND "
                + "i.is_disabled=0 AND i.has_filter=1 AND (SELECT COUNT(*) "
                + "FROM sys.index_columns c WHERE c.object_id=i.object_id "
                + "AND c.index_id=i.index_id AND c.key_ordinal>0)=1 AND "
                + "(SELECT COUNT(*) FROM sys.index_columns c WHERE "
                + "c.object_id=i.object_id AND c.index_id=i.index_id AND "
                + "c.key_ordinal>0 AND COL_NAME(c.object_id, c.column_id) "
                + "IN ('EventKey'))=1) AND EXISTS(SELECT 1 FROM sys.indexes "
                + "i WHERE i.object_id=OBJECT_ID('dbo.Notification') AND "
                + "i.name='UQ_Notification_EventRecipient' AND "
                + "i.is_unique=1 AND i.is_disabled=0 AND i.has_filter=1 AND "
                + "(SELECT COUNT(*) FROM sys.index_columns c WHERE "
                + "c.object_id=i.object_id AND c.index_id=i.index_id AND "
                + "c.key_ordinal>0)=2 AND (SELECT COUNT(*) FROM "
                + "sys.index_columns c WHERE c.object_id=i.object_id AND "
                + "c.index_id=i.index_id AND c.key_ordinal>0 AND "
                + "COL_NAME(c.object_id, c.column_id) IN ('SourceEventID', "
                + "'UserID'))=2) AND EXISTS(SELECT 1 FROM "
                + "sys.check_constraints WHERE "
                + "parent_object_id=OBJECT_ID('dbo.AuditEvent') AND "
                + "name='CK_AuditEvent_ChangeSnapshot_JSON' AND "
                + "is_disabled=0 AND is_not_trusted=0) THEN 1 ELSE 0 END";

        return Integer.valueOf(1).equals(jdbc.getJdbcTemplate()
                .queryForObject(sql, Integer.class));
    }

    /** @param resource bounded transaction lock key */
    public void lock(String resource) {
        Integer code = jdbc.queryForObject("DECLARE @r int;"
                + " EXEC @r=sys.sp_getapplock @Resource=:resource,"
                + "@LockMode='Exclusive',@LockOwner='Transaction',"
                + "@LockTimeout=3000; SELECT @r", Map.of("resource", resource),
                Integer.class);

        if (code == null || code < 0) {
            throw new IllegalStateException("Activity storage busy.");
        }
    }

    /**
     * @param key server-defined query identifier
     * @param args bound values including trusted access scope
     * @return internal rows, never directly exposed by controllers
     */
    public List<Map<String, Object>> rows(String key, Map<String, ?> args) {
        return jdbc.queryForList(statement(key).replace("{scope}", SCOPE),
                args);
    }

    /**
     * @param key server-owned scalar query
     * @param args bound values
     * @return exact amount/count/identity
     */
    public BigDecimal scalar(String key, Map<String, ?> args) {
        return jdbc.queryForObject(statement(key).replace("{scope}", SCOPE),
                args, BigDecimal.class);
    }

    /**
     * @param key server-owned write query
     * @param args bound values
     * @return rows affected
     */
    public int update(String key, Map<String, ?> args) {
        return jdbc.update(statement(key).replace("{scope}", SCOPE), args);
    }

    private String statement(String key) {
        return switch (key) {
            case "orders" ->
                    "SELECT o.OrderID,o.OrderNumber,o.OrderStatus FROM "
                    + "dbo.TransportOrder o WHERE {scope} ORDER BY "
                    + "o.OrderID DESC OFFSET :offset ROWS FETCH NEXT "
                    + ":size ROWS ONLY";
            case "orderCount" ->
                    "SELECT COUNT_BIG(*) FROM dbo.TransportOrder o "
                    + "WHERE {scope}";
            case "counts" ->
                    "SELECT o.OrderStatus,COUNT_BIG(*) Total FROM "
                    + "dbo.TransportOrder o WHERE {scope} GROUP BY "
                    + "o.OrderStatus";
            case "orderAccess" ->
                    "SELECT COUNT_BIG(*) FROM dbo.TransportOrder o "
                    + "WHERE o.OrderID=:order AND {scope}";
            case "notifications" ->
                    "SELECT n.NotificationID,n.OrderID,n.Title,"
                    + "n.Message,n.CreatedAt,n.ReadAt FROM "
                    + "dbo.Notification n LEFT JOIN dbo.TransportOrder o "
                    + "ON o.OrderID=n.OrderID WHERE n.UserID=:user AND "
                    + "(n.OrderID IS NULL OR ({scope})) ORDER BY "
                    + "n.NotificationID DESC OFFSET :offset ROWS FETCH "
                    + "NEXT :size ROWS ONLY";
            case "notificationCount" ->
                    "SELECT COUNT_BIG(*) FROM dbo.Notification n LEFT "
                    + "JOIN dbo.TransportOrder o ON o.OrderID=n.OrderID "
                    + "WHERE n.UserID=:user AND (n.OrderID IS NULL OR "
                    + "({scope}))";
            case "read" ->
                    "UPDATE n SET ReadAt=COALESCE(n.ReadAt,"
                    + "SYSUTCDATETIME()) FROM dbo.Notification n LEFT "
                    + "JOIN dbo.TransportOrder o ON o.OrderID=n.OrderID "
                    + "WHERE n.NotificationID=:id AND n.UserID=:user AND "
                    + "(n.OrderID IS NULL OR ({scope}))";
            case "notification" ->
                    "SELECT n.NotificationID,n.OrderID,n.Title,"
                    + "n.Message,n.CreatedAt,n.ReadAt FROM "
                    + "dbo.Notification n LEFT JOIN dbo.TransportOrder o "
                    + "ON o.OrderID=n.OrderID WHERE n.NotificationID=:id "
                    + "AND n.UserID=:user AND (n.OrderID IS NULL OR "
                    + "({scope}))";
            case "audits" ->
                    "SELECT EventID,UserID,EntityType,EntityID,"
                    + "ActionCode,ChangeSnapshot,OccurredAt FROM "
                    + "dbo.AuditEvent ORDER BY EventID DESC OFFSET "
                    + ":offset ROWS FETCH NEXT :size ROWS ONLY";
            case "auditCount" ->
                    "SELECT COUNT_BIG(*) FROM dbo.AuditEvent";
            case "auditKey" ->
                    "SELECT EventID FROM dbo.AuditEvent WITH (UPDLOCK,"
                    + "HOLDLOCK) WHERE EventKey=:key";
            case "auditAppend" ->
                    "INSERT dbo.AuditEvent (UserID,EntityType,EntityID,"
                    + "ActionCode,ChangeSnapshot,EventKey) OUTPUT "
                    + "INSERTED.EventID VALUES (:user,'ORDER_ACTIVITY',"
                    + ":order,:code,:snapshot,:key)";
            case "staffAuditAppend" ->
                    "INSERT dbo.AuditEvent (UserID,EntityType,EntityID,"
                    + "ActionCode,ChangeSnapshot,EventKey) OUTPUT "
                    + "INSERTED.EventID VALUES (:user,'STAFF_PROFILE',"
                    + ":staff,:code,:snapshot,:key)";
            case "preference" ->
                    "SELECT EmailNotificationsEnabled FROM dbo.Customer "
                    + "WHERE UserID=:user";
            case "preferencePut" ->
                    "UPDATE dbo.Customer SET "
                    + "EmailNotificationsEnabled=:enabled WHERE "
                    + "UserID=:user";
            case "project" ->
                    "DECLARE @ids TABLE(EventID bigint PRIMARY KEY); "
                    + "INSERT @ids SELECT TOP (100) a.EventID FROM "
                    + "dbo.AuditEvent a WHERE "
                    + "a.EntityType IN ('ORDER_ACTIVITY',"
                    + "'ORDER_ASSESSMENT','ORDER_QUOTATION',"
                    + "'ORDER_PAYMENT','SERVICE_SELECTION') AND NOT "
                    + "EXISTS(SELECT 1 FROM dbo.NotificationProjection p "
                    + "WHERE p.EventID=a.EventID) ORDER BY a.EventID; "
                    + "WITH recipients AS (SELECT a.EventID,a.EntityID "
                    + "OrderID,c.UserID FROM @ids i JOIN dbo.AuditEvent a "
                    + "ON a.EventID=i.EventID JOIN dbo.TransportOrder o "
                    + "ON o.OrderID=a.EntityID JOIN dbo.Customer c ON "
                    + "c.CustomerID=o.CustomerID UNION SELECT a.EventID,"
                    + "a.EntityID,s.UserID FROM @ids i JOIN "
                    + "dbo.AuditEvent a ON a.EventID=i.EventID JOIN "
                    + "dbo.OrderAssignment x ON x.OrderID=a.EntityID AND "
                    + "x.EndedAt IS NULL JOIN dbo.StaffProfile s ON "
                    + "s.StaffID=x.StaffID UNION SELECT a.EventID,"
                    + "a.EntityID,s.UserID FROM @ids i JOIN "
                    + "dbo.AuditEvent a ON a.EventID=i.EventID JOIN "
                    + "dbo.TransportOrder o ON o.OrderID=a.EntityID JOIN "
                    + "dbo.TripLeg l ON l.PlanID=o.CurrentPlanID JOIN "
                    + "dbo.TripAssignment x ON x.LegID=l.LegID AND "
                    + "x.Status='ACTIVE' AND x.RoleInTrip='DRIVER' AND "
                    + "(x.AssignedFrom IS NULL OR "
                    + "x.AssignedFrom<=SYSUTCDATETIME()) AND "
                    + "(x.AssignedTo IS NULL OR "
                    + "x.AssignedTo>=SYSUTCDATETIME()) JOIN "
                    + "dbo.StaffProfile s ON s.StaffID=x.StaffID) INSERT "
                    + "dbo.Notification (UserID,OrderID,Title,Message,"
                    + "SourceEventID,EmailState) SELECT r.UserID,"
                    + "r.OrderID,N'Order update',N'Open EquiTransit to "
                    + "view your order update.',r.EventID,CASE WHEN "
                    + "c.EmailNotificationsEnabled=1 THEN 'PENDING' ELSE "
                    + "'SUPPRESSED' END FROM recipients r JOIN dbo.[User] "
                    + "u ON u.UserID=r.UserID AND u.Status='ACTIVE' LEFT "
                    + "JOIN dbo.Customer c ON c.UserID=r.UserID WHERE NOT "
                    + "EXISTS(SELECT 1 FROM dbo.Notification n WHERE "
                    + "n.SourceEventID=r.EventID AND n.UserID=r.UserID); "
                    + "INSERT dbo.NotificationProjection(EventID) SELECT "
                    + "EventID FROM @ids;";
            case "claimMail" ->
                    "UPDATE TOP (20) dbo.Notification WITH (UPDLOCK,"
                    + "READPAST) SET EmailState='SENDING',"
                    + "EmailLeaseUntil=DATEADD(minute,5,SYSUTCDATETIME()),"
                    + "EmailAttempts=EmailAttempts+1 OUTPUT "
                    + "INSERTED.NotificationID WHERE EmailAttempts<10 AND "
                    + "(EmailState='PENDING' OR (EmailState='SENDING' AND "
                    + "EmailLeaseUntil<SYSUTCDATETIME()))";
            case "mail" ->
                    "SELECT n.NotificationID,u.Email FROM "
                    + "dbo.Notification n JOIN dbo.[User] u ON "
                    + "u.UserID=n.UserID AND u.Status='ACTIVE' JOIN "
                    + "dbo.Customer c ON c.UserID=u.UserID AND "
                    + "c.EmailNotificationsEnabled=1 JOIN "
                    + "dbo.TransportOrder o ON o.OrderID=n.OrderID AND "
                    + "o.CustomerID=c.CustomerID WHERE "
                    + "n.NotificationID=:id AND n.EmailState='SENDING'";
            case "mailDone" ->
                    "UPDATE dbo.Notification SET EmailState=:state "
                    + "WHERE NotificationID=:id AND EmailState='SENDING'";
            case "mailFail" ->
                    "UPDATE dbo.Notification SET "
                    + "EmailLeaseUntil=DATEADD(minute,5,SYSUTCDATETIME()) "
                    + "WHERE NotificationID=:id AND EmailState='SENDING'";
            case "milestones" ->
                    "SELECT Code,OccurredAt FROM (SELECT 'SUBMITTED' "
                    + "Code,SubmittedAt OccurredAt FROM dbo.OrderRevision "
                    + "WHERE OrderID=:order UNION ALL SELECT 'ACCEPTED',"
                    + "q.AcceptedAt FROM dbo.Quote q JOIN "
                    + "dbo.OrderRevision r ON r.RevisionID=q.RevisionID "
                    + "WHERE r.OrderID=:order AND q.AcceptedAt IS NOT "
                    + "NULL UNION ALL SELECT ActionCode,OccurredAt FROM "
                    + "dbo.AuditEvent WHERE EntityType='ORDER_ACTIVITY' "
                    + "AND EntityID=:order AND ActionCode IN ('CLEARED',"
                    + "'DEPARTED','DELIVERED','CLOSED') UNION ALL SELECT "
                    + "'PAID',MAX(a.AllocatedAt) FROM dbo.TransportOrder "
                    + "o JOIN dbo.Quote q ON "
                    + "q.RevisionID=o.CurrentRevisionID AND "
                    + "q.QuoteStatus='ACCEPTED' JOIN dbo.PaymentRequest p "
                    + "ON p.QuoteID=q.QuoteID JOIN dbo.PaymentAllocation "
                    + "a ON a.PaymentRequestID=p.PaymentRequestID JOIN "
                    + "dbo.PaymentTransaction t ON "
                    + "t.TransactionID=a.TransactionID OUTER APPLY "
                    + "(SELECT SUM(Amount) Amount FROM dbo.Refund f WHERE "
                    + "f.AllocationID=a.AllocationID AND "
                    + "f.Status='COMPLETED') f WHERE o.OrderID=:order AND "
                    + "q.Currency='USD' AND t.Currency='USD' AND "
                    + "t.Status='RECONCILED' AND t.ReconciledAt IS NOT "
                    + "NULL HAVING "
                    + "SUM(a.AllocatedAmount-COALESCE(f.Amount,"
                    + "0))>=MAX(q.TotalQuotedAmount) AND "
                    + "MAX(q.TotalQuotedAmount)>0) facts ORDER BY "
                    + "OccurredAt,Code OFFSET :offset ROWS FETCH NEXT "
                    + ":size ROWS ONLY";
            case "financialReport" ->
                    "SELECT (SELECT COALESCE(SUM(q.TotalQuotedAmount),"
                    + "0) FROM dbo.TransportOrder o CROSS APPLY (SELECT "
                    + "TOP (1) TotalQuotedAmount FROM dbo.Quote q WHERE "
                    + "q.RevisionID=o.CurrentRevisionID AND "
                    + "q.Currency='USD' AND q.QuoteStatus IN ('ACCEPTED',"
                    + "'ISSUED') ORDER BY VersionNo DESC) q) Quoted,"
                    + "(SELECT COALESCE(SUM(ReportedAmount),0) FROM "
                    + "dbo.PaymentReport WHERE ReportedCurrency='USD' AND "
                    + "Status='SUBMITTED') Reported,(SELECT "
                    + "COALESCE(SUM(t.AmountReceived-COALESCE(f.Amount,"
                    + "0)),0) FROM dbo.PaymentTransaction t OUTER APPLY "
                    + "(SELECT SUM(r.Amount) Amount FROM dbo.Refund r "
                    + "JOIN dbo.PaymentAllocation a ON "
                    + "a.AllocationID=r.AllocationID WHERE "
                    + "a.TransactionID=t.TransactionID AND "
                    + "r.Status='COMPLETED') f WHERE t.Currency='USD' AND "
                    + "t.Status='RECONCILED' AND t.ReconciledAt IS NOT "
                    + "NULL) BankNet,(SELECT "
                    + "COALESCE(SUM(a.AllocatedAmount-COALESCE(f.Amount,"
                    + "0)),0) FROM dbo.PaymentAllocation a JOIN "
                    + "dbo.PaymentTransaction t ON "
                    + "t.TransactionID=a.TransactionID OUTER APPLY "
                    + "(SELECT SUM(Amount) Amount FROM dbo.Refund WHERE "
                    + "AllocationID=a.AllocationID AND "
                    + "Status='COMPLETED') f WHERE t.Currency='USD' AND "
                    + "t.Status='RECONCILED' AND t.ReconciledAt IS NOT "
                    + "NULL) AllocatedNet,(SELECT COALESCE(SUM(ac.Amount),0) "
                    + "FROM dbo.ActualCost ac JOIN dbo.TransportPlan p ON "
                    + "p.PlanID=ac.PlanID JOIN dbo.TransportOrder o ON "
                    + "o.OrderID=p.OrderID AND o.CurrentPlanID=p.PlanID "
                    + "WHERE ac.Currency='USD') "
                    + "ActualCost,(SELECT COUNT_BIG(*) FROM "
                    + "dbo.TransportOrder WHERE OrderStatus IN "
                    + "('DELIVERED','COMPLETED','CLOSED')) "
                    + "OperationalCount,(SELECT COUNT_BIG(*) FROM "
                    + "dbo.TransportOrder WHERE OrderStatus='CLOSED') "
                    + "ClosedCount";
            case "tasks" ->
                    "SELECT * FROM (SELECT x.AssignmentID,x.OrderID,"
                    + "CAST(NULL AS bigint) LegID,x.AssignmentRole "
                    + "RoleCode,x.DueAt FROM dbo.OrderAssignment x WHERE "
                    + ":role IN ('SPECIALIST','COORDINATOR') AND "
                    + "x.StaffID=:staff AND x.AssignmentRole=:role AND "
                    + "x.EndedAt IS NULL UNION ALL SELECT x.AssignmentID,"
                    + "p.OrderID,l.LegID,'DRIVER',x.AssignedTo FROM "
                    + "dbo.TripAssignment x JOIN dbo.TripLeg l ON "
                    + "l.LegID=x.LegID JOIN dbo.TransportPlan p ON "
                    + "p.PlanID=l.PlanID JOIN dbo.TransportOrder o ON "
                    + "o.CurrentPlanID=p.PlanID WHERE :role='DRIVER' AND "
                    + "x.StaffID=:staff AND x.RoleInTrip='DRIVER' AND "
                    + "x.Status='ACTIVE' AND (x.AssignedFrom IS NULL OR "
                    + "x.AssignedFrom<=SYSUTCDATETIME()) AND "
                    + "(x.AssignedTo IS NULL OR "
                    + "x.AssignedTo>=SYSUTCDATETIME())) t ORDER BY "
                    + "OrderID,AssignmentID OFFSET :offset ROWS FETCH "
                    + "NEXT :size ROWS ONLY";
            case "taskCount" ->
                    "SELECT COUNT_BIG(*) FROM (SELECT x.AssignmentID,"
                    + "x.OrderID,CAST(NULL AS bigint) LegID,"
                    + "x.AssignmentRole RoleCode,x.DueAt FROM "
                    + "dbo.OrderAssignment x WHERE :role IN ('SPECIALIST',"
                    + "'COORDINATOR') AND x.StaffID=:staff AND "
                    + "x.AssignmentRole=:role AND x.EndedAt IS NULL UNION "
                    + "ALL SELECT x.AssignmentID,p.OrderID,l.LegID,"
                    + "'DRIVER',x.AssignedTo FROM dbo.TripAssignment x "
                    + "JOIN dbo.TripLeg l ON l.LegID=x.LegID JOIN "
                    + "dbo.TransportPlan p ON p.PlanID=l.PlanID JOIN "
                    + "dbo.TransportOrder o ON o.CurrentPlanID=p.PlanID "
                    + "WHERE :role='DRIVER' AND x.StaffID=:staff AND "
                    + "x.RoleInTrip='DRIVER' AND x.Status='ACTIVE' AND "
                    + "(x.AssignedFrom IS NULL OR "
                    + "x.AssignedFrom<=SYSUTCDATETIME()) AND "
                    + "(x.AssignedTo IS NULL OR "
                    + "x.AssignedTo>=SYSUTCDATETIME())) t";
            default -> throw new IllegalArgumentException("Unknown query");
        };
    }
}
