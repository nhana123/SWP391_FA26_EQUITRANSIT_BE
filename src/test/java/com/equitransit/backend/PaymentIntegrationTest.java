/*
 * PaymentIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend;


import com.equitransit.backend.dto.common.ApiExamples;
import com.equitransit.backend.entity.customer.Customer;
import com.equitransit.backend.entity.auth.UserAccount;
import com.equitransit.backend.repository.auth.AccountRoleRepository;
import com.equitransit.backend.repository.customer.CustomerRepository;
import com.equitransit.backend.repository.auth.UserAccountRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        "spring.datasource.url=${EQ_PAYMENT_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_PAYMENT_TEST_ENABLED",
        matches = "true")
public class PaymentIntegrationTest {

    @Autowired
    private com.equitransit.backend.service.QuotationCommitmentFacade gate;

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
    private long coordinatorId;
    private long specialistId;

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
        coordinator = account("COORDINATOR");
        coordinatorId = lastStaff();
        account("SPECIALIST");
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

    private com.equitransit.backend.entity.quote.CommercialReadiness ready() {
        return new TransactionTemplate(transactions).execute(status ->
                gate.readiness(order, revision, acceptedQuote,
                        OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(2)));
    }

    /** @throws Exception if transfer fixtures fail */
    @Test
    public void customerReportIsNotPaidAndAllocationIsIdempotent()
            throws Exception {
        paymentFixture();
        var report = paymentBody();

        report.put("paymentRequestId", requestId);
        report.put("reportedAmount", "260.00");
        report.put("reportedCurrency", "USD");
        report.put("transferredAt", time(-1));
        report.put("bankReference", "TEST-BANK-REPORT");
        report.put("proofFileKey", proof);
        long reportId = post(pay() + "/reports", owner, report, 200)
                .path("entityId").asLong();
        var rejection = paymentBody();

        rejection.put("reason", "Artificial report does not match bank proof");
        post(pay() + "/reports/" + reportId + "/reject", manager,
                rejection, 200);
        assertThat(ready().paidAmount()).isZero();
        assertThat(ready().departureReady()).isFalse();
        long bank = receive("260.00");
        var allocation = allocationBody(bank, "260.00");
        var first = post(pay() + "/allocations", manager, allocation, 200);

        assertThat(post(pay() + "/allocations", manager, allocation, 200))
                .isEqualTo(first);
        assertThat(ready().paidAmount()).isEqualByComparingTo("260.00");
        assertThat(ready().settlementReady()).isTrue();
        assertThat(get(pay(), owner, 200).toString()).doesNotContain(
                "Private test-only note", "RecipientAccount", "InternalNotes");
        post(pay() + "/allocations", manager,
                allocationBody(bank, "0.01"), 409);
    }

    /** @throws Exception if negative-case fixtures fail */
    @Test
    public void rejectsRolesOwnershipCurrencyProofAndDuplicateBank()
            throws Exception {
        paymentFixture();
        var receipt = receiptBody("260.00");

        post(pay() + "/transactions", owner, receipt, 403);
        post(pay() + "/transactions", coordinator, receipt, 403);
        get(pay(), other, 404);
        get(pay() + "/proofs/" + proof, other, 409);
        receipt.put("currency", "EUR");
        post(pay() + "/transactions", manager, receipt, 400);
        receipt.put("currency", "USD");
        post(pay() + "/transactions", manager, receipt, 200);
        receipt.set("command", command());
        post(pay() + "/transactions", manager, receipt, 409);
        var badProof = paymentBody();

        badProof.put("mediaType", "image/png");
        badProof.put("contentBase64", "PHNjcmlwdD5iYWQ8L3NjcmlwdD4=");
        post(pay() + "/proofs", owner, badProof, 409);
    }

    /** @throws Exception if refund lifecycle fixtures fail */
    @Test
    public void refundReservesBalanceAndNeedsApprovalAndRealProof()
            throws Exception {
        paymentFixture();
        long allocation = post(pay() + "/allocations", manager,
                allocationBody(receive("260.00"), "260.00"), 200)
                .path("entityId").asLong();
        var body = paymentBody();

        body.put("allocationId", allocation);
        body.put("amount", "100.00");
        body.put("reason", "Artificial customer refund request");
        long refund = post(pay() + "/refunds", owner, body, 200)
                .path("entityId").asLong();

        assertThat(ready().departureBlockers()).contains("REFUND_PENDING");
        body = paymentBody();
        body.put("allocationId", allocation);
        body.put("amount", "200.00");
        body.put("reason", "Would exceed refundable funds");
        post(pay() + "/refunds", manager, body, 409);
        var approve = paymentBody();

        approve.put("action", "COMPLETE");
        post(pay() + "/refunds/" + refund + "/actions", manager, approve, 409);
        approve.put("action", "APPROVE");
        post(pay() + "/refunds/" + refund + "/actions", owner, approve, 403);
        post(pay() + "/refunds/" + refund + "/actions", manager, approve, 200);
        var complete = paymentBody();

        complete.put("action", "COMPLETE");
        complete.put("bankRefundReference", UUID.randomUUID().toString());
        complete.put("proofFileKey", proof);
        complete.put("refundedAt", OffsetDateTime.now().toString());
        post(pay() + "/refunds/" + refund + "/actions", manager, complete, 200);
        assertThat(ready().paidAmount()).isEqualByComparingTo("160.00");
        assertThat(ready().settlementReady()).isFalse();
    }

    /** @throws Exception if bank reversal fixtures fail */
    @Test
    public void reversalRemovesActualPaidWithoutDeletingAllocations()
            throws Exception {
        paymentFixture();
        long bank = receive("260.00");

        post(pay() + "/allocations", manager,
                allocationBody(bank, "260.00"), 200);
        var body = paymentBody();

        body.put("reason", "Bank reversed the artificial receipt");
        body.put("proofFileKey", proof);
        post(pay() + "/transactions/" + bank + "/reversal", manager, body, 200);
        assertThat(ready().paidAmount()).isZero();
        assertThat(ready().settlementReady()).isFalse();
    }

    /** @throws Exception if concurrency fixtures fail */
    @Test
    public void competingAllocationsHaveOneWinner() throws Exception {
        paymentFixture();
        long bank = receive("260.00");
        var first = allocationBody(bank, "200.00");
        var second = allocationBody(bank, "200.00");
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);

        try {
            var a = pool.submit(() -> paymentStatus(start, "/allocations",
                    first, manager));
            var b = pool.submit(() -> paymentStatus(start, "/allocations",
                    second, manager));

            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS),
                    b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(ready().paidAmount()).isEqualByComparingTo("200.00");
        } finally {
            pool.shutdownNow();
        }
    }

    /** @throws Exception if concurrency fixtures fail */
    @Test
    public void competingRefundsHaveOneWinner() throws Exception {
        paymentFixture();
        long allocation = post(pay() + "/allocations", manager,
                allocationBody(receive("260.00"), "260.00"), 200)
                .path("entityId").asLong();
        var first = paymentBody();

        first.put("allocationId", allocation);
        first.put("amount", "200.00");
        first.put("reason", "Artificial refund race");
        var second = first.deepCopy();

        second.set("command", command());
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);

        try {
            var a = pool.submit(() -> paymentStatus(start, "/refunds",
                    first, owner));
            var b = pool.submit(() -> paymentStatus(start, "/refunds",
                    second, owner));

            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS),
                    b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    /** @throws Exception if negative allocation fixtures fail */
    @Test
    public void boundsRequestAndBankSeparatelyAndRejectsStaleRetries()
            throws Exception {
        paymentFixture();
        long small = receive("100.00");

        post(pay() + "/allocations", manager,
                allocationBody(small, "100.01"), 409);
        long large = receive("500.00");

        post(pay() + "/allocations", manager,
                allocationBody(large, "260.01"), 409);
        var body = allocationBody(large, "10.00");
        var first = post(pay() + "/allocations", manager, body, 200);

        body.put("amount", "11.00");
        post(pay() + "/allocations", manager, body, 409);
        body.set("command", command());
        ((ObjectNode) body.get("command"))
                .put("recordVersion", "0000000000000000");
        post(pay() + "/allocations", manager, body, 409);
        assertThat(first.path("entityId").asLong()).isPositive();
        assertThat(ready().paidAmount()).isEqualByComparingTo("10.00");
    }

    /** @throws Exception if rejection and expiry fixtures fail */
    @Test
    public void rejectedReportAndExpiredUnacceptedQuoteCannotFakePaid()
            throws Exception {
        long quote = issued();
        var request = paymentBody();

        request.put("quoteId", quote);
        request.put("scheduleId", jdbc.queryForObject("SELECT ScheduleID"
                + " FROM dbo.QuotePaymentSchedule WHERE QuoteID=?",
                Long.class, quote));
        post(pay() + "/requests", manager, request, 409);
        jdbc.update("UPDATE dbo.Quote SET IssuedAt=DATEADD(day,-2,"
                + "SYSUTCDATETIME()),ValidUntil=DATEADD(day,-1,"
                + "SYSUTCDATETIME()) WHERE QuoteID=?", quote);
        action(quote, "ACCEPT", owner, 409);
        post(pay() + "/requests", manager, request, 409);
    }

    /** @throws Exception if transaction rollback fixtures fail */
    @Test
    public void outerRollbackLeavesNoBankRecordOrReceipt() throws Exception {
        paymentFixture();
        var body = receiptBody("100.00");
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM"
                + " dbo.PaymentTransaction", Long.class);
        var original = command().path("recordVersion").asText();
        var tx = new TransactionTemplate(transactions);

        tx.execute(status -> {
            try {
                post(pay() + "/transactions", manager, body, 200);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            status.setRollbackOnly();
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM"
                + " dbo.PaymentTransaction", Long.class)).isEqualTo(before);
        assertThat(command().path("recordVersion").asText())
                .isEqualTo(original);
    }

    /** Partial F07 schema must fail closed; all test DDL is rolled back. */
    @Test
    public void readinessRejectsMissingLedgerColumnsAndConstraints()
            throws Exception {
        paymentFixture();
        var ledger = new com.equitransit.backend.repository.PaymentRepository(
                dataSource);
        var changes = new ArrayList<String>();

        for (String table : List.of("PaymentProof", "PaymentOperation")) {
            changes.add("EXEC sys.sp_rename 'dbo." + table
                    + "','Missing" + table + "'");
        }
        for (String field : List.of("ReversalReason", "ReversedAt",
                "ReversedBy", "ReversalProofFileKey")) {
            changes.add("EXEC sys.sp_rename 'dbo.PaymentTransaction." + field
                    + "','Missing" + field + "','COLUMN'");
        }
        changes.add("ALTER INDEX UQ_PaymentRequest_Schedule"
                + " ON dbo.PaymentRequest DISABLE");
        changes.add("ALTER INDEX UQ_Refund_BankReference"
                + " ON dbo.Refund DISABLE");
        changes.add("ALTER INDEX UQ_PaymentTransaction_BankReference"
                + " ON dbo.PaymentTransaction DISABLE");
        changes.add("ALTER TABLE dbo.PaymentRequest NOCHECK CONSTRAINT"
                + " FK_Figma_PaymentRequest_ScheduleID");
        changes.add("ALTER TABLE dbo.PaymentProof NOCHECK CONSTRAINT"
                + " CK_PaymentProof_Size");
        changes.add("ALTER TABLE dbo.PaymentProof NOCHECK CONSTRAINT"
                + " CK_PaymentProof_Mime");
        assertThat(ledger.ready()).isTrue();
        for (String sql : changes) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.execute(sql);
                assertThat(ledger.ready()).as(sql).isFalse();
                var readiness = ready();

                assertThat(readiness.departureBlockers())
                        .contains("PAYMENT_SCHEMA_PENDING");
                assertThat(readiness.settlementBlockers())
                        .contains("PAYMENT_SCHEMA_PENDING");
                try {
                    var response = get(pay(), owner, 503);

                    assertThat(response.path("code").asText())
                            .isEqualTo("PAYMENT_SCHEMA_PENDING");
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                } finally {
                    tx.setRollbackOnly();
                }
            });
            assertThat(ledger.ready()).isTrue();
        }
    }

    /** @throws Exception if the allocation update lock is not retained */
    @Test
    public void refundAllocationIsLockedUntilTransactionCompletes()
            throws Exception {
        paymentFixture();
        long allocation = post(pay() + "/allocations", manager,
                allocationBody(receive("260.00"), "260.00"), 200)
                .path("entityId").asLong();
        var ledger = new com.equitransit.backend.repository.PaymentRepository(
                dataSource);

        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertThat(ledger.rows("allocationForUpdate", Map.of(
                        "order", order, "id", allocation))).hasSize(1);
                var blocked = pool.submit(() -> {
                    try (var connection = dataSource.getConnection();
                            var statement = connection.createStatement()) {
                        connection.setAutoCommit(false);
                        statement.execute("SET LOCK_TIMEOUT 500");
                        try {
                            statement.executeUpdate("UPDATE"
                                    + " dbo.PaymentAllocation SET"
                                    + " AllocatedAmount=AllocatedAmount"
                                    + " WHERE AllocationID=" + allocation);
                            return 0;
                        } catch (java.sql.SQLException error) {
                            return error.getErrorCode();
                        } finally {
                            connection.rollback();
                            statement.execute("SET LOCK_TIMEOUT -1");
                        }
                    }
                });

                try {
                    assertThat(blocked.get(10, TimeUnit.SECONDS))
                            .isEqualTo(1222);
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            });
        }
    }

    private int paymentStatus(CountDownLatch start, String path,
            ObjectNode body, String token) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return mvc.perform(MockMvcRequestBuilders.post(pay() + path)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andReturn().getResponse().getStatus();
    }
}
