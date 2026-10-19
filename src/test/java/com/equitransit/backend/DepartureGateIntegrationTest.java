/*
 * DepartureGateIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify F11 departure gates, manager authorization & horse receipt.
 */
package com.equitransit.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;

import com.equitransit.backend.dto.resource.ResourceExamples;
import com.equitransit.backend.entity.customer.Customer;
import com.equitransit.backend.entity.auth.UserAccount;
import com.equitransit.backend.repository.auth.AccountRoleRepository;
import com.equitransit.backend.repository.customer.CustomerRepository;
import com.equitransit.backend.repository.departure.DepartureRepository;
import com.equitransit.backend.repository.auth.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Native SQL integration suite for F11 departure gates and horse receipt. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_PLAN_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(
        named = "EQ_DEPARTURE_TEST_ENABLED", matches = "true")
public class DepartureGateIntegrationTest {

    private static final String MICROCHIP = "981020000123456";
    private static final String PASSPORT = "FEI-VN-2026-009";

    private final Map<String, Actor> actors = new HashMap<>();
    private final List<Long> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureOrders = new ArrayList<>();
    private final List<Long> fixtureRules = new ArrayList<>();
    private final List<String> fixtureDocTypes = new ArrayList<>();
    private final List<Long> fixtureTx = new ArrayList<>();
    private final Map<String, List<Long>> resources = new HashMap<>();
    private String passwordHash;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource source;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private AccountRoleRepository roles;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private JwtTokenService tokens;
    @Autowired
    private DepartureRepository repository;

    /** @throws Exception if the isolated test catalog guard fails */
    @BeforeEach
    public void setup() throws Exception {
        try (var connection = source.getConnection()) {
            assertThat(connection.getCatalog()).isIn(
                    "EquiTransit_FigmaMigrationTest_20261013_143729",
                    "EquiTransit_FigmaMigrationTest_Bootstrap_20261018");
        }
        assertThat(repository.ready()).isTrue();
        passwordHash = passwords.encode("TestPassword123!");
        actor("MANAGER");
    }

    /** Cleans up all disposable fixtures created by each test. */
    @AfterEach
    public void cleanup() {
        for (long id : fixtureOrders) {
            jdbc.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=NULL,"
                    + "CurrentPlanID=NULL WHERE OrderID=?", id);
        }
        for (long id : fixtureOrders) {
            String rev = " IN (SELECT RevisionID FROM dbo.OrderRevision"
                    + " WHERE OrderID=?)";
            String plan = " IN (SELECT PlanID FROM dbo.TransportPlan"
                    + " WHERE OrderID=?)";
            String leg = " IN (SELECT LegID FROM dbo.TripLeg"
                    + " WHERE PlanID" + plan + ")";
            String choice = " IN (SELECT ChoiceID FROM dbo.OrderServiceChoice"
                    + " WHERE RevisionID" + rev + ")";
            String quote = " IN (SELECT QuoteID FROM dbo.Quote"
                    + " WHERE RevisionID" + rev + ")";
            String req = " IN (SELECT PaymentRequestID FROM dbo.PaymentRequest"
                    + " WHERE QuoteID" + quote + ")";
            String docReq = " IN (SELECT RequirementID FROM "
                    + "dbo.DocumentRequirement WHERE RevisionID" + rev + ")";

            jdbc.update("DELETE dbo.PlanOperation WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.PaymentOperation WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.AuditEvent WHERE EntityID=?", id);
            jdbc.update("DELETE dbo.HorseReceipt WHERE PlanID" + plan, id);
            jdbc.update("DELETE dbo.FlightReservation WHERE LegID" + leg, id);
            jdbc.update("DELETE dbo.StallAssignment WHERE LegID" + leg, id);
            jdbc.update("DELETE dbo.TripAssignment WHERE LegID" + leg, id);
            jdbc.update("DELETE dbo.TripLeg WHERE PlanID" + plan, id);
            jdbc.update("DELETE dbo.TransportPlan WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.QuarantineReservation WHERE "
                    + "QuarantineChoiceID IN (SELECT QuarantineChoiceID FROM "
                    + "dbo.QuarantineChoice WHERE ChoiceID" + choice + ")", id);
            jdbc.update("DELETE dbo.InsurancePolicy WHERE InsuranceChoiceID "
                    + "IN (SELECT InsuranceChoiceID FROM dbo.InsuranceChoice "
                    + "WHERE ChoiceID" + choice + ")", id);
            jdbc.update("DELETE dbo.Refund WHERE AllocationID IN (SELECT "
                    + "AllocationID FROM dbo.PaymentAllocation WHERE "
                    + "PaymentRequestID" + req + ")", id);
            jdbc.update("DELETE dbo.PaymentAllocation WHERE PaymentRequestID"
                    + req, id);
            jdbc.update("DELETE dbo.PaymentReport WHERE PaymentRequestID"
                    + req, id);
            jdbc.update("DELETE dbo.PaymentRequest WHERE QuoteID" + quote, id);
            jdbc.update("DELETE dbo.PaymentProof WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.QuoteItem WHERE QuoteID" + quote, id);
            jdbc.update("DELETE dbo.QuotePaymentSchedule WHERE QuoteID"
                    + quote, id);
            jdbc.update("DELETE dbo.Quote WHERE RevisionID" + rev, id);
            jdbc.update("DELETE dbo.SupplierOffer WHERE RevisionID" + rev, id);
            jdbc.update("DELETE dbo.DocumentReview WHERE SubmissionID IN "
                    + "(SELECT SubmissionID FROM dbo.DocumentSubmission "
                    + "WHERE RequirementID" + docReq + ")", id);
            jdbc.update("DELETE dbo.DocumentSubmission WHERE RequirementID"
                    + docReq, id);
            jdbc.update("DELETE dbo.DocumentRequirement WHERE RevisionID"
                    + rev, id);
            jdbc.update("DELETE dbo.AuthorityProcedure WHERE RevisionID"
                    + rev, id);
            jdbc.update("DELETE dbo.ComplianceClearance WHERE RevisionID"
                    + rev, id);
            jdbc.update("DELETE dbo.ComplianceChecklist WHERE RevisionID"
                    + rev, id);
            jdbc.update("DELETE dbo.ComplianceFile WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.RevisionQuarantineAssessment WHERE "
                    + "RevisionID" + rev, id);
            jdbc.update("DELETE dbo.RevisionVeterinaryAssessment WHERE "
                    + "RevisionID" + rev, id);
            jdbc.update("DELETE dbo.OrderDecision WHERE RevisionID" + rev, id);
            for (String table : List.of("StallChoice", "QuarantineChoice",
                    "InsuranceChoice")) {
                jdbc.update("DELETE dbo." + table + " WHERE ChoiceID"
                        + choice, id);
            }
            jdbc.update("DELETE dbo.OrderServiceChoice WHERE RevisionID"
                    + rev, id);
            jdbc.update("DELETE dbo.OrderAssignment WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.TransportOrderDetail WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.OrderRevision WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.TransportOrder WHERE OrderID=?", id);
        }
        for (long tx : fixtureTx) {
            jdbc.update("DELETE dbo.PaymentTransaction WHERE TransactionID=?",
                    tx);
        }
        for (long rule : fixtureRules) {
            jdbc.update("DELETE dbo.ComplianceRuleVersion WHERE RuleID=?",
                    rule);
        }
        resources.getOrDefault("policies", List.of()).forEach(id ->
                jdbc.update("DELETE dbo.StallFitPolicy WHERE PolicyID=?", id));
        resources.getOrDefault("stalls", List.of()).forEach(id ->
                jdbc.update("DELETE dbo.StallEquipment WHERE StallID=?", id));
        resources.getOrDefault("vehicles", List.of()).forEach(id ->
                jdbc.update("DELETE dbo.FleetVehicle WHERE VehicleID=?", id));
        resources.getOrDefault("suppliers", List.of()).forEach(id ->
                jdbc.update("DELETE dbo.Supplier WHERE SupplierID=?", id));
        for (long id : fixtureUsers) {
            jdbc.update("DELETE dbo.DocumentVersion WHERE DocumentID IN "
                    + "(SELECT hd.DocumentID FROM dbo.HorseDocument hd JOIN "
                    + "dbo.Horse h ON h.HorseID=hd.HorseID JOIN dbo.Customer c "
                    + "ON c.CustomerID=h.CustomerID WHERE c.UserID=?)", id);
            jdbc.update("DELETE dbo.HorseDocument WHERE HorseID IN (SELECT "
                    + "h.HorseID FROM dbo.Horse h JOIN dbo.Customer c ON "
                    + "c.CustomerID=h.CustomerID WHERE c.UserID=?)", id);
            jdbc.update("DELETE h FROM dbo.Horse h JOIN dbo.Customer c "
                    + "ON c.CustomerID=h.CustomerID WHERE c.UserID=?", id);
            jdbc.update("DELETE dbo.ResourceOperation WHERE UserID=?", id);
            jdbc.update("DELETE dbo.ComplianceOperation WHERE UserID=?", id);
            jdbc.update("DELETE dbo.AuditEvent WHERE UserID=?", id);
            jdbc.update("DELETE dbo.StaffProfile WHERE UserID=?", id);
            jdbc.update("DELETE dbo.Customer WHERE UserID=?", id);
            jdbc.update("DELETE dbo.[User] WHERE UserID=?", id);
        }
        for (String docType : fixtureDocTypes) {
            jdbc.update("DELETE dbo.DocumentTypeCatalog WHERE TypeCode=?",
                    docType);
        }
        fixtureOrders.clear();
        fixtureTx.clear();
        fixtureRules.clear();
        fixtureDocTypes.clear();
        resources.clear();
        fixtureUsers.clear();
        actors.clear();
    }

    /**
     * Full F11 lifecycle: 3-gate evaluation, unreconciled report rejection,
     * no-bypass legal gate, manager authorization, driver identity check,
     * BR-09 stale gate invalidation, atomic depart and customer redaction.
     *
     * @throws Exception if any HTTP or SQL assertion fails
     */
    @Test
    public void fullDepartureGateReceiptAndAtomicDepartLifecycle()
            throws Exception {
        long horse = createMeasuredHorse();
        var f = createConfirmedPlanFixture(horse);
        long order = f.orderId();
        long rev = f.revisionId();
        long quote = f.quoteId();
        long plan = f.planId();
        long firstLeg = f.firstLegId();

        // 1. Customer PaymentReport (unreconciled) does NOT open Financial Gate
        long scheduleId = jdbc.queryForObject("SELECT ScheduleID FROM "
                + "dbo.QuotePaymentSchedule WHERE QuoteID=?",
                Long.class, quote);
        long payReqId = jdbc.queryForObject("INSERT dbo.PaymentRequest "
                + "(QuoteID,ScheduleID,PaymentNumber,PaymentStage,AmountDue,"
                + "Currency,DueAt,Status) OUTPUT INSERTED.PaymentRequestID "
                + "SELECT QuoteID,ScheduleID,'PAY-F11',PaymentStage,AmountDue,"
                + "Currency,DueAt,'OPEN' FROM dbo.QuotePaymentSchedule "
                + "WHERE ScheduleID=?", Long.class, scheduleId);
        jdbc.update("INSERT dbo.PaymentReport (PaymentRequestID,ReportedAmount,"
                + "BankReference,ProofFileKey,TransferredAt,ReportedCurrency,"
                + "Status) VALUES (?,1500.00,'CUST-REP-1','proof-1',"
                + "SYSUTCDATETIME(),'USD','SUBMITTED')", payReqId);

        var eval1 = postJson(depBase(order) + "/gates/evaluate",
                Map.of("command", command(order)),
                actor("COORDINATOR").token(), 200);
        assertThat(eval1.at("/financialGate/status").asText())
                .isEqualTo("BLOCKED");
        assertThat(eval1.at("/financialGate/blockers").toString())
                .contains("NO_RECONCILED_RECEIPT",
                        "SCHEDULE_UNPAID:" + scheduleId);
        assertThat(eval1.at("/legalGate/status").asText())
                .isEqualTo("BLOCKED");
        assertThat(eval1.at("/legalGate/blockers").toString())
                .contains("RULE_ASSESSMENT_PENDING");
        assertThat(eval1.at("/operationalGate/status").asText())
                .isEqualTo("PASS");
        assertThat(eval1.get("readyForAuthorization").asBoolean()).isFalse();

        // 2. Reconciled bank allocation opens Financial Gate, but Manager STILL
        //    cannot bypass Legal Gate!
        long allocId = seedReconciledAllocation(payReqId);
        var eval2 = postJson(depBase(order) + "/gates/evaluate",
                Map.of("command", command(order)),
                actor("MANAGER").token(), 200);
        assertThat(eval2.at("/financialGate/status").asText())
                .isEqualTo("PASS");
        assertThat(eval2.at("/legalGate/status").asText())
                .isEqualTo("BLOCKED");

        var bypassAttempt = postJson(depBase(order) + "/authorize",
                Map.of("command", command(order), "planId", plan),
                actor("MANAGER").token(), 409);
        assertThat(bypassAttempt.get("code").asText())
                .isEqualTo("DEPARTURE_GATE_BLOCKED");

        // 3. Seed PRE_EXPORT legal compliance and clearance -> all 3 gates PASS
        long clearanceId = seedPreExportClearance(order, rev, horse);
        var eval3 = postJson(depBase(order) + "/gates/evaluate",
                Map.of("command", command(order)),
                actor("COORDINATOR").token(), 200);
        assertThat(eval3.at("/financialGate/status").asText())
                .isEqualTo("PASS");
        assertThat(eval3.at("/legalGate/status").asText())
                .isEqualTo("PASS");
        assertThat(eval3.at("/operationalGate/status").asText())
                .isEqualTo("PASS");
        assertThat(eval3.get("readyForAuthorization").asBoolean()).isTrue();

        // Coordinator cannot authorize departure (403 Forbidden)
        postJson(depBase(order) + "/authorize",
                Map.of("command", command(order), "planId", plan),
                actor("COORDINATOR").token(), 403);

        // Active MANAGER authorizes departure (200 OK)
        var authRes = postJson(depBase(order) + "/authorize",
                Map.of("command", command(order), "planId", plan),
                actor("MANAGER").token(), 200);
        assertThat(authRes.get("departureAuthorizedBy").asLong())
                .isEqualTo(actor("MANAGER").staff());
        assertThat(authRes.get("departureAuthorizedAt").isNull()).isFalse();
        assertThat(authRes.get("readyForDeparture").asBoolean()).isFalse();
        assertThat(authRes.get("departureBlockers").toString())
                .contains("HORSE_RECEIPT_MISSING");

        // 4. Role & assignment guards on HorseReceipt, and MISMATCHED check
        long unassignedDriver = createExtraDriver();
        postJson(depBase(order) + "/horse-receipts",
                Map.of("command", command(order), "planId", plan,
                        "legId", firstLeg, "observedMicrochip", MICROCHIP,
                        "observedPassportNumber", PASSPORT,
                        "fitToTravel", true,
                        "locationDescription", "Hanoi Stable Gate A"),
                unassignedDriverToken(unassignedDriver), 403);
        postJson(depBase(order) + "/horse-receipts",
                Map.of("command", command(order), "planId", plan,
                        "legId", firstLeg, "observedMicrochip", MICROCHIP,
                        "observedPassportNumber", PASSPORT,
                        "fitToTravel", true,
                        "locationDescription", "Hanoi Stable Gate A"),
                actor("MANAGER").token(), 403);

        // Assigned DRIVER submits wrong microchip -> MISMATCHED / REJECTED
        var mismatchRes = postJson(depBase(order) + "/horse-receipts",
                Map.of("command", command(order), "planId", plan,
                        "legId", firstLeg,
                        "observedMicrochip", "981020000999999",
                        "observedPassportNumber", PASSPORT,
                        "fitToTravel", true,
                        "conditionNotes", "Wrong chip scanned",
                        "locationDescription", "Hanoi Stable Gate A"),
                actor("DRIVER").token(), 200);
        long badReceiptId = mismatchRes.at("/receipts/0/receiptId").asLong();
        assertThat(mismatchRes.at("/receipts/0/identityCheckResult").asText())
                .isEqualTo("MISMATCHED");
        assertThat(mismatchRes.at("/receipts/0/receiptStatus").asText())
                .isEqualTo("REJECTED");
        assertThat(mismatchRes.get("readyForDeparture").asBoolean()).isFalse();

        // Attempting depart with MISMATCHED receipt fails (409 Conflict)
        var badDepart = postJson(depBase(order) + "/depart",
                Map.of("command", command(order), "planId", plan,
                        "legId", firstLeg, "receiptId", badReceiptId),
                actor("DRIVER").token(), 409);
        assertThat(badDepart.get("code").asText())
                .isEqualTo("HORSE_RECEIPT_REJECTED_OR_MISMATCHED");

        // Assigned DRIVER submits matching microchip & passport + idempotent
        // retry on the same operationId
        var receiptCmd = command(order);
        var receiptBody = Map.of("command", receiptCmd, "planId", plan,
                "legId", firstLeg, "observedMicrochip", MICROCHIP,
                "observedPassportNumber", PASSPORT, "fitToTravel", true,
                "conditionNotes", "Calm, hydrated, temperature normal.",
                "evidenceFileKey", "f3046c13-4c31-49b9-8695-edbab59eec2f",
                "locationDescription", "Hanoi Stable Gate A");
        var matchedRes = postJson(depBase(order) + "/horse-receipts",
                receiptBody, actor("DRIVER").token(), 200);
        var retryRes = postJson(depBase(order) + "/horse-receipts",
                receiptBody, actor("DRIVER").token(), 200);
        long goodReceiptId = matchedRes.at("/receipts/0/receiptId").asLong();
        assertThat(retryRes.at("/receipts/0/receiptId").asLong())
                .isEqualTo(goodReceiptId);
        assertThat(matchedRes.get("receipts").size()).isEqualTo(2);
        assertThat(matchedRes.at("/receipts/0/identityCheckResult").asText())
                .isEqualTo("MATCHED");
        assertThat(matchedRes.at("/receipts/0/receiptStatus").asText())
                .isEqualTo("ACCEPTED");
        assertThat(matchedRes.get("readyForDeparture").asBoolean()).isTrue();

        // 5. BR-09 Stale Gate Invalidation after authorization:
        //    (a) Pending refund blocks depart
        long refundId = jdbc.queryForObject("INSERT dbo.Refund (AllocationID,"
                + "Amount,Reason,Status,RequestedBy,RequestedAt) "
                + "OUTPUT INSERTED.RefundID VALUES (?,100.00,'Dispute',"
                + "'REQUESTED',?,SYSUTCDATETIME())",
                Long.class, allocId, actor("CUSTOMER").user());
        var staleFin = postJson(depBase(order) + "/depart",
                Map.of("command", command(order), "planId", plan,
                        "legId", firstLeg, "receiptId", goodReceiptId),
                actor("DRIVER").token(), 409);
        assertThat(staleFin.get("code").asText())
                .isEqualTo("DEPARTURE_GATE_BLOCKED");
        assertThat(staleFin.get("message").asText()).contains("REFUND_PENDING");
        jdbc.update("DELETE dbo.Refund WHERE RefundID=?", refundId);

        //    (b) Expired/held legal clearance blocks depart
        jdbc.update("UPDATE dbo.ComplianceClearance SET Status='HOLD' "
                + "WHERE ClearanceID=?", clearanceId);
        var staleLegal = postJson(depBase(order) + "/depart",
                Map.of("command", command(order), "planId", plan,
                        "legId", firstLeg, "receiptId", goodReceiptId),
                actor("DRIVER").token(), 409);
        assertThat(staleLegal.get("code").asText())
                .isEqualTo("DEPARTURE_GATE_BLOCKED");
        assertThat(staleLegal.get("message").asText())
                .contains("PHASE_CLEARANCE_PENDING");
        jdbc.update("UPDATE dbo.ComplianceClearance SET Status='CLEARED' "
                + "WHERE ClearanceID=?", clearanceId);

        //    (c) Vehicle put in maintenance blocks depart
        jdbc.update("UPDATE dbo.FleetVehicle SET OperationalStatus="
                + "'MAINTENANCE' WHERE VehicleID=?", f.vehicleId());
        var staleOps = postJson(depBase(order) + "/depart",
                Map.of("command", command(order), "planId", plan,
                        "legId", firstLeg, "receiptId", goodReceiptId),
                actor("DRIVER").token(), 409);
        assertThat(staleOps.get("code").asText())
                .isEqualTo("DEPARTURE_GATE_BLOCKED");
        assertThat(staleOps.get("message").asText())
                .contains("ROAD_VEHICLE_UNAVAILABLE");
        jdbc.update("UPDATE dbo.FleetVehicle SET OperationalStatus='ACTIVE' "
                + "WHERE VehicleID=?", f.vehicleId());

        // 6. Atomic depart by assigned DRIVER transitions leg, plan and order
        var departed = postJson(depBase(order) + "/depart",
                Map.of("command", command(order), "planId", plan,
                        "legId", firstLeg, "receiptId", goodReceiptId),
                actor("DRIVER").token(), 200);
        assertThat(departed.get("orderStatus").asText())
                .isEqualTo("IN_TRANSIT");
        assertThat(departed.get("planStatus").asText())
                .isEqualTo("IN_TRANSIT");
        assertThat(departed.get("firstLegStatus").asText())
                .isEqualTo("IN_TRANSIT");
        assertThat(departed.get("firstLegActualDeparture").isNull()).isFalse();

        Integer auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.AuditEvent WHERE EntityID=? AND ActionCode='DEPARTED'",
                Integer.class, order);
        assertThat(auditCount).isEqualTo(1);

        // 7. Customer view redacts internal staff IDs, microchip and notes
        var custView = read(mvc.perform(get(depBase(order))
                .header("Authorization", actor("CUSTOMER").token()))
                .andExpect(status().isOk()).andReturn());
        assertThat(custView.get("orderStatus").asText())
                .isEqualTo("IN_TRANSIT");
        assertThat(custView.get("departureAuthorizedBy").isNull()).isTrue();
        assertThat(custView.at("/receipts/0/receivedBy").isNull()).isTrue();
        assertThat(custView.at("/receipts/0/observedMicrochip").isNull())
                .isTrue();
        assertThat(custView.at("/receipts/0/observedPassportNumber").isNull())
                .isTrue();
        assertThat(custView.at("/receipts/0/conditionNotes").isNull()).isTrue();
        assertThat(custView.at("/receipts/0/evidenceFileKey").isNull())
                .isTrue();
        assertThat(custView.at("/receipts/0/identityCheckResult").asText())
                .isEqualTo("MATCHED");
        assertThat(custView.at("/receipts/0/receiptStatus").asText())
                .isEqualTo("ACCEPTED");
    }

    private ConfirmedFixture createConfirmedPlanFixture(long horseId)
            throws Exception {
        var now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        var start = now.plusDays(7);
        var end = start.plusHours(2);
        var fields = new HashMap<String, Object>();

        fields.put("horseId", horseId);
        fields.put("originCountryCode", "VN");
        fields.put("destinationCountryCode", "GB");
        fields.put("originAddress", "Hanoi Stable");
        fields.put("destinationAddress", "Newmarket Stable");
        fields.put("originTimezone", "Asia/Ho_Chi_Minh");
        fields.put("destinationTimezone", "Europe/London");
        fields.put("importType", "TEMPORARY");
        fields.put("preferredDepartureAt", start.toString());
        fields.put("deliveryDeadline", now.plusDays(30).toString());
        fields.put("services", Map.of("roadArrangement", "EQUITRANSIT",
                "airStall", "WIDE", "quarantinePackage", "STANDARD",
                "insurance", "COMBINED", "declaredValue", 50000));
        var draft = postJson("/api/transport-orders", fields,
                actor("CUSTOMER").token(), 201);
        long order = draft.get("orderId").asLong();

        fixtureOrders.add(order);
        postJson("/api/transport-orders/" + order + "/submit",
                Map.of("recordVersion", draft.get("recordVersion").asText(),
                        "operationId", UUID.randomUUID().toString()),
                actor("CUSTOMER").token(), 200);
        var current = read(mvc.perform(get("/api/transport-orders/" + order)
                .header("Authorization", actor("CUSTOMER").token()))
                .andExpect(status().isOk()).andReturn());
        long rev = current.get("currentRevisionId").asLong();

        postJson("/api/manager/transport-orders/" + order + "/decisions",
                Map.of("recordVersion", current.get("recordVersion").asText(),
                        "operationId", UUID.randomUUID().toString(),
                        "revisionId", rev, "decision", "APPROVED",
                        "specialistId", actor("SPECIALIST").staff(),
                        "coordinatorId", actor("COORDINATOR").staff()),
                actor("MANAGER").token(), 200);
        jdbc.update("INSERT dbo.RevisionQuarantineAssessment (RevisionID,"
                + "Applicability,RequiredDays,SpecialistID,AssessedAt,"
                + "ValidUntil,SourceReference) VALUES (?,'REQUIRED',14,?,"
                + "DATEADD(second,-1,SYSUTCDATETIME()),DATEADD(day,60,"
                + "SYSUTCDATETIME()),'Official quarantine rule')",
                rev, actor("SPECIALIST").staff());
        jdbc.update("UPDATE q SET RequiredDays=14,ConfirmedBy=?,"
                + "DaysConfirmedAt=SYSUTCDATETIME() FROM dbo.QuarantineChoice q"
                + " JOIN dbo.OrderServiceChoice c ON c.ChoiceID=q.ChoiceID "
                + "WHERE c.RevisionID=?", actor("SPECIALIST").staff(), rev);

        var vehicle = createVehicleResource();
        long vehicleId = vehicle.get("id").asLong();
        String vehicleVer = vehicle.get("recordVersion").asText();
        String planBase = "/api/transport-orders/" + order + "/plans";

        var planDraft = postJson(planBase, Map.of("command", command(order),
                "revisionId", rev, "setAsCurrentDraft", true, "legs", List.of(
                        Map.of("sequenceNo", 1, "transportMode", "ROAD",
                                "startPoint", "Hanoi Stable",
                                "endPoint", "Border Checkpoint",
                                "distanceKm", "45.500",
                                "distanceSource", "Google Maps verified",
                                "plannedDeparture", start.toString(),
                                "plannedArrival", end.toString()))),
                actor("COORDINATOR").token(), 200);
        long planId = planDraft.get("planId").asLong();
        long firstLegId = planDraft.at("/legs/0/legId").asLong();

        postJson(planBase + "/" + planId + "/legs/" + firstLegId
                + "/road-assignment",
                Map.of("command", command(order), "vehicleId", vehicleId,
                        "vehicleRecordVersion", vehicleVer,
                        "driverStaffId", actor("DRIVER").staff(),
                        "driverBufferMinutes", 30),
                actor("COORDINATOR").token(), 200);
        postJson(planBase + "/" + planId + "/quarantine-reservation",
                Map.of("command", command(order), "revisionId", rev,
                        "facilityName", "Quarantine A", "reservationReference",
                        "Q-F11", "startsAt", end.toString(), "endsAt",
                        end.plusDays(14).toString(), "status", "CONFIRMED",
                        "evidenceFileKey", "q.pdf", "expiresAt",
                        end.plusDays(20).toString()),
                actor("COORDINATOR").token(), 200);
        postJson(planBase + "/" + planId + "/insurance-policy",
                Map.of("command", command(order), "revisionId", rev,
                        "insurerName", "EquiInsure", "policyNumber", "POL-F11",
                        "evidenceFileKey", "ins.pdf", "coverageFrom",
                        start.minusHours(1).toString(), "coverageTo",
                        end.plusDays(15).toString(), "status", "CONFIRMED"),
                actor("COORDINATOR").token(), 200);

        long quoteId = seedAcceptedQuote(order, rev);
        postJson(planBase + "/" + planId + "/confirm",
                Map.of("command", command(order), "acceptedQuoteId", quoteId),
                actor("COORDINATOR").token(), 200);
        return new ConfirmedFixture(order, rev, quoteId, planId,
                firstLegId, vehicleId);
    }

    private long seedAcceptedQuote(long order, long rev) {
        long quoteId = jdbc.queryForObject("DECLARE @ids TABLE (ID BIGINT); "
                + "INSERT dbo.Quote (RevisionID,QuoteNumber,VersionNo,"
                + "Currency,TotalQuotedAmount,QuoteStatus,PreparedBy,"
                + "SubmittedAt,ValidUntil,IssuedBy,IssuedAt,"
                + "AcceptedByCustomerID,AcceptedAt,TermsVersion,TermsText,"
                + "CancellationTerms) OUTPUT INSERTED.QuoteID INTO @ids "
                + "VALUES (?,'Q-' + CONVERT(varchar(20),?) + '-1',1,'USD',"
                + "1500.00,'ACCEPTED',?,DATEADD(minute,-15,SYSUTCDATETIME()),"
                + "DATEADD(day,30,SYSUTCDATETIME()),?,"
                + "DATEADD(minute,-10,SYSUTCDATETIME()),"
                + "(SELECT CustomerID FROM dbo.TransportOrder WHERE OrderID=?),"
                + "DATEADD(minute,-5,SYSUTCDATETIME()),'V1','Terms','Cancel'); "
                + "SELECT ID FROM @ids", Long.class, rev, order,
                actor("COORDINATOR").staff(), actor("MANAGER").staff(), order);

        jdbc.update("INSERT dbo.QuotePaymentSchedule (QuoteID,SequenceNo,"
                + "PaymentStage,AmountDue,Currency,DueAt) VALUES "
                + "(?,1,'DEPOSIT',1500.00,'USD',"
                + "DATEADD(minute,-2,SYSUTCDATETIME()))", quoteId);
        return quoteId;
    }

    private long seedReconciledAllocation(long payReqId) {
        long txId = jdbc.queryForObject("INSERT dbo.PaymentTransaction "
                + "(ReconciledBy,RecipientAccount,BankTransactionReference,"
                + "AmountReceived,Currency,PostedAt,ReconciledAt,Status,"
                + "VerificationSource,EvidenceFileKey,InternalNotes) "
                + "OUTPUT INSERTED.TransactionID VALUES (?,'EQ-USD-001',?,"
                + "1500.00,'USD',DATEADD(minute,-2,SYSUTCDATETIME()),"
                + "DATEADD(minute,-1,SYSUTCDATETIME()),'RECONCILED',"
                + "'BANK_STATEMENT','proof-bank','Verified deposit')",
                Long.class, actor("MANAGER").staff(),
                "BANK-" + UUID.randomUUID());

        fixtureTx.add(txId);
        return jdbc.queryForObject("INSERT dbo.PaymentAllocation "
                + "(TransactionID,PaymentRequestID,AllocatedAmount,"
                + "AllocatedAt) OUTPUT INSERTED.AllocationID VALUES "
                + "(?,?,1500.00,DATEADD(second,-5,SYSUTCDATETIME()))",
                Long.class, txId, payReqId);
    }

    private long seedPreExportClearance(long order, long rev, long horse) {
        long staff = actor("SPECIALIST").staff();
        long user = actor("SPECIALIST").user();
        String docType = "HC_" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase();

        jdbc.update("INSERT dbo.DocumentTypeCatalog (TypeCode,DisplayName,"
                + "IsActive) VALUES (?,'Export Health Certificate',1)",
                docType);
        fixtureDocTypes.add(docType);
        long ruleId = jdbc.queryForObject("INSERT dbo.ComplianceRuleVersion "
                + "(RuleCode,VersionNo,OriginCountryCode,"
                + "DestinationCountryCode,DocumentType,PhaseCode,SourceURL,"
                + "ReviewedAt,EffectiveFrom,EffectiveTo,TransitCountryCode,"
                + "AuthorityRequired,RuleDescription) OUTPUT INSERTED.RuleID "
                + "VALUES (?,1,'VN','GB',?,'PRE_EXPORT',"
                + "'https://gov.example/rules/vn-gb',"
                + "DATEADD(day,-2,SYSUTCDATETIME()),"
                + "DATEADD(day,-10,SYSUTCDATETIME()),"
                + "DATEADD(day,60,SYSUTCDATETIME()),NULL,0,'Export health')",
                Long.class, "R-" + UUID.randomUUID().toString()
                        .substring(0, 8), docType);

        fixtureRules.add(ruleId);
        jdbc.update("INSERT dbo.ComplianceChecklist (RevisionID,PhaseCode,"
                + "TransitCountriesJSON,RuleIDsJSON,SourceReference,"
                + "AssessedAt,ValidUntil,SpecialistID) VALUES (?,'PRE_EXPORT',"
                + "'[]',?,'https://gov.example/rules/vn-gb',"
                + "DATEADD(minute,-5,SYSUTCDATETIME()),"
                + "DATEADD(day,30,SYSUTCDATETIME()),?)",
                rev, "[" + ruleId + "]", staff);
        long reqId = jdbc.queryForObject("INSERT dbo.DocumentRequirement "
                + "(RevisionID,RuleID,DocumentType,PhaseCode,IsMandatory,"
                + "Status,ScopeCode,HorseID,Instructions) OUTPUT "
                + "INSERTED.RequirementID VALUES (?,?,?,"
                + "'PRE_EXPORT',1,'APPROVED','HORSE',?,'Health cert')",
                Long.class, rev, ruleId, docType, horse);
        UUID fileKey = UUID.randomUUID();
        byte[] bytes = "PDF-EVIDENCE".getBytes(StandardCharsets.UTF_8);
        String hash = TokenTools.hash("PDF-EVIDENCE");

        jdbc.update("INSERT dbo.ComplianceFile (FileKey,OrderID,RevisionID,"
                + "UserID,ContentType,OriginalFileName,ContentBytes,FileHash) "
                + "VALUES (?,?,?,?,'application/pdf','cert.pdf',?,?)",
                fileKey, order, rev, user, bytes, hash);
        long docId = jdbc.queryForObject("INSERT dbo.HorseDocument "
                + "(HorseID,DocumentType) OUTPUT INSERTED.DocumentID "
                + "VALUES (?,?)", Long.class, horse, docType);
        long verId = jdbc.queryForObject("INSERT dbo.DocumentVersion "
                + "(DocumentID,VersionNo,DocumentNumber,FileKey,FileHash,"
                + "IssuedDate,ExpiryDate,UploadedBy,UploadedAt,"
                + "OriginalFileName,ContentType,FileSizeBytes) OUTPUT "
                + "INSERTED.DocumentVersionID VALUES (?,1,'HC-001',?,?,"
                + "CAST(DATEADD(day,-2,SYSUTCDATETIME()) AS date),"
                + "CAST(DATEADD(day,30,SYSUTCDATETIME()) AS date),?,"
                + "DATEADD(minute,-4,SYSUTCDATETIME()),'cert.pdf',"
                + "'application/pdf',12)",
                Long.class, docId, fileKey.toString(), hash, user);
        long subId = jdbc.queryForObject("INSERT dbo.DocumentSubmission "
                + "(RequirementID,DocumentVersionID,Status) OUTPUT "
                + "INSERTED.SubmissionID VALUES (?,?,'APPROVED')",
                Long.class, reqId, verId);

        jdbc.update("INSERT dbo.DocumentReview (SubmissionID,ReviewedBy,"
                + "Decision,Feedback,ReviewedAt) VALUES (?,?,'APPROVED','OK',"
                + "DATEADD(minute,-3,SYSUTCDATETIME()))", subId, staff);
        return jdbc.queryForObject("INSERT dbo.ComplianceClearance "
                + "(RevisionID,SpecialistID,PhaseCode,Status,CheckedAt,"
                + "ExpiresAt) OUTPUT INSERTED.ClearanceID VALUES "
                + "(?,?,'PRE_EXPORT','CLEARED',"
                + "DATEADD(minute,-2,SYSUTCDATETIME()),"
                + "DATEADD(day,15,SYSUTCDATETIME()))",
                Long.class, rev, staff);
    }

    private long createMeasuredHorse() throws Exception {
        var horse = postJson("/api/horses", Map.of("name", "Eclipse F11",
                "passportNumber", PASSPORT, "microchipId", MICROCHIP,
                "weightKg", 500, "heightCm", 160, "bodyWidthCm", 70,
                "measuredAt", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
                        .withNano(0).toString()),
                actor("CUSTOMER").token(), 201);

        return horse.get("horseId").asLong();
    }

    private JsonNode createVehicleResource() throws Exception {
        var result = mapper.createObjectNode();
        var data = (ObjectNode) mapper.readTree(ResourceExamples.VEHICLE);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        data.put("vehicleCode", "V-" + suffix);
        data.put("plateNumber", "P-" + suffix);
        ((ObjectNode) data.get("window")).put("bufferMinutes", 0);
        result.set("command", mapper.valueToTree(Map.of("operationId",
                UUID.randomUUID().toString())));
        result.set("data", data);
        var res = postJson("/api/manager/resources/vehicles", result,
                actor("MANAGER").token(), 200);

        resources.computeIfAbsent("vehicles", k -> new ArrayList<>())
                .add(res.get("id").asLong());
        return res;
    }

    private long createExtraDriver() {
        var user = new UserAccount();

        user.setRole(roles.findByRoleCode("DRIVER").orElseThrow());
        user.setEmail(UUID.randomUUID() + "@example.invalid");
        user.setPasswordHash(passwordHash);
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        users.saveAndFlush(user);
        fixtureUsers.add(user.getUserId());
        return jdbc.queryForObject("INSERT dbo.StaffProfile (UserID,FullName) "
                + "OUTPUT INSERTED.StaffID VALUES (?,'Unassigned Driver')",
                Long.class, user.getUserId());
    }

    private String unassignedDriverToken(long staffId) {
        Long userId = jdbc.queryForObject("SELECT UserID FROM "
                + "dbo.StaffProfile WHERE StaffID=?", Long.class, staffId);
        var user = users.findById(userId).orElseThrow();

        user.setRole(roles.findByRoleCode("DRIVER").orElseThrow());
        return "Bearer " + tokens.issue(user).getAccessToken();
    }

    private Actor actor(String role) {
        return actors.computeIfAbsent(role, name -> {
            var user = new UserAccount();

            user.setRole(roles.findByRoleCode(name).orElseThrow());
            user.setEmail(UUID.randomUUID() + "@example.invalid");
            user.setPasswordHash(passwordHash);
            user.setStatus("ACTIVE");
            user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
            users.saveAndFlush(user);
            fixtureUsers.add(user.getUserId());
            Long staff = null;

            if (role.equals("CUSTOMER")) {
                var customer = new Customer();

                customer.setUser(user);
                customer.setFullName("Departure test customer");
                customers.saveAndFlush(customer);
            } else {
                staff = jdbc.queryForObject("INSERT dbo.StaffProfile "
                        + "(UserID,FullName) OUTPUT INSERTED.StaffID "
                        + "VALUES (?,?)", Long.class, user.getUserId(),
                        "Departure test " + name);
            }
            return new Actor(user.getUserId(), staff,
                    "Bearer " + tokens.issue(user).getAccessToken());
        });
    }

    private Map<String, Object> command(long order) {
        byte[] ver = jdbc.queryForObject("SELECT RecordVersion FROM "
                + "dbo.TransportOrder WHERE OrderID=?", byte[].class, order);

        return Map.of("operationId", UUID.randomUUID().toString(),
                "recordVersion", HexFormat.of().formatHex(ver));
    }

    private String depBase(long order) {
        return "/api/transport-orders/" + order + "/departure";
    }

    private JsonNode postJson(String url, Object body, String token,
            int expected) throws Exception {
        var res = mvc.perform(post(url).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andReturn();

        assertThat(res.getResponse().getStatus())
                .as(res.getResponse().getContentAsString()).isEqualTo(expected);
        return read(res);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private record Actor(long user, Long staff, String token) { }

    private record ConfirmedFixture(
            long orderId, long revisionId, long quoteId, long planId,
            long firstLegId, long vehicleId) { }
}
