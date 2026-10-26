/*
 * ActivityIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify scoped activity on the isolated SQL test catalog.
 */
package com.equitransit.backend;


import com.equitransit.backend.dto.common.ApiExamples;
import com.equitransit.backend.entity.customer.Customer;
import com.equitransit.backend.entity.auth.UserAccount;
import com.equitransit.backend.repository.auth.AccountRoleRepository;
import com.equitransit.backend.repository.customer.CustomerRepository;
import com.equitransit.backend.repository.auth.UserAccountRepository;
import com.equitransit.backend.repository.activity.ActivityRepository;
import com.equitransit.backend.service.activity.AuditWriter;
import com.equitransit.backend.service.activity.NotificationWriter;
import com.equitransit.backend.security.LiveJwtConverter;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;


import static org.assertj.core.api.Assertions.assertThat;


/** Real SQL fixtures stay in the exact opt-in test database and are removed. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_ACTIVITY_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_ACTIVITY_TEST_ENABLED",
        matches = "true")
@SuppressWarnings("null")
public class ActivityIntegrationTest {

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private ActivityRepository activity;

    @Autowired
    private AuditWriter auditWriter;
    @Autowired
    private NotificationWriter notifications;
    @Autowired
    private org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @Autowired
    private LiveJwtConverter liveTokens;

    private long acceptedQuote;
    private long requestId;
    private String proof;

    private final List<Long> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureTariffs = new ArrayList<>();
    private long order;
    private long revision;
    private String owner;
    private String other;
    private String manager;
    private String coordinator;
    private String specialist;
    private long coordinatorId;
    private long specialistId;
    private long managerId;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private AccountRoleRepository roles;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private JwtTokenService tokens;

    /**
     * Guard executes before the first fixture write.
     * @throws Exception if setup fails
     */
    @BeforeEach
    public void setup() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo(
                    "EquiTransit_FigmaMigrationTest_20261013_143729");
        }
        owner = account("CUSTOMER");
        other = account("CUSTOMER");
        manager = account("MANAGER");
        managerId = lastStaff();
        coordinator = account("COORDINATOR");
        coordinatorId = lastStaff();
        specialist = account("SPECIALIST");
        specialistId = lastStaff();
        JsonNode horse = post("/api/horses", owner,
                mapper.readTree("{\"name\":\"Quotation fixture\"}"), 201);
        ObjectNode draft = (ObjectNode) mapper.readTree(ApiExamples.DRAFT);

        draft.put("horseId", horse.get("horseId").asLong());
        draft.put("preferredDepartureAt", time(10));
        draft.put("deliveryDeadline", time(15));
        draft.put("eventStartAt", time(20));
        draft.set("services", mapper.readTree("{\"roadArrangement\":"
                + "\"EQUITRANSIT\",\"veterinaryArrangement\":"
                + "\"EQUITRANSIT\",\"insurance\":\"NONE\"}"));
        JsonNode saved = post("/api/transport-orders", owner, draft, 201);

        order = saved.get("orderId").asLong();
        post(base() + "/submit", owner, mapper.valueToTree(Map.of(
                "recordVersion", saved.get("recordVersion").asText(),
                "operationId", UUID.randomUUID())), 200);
        JsonNode current = get("/api/manager/transport-orders/" + order,
                manager, 200);

        revision = current.get("currentRevisionId").asLong();
        post("/api/manager/transport-orders/" + order + "/decisions", manager,
                mapper.valueToTree(Map.of("revisionId", revision,
                        "recordVersion", current.get("recordVersion").asText(),
                        "operationId", UUID.randomUUID(), "decision",
                        "APPROVED", "specialistId", specialistId,
                        "coordinatorId", coordinatorId)), 200);
        sources();
    }

    /** Only IDs created by this test are removed, including committed races. */
    @AfterEach
    public void cleanup() {
        if (order != 0) {
            String rev = " IN (SELECT RevisionID FROM dbo.OrderRevision"
                    + " WHERE OrderID=?)";
            String quote = " IN (SELECT QuoteID FROM dbo.Quote WHERE"
                    + " RevisionID" + rev + ")";

            jdbc.update("DELETE dbo.Notification WHERE OrderID=?", order);
            jdbc.update("DELETE dbo.NotificationProjection WHERE EventID IN"
                    + " (SELECT EventID FROM dbo.AuditEvent WHERE EntityID=?)",
                    order);
            jdbc.update("DELETE dbo.AuditEvent WHERE EntityID=? AND"
                    + " EntityType IN ('ORDER_QUOTATION','ORDER_ASSESSMENT',"
                    + "'SERVICE_SELECTION')", order);
            jdbc.update("DELETE dbo.PaymentOperation WHERE OrderID=?", order);
            jdbc.update("DELETE dbo.AuditEvent WHERE EntityType="
                    + "'ORDER_PAYMENT' AND EntityID=?", order);
            String payment = " IN (SELECT p.PaymentRequestID FROM"
                    + " dbo.PaymentRequest p JOIN dbo.Quote q ON"
                    + " q.QuoteID=p.QuoteID JOIN dbo.OrderRevision r ON"
                    + " r.RevisionID=q.RevisionID WHERE r.OrderID=?)";

            jdbc.update("DELETE dbo.Refund WHERE AllocationID IN (SELECT"
                    + " AllocationID FROM dbo.PaymentAllocation WHERE"
                    + " PaymentRequestID" + payment + ")", order);
            var bankIds = jdbc.queryForList("SELECT DISTINCT TransactionID"
                    + " FROM dbo.PaymentAllocation WHERE PaymentRequestID"
                    + payment, Long.class, order);

            jdbc.update("DELETE dbo.PaymentAllocation WHERE PaymentRequestID"
                    + payment, order);
            jdbc.update("DELETE dbo.PaymentReport WHERE PaymentRequestID"
                    + payment, order);
            jdbc.update("DELETE dbo.PaymentRequest WHERE QuoteID" + quote,
                    order);
            for (long bankId : bankIds) {
                jdbc.update("DELETE dbo.PaymentTransaction WHERE"
                        + " TransactionID=?", bankId);
            }
            jdbc.update("DELETE dbo.PaymentProof WHERE OrderID=?", order);
            jdbc.update("DELETE dbo.ActualCost WHERE PlanID IN"
                    + " (SELECT PlanID FROM dbo.TransportPlan WHERE OrderID=?)",
                    order);
            jdbc.update("DELETE dbo.TripAssignment WHERE LegID IN (SELECT"
                    + " l.LegID FROM dbo.TripLeg l JOIN dbo.TransportPlan p ON"
                    + " p.PlanID=l.PlanID WHERE p.OrderID=?)", order);
            jdbc.update("DELETE dbo.QuoteItem WHERE QuoteID" + quote, order);
            jdbc.update("DELETE dbo.QuotePaymentSchedule WHERE QuoteID"
                    + quote, order);
            jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=NULL,"
                    + "CurrentRevisionID=NULL WHERE OrderID=?", order);
            jdbc.update("DELETE dbo.TripLeg WHERE PlanID IN"
                    + " (SELECT PlanID FROM dbo.TransportPlan WHERE OrderID=?)",
                    order);
            jdbc.update("DELETE dbo.TransportPlan WHERE OrderID=?", order);
            jdbc.update("DELETE dbo.Quote WHERE RevisionID" + rev, order);
            jdbc.update("DELETE dbo.SupplierOffer WHERE RevisionID" + rev,
                    order);
            jdbc.update("DELETE dbo.RevisionVeterinaryAssessment WHERE"
                    + " RevisionID" + rev, order);
            jdbc.update("DELETE dbo.RevisionQuarantineAssessment WHERE"
                    + " RevisionID" + rev, order);
            jdbc.update("DELETE dbo.OrderAssignment WHERE OrderID=?", order);
            jdbc.update("DELETE dbo.OrderDecision WHERE RevisionID" + rev,
                    order);
            for (String table : List.of("StallChoice", "QuarantineChoice",
                    "InsuranceChoice")) {
                jdbc.update("DELETE dbo." + table + " WHERE ChoiceID IN"
                        + " (SELECT ChoiceID FROM dbo.OrderServiceChoice"
                        + " WHERE RevisionID" + rev + ")", order);
            }
            jdbc.update("DELETE dbo.OrderServiceChoice WHERE RevisionID"
                    + rev, order);
            jdbc.update("DELETE dbo.TransportOrderDetail WHERE OrderID=?",
                    order);
            jdbc.update("DELETE dbo.OrderRevision WHERE OrderID=?", order);
            jdbc.update("DELETE dbo.TransportOrder WHERE OrderID=?", order);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM"
                    + " dbo.TransportOrder WHERE OrderID=?", Integer.class,
                    order)).isZero();
        }
        for (long id : fixtureTariffs) {
            jdbc.update("DELETE dbo.ServiceTariff WHERE TariffID=?", id);
        }
        for (long user : fixtureUsers) {
            jdbc.update("DELETE dbo.AuditEvent WHERE UserID=?"
                    + " AND EntityType='ORDER_ACTIVITY'", user);
            jdbc.update("DELETE dbo.PaymentTransaction WHERE ReconciledBy"
                    + " IN (SELECT StaffID FROM dbo.StaffProfile WHERE"
                    + " UserID=?)", user);

            jdbc.update("DELETE dbo.AuditEvent WHERE UserID=?"
                    + " AND EntityType='SERVICE_TARIFF'", user);
            jdbc.update("DELETE dbo.Horse WHERE CustomerID IN"
                    + " (SELECT CustomerID FROM dbo.Customer WHERE UserID=?)",
                    user);
            jdbc.update("DELETE dbo.Customer WHERE UserID=?", user);
            jdbc.update("DELETE dbo.StaffProfile WHERE UserID=?", user);
            jdbc.update("DELETE dbo.[User] WHERE UserID=?", user);
        }
    }


    /**
     * A catalog write rolled back by its caller leaves no rate or receipt.
     * @throws Exception if API calls fail
     */


    private long issued() throws Exception {
        long id = post(base() + "/quotes", manager, prepare(), 200)
                .at("/quote/quoteId").asLong();

        action(id, "APPROVE", manager, 200);
        action(id, "ISSUE", manager, 200);
        return id;
    }

    private JsonNode action(long id, String action, String token, int expected)
            throws Exception {
        return post(base() + "/quotes/" + id + "/actions", token,
                actionBody(action), expected);
    }

    private ObjectNode actionBody(String action) {
        ObjectNode body = mapper.createObjectNode();

        body.set("command", command());
        body.put("action", action);
        return body;
    }

    private ObjectNode command() {
        ObjectNode command = mapper.createObjectNode();

        byte[] version = jdbc.queryForObject("SELECT RecordVersion"
                + " FROM dbo.TransportOrder WHERE OrderID=?",
                byte[].class, order);

        command.put("recordVersion", java.util.HexFormat.of()
                .formatHex(version));
        command.put("operationId", UUID.randomUUID().toString());
        return command;
    }

    private ObjectNode prepare() {
        ObjectNode body = mapper.createObjectNode();

        body.set("command", command());
        body.put("revisionId", revision);
        body.put("validUntil", time(2));
        body.put("termsVersion", "TEST-ONLY-TERMS");
        body.put("terms", "Artificial test payment terms.");
        body.put("cancellationTerms", "Artificial cancellation terms.");
        body.set("paymentSchedule", mapper.valueToTree(List.of(Map.of(
                "sequenceNo", 1, "paymentStage", "FULL", "amountDue", "260.00",
                "dueAt", time(3)))));
        body.set("assessments", mapper.valueToTree(List.of(
                evidence("AIRPORT_HANDLING"), evidence("TERMINAL_HANDLING"),
                evidence("CUSTOMS_DECLARATION"), evidence("GOVERNMENT_DUTY"))));
        return body;
    }

    private Map<String, Object> evidence(String code) {
        return Map.of("code", code, "status", "NOT_REQUIRED", "validUntil",
                time(4), "sourceReference", "Artificial test assessment",
                "reason", "Not applicable in this artificial fixture.");
    }

    private void sources() {
        long plan = jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids TABLE"
                + " (ID BIGINT); INSERT dbo.TransportPlan (OrderID,"
                + "RevisionID,CreatedBy,VersionNo,FinancialGateStatus,"
                + "OperationalGateStatus,DocGateStatus,PlanStatus)"
                + " OUTPUT INSERTED.PlanID INTO @ids"
                + " VALUES (?,?,?,1,'PENDING',"
                + "'PENDING','PENDING','DRAFT'); SELECT ID FROM @ids",
                Long.class,
                order, revision, coordinatorId);

        jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=?"
                + " WHERE OrderID=?", plan, order);
        jdbc.update("INSERT dbo.TripLeg (PlanID,SequenceNo,TransportMode,"
                + "StartPoint,EndPoint,DistanceKm,LegStatus,DistanceSource,"
                + "DistanceConfirmedBy,DistanceConfirmedAt) VALUES"
                + " (?,1,'ROAD','Fixture A','Fixture B',100,'PLANNED',"
                + "'Artificial sourced distance',?,DATEADD(second,-1,"
                + "SYSUTCDATETIME()))", plan, coordinatorId);
        jdbc.update("INSERT dbo.RevisionQuarantineAssessment (RevisionID,"
                + "Applicability,SpecialistID,AssessedAt,ValidUntil,"
                + "SourceReference) VALUES (?,'NOT_REQUIRED',?,"
                + "DATEADD(second,-1,SYSUTCDATETIME()),DATEADD(day,40,"
                + "SYSUTCDATETIME()),'Artificial assessment')",
                revision, specialistId);
        jdbc.update("INSERT dbo.RevisionVeterinaryAssessment (RevisionID,"
                + "VisitCount,TestCount,DoseCount,CertificateCount,"
                + "SpecialistID,"
                + "AssessedAt,ValidUntil,SourceReference) VALUES (?,1,0,0,0,?,"
                + "DATEADD(second,-1,SYSUTCDATETIME()),DATEADD(day,40,"
                + "SYSUTCDATETIME()),'Artificial assessment')",
                revision, specialistId);
        tariff("ROAD_TRANSPORT", "EQUITRANSIT", "KM", "TRANSPORTATION", "2");
        tariff("DOCUMENT_COORDINATION", "DEFAULT", "ROUTE_KM",
                "DOCUMENTATION_QUARANTINE", "0.5");
        tariff("VETERINARY", "EXAM", "HORSE_VISIT", "VETERINARY", "10");
    }

    private void tariff(String code, String option, String unit, String group,
            String rate) {
        fixtureTariffs.add(jdbc.queryForObject("INSERT dbo.ServiceTariff"
                + " (ServiceCode,OptionCode,UnitCode,CostGroup,UnitPrice,"
                + "Currency,VersionNo,EffectiveFrom,Status) OUTPUT"
                + " INSERTED.TariffID VALUES (?,?,?,?,?,'USD',1,"
                + "DATEADD(day,-1,SYSUTCDATETIME()),'ACTIVE')", Long.class,
                code, option, unit, group, rate));
    }

    private String account(String role) {
        UserAccount user = new UserAccount();

        user.setRole(roles.findByRoleCode(role).orElseThrow());
        user.setEmail(TokenTools.randomToken() + "@example.invalid");
        user.setPasswordHash("{bcrypt}fixture-hash-not-a-login-password");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        users.saveAndFlush(user);
        fixtureUsers.add(user.getUserId());
        if ("CUSTOMER".equals(role)) {
            Customer customer = new Customer();

            customer.setUser(user);
            customer.setFullName("Quotation fixture");
            customers.saveAndFlush(customer);
        } else {
            jdbc.update("INSERT dbo.StaffProfile (UserID,FullName)"
                    + " VALUES (?,'Quotation fixture')", user.getUserId());
        }
        return "Bearer " + tokens.issue(user).getAccessToken();
    }

    private long lastStaff() {
        return jdbc.queryForObject("SELECT StaffID FROM dbo.StaffProfile"
                + " WHERE UserID=?", Long.class, fixtureUsers.getLast());
    }

    private JsonNode get(String path, String token, int expected)
            throws Exception {
        var result = mvc.perform(MockMvcRequestBuilders.get(path)
                .header("Authorization", token))
                .andReturn().getResponse();

        assertThat(result.getStatus()).as(result.getContentAsString())
                .isEqualTo(expected);
        return mapper.readTree(result.getContentAsString());
    }

    private JsonNode post(String path, String token, JsonNode body,
            int expected) throws Exception {
        var result = mvc.perform(MockMvcRequestBuilders.post(path)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
                .andReturn().getResponse();

        assertThat(result.getStatus()).as(result.getContentAsString())
                .isEqualTo(expected);
        return mapper.readTree(result.getContentAsString());
    }

    private int count(String table, String column, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo." + table
                + " WHERE " + column + "=?", Integer.class, id);
    }

    private String base() {
        return "/api/transport-orders/" + order;
    }

    private String time(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(days)
                .withNano(0).toString();
    }

    private void paymentFixture() throws Exception {
        acceptedQuote = issued();
        action(acceptedQuote, "ACCEPT", owner, 200);
        long schedule = jdbc.queryForObject("SELECT ScheduleID FROM"
                + " dbo.QuotePaymentSchedule WHERE QuoteID=?", Long.class,
                acceptedQuote);
        ObjectNode body = paymentBody();

        body.put("quoteId", acceptedQuote);
        body.put("scheduleId", schedule);
        requestId = post(pay() + "/requests", manager, body, 200)
                .path("entityId").asLong();
        var image = new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();

        javax.imageio.ImageIO.write(image, "png", bytes);
        body = paymentBody();
        body.put("mediaType", "image/png");
        body.put("contentBase64", java.util.Base64.getEncoder()
                .encodeToString(bytes.toByteArray()));
        proof = post(pay() + "/proofs", owner, body, 200)
                .path("fileKey").asText();
    }

    private ObjectNode paymentBody() {
        var body = mapper.createObjectNode();

        body.set("command", command());
        return body;
    }

    private String pay() {
        return base() + "/payments";
    }

    private ObjectNode receiptBody(String amount) {
        var body = paymentBody();

        body.put("amountReceived", amount);
        body.put("currency", "USD");
        body.put("recipientAccount", "ARTIFICIAL-USD-ACCOUNT");
        body.put("bankTransactionReference", UUID.randomUUID().toString());
        body.put("postedAt", time(-1));
        body.put("verificationSource", "Artificial bank test evidence");
        body.put("proofFileKey", proof);
        body.put("internalNotes", "Private test-only note");
        return body;
    }

    private long receive(String amount) throws Exception {
        return post(pay() + "/transactions", manager, receiptBody(amount), 200)
                .path("entityId").asLong();
    }

    private ObjectNode allocationBody(long bank, String amount) {
        var body = paymentBody();

        body.put("transactionId", bank);
        body.put("paymentRequestId", requestId);
        body.put("amount", amount);
        return body;
    }

    private long event(String code, UUID operation, String payload)
            throws Exception {
        // Simulate a BE2 producer after its own validated transition.
        String producer = "CLEARED".equals(code) ? specialist : coordinator;
        var jwt = decoder.decode(producer.substring("Bearer ".length()));
        var authentication = liveTokens.convert(jwt);

        org.springframework.security.core.context.SecurityContextHolder
                .getContext().setAuthentication(authentication);
        try {
            var value = mapper.readTree(payload);

            return new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("SELECT OrderID FROM dbo.TransportOrder"
                        + " WITH (UPDLOCK,HOLDLOCK) WHERE OrderID=?",
                        Long.class, order);
                return auditWriter.append(order,
                        com.equitransit.backend.entity.activity.ActivityCode
                                .valueOf(code), operation, value);
            });
        } finally {
            org.springframework.security.core.context.SecurityContextHolder
                    .clearContext();
        }
    }

    /** @throws Exception if audit fixtures fail */
    @Test
    public void auditWriterRedactsAndAppendsWithoutEditingHistory()
            throws Exception {
        var operation = UUID.randomUUID();
        long first = event("CLEARED", operation,
                "{\"password\":\"never-store-password\","
                + "\"status\":\"APPROVED\",\"note\":\"raw-token-value\"}");
        assertThat(jdbc.queryForObject("SELECT UserID FROM dbo.AuditEvent"
                + " WHERE EventID=?", Long.class, first)).isEqualTo(
                        jdbc.queryForObject("SELECT UserID FROM"
                                + " dbo.StaffProfile WHERE StaffID=?",
                                Long.class, specialistId));
        String before = jdbc.queryForObject("SELECT ChangeSnapshot FROM"
                + " dbo.AuditEvent WHERE EventID=?", String.class, first);

        assertThat(event("CLEARED", operation, "{}" )).isEqualTo(first);
        event("DEPARTED", UUID.randomUUID(), "{}");
        assertThat(jdbc.queryForObject("SELECT ChangeSnapshot FROM"
                + " dbo.AuditEvent WHERE EventID=?", String.class, first))
                .isEqualTo(before).doesNotContain("never-store-password",
                        "raw-token-value").contains("[REDACTED]");
        get("/api/manager/audit-events", owner, 403);
        assertThat(get("/api/manager/audit-events", manager, 200).toString())
                .doesNotContain("never-store-password", "raw-token-value");
    }

    /** @throws Exception if dispatch fixtures fail */
    @Test
    public void notificationRecipientIsolationDedupAndReadAreIdempotent()
            throws Exception {
        event("CLEARED", UUID.randomUUID(), "{}");
        notifications.dispatch();
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM"
                + " dbo.Notification WHERE OrderID=?", Long.class, order);

        notifications.dispatch();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM"
                + " dbo.Notification WHERE OrderID=?", Long.class, order))
                .isEqualTo(before);
        long id = get("/api/notifications", owner, 200)
                .at("/items/0/notificationId").asLong();

        assertThat(get("/api/notifications", other, 200)
                .path("items").isEmpty()).isTrue();
        put("/api/notifications/" + id + "/read", other, "", 404);
        var read = put("/api/notifications/" + id + "/read", owner, "", 200);

        assertThat(put("/api/notifications/" + id + "/read", owner, "", 200))
                .isEqualTo(read);
        assertThat(read.path("readAt").isNull()).isFalse();
    }

    /** @throws Exception if dashboard fixtures fail */
    @Test
    public void dashboardsScopeCustomerAndOnlyActiveAssignedStaff()
            throws Exception {
        assertThat(get("/api/dashboard", owner, 200)
                .path("totalElements").asLong()).isEqualTo(1);
        assertThat(get("/api/dashboard", other, 200)
                .path("totalElements").asLong()).isZero();
        assertThat(get("/api/dashboard", coordinator, 200)
                .path("totalElements").asLong()).isEqualTo(1);
        jdbc.update("UPDATE dbo.OrderAssignment SET EndedAt=SYSUTCDATETIME()"
                + " WHERE OrderID=? AND StaffID=?", order, coordinatorId);
        assertThat(get("/api/dashboard", coordinator, 200)
                .path("items").isEmpty()).isTrue();
        get("/api/activity/orders/" + order + "/timeline", coordinator, 404);
        get("/api/activity/orders/" + order + "/timeline", other, 404);
        get("/api/dashboard?size=101", owner, 400);
        get("/api/manager/reports/financial", owner, 403);
        assertThat(get("/api/dashboard", owner, 200).toString())
                .doesNotContain("actualCost", "InternalNotes", "UnitPrice");
    }

    /** @throws Exception if financial fixtures fail */
    @Test
    public void managerSeparatesQuoteReportsReceiptsRefundsAndCosts()
            throws Exception {
        paymentFixture();
        var report = paymentBody();

        report.put("paymentRequestId", requestId);
        report.put("reportedAmount", "200.00");
        report.put("reportedCurrency", "USD");
        report.put("transferredAt", time(-1));
        report.put("bankReference", "Artificial customer claim");
        report.put("proofFileKey", proof);
        post(pay() + "/reports", owner, report, 200);
        var first = get("/api/manager/reports/financial", manager, 200);

        assertThat(get("/api/activity/orders/" + order + "/timeline", owner,
                200).toString()).doesNotContain("PAID");
        assertThat(first.path("quotedValue").asText()).isEqualTo("260.00");
        assertThat(first.path("reportedTransfers").asText())
                .isEqualTo("200.00");
        assertThat(first.path("netBankReceipts").asText()).isEqualTo("0.00");
        post(pay() + "/allocations", manager,
                allocationBody(receive("100.00"), "80.00"), 200);
        var second = get("/api/manager/reports/financial", manager, 200);

        assertThat(second.path("netBankReceipts").asText()).isEqualTo("100.00");
        assertThat(second.path("netAllocated").asText()).isEqualTo("80.00");
        assertThat(second.path("actualCost").asText()).isEqualTo("0.00");
        long plan = jdbc.queryForObject("SELECT TOP(1) PlanID FROM"
                + " dbo.TransportPlan WHERE OrderID=?", Long.class, order);

        jdbc.update("INSERT dbo.ActualCost (PlanID,CostCode,Amount,Currency,"
                + "IncurredAt,RecordedBy,Description) VALUES (?,"
                + "'TEST-COST',40,'USD',SYSUTCDATETIME(),?,'Private supplier')",
                plan, managerId);
        long historical = jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids"
                + " TABLE(ID BIGINT); INSERT dbo.TransportPlan (OrderID,"
                + "RevisionID,CreatedBy,VersionNo,FinancialGateStatus,"
                + "OperationalGateStatus,DocGateStatus,PlanStatus)"
                + " OUTPUT INSERTED.PlanID INTO @ids VALUES (?,?,?,2,"
                + "'PENDING','PENDING','PENDING','DRAFT'); SELECT ID FROM @ids",
                Long.class, order, revision, coordinatorId);

        jdbc.update("INSERT dbo.ActualCost (PlanID,CostCode,Amount,Currency,"
                + "IncurredAt,RecordedBy) VALUES (?,'OLD-PLAN',500,'USD',"
                + "SYSUTCDATETIME(),?)", historical, managerId);
        assertThat(get("/api/manager/reports/financial", manager, 200)
                .path("actualCost").asText()).isEqualTo("40.00");
        long allocation = jdbc.queryForObject("SELECT TOP(1) AllocationID"
                + " FROM dbo.PaymentAllocation WHERE PaymentRequestID=?",
                Long.class, requestId);
        var refund = paymentBody();

        refund.put("allocationId", allocation);
        refund.put("amount", "20.00");
        refund.put("reason", "Artificial accounting refund");
        long refundId = post(pay() + "/refunds", owner, refund, 200)
                .path("entityId").asLong();
        var approve = paymentBody();

        approve.put("action", "APPROVE");
        post(pay() + "/refunds/" + refundId + "/actions", manager,
                approve, 200);
        var complete = paymentBody();

        complete.put("action", "COMPLETE");
        complete.put("bankRefundReference", UUID.randomUUID().toString());
        complete.put("proofFileKey", proof);
        complete.put("refundedAt", OffsetDateTime.now().toString());
        post(pay() + "/refunds/" + refundId + "/actions", manager,
                complete, 200);
        var net = get("/api/manager/reports/financial", manager, 200);

        assertThat(net.path("netBankReceipts").asText()).isEqualTo("80.00");
        assertThat(net.path("netAllocated").asText()).isEqualTo("60.00");
        assertThat(get("/api/dashboard", owner, 200).toString())
                .doesNotContain("Private supplier", "actualCost");
    }

    /** @throws Exception if rollback fixtures fail */
    @Test
    public void rolledBackProducerHasNoNotificationAndPreferenceIsRespected()
            throws Exception {
        var jwt = decoder.decode(manager.substring("Bearer ".length()));
        var auth = liveTokens.convert(jwt);
        var context = org.springframework.security.core.context
                .SecurityContextHolder.getContext();

        context.setAuthentication(auth);
        try {
            new TransactionTemplate(transactions).execute(status -> {
                auditWriter.append(order,
                        com.equitransit.backend.entity.activity.ActivityCode
                                .DELIVERED, UUID.randomUUID(), null);
                status.setRollbackOnly();
                return null;
            });
        } finally {
            org.springframework.security.core.context.SecurityContextHolder
                    .clearContext();
        }
        assertThat(get("/api/activity/orders/" + order + "/timeline", owner,
                200).toString()).doesNotContain("DELIVERED");
        put("/api/customers/me/notification-preferences", owner,
                "{\"enabled\":false}", 200);
        event("CLEARED", UUID.randomUUID(), "{}");
        notifications.dispatch();
        assertThat(notifications.claimMail()).isEmpty();
        put("/api/customers/me/notification-preferences", owner,
                "{\"enabled\":true}", 200);
        event("DEPARTED", UUID.randomUUID(), "{}");
        notifications.dispatch();
        assertThat(notifications.claimMail()).isNotEmpty();
    }

    /** @throws Exception if driver fixtures fail */
    @Test
    public void driverSeesOnlyCurrentlyActiveLegAssignment() throws Exception {
        String driver = account("DRIVER");
        long driverId = lastStaff();
        long plan = jdbc.queryForObject("SELECT TOP(1) PlanID FROM"
                + " dbo.TransportPlan WHERE OrderID=?", Long.class, order);
        long leg = jdbc.queryForObject("SELECT TOP(1) LegID FROM"
                + " dbo.TripLeg WHERE PlanID=?", Long.class, plan);

        jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=?"
                + " WHERE OrderID=?", plan, order);
        jdbc.update("INSERT dbo.TripAssignment (LegID,StaffID,RoleInTrip,"
                + "Status,AssignedFrom,AssignedTo) VALUES (?,?,'DRIVER',"
                + "'ACTIVE',DATEADD(day,-2,SYSUTCDATETIME()),"
                + "DATEADD(day,1,SYSUTCDATETIME()))", leg, driverId);
        assertThat(get("/api/dashboard", driver, 200)
                .path("totalElements").asLong()).isEqualTo(1);
        assertThat(get("/api/dashboard/tasks", driver, 200)
                .at("/items/0/legId").asLong()).isEqualTo(leg);
        jdbc.update("UPDATE dbo.TripAssignment SET AssignedTo="
                + "DATEADD(day,-1,SYSUTCDATETIME()) WHERE StaffID=?", driverId);
        assertThat(get("/api/dashboard", driver, 200)
                .path("totalElements").asLong()).isZero();
        assertThat(get("/api/dashboard/tasks", driver, 200)
                .path("items").isEmpty()).isTrue();
    }

    /** @throws Exception if SQL projection retry/race fails */
    @Test
    public void failedProjectionRollsBackAndTwoWorkersRetryWithoutDuplicates()
            throws Exception {
        long fact = event("CLEARED", UUID.randomUUID(), "{}");

        org.mockito.Mockito.doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("Synthetic projection failure");
        }).when(activity).update(org.mockito.ArgumentMatchers.eq("project"),
                org.mockito.ArgumentMatchers.anyMap());
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> notifications.dispatch())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Synthetic projection failure");
        } finally {
            org.mockito.Mockito.reset(activity);
        }
        assertThat(count("AuditEvent", "EventID", fact)).isEqualTo(1);
        assertThat(count("Notification", "SourceEventID", fact)).isZero();
        assertThat(count("NotificationProjection", "EventID", fact)).isZero();
        try (var pool = java.util.concurrent.Executors
                .newFixedThreadPool(2)) {
            var first = pool.submit(() -> notifications.dispatch());
            var second = pool.submit(() -> notifications.dispatch());

            first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            second.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(count("Notification", "SourceEventID", fact)).isEqualTo(3);
        assertThat(count("NotificationProjection", "EventID", fact))
                .isEqualTo(1);
    }

    /** @throws Exception if actual payment/timeline fixtures fail */
    @Test
    public void paidMilestoneRequiresFullActualReconciledAllocations()
            throws Exception {
        paymentFixture();
        assertThat(get("/api/activity/orders/" + order + "/timeline", owner,
                200).toString()).doesNotContain("PAID");
        post(pay() + "/allocations", manager,
                allocationBody(receive("260.00"), "260.00"), 200);
        assertThat(get("/api/activity/orders/" + order + "/timeline", owner,
                200).toString()).contains("ACCEPTED", "PAID", "SUBMITTED")
                .doesNotContain("CLEARED", "DEPARTED", "CLOSED");
    }

    /** @throws Exception if a partial F16 deployment is incorrectly enabled */
    @Test
    public void readinessRejectsMissingColumnsIndexesAndJsonConstraint()
            throws Exception {
        assertThat(activity.ready()).isTrue();
        var mutations = List.of(
                "DROP INDEX UQ_AuditEvent_EventKey ON dbo.AuditEvent;"
                        + " EXEC sys.sp_rename 'dbo.AuditEvent.EventKey',"
                        + "'MissingEventKey','COLUMN'",
                "EXEC sys.sp_rename 'dbo.NotificationProjection',"
                        + "'MissingProjection'",
                "ALTER INDEX UQ_AuditEvent_EventKey"
                        + " ON dbo.AuditEvent DISABLE",
                "ALTER INDEX UQ_Notification_EventRecipient"
                        + " ON dbo.Notification DISABLE",
                "ALTER TABLE dbo.AuditEvent NOCHECK CONSTRAINT"
                        + " CK_AuditEvent_ChangeSnapshot_JSON");
        var changes = new ArrayList<>(mutations);

        for (String field : List.of("SourceEventID", "EmailState",
                "EmailLeaseUntil", "EmailAttempts")) {
            String prefix = "SourceEventID".equals(field)
                    ? "DROP INDEX UQ_Notification_EventRecipient"
                            + " ON dbo.Notification; " : "";

            changes.add(prefix + "EXEC sys.sp_rename 'dbo.Notification."
                    + field + "','Missing" + field + "','COLUMN'");
        }
        for (String sql : changes) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.execute(sql);
                assertThat(activity.ready()).as(sql).isFalse();
                try {
                    var response = get("/api/dashboard", owner, 503);

                    assertThat(response.path("code").asText())
                            .isEqualTo("ACTIVITY_SCHEMA_PENDING");
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                } finally {
                    tx.setRollbackOnly();
                }
            });
            assertThat(activity.ready()).isTrue();
        }
    }

    private JsonNode put(String path, String token, String body, int status)
            throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.put(path)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();

        assertThat(response.getStatus()).as(response.getContentAsString())
                .isEqualTo(status);
        return mapper.readTree(response.getContentAsString());
    }
}
