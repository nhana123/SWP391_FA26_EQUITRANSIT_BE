/*
 * ComplianceIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify F08 using isolated native SQL only.
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
import com.equitransit.backend.service.compliance.ComplianceReadinessFacade;
import com.equitransit.backend.service.pricing.ServiceAssessmentFacade;
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



/** Native legal fixtures are removed only from the exact isolated catalog. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_COMPLIANCE_TEST_DB_URL}",
        "app.compliance.auto-approval=false",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_COMPLIANCE_TEST_ENABLED",
        matches = "true")
@SuppressWarnings("null")
public class ComplianceIntegrationTest {

    private final List<Long> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureTariffs = new ArrayList<>();
    private final List<Long> fixtureRules = new ArrayList<>();
    private final List<Long> fixtureVehicles = new ArrayList<>();
    private final String type = "T_"
            + UUID.randomUUID().toString().substring(0, 20).toUpperCase();
    private long order;
    private long revision;
    private String owner;
    private String other;
    private String manager;
    private String coordinator;
    private String specialist;
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
    private UserAccountRepository users;
    @Autowired
    private AccountRoleRepository roles;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private JwtTokenService tokens;
    @Autowired
    private ServiceAssessmentFacade sources;
    @Autowired
    private ComplianceReadinessFacade gates;
    @Autowired
    private PlatformTransactionManager transactions;

    /** @throws Exception if guarded setup fails */
    @BeforeEach
    public void setup() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo(
                    "EquiTransit_Demo");
        }
        owner = account("CUSTOMER");
        other = account("CUSTOMER");
        manager = account("MANAGER");
        coordinator = account("COORDINATOR");
        coordinatorId = lastStaff();
        specialist = account("SPECIALIST");
        specialistId = lastStaff();
        JsonNode horse = post("/api/horses", owner,
                mapper.readTree("{\"name\":\"Quotation fixture\"}"), 201);
        ObjectNode draft = (ObjectNode) mapper.readTree(ApiExamples.DRAFT);
        draft.put("originCountryCode", "IS");
        draft.put("destinationCountryCode", "NZ");
        draft.putArray("transitAirports");
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
        assertOrder("SUBMITTED");
        JsonNode current = get("/api/manager/transport-orders/" + order,
                manager, 200);

        revision = current.get("currentRevisionId").asLong();
        post("/api/manager/transport-orders/" + order + "/decisions", manager,
                mapper.valueToTree(Map.of("revisionId", revision,
                        "recordVersion", current.get("recordVersion").asText(),
                        "operationId", UUID.randomUUID(), "decision",
                        "APPROVED", "specialistId", specialistId,
                        "coordinatorId", coordinatorId)), 200);

    }

    /** Only IDs created by this test are removed, including committed races. */
    @AfterEach
    public void cleanup() {
        if (org.springframework.test.context.transaction.TestTransaction
                .isActive()) {
            return;
        }
        if (order != 0) {
            jdbc.update("DELETE dbo.DocumentReview WHERE SubmissionID IN"
                    + " (SELECT s.SubmissionID FROM dbo.DocumentSubmission s"
                    + " JOIN dbo.DocumentRequirement d ON"
                    + " d.RequirementID=s.RequirementID WHERE d.RevisionID=?)",
                    revision);
            jdbc.update("DELETE dbo.DocumentSubmission WHERE RequirementID"
                    + " IN (SELECT RequirementID FROM dbo.DocumentRequirement"
                    + " WHERE RevisionID=?)", revision);
            jdbc.update("DELETE dbo.DocumentRequirement WHERE RevisionID=?",
                    revision);
            jdbc.update("DELETE dbo.AuthorityProcedure WHERE RevisionID=?",
                    revision);
            jdbc.update("DELETE dbo.ComplianceClearance WHERE RevisionID=?",
                    revision);
            jdbc.update("DELETE dbo.ComplianceChecklistHistory WHERE"
                    + " RevisionID=?", revision);
            jdbc.update("DELETE dbo.ComplianceChecklist WHERE RevisionID=?",
                    revision);
            jdbc.update("DELETE dbo.DocumentVersion WHERE DocumentID IN"
                    + " (SELECT h.DocumentID FROM dbo.HorseDocument h JOIN"
                    + " dbo.TransportOrderDetail d ON d.HorseID=h.HorseID"
                    + " WHERE d.OrderID=?)", order);
            jdbc.update("DELETE h FROM dbo.HorseDocument h JOIN"
                    + " dbo.TransportOrderDetail d ON d.HorseID=h.HorseID"
                    + " WHERE d.OrderID=?", order);
            jdbc.update("DELETE dbo.ComplianceFile WHERE OrderID=?", order);
            jdbc.update("DELETE dbo.ComplianceOperation WHERE OrderID=?",
                    order);
            jdbc.update("DELETE dbo.PlanOperation WHERE OrderID=?", order);
            jdbc.update("DELETE dbo.LegExecutionOperation WHERE OrderID=?",
                    order);
            jdbc.update("DELETE dbo.HorseReceipt WHERE PlanID IN (SELECT"
                    + " PlanID FROM dbo.TransportPlan WHERE OrderID=?)", order);
            jdbc.update("DELETE dbo.LegMilestoneLog WHERE LegID IN (SELECT"
                    + " l.LegID FROM dbo.TripLeg l JOIN dbo.TransportPlan p"
                    + " ON p.PlanID=l.PlanID WHERE p.OrderID=?)", order);
            jdbc.update("DELETE dbo.HandoverFile WHERE OrderID=?", order);
            jdbc.update("DELETE dbo.TripHandover WHERE LegID IN (SELECT"
                    + " l.LegID FROM dbo.TripLeg l JOIN dbo.TransportPlan p"
                    + " ON p.PlanID=l.PlanID WHERE p.OrderID=?)", order);
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
            jdbc.update("DELETE dbo.PlanCheckpointNode WHERE PlanID IN"
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
        for (long id : fixtureRules) {
            jdbc.update("DELETE dbo.ComplianceRuleVersion WHERE RuleID=?", id);
        }
        jdbc.update("DELETE dbo.ComplianceRuleVersion WHERE"
                + " OriginCountryCode='IS' AND DestinationCountryCode='NZ'"
                + " AND RuleCode LIKE 'ET-DEF-%' AND NOT EXISTS(SELECT 1"
                + " FROM dbo.DocumentRequirement d WHERE d.RuleID="
                + "dbo.ComplianceRuleVersion.RuleID)");
        jdbc.update("DELETE dbo.DocumentTypeCatalog WHERE TypeCode=?", type);
        for (long vehicle : fixtureVehicles) {
            jdbc.update("DELETE dbo.FleetVehicle WHERE VehicleID=?", vehicle);
        }
        for (long id : fixtureTariffs) {
            jdbc.update("DELETE dbo.ServiceTariff WHERE TariffID=?", id);
        }
        for (long user : fixtureUsers) {
            jdbc.update("DELETE dbo.ResourceOperation WHERE UserID=?", user);
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


    private String legal() {
        return base() + "/compliance";
    }

    private ObjectNode body() {
        var result = mapper.createObjectNode();

        result.set("command", command());
        return result;
    }

    private ObjectNode ruleBody(String phase) {
        var result = body();

        result.put("phase", phase);
        result.put("ruleCode", type + "_" + phase);
        result.put("documentType", type);
        result.put("displayName", "Isolated legal test only");
        result.put("sourceUrl", "https://authority.example/test-rule");
        result.put("effectiveFrom", time(-1));
        result.put("effectiveTo", time(60));
        result.put("authorityRequired", true);
        return result;
    }

    private long rule(String phase) throws Exception {
        long id = post(legal() + "/rules", specialist, ruleBody(phase), 200)
                .path("entityId").asLong();

        fixtureRules.add(id);
        return id;
    }

    private void checklist(String phase) throws Exception {
        var request = body();

        request.put("phase", phase);
        request.putArray("transitCountryCodes");
        request.put("sourceReference", "Isolated route assessment");
        request.put("validUntil", time(50));
        post(legal() + "/checklists", specialist, request, 200);
    }

    private String image() throws Exception {
        var out = new java.io.ByteArrayOutputStream();

        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private JsonNode upload() throws Exception {
        var request = body();

        request.put("documentType", type);
        request.put("fileName", "evidence.png");
        request.put("contentType", "image/png");
        request.put("contentBase64", image());
        request.put("issuedDate", OffsetDateTime.now().minusDays(2)
                .toLocalDate().toString());
        request.put("expiryDate", OffsetDateTime.now().plusDays(40)
                .toLocalDate().toString());
        return post(legal() + "/documents", owner, request, 200);
    }

    private JsonNode overview(String phase) throws Exception {
        return get(legal() + "?phase=" + phase + "&bootstrap=false",
                owner, 200);
    }

    private void approveDocument(long version) throws Exception {
        long requirement = overview("PRE_EXPORT")
                .at("/requirements/0/requirementId").asLong();
        var submission = body();

        submission.put("documentVersionId", version);
        long id = post(legal() + "/requirements/" + requirement
                + "/submissions", owner, submission, 200)
                .path("entityId").asLong();
        var review = body();

        review.put("decision", "APPROVED");
        post(legal() + "/submissions/" + id + "/reviews", specialist,
                review, 200);
    }

    private void authority(long rule) throws Exception {
        var file = body();

        file.put("fileName", "authority.png");
        file.put("contentType", "image/png");
        file.put("contentBase64", image());
        String key = post(legal() + "/files", specialist, file, 200)
                .path("fileKey").asText();
        var request = body();

        request.put("ruleId", rule);
        request.put("decision", "APPROVED");
        request.put("authorityName", "Isolated authority fixture");
        request.put("applicationReference", "TEST-REFERENCE");
        request.put("sourceReference", "Test-only external decision");
        request.put("evidenceFileKey", key);
        request.put("decidedAt", time(-1));
        request.put("expiresAt", time(30));
        post(legal() + "/procedures", specialist, request, 200);
    }

    private void clear(String phase, int expected) throws Exception {
        var request = body();

        request.put("phase", phase);
        request.put("expiresAt", time(20));
        post(legal() + "/clearances", specialist, request, expected);
    }

    /** @throws Exception if default phases, retries or clearances differ */
    @Test
    public void defaultPhasesUploadAndClearWithoutSeparateProcedures()
            throws Exception {
        assertThat(overview("PRE_EXPORT").at("/readiness/blockers").toString())
                .contains("RULE_ASSESSMENT_PENDING");
        var initial = get(legal() + "?phase=PRE_EXPORT", owner, 200);
        int baseDocuments = jdbc.queryForObject("SELECT CASE WHEN "
                + "OBJECT_ID('dbo.ComplianceDocumentPolicy') IS NULL "
                + "THEN 2 ELSE 4 END", Integer.class);
        assertThat(initial.path("requirements").size())
                .isEqualTo(baseDocuments);
        assertThat(count("ComplianceChecklist", "RevisionID", revision))
                .isEqualTo(3);
        assertThat(get(legal() + "?phase=PRE_EXPORT", owner, 200)
                .path("recordVersion"))
                .isEqualTo(initial.path("recordVersion"));
        for (String phase : List.of("PRE_EXPORT", "ARRIVAL",
                "QUARANTINE_RELEASE")) {
            var requirements = overview(phase).path("requirements");
            assertThat(requirements.size()).isEqualTo(
                    phase.equals("PRE_EXPORT") ? baseDocuments : 1);
            for (var requirement : requirements) {
                long id = requirement.path("requirementId").asLong();
                var request = oneStep();
                long submission = post(legal() + "/requirements/" + id
                        + "/upload-and-submit", phase.equals("ARRIVAL")
                                ? specialist : owner, request, 200)
                        .path("entityId").asLong();
                var review = body();
                review.put("decision", "APPROVED");
                post(legal() + "/submissions/" + submission + "/reviews",
                        specialist, review, 200);
            }
        }
        for (String phase : List.of("PRE_EXPORT", "ARRIVAL",
                "QUARANTINE_RELEASE")) {
            clear(phase, 200);
            assertThat(overview(phase).at("/readiness/status").asText())
                    .isEqualTo("READY");
            var procedures = get(legal() + "/procedures?phase=" + phase,
                    owner, 200);
            assertThat(procedures.size()).isEqualTo(
                    phase.equals("PRE_EXPORT") ? baseDocuments : 1);
            assertThat(procedures.get(0).path("sourceReference").asText())
                    .startsWith("SPECIALIST_DOCUMENT_REVIEW:");
        }
    }

    /** @throws Exception if feedback/file history or authorization is lost */
    @Test
    public void oneStepRejectionResubmissionAndRetryAreVisibleToBothActors()
            throws Exception {
        rule("PRE_EXPORT");
        checklist("PRE_EXPORT");
        long requirement = overview("PRE_EXPORT")
                .at("/requirements/0/requirementId").asLong();
        String path = legal() + "/requirements/" + requirement
                + "/upload-and-submit";
        var request = oneStep();
        post(path, other, request, 404);
        post(path, manager, request, 403);
        post(path, coordinator, request, 403);
        post(path, account("SPECIALIST"), request, 404);
        var bad = request.deepCopy();
        bad.put("contentBase64", "invalid-base64");
        post(path, owner, bad, 409);
        assertThat(count("ComplianceFile", "OrderID", order)).isZero();
        var receipt = post(path, owner, request, 200);
        assertThat(post(path, owner, request, 200)).isEqualTo(receipt);
        long submission = receipt.path("entityId").asLong();
        var review = body();
        review.put("decision", "REJECTED");
        post(legal() + "/submissions/" + submission + "/reviews",
                specialist, review, 409);
        review.put("reason", "Passport scan is unreadable");
        post(legal() + "/submissions/" + submission + "/reviews",
                specialist, review, 200);
        for (String actor : List.of(owner, specialist)) {
            var evidence = get(legal() + "?phase=PRE_EXPORT&bootstrap=false",
                    actor, 200).at("/requirements/0");
            assertThat(evidence.path("status").asText()).isEqualTo("REJECTED");
            assertThat(evidence.path("feedback").asText())
                    .isEqualTo("Passport scan is unreadable");
            assertThat(evidence.at("/attachment/fileName").asText())
                    .isEqualTo("evidence.png");
            assertThat(evidence.at("/attachment/referenceNumber").asText())
                    .isEqualTo("REF-BE02");
            assertThat(evidence.at("/attachment/expiresAt").asText())
                    .isEqualTo(request.path("expiresAt").asText());
        }
        clear("PRE_EXPORT", 409);
        request.put("operationId", UUID.randomUUID().toString());
        post(path, specialist, request, 409);
        request = oneStep();
        long replacement = post(path, specialist, request, 200)
                .path("entityId").asLong();
        assertThat(overview("PRE_EXPORT").at("/requirements/0/status").asText())
                .isEqualTo("SUBMITTED");
        assertThat(overview("PRE_EXPORT").at("/requirements/0/feedback")
                .isNull()).isTrue();
        review = body();
        review.put("decision", "APPROVED");
        post(legal() + "/submissions/" + replacement + "/reviews", specialist,
                review, 200);
        clear("PRE_EXPORT", 200);
        assertThat(get(legal() + "/documents?view=versions", owner, 200).size())
                .isEqualTo(2);
    }

    /** @throws Exception if F10 countries are not deduplicated and isolated */
    @Test
    public void graphTransitFlowsIntoAllDefaultAndManualChecklists()
            throws Exception {
        long plan = jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids TABLE"
                + " (ID BIGINT); INSERT dbo.TransportPlan(OrderID,"
                + "RevisionID,CreatedBy,VersionNo,FinancialGateStatus,"
                + "OperationalGateStatus,DocGateStatus,PlanStatus) OUTPUT"
                + " INSERTED.PlanID INTO @ids VALUES(?,?,?,1,'PENDING',"
                + "'PENDING','PENDING','DRAFT'); SELECT ID FROM @ids",
                Long.class, order, revision,
                coordinatorId);
        jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=?"
                + " WHERE OrderID=?", plan, order);
        var countries = List.of("IS", "SG", "SG", "NZ");
        for (int index = 0; index < countries.size(); index++) {
            jdbc.update("INSERT dbo.PlanCheckpointNode(PlanID,NodeKey,"
                    + "NodeType,SequenceOrder,LocationName,CountryCode)"
                    + " VALUES(?,?,'TRANSIT_HUB',?,'Test stop',?)", plan,
                    "test-" + index, index + 1, countries.get(index));
        }
        get(legal() + "?phase=PRE_EXPORT", owner, 200);
        assertThat(jdbc.queryForList("SELECT TransitCountriesJSON FROM"
                + " dbo.ComplianceChecklist WHERE RevisionID=?", String.class,
                revision)).containsExactly("[\"SG\"]", "[\"SG\"]", "[\"SG\"]");
        var manual = body();
        manual.put("phase", "ARRIVAL").put("sourceReference", "Route graph")
                .put("validUntil", time(30));
        post(legal() + "/checklists", specialist, manual, 200);
        assertThat(overview("ARRIVAL").path("requirements").size())
                .isEqualTo(2);
        for (String phase : List.of("PRE_EXPORT", "ARRIVAL",
                "QUARANTINE_RELEASE")) {
            for (var requirement : overview(phase).path("requirements")) {
                long id = requirement.path("requirementId").asLong();
                long submission = post(legal() + "/requirements/" + id
                        + "/upload-and-submit", owner, oneStep(), 200)
                        .path("entityId").asLong();
                var review = body();
                review.put("decision", "APPROVED");
                post(legal() + "/submissions/" + submission + "/reviews",
                        specialist, review, 200);
            }
        }
        for (String phase : List.of("PRE_EXPORT", "ARRIVAL",
                "QUARANTINE_RELEASE")) {
            clear(phase, 200);
        }
    }

    /** @throws Exception if exact expiry or rollback permits legal readiness */
    @Test
    public void oneStepExpiryAndRollbackDoNotExtendDocumentValidity()
            throws Exception {
        rule("PRE_EXPORT");
        checklist("PRE_EXPORT");
        long requirement = overview("PRE_EXPORT")
                .at("/requirements/0/requirementId").asLong();
        String path = legal() + "/requirements/" + requirement
                + "/upload-and-submit";
        var request = oneStep();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try {
                post(path, owner, request, 200);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            tx.setRollbackOnly();
        });
        assertThat(count("ComplianceFile", "OrderID", order)).isZero();
        assertThat(count("DocumentSubmission", "RequirementID", requirement))
                .isZero();
        long submission = post(path, owner, request, 200)
                .path("entityId").asLong();
        var review = body();
        review.put("decision", "APPROVED");
        post(legal() + "/submissions/" + submission + "/reviews", specialist,
                review, 200);
        jdbc.update("UPDATE dbo.DocumentVersion SET ExpiresAt="
                + "DATEADD(second,-1,SYSUTCDATETIME()) WHERE DocumentVersionID"
                + " IN(SELECT DocumentVersionID FROM dbo.DocumentSubmission"
                + " WHERE SubmissionID=?)", submission);
        assertThat(overview("PRE_EXPORT").at("/requirements/0/status").asText())
                .isEqualTo("EXPIRED");
        clear("PRE_EXPORT", 409);
    }

    /** @throws Exception if concurrent retries create duplicate evidence */
    @Test
    public void oneStepConcurrentRetryCreatesOneFileVersionAndSubmission()
            throws Exception {
        rule("PRE_EXPORT");
        checklist("PRE_EXPORT");
        long requirement = overview("PRE_EXPORT")
                .at("/requirements/0/requirementId").asLong();
        String path = legal() + "/requirements/" + requirement
                + "/upload-and-submit";
        var request = oneStep();
        var latch = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                latch.await();
                return post(path, owner, request, 200);
            });
            var second = pool.submit(() -> {
                latch.await();
                return post(path, owner, request, 200);
            });
            latch.countDown();
            assertThat(first.get(25, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(second.get(25,
                            java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(count("ComplianceFile", "OrderID", order)).isEqualTo(1);
        assertThat(get(legal() + "/documents?view=versions", owner, 200).size())
                .isEqualTo(1);
        assertThat(count("DocumentSubmission", "RequirementID", requirement))
                .isEqualTo(1);
    }

    private ObjectNode oneStep() throws Exception {
        var request = command();
        request.put("fileName", "evidence.png").put("contentType", "image/png")
                .put("contentBase64", image()).put("expiresAt", time(40))
                .put("referenceNumber", "REF-BE02");
        return request;
    }

    /** @throws Exception if any legal state or retained evidence differs */
    @Test
    public void exactVersionAuthorityAndPhaseControlClearance()
            throws Exception {
        long rule = rule("PRE_EXPORT");

        checklist("PRE_EXPORT");
        var uploaded = upload();
        long version = uploaded.path("entityId").asLong();
        String key = uploaded.path("fileKey").asText();

        approveDocument(version);
        clear("PRE_EXPORT", 200);
        authority(rule);
        clear("PRE_EXPORT", 200);
        assertThat(overview("PRE_EXPORT").at("/readiness/status").asText())
                .isEqualTo("READY");
        assertThat(overview("QUARANTINE_RELEASE")
                .at("/readiness/status").asText()).isEqualTo("PENDING");
        byte[] original = jdbc.queryForObject("SELECT ContentBytes FROM"
                + " dbo.ComplianceFile WHERE FileKey=?", byte[].class, key);

        upload();
        assertThat(overview("PRE_EXPORT").at("/readiness/status").asText())
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT ContentBytes FROM"
                + " dbo.ComplianceFile WHERE FileKey=?", byte[].class, key))
                .isEqualTo(original);
        assertThat(get(legal() + "/documents?view=versions", owner, 200).size())
                .isEqualTo(2);
        assertThat(get(legal() + "/files/" + key, owner, 200)
                .path("contentBase64").asText()).isEqualTo(image());
    }

    /** @throws Exception if role or owner isolation is bypassed */
    @Test
    public void onlyAssignedSpecialistMayWriteAndOnlyOwnerMayRead()
            throws Exception {
        post(legal() + "/rules", manager, ruleBody("PRE_EXPORT"), 403);
        post(legal() + "/rules", coordinator, ruleBody("PRE_EXPORT"), 403);
        String unassigned = account("SPECIALIST");

        post(legal() + "/rules", unassigned, ruleBody("PRE_EXPORT"), 404);
        get(legal() + "?phase=PRE_EXPORT", other, 404);
        rule("PRE_EXPORT");
        checklist("PRE_EXPORT");
        String key = upload().path("fileKey").asText();

        get(legal() + "/files/" + key, other, 404);
    }

    /** @throws Exception if missing rule or expiry becomes legal exemption */
    @Test
    public void missingRulesAndExpiredOrRevokedDocumentsBlock()
            throws Exception {
        assertThat(overview("PRE_EXPORT").at("/readiness/blockers").toString())
                .contains("RULE_ASSESSMENT_PENDING");
        long rule = rule("PRE_EXPORT");

        checklist("PRE_EXPORT");
        long version = upload().path("entityId").asLong();

        approveDocument(version);
        authority(rule);
        jdbc.update("UPDATE dbo.DocumentVersion SET ExpiryDate="
                + "DATEADD(day,-1,CAST(GETUTCDATE() AS date)) WHERE"
                + " DocumentVersionID=?", version);
        clear("PRE_EXPORT", 409);
        jdbc.update("UPDATE dbo.DocumentVersion SET ExpiryDate="
                + "DATEADD(day,40,CAST(GETUTCDATE() AS date)) WHERE"
                + " DocumentVersionID=?", version);
        var request = body();

        request.put("reason", "Test issuing authority revoked evidence");
        post(legal() + "/documents/" + version + "/revocations", specialist,
                request, 200);
        clear("PRE_EXPORT", 409);
        assertThat(overview("PRE_EXPORT").at("/readiness/blockers").toString())
                .contains("DOCUMENT_EVIDENCE_PENDING");
    }

    /** @throws Exception if NULL quarantine days or F05 contract differs */
    @Test
    public void clinicalAssessmentsFeedF05AndNeverStoreZeroDays()
            throws Exception {
        var request = body();

        request.put("applicability", "REQUIRED");
        request.put("requiredDays", 14);
        request.put("visitCount", 1);
        request.put("testCount", 2);
        request.put("doseCount", 0);
        request.put("certificateCount", 1);
        request.put("sourceReference", "Test Specialist clinical evidence");
        request.put("validUntil", time(40));
        var incomplete = request.deepCopy();

        incomplete.remove("doseCount");
        post(legal() + "/assessments", specialist, incomplete, 400);
        post(legal() + "/assessments", specialist, request, 200);
        var at = OffsetDateTime.now(ZoneOffset.UTC);
        var horizon = at.plusDays(20);

        assertThat(sources.quarantine(order, revision, at, horizon)
                .requiredDays()).isEqualTo(14);
        assertThat(sources.veterinary(order, revision, at, horizon)
                .tests()).isEqualTo(2);
        assertThat(get(legal() + "/assessments", owner, 200)
                .path("requiredDays").asInt()).isEqualTo(14);
        request.set("command", command());
        request.put("applicability", "NOT_REQUIRED");
        request.putNull("requiredDays");
        post(legal() + "/assessments", specialist, request, 200);
        assertThat(sources.quarantine(order, revision, at.plusSeconds(1),
                horizon).applicability()).isEqualTo("NOT_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM"
                + " dbo.RevisionQuarantineAssessment WHERE RevisionID=?"
                + " AND RequiredDays=0", Integer.class, revision)).isZero();
    }

    /** @throws Exception if retry duplicates a file or stale writes succeed */
    @Test
    public void retryIsStableAndChangedInputOrStaleVersionIsRejected()
            throws Exception {
        var request = ruleBody("PRE_EXPORT");
        var missingDecision = request.deepCopy();

        missingDecision.remove("authorityRequired");
        post(legal() + "/rules", specialist, missingDecision, 400);
        var first = post(legal() + "/rules", specialist, request, 200);

        fixtureRules.add(first.path("entityId").asLong());
        assertThat(post(legal() + "/rules", specialist, request, 200))
                .isEqualTo(first);
        request.put("sourceUrl", "https://authority.example/changed");
        post(legal() + "/rules", specialist, request, 409);
        request.set("command", mapper.createObjectNode()
                .put("recordVersion", "0000000000000000")
                .put("operationId", UUID.randomUUID().toString()));
        post(legal() + "/rules", specialist, request, 409);
        var gate = new TransactionTemplate(transactions).execute(tx ->
                gates.readiness(order, revision + 1,
                        com.equitransit.backend.entity.compliance.
                                CompliancePhase.PRE_EXPORT,
                        OffsetDateTime.now(ZoneOffset.UTC)));

        assertThat(gate).isNotNull();
        if (gate != null) {
            assertThat(gate.blockers()).contains("REVISION_MISMATCH");
        }
    }

    /** @throws Exception if a new route inherits old legal readiness */
    @Test
    public void revisedRouteRequiresNewChecklistAndFreshAssessment()
            throws Exception {
        rule("PRE_EXPORT");
        checklist("PRE_EXPORT");
        String encoded = jdbc.queryForObject("SELECT OrderSnapshot FROM"
                + " dbo.OrderRevision WHERE RevisionID=?", String.class,
                revision);
        var request = (ObjectNode) mapper.readTree(encoded).path("request");

        request.put("recordVersion", command().path("recordVersion").asText());
        request.put("operationId", UUID.randomUUID().toString());
        request.put("changeReason", "Test route changed for reassessment");
        request.put("destinationCountryCode", "FR");
        post(base() + "/revisions", owner, request, 200);
        assertThat(overview("PRE_EXPORT").at("/readiness/blockers").toString())
                .contains("RULE_ASSESSMENT_PENDING");
    }

    /** @throws Exception if concurrent retry writes duplicate file versions */
    @Test
    public void twoConcurrentRetriesCreateOneImmutableVersion()
            throws Exception {
        rule("PRE_EXPORT");
        var request = body();

        request.put("documentType", type);
        request.put("fileName", "concurrent.png");
        request.put("contentType", "image/png");
        request.put("contentBase64", image());
        request.put("issuedDate", OffsetDateTime.now().minusDays(2)
                .toLocalDate().toString());
        request.put("expiryDate", OffsetDateTime.now().plusDays(30)
                .toLocalDate().toString());
        var latch = new java.util.concurrent.CountDownLatch(1);

        try (var pool = java.util.concurrent.Executors
                .newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                latch.await();
                return post(legal() + "/documents", owner, request, 200);
            });
            var second = pool.submit(() -> {
                latch.await();
                return post(legal() + "/documents", owner, request, 200);
            });

            latch.countDown();
            assertThat(first.get(25, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(second.get(25,
                            java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(get(legal() + "/documents?view=versions", owner, 200).size())
                .isEqualTo(1);
        assertThat(count("ComplianceFile", "OrderID", order)).isEqualTo(1);
    }

    /** @throws Exception if rejected evidence or expired authority passes */
    @Test
    public void rejectedReviewAndExpiredAuthorityCannotBeCleared()
            throws Exception {
        long rule = rule("PRE_EXPORT");

        checklist("PRE_EXPORT");
        approveDocument(upload().path("entityId").asLong());
        authority(rule);
        long submission = overview("PRE_EXPORT")
                .at("/requirements/0/submissionId").asLong();
        var request = body();

        request.put("decision", "REJECTED");
        request.put("feedback", "Test evidence no longer meets requirement");
        post(legal() + "/submissions/" + submission + "/reviews", specialist,
                request, 200);
        clear("PRE_EXPORT", 409);
        request.set("command", command());
        request.put("decision", "APPROVED");
        post(legal() + "/submissions/" + submission + "/reviews", specialist,
                request, 200);
        jdbc.update("UPDATE dbo.AuthorityProcedure SET ExpiresAt="
                + "DATEADD(day,-1,GETUTCDATE()) WHERE RevisionID=?", revision);
        clear("PRE_EXPORT", 409);
        assertThat(overview("PRE_EXPORT").at("/readiness/blockers").toString())
                .contains("AUTHORITY_EVIDENCE_PENDING");
    }

    /** @throws Exception if legacy nullable fields crash the new read API */
    @Test
    public void legacyExternalKeysRemainHistoricalAndNeverBecomeProof()
            throws Exception {
        rule("PRE_EXPORT");
        checklist("PRE_EXPORT");
        long version = upload().path("entityId").asLong();

        jdbc.update("UPDATE dbo.DocumentVersion SET FileKey='legacy/file.pdf',"
                + "IssuedDate=NULL,ExpiryDate=NULL WHERE DocumentVersionID=?",
                version);
        var history = get(legal() + "/documents?view=versions", owner, 200)
                .get(0);

        assertThat(history.path("fileKey").isNull()).isTrue();
        assertThat(history.path("issuedDate").isNull()).isTrue();
        assertThat(history.toString()).doesNotContain("legacy/file.pdf");
        clear("PRE_EXPORT", 409);
    }

    /** @throws Exception if arrival work is incorrectly limited to F04 state */
    @Test
    public void activeSpecialistCanAssessArrivalDuringTransit()
            throws Exception {
        jdbc.update("UPDATE dbo.TransportOrder SET OrderStatus='IN_TRANSIT'"
                + " WHERE OrderID=?", order);
        rule("ARRIVAL");
        checklist("ARRIVAL");
        assertThat(overview("ARRIVAL").path("requirements").size())
                .isEqualTo(1);
        jdbc.update("UPDATE dbo.OrderAssignment SET EndedAt=SYSUTCDATETIME()"
                + " WHERE OrderID=? AND AssignmentRole='SPECIALIST'", order);
        post(legal() + "/rules", specialist, ruleBody("ARRIVAL"), 404);
    }

    /** @throws Exception if staff hits F08 authorization or time SQL errors */
    @Test
    @org.springframework.transaction.annotation.Transactional
    public void handoverGateIsInternalAndTimesUseUtcInstants()
            throws Exception {
        long leg = handoverFixture();
        String driver = account("DRIVER");
        long driverId = lastStaff();
        jdbc.update("INSERT dbo.TripAssignment (LegID,StaffID,RoleInTrip,"
                + "Status,AssignedFrom,AssignedTo) VALUES (?,?,"
                + "'DRIVER','ACTIVE',DATEADD(hour,-3,SYSUTCDATETIME()),"
                + "DATEADD(hour,1,SYSUTCDATETIME()))", leg, driverId);
        var delivery = delivery();
        for (String token : List.of(manager, coordinator, driver)) {
            String path = token.equals(driver) ? "/api/driver/legs/"
                    : "/api/coordinator/legs/";
            var blocked = post(path + leg + "/final-delivery", token,
                    delivery, 409);
            assertThat(blocked.path("code").asText())
                    .isEqualTo("QUARANTINE_RELEASE_REQUIRED");
        }
        var early = delivery.deepCopy();
        early.put("handoverTime", OffsetDateTime.now(ZoneOffset.UTC)
                .minusHours(3).toString());
        assertThat(post("/api/driver/legs/" + leg + "/final-delivery",
                driver, early, 422).path("code").asText())
                .isEqualTo("HANDOVER_TIME_BEFORE_DEPARTURE");
        defaultPhasesUploadAndClearWithoutSeparateProcedures();
        var delivered = post("/api/driver/legs/" + leg + "/final-delivery",
                driver, delivery, 200);
        assertThat(delivered.path("orderStatus").asText())
                .isEqualTo("DELIVERED");
        var stored = jdbc.queryForObject("SELECT HandoverTime FROM"
                + " dbo.TripHandover WHERE LegID=?", java.sql.Timestamp.class,
                leg).toLocalDateTime();
        assertThat(stored).isEqualTo(OffsetDateTime.parse(delivery.path(
                "handoverTime").asText()).withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime());
    }

    /** @throws Exception if file scope, role policy or replay is broken */
    @Test
    @org.springframework.transaction.annotation.Transactional
    public void handoverFilesAndManagerClosureAreScopedAndRetrySafe()
            throws Exception {
        long leg = handoverFixture();
        String driver = account("DRIVER");
        long driverId = lastStaff();
        jdbc.update("INSERT dbo.TripAssignment (LegID,StaffID,RoleInTrip,"
                + "Status,AssignedFrom,AssignedTo) VALUES (?,?,"
                + "'DRIVER','ACTIVE',DATEADD(hour,-3,SYSUTCDATETIME()),"
                + "DATEADD(hour,1,SYSUTCDATETIME()))", leg, driverId);
        String fileBase = base() + "/handover-files";
        var upload = mapper.createObjectNode();
        upload.put("fileName", "handover.png");
        upload.put("contentType", "image/png");
        upload.put("contentBase64", image());
        upload.put("purpose", "HANDOVER_SIGNATURE");
        for (String token : List.of(manager, coordinator, driver)) {
            var file = post(fileBase, token, upload, 201);
            String path = fileBase + "/" + file.path("fileKey").asText();
            assertThat(get(path, token, 200).path("contentBase64").asText())
                    .isEqualTo(upload.path("contentBase64").asText());
            get(path, owner, 200);
            get(path, other, 403);
        }
        post(fileBase, owner, upload, 403);
        post(fileBase, specialist, upload, 403);
        String outsider = account("DRIVER");
        post(fileBase, outsider, upload, 403);
        upload.put("purpose", "ACTUAL_COST_EVIDENCE");
        var cost = post(fileBase, manager, upload, 201);
        get(fileBase + "/" + cost.path("fileKey").asText(), owner, 404);
        String close = base() + "/handovers/close-order";
        post(close, coordinator, command(), 403);
        post(close, driver, command(), 403);
        post(close, owner, command(), 403);
        assertThat(post(close, manager, command(), 409).path("code").asText())
                .isEqualTo("ORDER_NOT_DELIVERED");
        var stale = command();
        jdbc.update("UPDATE dbo.TransportOrder SET OrderStatus='DELIVERED'"
                + " WHERE OrderID=?", order);
        assertThat(post(close, manager, stale, 409).path("code").asText())
                .isEqualTo("ORDER_VERSION_CONFLICT");
        var command = command();
        var closed = post(close, manager, command, 200);
        assertThat(closed.path("orderStatus").asText()).isEqualTo("CLOSED");
        assertThat(post(close, manager, command, 200)).isEqualTo(closed);
        var changed = command.deepCopy();
        changed.put("recordVersion", closed.path("recordVersion").asText());
        assertThat(post(close, manager, changed, 409).path("code").asText())
                .isEqualTo("CLOSURE_RETRY_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent"
                + " WHERE EntityID=? AND EntityType='ORDER_ACTIVITY' AND"
                + " ActionCode='CLOSED'", Integer.class, order)).isEqualTo(1);
    }

    private long handoverFixture() {
        long plan = jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids TABLE"
                + " (ID BIGINT); INSERT dbo.TransportPlan (OrderID,RevisionID,"
                + "CreatedBy,VersionNo,PlanStatus,FinancialGateStatus,"
                + "OperationalGateStatus,DocGateStatus) OUTPUT INSERTED.PlanID"
                + " INTO @ids VALUES (?,?,?,1,'ACTIVE','PENDING','PENDING',"
                + "'PENDING'); SELECT ID FROM @ids",
                Long.class, order, revision, coordinatorId);
        jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=?,"
                + "OrderStatus='IN_TRANSIT' WHERE OrderID=?", plan, order);
        return jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids TABLE"
                + " (ID BIGINT); INSERT dbo.TripLeg (PlanID,SequenceNo,"
                + "TransportMode,StartPoint,EndPoint,DistanceKm,LegStatus,"
                + "ActualDeparture) OUTPUT INSERTED.LegID INTO @ids VALUES"
                + " (?,1,'ROAD','A','B',100,'IN_TRANSIT',"
                + "DATEADD(hour,-2,SYSUTCDATETIME()));"
                + " SELECT ID FROM @ids",
                Long.class, plan);
    }

    private ObjectNode delivery() {
        var body = mapper.createObjectNode();
        body.put("handoverTime", OffsetDateTime.now(ZoneOffset.UTC)
                .withOffsetSameInstant(ZoneOffset.ofHours(7))
                .withNano(0).toString());
        body.put("receivedByName", "Alex Nguyen");
        body.put("receiverSignatureFileKey", "legacy-signature");
        body.put("photoFileKey", "legacy-photo");
        body.put("horseConditionStatus", "GOOD");
        body.put("outcome", "SUCCESSFUL");
        return body;
    }

    /** @throws Exception if any actual API transition in the chain fails */
    @Test
    @org.springframework.transaction.annotation.Transactional
    public void fullBackendLifecycleFromSubmissionThroughClosure()
            throws Exception {
        // setup() submits and approves the request through the real APIs.
        assertOrder("APPROVED");
        String driver = account("DRIVER");
        long driverId = lastStaff();
        var measured = jdbc.queryForObject("SELECT HorseID FROM"
                + " dbo.TransportOrderDetail WHERE OrderID=?", Long.class,
                order);
        String microchip = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(java.util.Locale.ROOT);
        String passport = "TEST-" + microchip;
        jdbc.update("UPDATE dbo.Horse SET MicrochipID=?,"
                + "PassportNumber=?,WeightKg=450,HeightCm=155,"
                + "BodyWidthCm=65,MeasuredAt=SYSUTCDATETIME() WHERE HorseID=?",
                microchip, passport, measured);
        var assessment = body();
        assessment.put("applicability", "NOT_REQUIRED");
        assessment.put("visitCount", 0).put("testCount", 0);
        assessment.put("doseCount", 0).put("certificateCount", 0);
        assessment.put("sourceReference", "Artificial lifecycle assessment");
        assessment.put("validUntil", time(40));
        post(legal() + "/assessments", specialist, assessment, 200);
        var draft = body();
        draft.put("revisionId", revision).put("setAsCurrentDraft", true);
        var start = OffsetDateTime.parse(time(10));
        var legs = draft.putArray("legs");
        for (int sequence = 1; sequence <= 2; sequence++) {
            legs.addObject().put("sequenceNo", sequence)
                    .put("transportMode", "ROAD").put("startPoint", "A")
                    .put("endPoint", "B").put("distanceKm", "100.000")
                    .put("distanceSource", "Verified fixture route")
                    .put("plannedDeparture", start.plusHours(
                            sequence * 2L).toString())
                    .put("plannedArrival", start.plusHours(
                            sequence * 2L + 1).toString());
        }
        var plan = post(base() + "/plans", coordinator, draft, 200);
        long planId = plan.path("planId").asLong();
        long first = plan.at("/legs/0/legId").asLong();
        long last = plan.at("/legs/1/legId").asLong();
        var resource = mapper.createObjectNode();
        resource.putObject("command").put("operationId",
                UUID.randomUUID().toString());
        var vehicleData = (ObjectNode) mapper.readTree(
                com.equitransit.backend.dto.resource.ResourceExamples.VEHICLE);
        String code = UUID.randomUUID().toString().substring(0, 8);
        vehicleData.put("vehicleCode", "LIFE-" + code);
        vehicleData.put("plateNumber", "LIFE-" + code);
        ((ObjectNode) vehicleData.path("window")).put("bufferMinutes", 0);
        resource.set("data", vehicleData);
        var vehicle = post("/api/manager/resources/vehicles", manager,
                resource, 200);
        fixtureVehicles.add(vehicle.path("id").asLong());
        String planBase = base() + "/plans/" + planId;
        var quoteBody = body();
        quoteBody.put("revisionId", revision).put("validUntil", time(5));
        quoteBody.put("termsVersion", "BE03-TEST").put("terms", "Test terms");
        quoteBody.put("cancellationTerms", "Test cancellation terms");
        var schedules = quoteBody.putArray("paymentSchedule");
        var schedule = schedules.addObject();
        schedule.put("sequenceNo", 1).put("paymentStage", "DEPOSIT")
                .put("amountDue", "1.00").put("dueAt", time(6));
        var balanceSchedule = schedules.addObject();
        balanceSchedule.put("sequenceNo", 2).put("paymentStage", "BALANCE")
                .put("amountDue", "1.00").put("dueAt", time(6));
        var assessments = quoteBody.putArray("assessments");
        for (String service : List.of("AIRPORT_HANDLING", "TERMINAL_HANDLING",
                "CUSTOMS_DECLARATION", "GOVERNMENT_DUTY")) {
            assessments.addObject().put("code", service)
                    .put("status", "NOT_REQUIRED").put("validUntil", time(30))
                    .put("sourceReference", "Artificial lifecycle assessment")
                    .put("reason", "Not applicable to this test route.");
        }
        var priced = post(base() + "/quotes", coordinator, quoteBody, 200);
        assertThat(priced.path("blockers").toString()).isEqualTo("[]");
        String total = priced.at("/quote/total").asText();
        var totalMoney = new java.math.BigDecimal(total);
        var depositMoney = totalMoney.multiply(new java.math.BigDecimal("0.30"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        String deposit = depositMoney.toPlainString();
        String balance = totalMoney.subtract(depositMoney).toPlainString();
        schedule.put("amountDue", deposit);
        balanceSchedule.put("amountDue", balance);
        quoteBody.set("command", command());
        var quote = post(base() + "/quotes", coordinator, quoteBody, 200);
        long quoteId = quote.at("/quote/quoteId").asLong();
        for (String action : List.of("APPROVE", "ISSUE", "ACCEPT")) {
            var actionBody = body();
            actionBody.put("action", action);
            post(base() + "/quotes/" + quoteId + "/actions",
                    action.equals("ACCEPT") ? owner : manager,
                    actionBody, 200);
        }
        long scheduleId = jdbc.queryForObject("SELECT ScheduleID FROM"
                + " dbo.QuotePaymentSchedule WHERE QuoteID=? AND "
                + "PaymentStage='DEPOSIT'", Long.class,
                quoteId);
        var obligation = body();
        obligation.put("quoteId", quoteId).put("scheduleId", scheduleId);
        long requestId = post(base() + "/payments/requests", manager,
                obligation, 200).path("entityId").asLong();
        var proof = body();
        proof.put("mediaType", "image/png").put("contentBase64", image());
        String key = post(base() + "/payments/proofs", owner, proof, 200)
                .path("fileKey").asText();
        var bank = body();
        bank.put("amountReceived", total).put("currency", "USD");
        bank.put("recipientAccount", "TEST-ACCOUNT");
        bank.put("bankTransactionReference", UUID.randomUUID().toString());
        bank.put("postedAt", time(-1));
        bank.put("verificationSource", "Artificial bank evidence");
        bank.put("proofFileKey", key);
        long transaction = post(base() + "/payments/transactions", manager,
                bank, 200).path("entityId").asLong();
        var allocation = body();
        allocation.put("transactionId", transaction);
        allocation.put("paymentRequestId", requestId).put("amount", deposit);
        post(base() + "/payments/allocations", manager, allocation, 200);
        assertOrder("IN_PREPARATION");
        for (long leg : List.of(first, last)) {
            var assignment = body();
            assignment.put("vehicleId", vehicle.path("id").asLong());
            assignment.put("vehicleRecordVersion", vehicle.path(
                    "recordVersion").asText());
            assignment.put("driverStaffId", driverId);
            assignment.put("driverBufferMinutes", 0);
            post(planBase + "/legs/" + leg + "/road-assignment",
                    coordinator, assignment, 200);
        }
        long balanceId = jdbc.queryForObject("SELECT ScheduleID FROM"
                + " dbo.QuotePaymentSchedule WHERE QuoteID=? AND "
                + "PaymentStage='BALANCE'", Long.class, quoteId);
        var remainder = body();
        remainder.put("quoteId", quoteId).put("scheduleId", balanceId);
        long balanceRequest = post(base() + "/payments/requests", manager,
                remainder, 200).path("entityId").asLong();
        var finalPayment = body();
        finalPayment.put("transactionId", transaction);
        finalPayment.put("paymentRequestId", balanceRequest)
                .put("amount", balance);
        post(base() + "/payments/allocations", manager, finalPayment, 200);
        defaultPhasesUploadAndClearWithoutSeparateProcedures();
        var confirm = body();
        confirm.put("acceptedQuoteId", quoteId);
        post(planBase + "/confirm", coordinator, confirm, 200);
        assertOrder("CONFIRMED");
        var authorize = body();
        authorize.put("planId", planId);
        post(base() + "/departure/authorize", manager, authorize, 200);
        var receipt = body();
        receipt.put("planId", planId).put("legId", first);
        receipt.put("observedMicrochip", microchip);
        receipt.put("observedPassportNumber", passport);
        receipt.put("fitToTravel", true).put("conditionNotes", "Fit");
        receipt.put("locationDescription", "Fixture stable");
        var received = post(base() + "/departure/horse-receipts", driver,
                receipt, 200);
        var depart = body();
        depart.put("planId", planId).put("legId", first).put("receiptId",
                received.at("/receipts/0/receiptId").asLong());
        post(base() + "/departure/depart", driver, depart, 200);
        assertOrder("IN_TRANSIT");
        Thread.sleep(1100);
        var transfer = delivery();
        transfer.put("handoverType", "INTERMEDIATE_AIRPORT");
        post("/api/driver/legs/" + first + "/intermediate-handover", driver,
                transfer, 201);
        assertThat(jdbc.queryForObject("SELECT LegStatus FROM dbo.TripLeg"
                + " WHERE LegID=?", String.class, last))
                .isEqualTo("CONFIRMED");
        var execution = body();
        execution.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC)
                .withNano(0).toString()).put("sourceCode", "DRIVER_REPORT")
                .put("sourceReference", "Artificial driver observation");
        post(base() + "/legs/" + last + "/start", driver, execution, 200);
        Thread.sleep(1100);
        execution.set("command", command());
        execution.put("occurredAt", time(-1));
        assertThat(post(base() + "/legs/" + last + "/complete", driver,
                execution, 422).path("code").asText())
                .isEqualTo("HANDOVER_TIME_BEFORE_DEPARTURE");
        execution.set("command", command());
        execution.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC)
                .withNano(0).toString());
        post(base() + "/legs/" + last + "/complete", driver, execution, 200);
        var file = mapper.createObjectNode();
        file.put("fileName", "signature.png").put("contentType", "image/png")
                .put("contentBase64", image())
                .put("purpose", "HANDOVER_SIGNATURE");
        var finalDelivery = delivery();
        String fileBase = base() + "/handover-files";
        finalDelivery.put("receiverSignatureFileKey", post(fileBase, driver,
                file, 201).path("fileKey").asText());
        file.put("fileName", "photo.png").put("purpose", "HANDOVER_PHOTO");
        String photo = post(fileBase, driver, file, 201)
                .path("fileKey").asText();
        finalDelivery.put("photoFileKey", photo);
        get(fileBase + "/" + photo, owner, 200);
        post("/api/driver/legs/" + last + "/final-delivery", driver,
                finalDelivery, 200);
        assertOrder("DELIVERED");
        post(base() + "/handovers/close-order", manager, command(), 200);
        assertOrder("CLOSED");
    }

    private void assertOrder(String expected) {
        assertThat(jdbc.queryForObject("SELECT OrderStatus FROM"
                + " dbo.TransportOrder WHERE OrderID=?", String.class, order))
                .isEqualTo(expected);
    }

    /** Transactional F14 fixtures roll back all append-only facts. */
    @org.springframework.test.context.transaction.AfterTransaction
    public void handoverFixturesWereRolledBack() {
        if (order != 0) {
            assertThat(count("TransportOrder", "OrderID", order)).isZero();
            assertThat(count("HandoverFile", "OrderID", order)).isZero();
        }
    }
}
