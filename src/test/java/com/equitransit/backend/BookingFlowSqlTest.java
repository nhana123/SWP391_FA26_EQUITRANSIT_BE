package com.equitransit.backend;

import com.equitransit.backend.dto.common.ApiExamples;
import com.equitransit.backend.dto.plan.PlanExamples;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.dto.quote.QuoteLine;
import com.equitransit.backend.dto.quote.QuotePrepareRequest;
import com.equitransit.backend.dto.quote.QuoteSchedule;
import com.equitransit.backend.entity.quote.QuoteCalculationContext;
import com.equitransit.backend.entity.quote.QuoteSnapshot;
import com.equitransit.backend.entity.quote.QuoteStatus;
import com.equitransit.backend.entity.customer.Customer;
import com.equitransit.backend.entity.auth.UserAccount;
import com.equitransit.backend.repository.auth.AccountRoleRepository;
import com.equitransit.backend.repository.customer.CustomerRepository;
import com.equitransit.backend.repository.auth.UserAccountRepository;
import com.equitransit.backend.repository.quote.QuoteRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.service.GeoDistanceService;
import com.equitransit.backend.service.quote.QuotationCommitmentFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
        .AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

/** Real SQL automation, net deposit gates, ownership and idempotent submits. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_HUB_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_HUB_TEST", matches = "true")
@Transactional
public class BookingFlowSqlTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenService tokens;
    @Autowired private UserAccountRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private AccountRoleRepository roles;
    @Autowired private GeoDistanceService geo;
    @Autowired private QuoteRepository quotes;
    @Autowired private QuotationCommitmentFacade commitments;
    private String owner;
    private long order;
    private long revision;

    /** Guard every write; map calls remain offline for stable tests. */
    @BeforeEach
    public void sandbox() {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class))
                .isIn("EquiTransit_Demo", "EquiTransit_Test");
        geo.setExternalNetworkEnabled(false);
        owner = token("CUSTOMER");
    }

    /** @throws Exception if ROAD submit duplicates or misprices obligations */
    @Test
    public void roadApprovalCreatesQuoteAndDepositAtomically()
            throws Exception {
        create(false, "ROAD");
        assertThat(count("dbo.Quote", "RevisionID", revision)).isZero();
        assertThat(status(owner).path("orderStatus").asText())
                .isEqualTo("SUBMITTED");
        assertThat(status(owner).path("blockReason").asText())
                .isEqualTo("AWAITING_MANAGER_APPROVAL");
        assertThat(status(owner).path("depositRequired").asBoolean()).isFalse();
        assertThat(status(owner).path("canAssignDriver").asBoolean()).isFalse();
        send("GET", base() + "/booking-flow-status", token("COORDINATOR"),
                null, 404);
        approve();
        assertThat(status(owner).path("orderStatus").asText())
                .isEqualTo("APPROVED");
        assertThat(status(owner).path("blockReason").asText())
                .isEqualTo("DEPOSIT_REQUIRED");
        long quote = quote();
        assertThat(jdbc.queryForObject("SELECT QuoteStatus FROM dbo.Quote "
                + "WHERE QuoteID=?", String.class, quote))
                .isEqualTo("ACCEPTED");
        BigDecimal total = jdbc.queryForObject("SELECT TotalQuotedAmount FROM "
                + "dbo.Quote WHERE QuoteID=?", BigDecimal.class, quote);
        var view = status(owner);
        assertThat(new BigDecimal(view.path("depositAmount").asText()))
                .isEqualByComparingTo(total.multiply(new BigDecimal("0.30"))
                        .setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(view.path("hasAcceptedQuote").asBoolean()).isTrue();
        assertThat(view.path("isDepositSatisfied").asBoolean()).isFalse();
        assertThat(count("dbo.PaymentRequest", "QuoteID", quote)).isEqualTo(1);
        assertThat(count("dbo.QuotePaymentSchedule", "QuoteID", quote))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(AmountDue) FROM "
                + "dbo.QuotePaymentSchedule WHERE QuoteID=?", BigDecimal.class,
                quote)).isEqualByComparingTo(total);
        send("GET", base() + "/booking-flow-status", customer(), null, 404);
        send("GET", base() + "/booking-flow-status", token("DRIVER"),
                null, 403);
        status(token("MANAGER"));
        status(token("COORDINATOR"));
    }

    /** @throws Exception if pending extras or AIR accidentally open deposits */
    @Test
    public void unknownRoadFeesAndAirRemainCoordinatorQuoteWork()
            throws Exception {
        create(true, "ROAD");
        approve();
        assertThat(count("dbo.Quote", "RevisionID", revision)).isZero();
        assertThat(status(owner).path("blockReason").asText())
                .isEqualTo("AWAITING_COORDINATOR_QUOTE");
        for (String mode : List.of("AIR", "MULTIMODAL")) {
            create(false, mode);
            assertThat(status(owner).path("blockReason").asText())
                    .isEqualTo("AWAITING_MANAGER_APPROVAL");
            approve();
            assertThat(count("dbo.Quote", "RevisionID", revision)).isZero();
            assertThat(status(owner).path("isDepositSatisfied").asBoolean())
                    .isFalse();
        }
    }

    /** @throws Exception if partial/unreconciled money opens ROAD assignment */
    @Test
    public void roadAssignmentRequiresFullReconciledDeposit()
            throws Exception {
        create(false, "ROAD");
        approve();
        var plan = createPlan("ROAD");
        String path = base() + "/plans/" + plan.path("planId").asLong()
                + "/legs/" + plan.path("legs").get(0).path("legId").asLong()
                + "/road-assignment";
        var assignment = json.createObjectNode();
        assignment.put("vehicleId", 1).put("driverStaffId", staff("DRIVER"))
                .put("vehicleRecordVersion", "0000000000000001")
                .put("driverBufferMinutes", 0);
        assignment.set("command", command());
        assertThat(send("POST", path, token("COORDINATOR"), assignment, 409)
                .path("code").asText())
                .isEqualTo("DEPOSIT_REQUIRED_BEFORE_ROAD_ASSIGNMENT");
        BigDecimal deposit = new BigDecimal(status(owner)
                .path("depositAmount").asText());
        long request = request();
        String proof = report(request, deposit);
        assertThat(status(owner).path("isDepositSatisfied").asBoolean())
                .isFalse();
        long bank = bank(deposit);
        allocate(bank, request, deposit.subtract(new BigDecimal("0.01")));
        assertThat(status(owner).path("isDepositSatisfied").asBoolean())
                .isFalse();
        allocate(bank, request, new BigDecimal("0.01"));
        assertThat(status(owner).path("isDepositSatisfied").asBoolean())
                .isTrue();
        assertThat(status(owner).path("canAssignDriver").asBoolean()).isTrue();
        jdbc.update("UPDATE dbo.PaymentTransaction SET Status='REVERSED',"
                + "ReversedAt=DATEADD(second,-1,SYSUTCDATETIME()),"
                + "ReversedBy=?,ReversalReason='Fixture reversal',"
                + "ReversalProofFileKey=? WHERE TransactionID=?",
                staff("MANAGER"), proof, bank);
        assertThat(status(owner).path("isDepositSatisfied").asBoolean())
                .isFalse();
    }

    /** @throws Exception if AIR accepts money or books equipment too early */
    @Test
    public void airRequiresAcceptanceThenDepositForFlightAndStall()
            throws Exception {
        create(false, "AIR");
        approve();
        var plan = createPlan("AIR");
        String legPath = base() + "/plans/" + plan.path("planId").asLong()
                + "/legs/" + plan.path("legs").get(0).path("legId").asLong();
        var stall = json.createObjectNode().put("stallId", 1)
                .put("stallRecordVersion", "0000000000000001")
                .put("carrierAcceptanceStatus", "ACCEPTED")
                .put("status", "CONFIRMED");
        stall.set("command", command());
        assertThat(send("POST", legPath + "/stall-assignment",
                token("COORDINATOR"), stall, 409).path("code").asText())
                .isEqualTo("DEPOSIT_REQUIRED_BEFORE_AIR_ASSIGNMENT");
        var flight = json.createObjectNode().put("offerId", 1)
                .put("airlineName", "Fixture airline").put("flightNumber", "F1")
                .put("status", "CONFIRMED").put("usableCapacitySlots", "3.0");
        flight.set("command", command());
        assertThat(send("POST", legPath + "/flight-reservation",
                token("COORDINATOR"), flight, 409).path("code").asText())
                .isEqualTo("DEPOSIT_REQUIRED_BEFORE_AIR_ASSIGNMENT");
        long quote = issuedAirFixture();
        assertThat(count("dbo.PaymentRequest", "QuoteID", quote)).isZero();
        assertThat(status(owner).path("hasAcceptedQuote").asBoolean())
                .isFalse();
        var accept = json.createObjectNode().put("action", "ACCEPT");
        accept.set("command", command());
        var receipt = send("POST", base() + "/quotes/" + quote + "/actions",
                owner, accept, 200);
        assertThat(send("POST", base() + "/quotes/" + quote + "/actions",
                owner, accept, 200)).isEqualTo(receipt);
        assertThat(count("dbo.PaymentRequest", "QuoteID", quote)).isEqualTo(1);
        assertThat(status(owner).path("canAssignDriver").asBoolean()).isFalse();
        long bank = bank(new BigDecimal("300.00"));
        allocate(bank, request(), new BigDecimal("300.00"));
        assertThat(status(owner).path("canAssignDriver").asBoolean()).isTrue();
        assertThat(commitments.readiness(order, revision, quote,
                OffsetDateTime.now(ZoneOffset.UTC)).departureBlockers())
                .contains("BALANCE_REQUIRED_BEFORE_DEPARTURE");
        long balance = jdbc.queryForObject("SELECT ScheduleID FROM "
                + "dbo.QuotePaymentSchedule WHERE QuoteID=? "
                + "AND PaymentStage='BALANCE'", Long.class, quote);
        var obligation = json.createObjectNode().put("quoteId", quote)
                .put("scheduleId", balance);
        obligation.set("command", command());
        long payment = send("POST", base() + "/payments/requests",
                token("MANAGER"), obligation, 200).path("entityId").asLong();
        long secondBank = bank(new BigDecimal("700.00"));
        allocate(secondBank, payment, new BigDecimal("700.00"));
        assertThat(commitments.readiness(order, revision, quote,
                OffsetDateTime.now(ZoneOffset.UTC)).departureReady()).isTrue();
    }

    /** @throws Exception if invalid team opens a deposit or approves order */
    @Test
    public void invalidTeamLeavesRoadSubmittedWithoutDeposit()
            throws Exception {
        create(false, "ROAD");
        var body = command();
        body.put("revisionId", revision).put("decision", "APPROVED")
                .put("specialistId", staff("DRIVER"))
                .put("coordinatorId", staff("COORDINATOR"));
        send("POST", "/api/manager/transport-orders/" + order + "/decisions",
                token("MANAGER"), body, 409);
        assertThat(count("dbo.Quote", "RevisionID", revision)).isZero();
        assertThat(status(owner).path("blockReason").asText())
                .isEqualTo("AWAITING_MANAGER_APPROVAL");
    }

    /** @throws Exception if a historical request bypasses Manager approval */
    @Test
    public void submittedOrderRejectsLegacyDepositRequestAndReport()
            throws Exception {
        create(false, "AIR");
        long quote = issuedAirFixture();
        long customer = jdbc.queryForObject("SELECT CustomerID FROM "
                + "dbo.TransportOrder WHERE OrderID=?", Long.class, order);
        quotes.transition(quote, QuoteStatus.ACCEPTED, null, customer);
        long schedule = jdbc.queryForObject("SELECT ScheduleID FROM "
                + "dbo.QuotePaymentSchedule WHERE QuoteID=? AND "
                + "PaymentStage='DEPOSIT'", Long.class, quote);
        var obligation = json.createObjectNode().put("quoteId", quote)
                .put("scheduleId", schedule);
        obligation.set("command", command());
        assertThat(send("POST", base() + "/payments/requests",
                token("MANAGER"), obligation, 409).path("code").asText())
                .isEqualTo("AWAITING_MANAGER_APPROVAL");
        var report = json.createObjectNode().put("paymentRequestId", 1)
                .put("reportedAmount", "300.00").put("reportedCurrency", "USD")
                .put("transferredAt", time(-1)).put("bankReference", "fixture")
                .put("proofFileKey", UUID.randomUUID().toString());
        report.set("command", command());
        assertThat(send("POST", base() + "/payments/reports", owner,
                report, 409).path("code").asText())
                .isEqualTo("AWAITING_MANAGER_APPROVAL");
        assertThat(count("dbo.PaymentRequest", "QuoteID", quote)).isZero();
    }
    private long issuedAirFixture() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        var req = new QuotePrepareRequest(new QuoteCommand(
                command().path("recordVersion").asText(), UUID.randomUUID()),
                revision, now.plusDays(7), "AIR-FIXTURE",
                "Resolved airline fare",
                "Fixture terms", List.of(new QuoteSchedule(1, "DEPOSIT",
                "300.00", now.plusDays(1)), new QuoteSchedule(2, "BALANCE",
                "700.00", now.plusDays(90))), List.of());
        var line = new QuoteLine("AIRFARE", "TRANSPORTATION", "Resolved fare",
                "SHIPMENT", "1.000", "1000.00", "1000.00", "PRICED", List.of());
        var airKm = BigDecimal.valueOf(1000);
        var context = new QuoteCalculationContext(null, BigDecimal.ZERO,
                airKm, null, null, null, null, null, List.of());
        var snapshot = new QuoteSnapshot(1, req, List.of(line), List.of(),
                List.of(), context);
        long quote = quotes.prepare(order, staff("COORDINATOR"), snapshot);
        quotes.transition(quote, QuoteStatus.ISSUED,
                staff("COORDINATOR"), null);
        return quote;
    }

    private void create(boolean extras, String mode) throws Exception {
        var horse = send("POST", "/api/horses", owner,
                json.createObjectNode().put("name", "Booking flow fixture"),
                201);
        var draft = (ObjectNode) json.readTree(ApiExamples.DRAFT);
        draft.put("horseId", horse.path("horseId").asLong());
        draft.put("originCountryCode", "VN").put("destinationCountryCode", "CN")
                .put("preferredDepartureAt", time(90))
                .put("deliveryDeadline", time(95))
                .put("eventStartAt", time(100));
        draft.putArray("transitAirports");
        var services = json.createObjectNode().put("roadArrangement",
                "EQUITRANSIT").put("insurance", "NONE");
        if (extras) {
            services.put("veterinaryArrangement", "EQUITRANSIT");
        }
        draft.set("services", services);
        draft.set("route", json.createObjectNode()
                .put("originCountryCode", "VN")
                .put("destinationCountryCode", "CN").put("transportMode", mode)
                .put("originCity", "Hà Nội").put("destinationCity",
                        "Nam Ninh (Nanning)"));
        var saved = send("POST", "/api/transport-orders", owner, draft, 201);
        order = saved.path("orderId").asLong();
        var command = json.createObjectNode()
                .put("recordVersion", saved.path("recordVersion").asText())
                .put("operationId", UUID.randomUUID().toString());
        var submitted = send("POST", base() + "/submit", owner, command, 200);
        revision = submitted.path("revisionId").asLong();
        assertThat(send("POST", base() + "/submit", owner, command, 200))
                .isEqualTo(submitted);
    }

    private void approve() throws Exception {
        var body = command();
        body.put("revisionId", revision).put("decision", "APPROVED")
                .put("specialistId", staff("SPECIALIST"))
                .put("coordinatorId", staff("COORDINATOR"));
        var receipt = send("POST", "/api/manager/transport-orders/" + order
                + "/decisions", token("MANAGER"), body, 200);
        assertThat(send("POST", "/api/manager/transport-orders/" + order
                + "/decisions", token("MANAGER"), body, 200))
                .isEqualTo(receipt);
    }

    private JsonNode createPlan(String mode) throws Exception {
        var leg = (ObjectNode) json.readTree(PlanExamples.LEG_ITEM);
        leg.put("transportMode", mode).put("plannedDeparture", time(90))
                .put("plannedArrival", time(91));
        var body = json.createObjectNode().put("revisionId", revision)
                .put("setAsCurrentDraft", true);
        body.set("command", command());
        body.putArray("legs").add(leg);
        return send("POST", base() + "/plans", token("COORDINATOR"), body, 200);
    }

    private String report(long request, BigDecimal amount) {
        String key = UUID.randomUUID().toString();
        long user = jdbc.queryForObject("SELECT c.UserID FROM dbo.Customer c "
                + "JOIN dbo.TransportOrder o ON o.CustomerID=c.CustomerID "
                + "WHERE o.OrderID=?", Long.class, order);
        jdbc.update("INSERT dbo.PaymentProof(FileKey,OrderID,UserID,MediaType,"
                + "ContentBytes) VALUES(?,?,?,'image/png',?)", key, order,
                user, new byte[]{1});
        jdbc.update("INSERT dbo.PaymentReport(PaymentRequestID,ReportedAmount,"
                + "ProofFileKey,TransferredAt,ReportedCurrency,Status) "
                + "VALUES(?,?,?,DATEADD(second,-5,SYSUTCDATETIME()),'USD',"
                + "'SUBMITTED')", request, amount, key);
        return key;
    }

    private long bank(BigDecimal amount) {
        return jdbc.queryForObject("INSERT dbo.PaymentTransaction("
                + "RecipientAccount,BankTransactionReference,AmountReceived,"
                + "Currency,PostedAt,Status,ReconciledBy,ReconciledAt) "
                + "OUTPUT INSERTED.TransactionID "
                + "VALUES('BOOKING-TEST',?,?,'USD',DATEADD(second,-5,"
                + "SYSUTCDATETIME()),'RECONCILED',?,DATEADD(second,-2,"
                + "SYSUTCDATETIME()))", Long.class, UUID.randomUUID()
                        .toString(), amount, staff("MANAGER"));
    }

    private void allocate(long bank, long request, BigDecimal amount) {
        jdbc.update("INSERT dbo.PaymentAllocation(TransactionID,"
                + "PaymentRequestID,AllocatedAmount,AllocatedAt) VALUES(?,?,?,"
                + "DATEADD(second,-1,SYSUTCDATETIME()))",
                bank, request, amount);
    }

    private long quote() {
        return jdbc.queryForObject("SELECT QuoteID FROM dbo.Quote WHERE "
                + "RevisionID=?", Long.class, revision);
    }

    private long request() {
        return jdbc.queryForObject("SELECT PaymentRequestID FROM "
                + "dbo.PaymentRequest WHERE QuoteID=? AND PaymentStage="
                + "'DEPOSIT'", Long.class, quote());
    }

    private long count(String table, String column, long id) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM " + table
                + " WHERE " + column + "=?", Long.class, id);
    }

    private JsonNode status(String actor) throws Exception {
        return send("GET", base() + "/booking-flow-status", actor, null, 200);
    }

    private ObjectNode command() {
        return json.createObjectNode().put("operationId", UUID.randomUUID()
                .toString()).put("recordVersion", java.util.HexFormat.of()
                .formatHex(jdbc.queryForObject("SELECT RecordVersion FROM "
                        + "dbo.TransportOrder WHERE OrderID=?", byte[].class,
                        order)));
    }

    private long staff(String role) {
        return jdbc.queryForObject("SELECT TOP(1) s.StaffID FROM "
                + "dbo.StaffProfile s JOIN dbo.[User] u ON u.UserID=s.UserID "
                + "JOIN dbo.[Role] r ON r.RoleID=u.RoleID WHERE "
                + "r.RoleCode=? AND u.Status='ACTIVE' AND "
                + "s.StaffStatus='ACTIVE' ORDER BY s.StaffID",
                Long.class, role);
    }

    private String token(String role) {
        long user = jdbc.queryForObject("SELECT TOP(1) u.UserID FROM "
                + "dbo.[User] u JOIN dbo.[Role] r ON r.RoleID=u.RoleID WHERE "
                + "r.RoleCode=? AND u.Status='ACTIVE' ORDER BY u.UserID",
                Long.class, role);
        return "Bearer " + tokens.issue(users.findById(user).orElseThrow())
                .getAccessToken();
    }

    private String customer() {
        var user = new UserAccount();
        user.setRole(roles.findByRoleCode("CUSTOMER").orElseThrow());
        user.setEmail(UUID.randomUUID() + "@example.invalid");
        user.setPasswordHash("{bcrypt}fixture-not-for-login");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        users.saveAndFlush(user);
        var customer = new Customer();
        customer.setUser(user);
        customer.setFullName("Booking ownership fixture");
        customers.saveAndFlush(customer);
        return "Bearer " + tokens.issue(user).getAccessToken();
    }

    private String base() {
        return "/api/transport-orders/" + order;
    }

    private String time(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(days).withNano(0)
                .toString();
    }

    private JsonNode send(String method, String path, String actor,
            JsonNode body, int status) throws Exception {
        var request = MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), path)
                .header("Authorization", actor);
        if (body != null) {
            request.contentType("application/json").content(body.toString());
        }
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString())
                .isEqualTo(status);
        return response.getContentAsString().isEmpty() ? json.nullNode()
                : json.readTree(response.getContentAsString());
    }
}


