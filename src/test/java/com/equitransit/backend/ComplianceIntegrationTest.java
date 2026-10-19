/*
 * ComplianceIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify F08 using isolated native SQL only.
 */
package com.equitransit.backend;


import com.equitransit.backend.dto.ApiExamples;
import com.equitransit.backend.entity.Customer;
import com.equitransit.backend.entity.UserAccount;
import com.equitransit.backend.repository.AccountRoleRepository;
import com.equitransit.backend.repository.CustomerRepository;
import com.equitransit.backend.repository.UserAccountRepository;
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



/** Native legal fixtures are removed only from the exact isolated catalog. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_COMPLIANCE_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_COMPLIANCE_TEST_ENABLED",
        matches = "true")
public class ComplianceIntegrationTest {

    private final List<Long> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureTariffs = new ArrayList<>();
    private final List<Long> fixtureRules = new ArrayList<>();
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
    private com.equitransit.backend.service.ServiceAssessmentFacade sources;
    @Autowired
    private com.equitransit.backend.service.ComplianceReadinessFacade gates;
    @Autowired
    private PlatformTransactionManager transactions;

    /** @throws Exception if guarded setup fails */
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
        specialist = account("SPECIALIST");
        specialistId = lastStaff();
        JsonNode horse = post("/api/horses", owner,
                mapper.readTree("{\"name\":\"Quotation fixture\"}"), 201);
        ObjectNode draft = (ObjectNode) mapper.readTree(ApiExamples.DRAFT);

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
        for (long id : fixtureRules) {
            jdbc.update("DELETE dbo.ComplianceRuleVersion WHERE RuleID=?", id);
        }
        jdbc.update("DELETE dbo.DocumentTypeCatalog WHERE TypeCode=?", type);
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
        return get(legal() + "?phase=" + phase, owner, 200);
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
        clear("PRE_EXPORT", 409);
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
        assertThat(get(legal() + "/documents", owner, 200).size()).isEqualTo(2);
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
        checklist("PRE_EXPORT");
        assertThat(overview("PRE_EXPORT").at("/readiness/blockers").toString())
                .contains("ROUTE_RULE_PENDING");
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

        assertThat(gate.blockers()).contains("REVISION_MISMATCH");
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
        assertThat(get(legal() + "/documents", owner, 200).size()).isEqualTo(1);
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
        var history = get(legal() + "/documents", owner, 200).get(0);

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
}
