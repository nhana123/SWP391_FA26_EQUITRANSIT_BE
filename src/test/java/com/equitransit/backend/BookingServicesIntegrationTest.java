/*
 * BookingServicesIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify sourced estimates and owned confirmation on SQL Server.
 */
package com.equitransit.backend;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.entity.Customer;
import com.equitransit.backend.entity.UserAccount;
import com.equitransit.backend.repository.AccountRoleRepository;
import com.equitransit.backend.repository.CustomerRepository;
import com.equitransit.backend.repository.ServiceSelectionAuditRepository;
import com.equitransit.backend.repository.ServiceSourceRepository;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.security.TokenTools;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/** All prices/limits/proofs below are isolated artificial test fixtures. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_SERVICES_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_SERVICES_TEST_ENABLED", matches =
                "true")
@Transactional
public class BookingServicesIntegrationTest {

    private static final String MANAGER = "/api/manager/transport-orders";
    private static final String CUSTOMER = "/api/transport-orders";

    private final List<Long> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureOrders = new ArrayList<>();
    private final List<Long> fixtureTariffs = new ArrayList<>();
    private final List<Long> fixturePolicies = new ArrayList<>();

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
    private CustomerRepository customers;
    @Autowired
    private AccountRoleRepository roles;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private JwtTokenService tokens;
    @MockitoSpyBean
    private ServiceSelectionAuditRepository audit;
    @MockitoSpyBean
    private ServiceSourceRepository sources;

    private Account owner;
    private Account other;
    private Account manager;
    private Account specialist;
    private Account coordinator;
    private long horse;
    private long order;

    /**
     * Verifies the isolated catalogue before fixture writes.
     * @throws Exception if setup fails
     */
    @BeforeEach
    public void prepare() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog())
                    .startsWith("EquiTransit_FigmaMigrationTest_");
        }
        owner = account("CUSTOMER");
        other = account("CUSTOMER");
        manager = account("MANAGER");
        specialist = account("SPECIALIST");
        coordinator = account("COORDINATOR");
        horse = read(mvc.perform(post("/api/horses")
                .header("Authorization", owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Test horse",
                        "weightKg", 500, "heightCm", 160, "bodyWidthCm", 70,
                        "measuredAt", OffsetDateTime.now(ZoneOffset.UTC)
                                .minusDays(1).withNano(0).toString()))))
                .andExpect(status().isCreated()).andReturn())
                .get("horseId").asLong();
        order = submittedOrder();
    }

    /** Removes only recorded fixture IDs, including committed race fixtures. */
    @AfterEach
    public void cleanup() {
        reset(audit);
        for (long id : fixtureOrders) {
            String revisions = " IN (SELECT RevisionID FROM dbo.OrderRevision"
                    + " WHERE OrderID=?)";
            String choices = " IN (SELECT ChoiceID FROM dbo.OrderServiceChoice"
                    + " WHERE RevisionID" + revisions + ")";

            jdbc.update("DELETE dbo.AuditEvent WHERE EntityID=?"
                    + " AND EntityType IN ('SERVICE_SELECTION',"
                    + "'ORDER_ASSESSMENT')", id);
            jdbc.update("DELETE dbo.OrderAssignment WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.OrderDecision WHERE RevisionID"
                    + revisions, id);
            jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=NULL,"
                    + "CurrentRevisionID=NULL WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.TripLeg WHERE PlanID IN"
                    + " (SELECT PlanID FROM dbo.TransportPlan WHERE OrderID=?)",
                    id);
            jdbc.update("DELETE dbo.TransportPlan WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.Quote WHERE RevisionID" + revisions, id);
            jdbc.update("DELETE dbo.SupplierOffer WHERE RevisionID"
                    + revisions, id);
            jdbc.update("DELETE dbo.RevisionQuarantineAssessment"
                    + " WHERE RevisionID" + revisions, id);
            jdbc.update("DELETE dbo.RevisionVeterinaryAssessment"
                    + " WHERE RevisionID" + revisions, id);
            for (String table : List.of("StallChoice", "QuarantineChoice",
                    "InsuranceChoice")) {
                jdbc.update("DELETE dbo." + table + " WHERE ChoiceID"
                        + choices, id);
            }
            jdbc.update("DELETE dbo.OrderServiceChoice WHERE RevisionID"
                    + revisions, id);
            jdbc.update("DELETE dbo.OrderChangeRequest WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.TransportOrderDetail WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.OrderRevision WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.TransportOrder WHERE OrderID=?", id);
        }
        for (long id : fixturePolicies) {
            jdbc.update("DELETE dbo.StallFitPolicy WHERE PolicyID=?", id);
        }
        for (long id : fixtureTariffs) {
            jdbc.update("DELETE dbo.ServiceTariff WHERE TariffID=?", id);
        }
        for (long user : fixtureUsers) {
            jdbc.update("DELETE dbo.Horse WHERE CustomerID IN"
                    + " (SELECT CustomerID FROM dbo.Customer WHERE UserID=?)",
                    user);
            jdbc.update("DELETE dbo.Customer WHERE UserID=?", user);
            jdbc.update("DELETE dbo.StaffProfile WHERE UserID=?", user);
            jdbc.update("DELETE dbo.[User] WHERE UserID=?", user);
        }
        fixtureUsers.clear();
        fixtureOrders.clear();
        fixturePolicies.clear();
        fixtureTariffs.clear();
    }

    /** Missing quantities remain null despite a published rate. */
    @Test
    public void showAllIsReadOnlyAndDisclosesEveryMandatoryFee()
            throws Exception {
        tariff("DOCUMENT_COORDINATION", "DEFAULT", "ROUTE_KM",
                "DOCUMENTATION_QUARANTINE", "0.5");
        clearInvocations(sources);
        JsonNode first = disclosure(order);
        JsonNode row = charge(first, "DOCUMENT_COORDINATION");

        verify(sources).tariffs(anyList(), any(OffsetDateTime.class));
        assertThat(first.get("sections")).hasSize(7);
        assertThat(first.get("payable").asBoolean()).isFalse();
        assertThat(first.get("totalEstimate").isNull()).isTrue();
        assertThat(new BigDecimal(row.get("unitRate").asText()))
                .isEqualByComparingTo("0.5");
        assertThat(row.get("quantity").isNull()).isTrue();
        assertThat(row.get("amount").isNull()).isTrue();
        assertThat(row.get("pricingStatus").asText())
                .isEqualTo("QUANTITY_PENDING");
        assertThat(disclosure(order).get("charges"))
                .isEqualTo(first.get("charges"));
        assertThat(disclosure(order).get("recordVersion"))
                .isEqualTo(first.get("recordVersion"));
        assertThat(first.get("airStalls").toString())
                .doesNotContain("SHARED_ONE");
        assertThat(charge(first, "AIRPORT_HANDLING").get("uiSection").asText())
                .isEqualTo("ADDITIONAL_CHARGES");
        assertThat(charge(first, "DOCUMENT_REVIEW").get("amount").isNull())
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.PaymentRequest",
                Long.class)).isZero();
    }

    /** Source-backed fit opens one slot; actual carrier remains unconfirmed. */
    @Test
    public void approvedFitAllowsMeasuredOneSlotWithoutCarrierBooking()
            throws Exception {
        fitPolicy("1", 600, 30);
        JsonNode view = disclosure(order);
        ObjectNode command = command(view, Map.of("airStall", "SHARED_ONE"));
        JsonNode result = confirm(order, command, owner.token(), 200);

        assertThat(view.get("airStalls").toString()).contains("SHARED_ONE");
        assertThat(result.get("revision").isNull()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.StallChoice"
                + " WHERE FitStatus='SUITABLE' AND SlotQuantity=1", Long.class))
                .isEqualTo(1);
        assertThat(disclosure(order).get("carrierConfirmation").asText())
                .isEqualTo("NOT_CONFIRMED");
    }

    /** Oversized choices cannot be hidden in either confirmation or F03. */
    @Test
    public void oversizedOptionIsFilteredAndRejectedOnEverySubmissionPath()
            throws Exception {
        fitPolicy("1", 450, 30);
        fitPolicy("1.5", 450, 30);
        JsonNode view = disclosure(order);
        ObjectNode command = command(view, Map.of("airStall", "SHARED_ONE"));
        ObjectNode direct = customerCurrent(order).get("draft").deepCopy();

        assertThat(view.get("airStalls").toString())
                .doesNotContain("SHARED_ONE");
        assertThat(confirm(order, command, owner.token(), 409).get("code")
                .asText())
                .isEqualTo("AIR_STALL_UNSUITABLE");
        direct.set("services", mapper.valueToTree(Map.of("airStall", "WIDE")));
        direct.put("recordVersion", view.get("recordVersion").asText());
        direct.put("operationId", UUID.randomUUID().toString());
        direct.put("changeReason", "Try another unsuitable option");
        mvc.perform(post(CUSTOMER + "/{id}/revisions", order)
                .header("Authorization", owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                        .content(direct.toString()))
                .andExpect(status().isConflict());
        assertThat(revisionCount(order)).isEqualTo(1);
    }

    /**
     * Expiring and ambiguous source policies do not imply one-slot eligibility.
     */
    @Test
    public void expiredAndOverlappingFitPoliciesStayPending() throws Exception {
        fitPolicy("1", 600, 1);
        assertThat(disclosure(order).get("airStalls").toString())
                .doesNotContain("SHARED_ONE");
        fitPolicy("1", 600, 30);
        fitPolicy("1", 650, 30);
        JsonNode view = disclosure(order);

        assertThat(view.get("airStalls").toString())
                .doesNotContain("SHARED_ONE");
        confirm(order, command(view, Map.of("airStall", "SHARED_ONE")),
                owner.token(), 409);
    }

    /** Draft confirmation is a saved selection, not a submitted order. */
    @Test
    public void draftConfirmationHasStableRetryAndNoFakeRevision()
            throws Exception {
        long draft = draftOrder();
        JsonNode view = disclosure(draft);
        ObjectNode command = command(view, Map.of("enhancedCare", true));
        JsonNode first = confirm(draft, command, owner.token(), 200);

        assertThat(first.get("revision").isNull()).isTrue();
        assertThat(first.at("/order/orderStatus").asText()).isEqualTo("DRAFT");
        assertThat(revisionCount(draft)).isZero();
        assertThat(confirm(draft, command, owner.token(), 200))
                .isEqualTo(first);
        command.set("services", mapper.valueToTree(Map.of("enhancedCare",
                false)));
        assertThat(confirm(draft, command, owner.token(), 409).get("code")
                .asText())
                .isEqualTo("SERVICE_RETRY_CONFLICT");
    }

    /** New choices preserve old snapshots and persist typed specializations. */
    @Test
    public void serviceRevisionPersistsSelectionsWithoutSellingCoverOrDays()
            throws Exception {
        JsonNode view = disclosure(order);
        long original = view.get("revisionId").asLong();
        String originalSnapshot = jdbc.queryForObject("SELECT OrderSnapshot"
                + " FROM dbo.OrderRevision WHERE RevisionID=?", String.class,
                original);
        ObjectNode command = command(view, Map.of("airStall", "WIDE",
                "quarantinePackage", "STANDARD", "insurance", "COMBINED",
                "declaredValue", 50000));
        JsonNode receipt = confirm(order, command, owner.token(), 200);

        assertThat(receipt.at("/revision/revisionNo").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT OrderSnapshot FROM "
                + "dbo.OrderRevision"
                + " WHERE RevisionID=?", String.class, original))
                .isEqualTo(originalSnapshot);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.QuarantineChoice"
                + " WHERE RequiredDays IS NULL", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT CoverageCode FROM "
                + "dbo.InsuranceChoice",
                String.class)).isEqualTo("MORTALITY_INJURY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.InsurancePolicy",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.PaymentRequest",
                Long.class)).isZero();
    }

    /**
     * Owner/role checks and strict DTOs reject forged price/mandatory inputs.
     */
    @Test
    public void enforcesOwnershipRoleAndRejectsForgedFields() throws Exception {
        JsonNode view = disclosure(order);
        ObjectNode command = command(view, Map.of("enhancedCare", true));

        mvc.perform(get(CUSTOMER + "/{id}/services", order)
                .header("Authorization", other.token()))
                .andExpect(status().isNotFound());
        confirm(order, command, other.token(), 404);
        confirm(order, command, manager.token(), 403);
        mvc.perform(get(CUSTOMER + "/{id}/services", order))
                .andExpect(status().isUnauthorized());
        for (String forged : List.of("unitPrice", "requiredDays", "fitStatus",
                "horseId", "policyId")) {
            ObjectNode attempt = command.deepCopy();

            ((ObjectNode) attempt.get("services")).put(forged, 1);
            confirm(order, attempt, owner.token(), 400);
        }
        assertThat(revisionCount(order)).isEqualTo(1);
    }

    /** Fresh source quantities produce exact kilometre formulas, once each. */
    @Test
    public void sourcedPlanPricesRoadStallAndCoordinationExactlyOnce()
            throws Exception {
        JsonNode view = disclosure(order);
        JsonNode receipt = confirm(order, command(view, Map.of("airStall",
                "WIDE",
                "roadArrangement", "EQUITRANSIT")), owner.token(), 200);
        long revision = receipt.at("/revision/revisionId").asLong();

        approveCurrent();
        sourcedPlan(revision);
        tariff("ROAD_TRANSPORT", "EQUITRANSIT", "KM", "TRANSPORTATION", "4");
        tariff("AIR_STALL", "DEFAULT", "SLOT_KM", "HORSE_ACCOMMODATION", "0.6");
        tariff("DOCUMENT_COORDINATION", "DEFAULT", "ROUTE_KM",
                "DOCUMENTATION_QUARANTINE", "0.5");
        view = disclosure(order);
        assertThat(new BigDecimal(charge(view, "ROAD_TRANSPORT")
                .get("amount").asText()))
                .isEqualByComparingTo("400.00");
        assertThat(new BigDecimal(charge(view, "AIR_STALL").get("amount")
                .asText()))
                .isEqualByComparingTo("900.00");
        assertThat(new BigDecimal(charge(view, "DOCUMENT_COORDINATION")
                .get("amount").asText()))
                .isEqualByComparingTo("550.00");
        assertThat(new BigDecimal(view.get("knownSubtotal").asText()))
                .isEqualByComparingTo("1850.00");
        assertThat(view.get("totalEstimate").isNull()).isTrue();
        assertThat(view.get("charges").findValues("code"))
                .filteredOn(node -> node.asText()
                        .equals("DOCUMENT_COORDINATION"))
                .hasSize(1);
    }

    /** Current Specialist source distinguishes not-required from unknown. */
    @Test
    public void quarantineApplicabilityIsSourceBoundAndNeverZero()
            throws Exception {
        JsonNode before = disclosure(order);
        long revision = before.get("revisionId").asLong();

        assertThat(before.at("/quarantine/applicability").asText())
                .isEqualTo("UNKNOWN");
        approveCurrent();
        quarantine(revision, "NOT_REQUIRED", null);
        assertThat(disclosure(order).at("/quarantine/applicability").asText())
                .isEqualTo("NOT_REQUIRED");
        jdbc.update("UPDATE dbo.RevisionQuarantineAssessment SET SupersededAt="
                + "SYSUTCDATETIME() WHERE RevisionID=?", revision);
        quarantine(revision, "REQUIRED", 3);
        assertThat(disclosure(order).at("/quarantine/requiredDays").asInt())
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM"
                + " dbo.RevisionQuarantineAssessment WHERE RevisionID=?",
                Long.class, revision)).isEqualTo(2);
        jdbc.update("UPDATE dbo.RevisionQuarantineAssessment SET ValidUntil="
                + "DATEADD(day,1,SYSUTCDATETIME()) WHERE RevisionID=?"
                + " AND SupersededAt IS NULL", revision);
        assertThat(disclosure(order).at("/quarantine/applicability").asText())
                .isEqualTo("UNKNOWN");
    }

    /**
     * Explicit selected-offer bundles do not expose supplier costs or margins.
     */
    @Test
    public void bundlesSuppressDuplicateChargesWithoutLeakingSupplierCost()
            throws Exception {
        long revision = disclosure(order).get("revisionId").asLong();

        jdbc.update("INSERT dbo.SupplierOffer (RevisionID,ProviderName,"
                + "ServiceCode,"
                + "UnitCode,Quantity,UnitPrice,Currency,IncludesStall,"
                + "IncludedCharges,ValidUntil,Status,EvidenceFileKey) VALUES"
                + " (?,'Test provider','AIRFARE','SHIPMENT',1,123456.78,"
                        + "'USD',1,"
                + "?,DATEADD(day,30,SYSUTCDATETIME()),'SELECTED','test-proof')",
                revision, "{\"schemaVersion\":1,\"services\":["
                        + "\"AIRPORT_HANDLING\",\"TERMINAL_HANDLING\"]}");
        JsonNode view = disclosure(order);

        assertThat(charge(view, "AIR_STALL").get("pricingStatus").asText())
                .isEqualTo("INCLUDED");
        assertThat(charge(view, "AIRPORT_HANDLING").get("amount").isNull())
                .isTrue();
        assertThat(charge(view, "TERMINAL_HANDLING").get("pricingStatus")
                .asText())
                .isEqualTo("INCLUDED");
        assertThat(view.toString()).doesNotContain("123456.78", "unitCost",
                "margin", "test-proof");
    }

    /** Unverifiable bundle metadata is a blocker, not an inclusion guess. */
    @Test
    public void unknownBundleSchemaRequiresReview() throws Exception {
        long revision = disclosure(order).get("revisionId").asLong();

        jdbc.update("INSERT dbo.SupplierOffer (RevisionID,ProviderName,"
                + "ServiceCode,"
                + "UnitCode,Quantity,UnitPrice,Currency,IncludesStall,"
                + "IncludedCharges,ValidUntil,Status,EvidenceFileKey) VALUES"
                + " (?,'Test provider','AIRFARE','SHIPMENT',1,10,'USD',1,"
                + "'[]',DATEADD(day,30,SYSUTCDATETIME()),'SELECTED',"
                        + "'test-proof')",
                revision);
        assertThat(charge(disclosure(order), "AIRPORT_HANDLING")
                .get("pricingStatus").asText())
                        .isEqualTo("BUNDLE_REVIEW_REQUIRED");
    }

    /**
     * Changed horse measurements cannot be silently confirmed against old fit.
     */
    @Test
    public void horseVersionChangeRequiresExplicitSnapshotRefresh()
            throws Exception {
        JsonNode view = disclosure(order);
        JsonNode profile = read(mvc.perform(get("/api/horses/{id}", horse)
                .header("Authorization", owner.token()))
                .andExpect(status().isOk()).andReturn());
        ObjectNode command = command(view, Map.of("airStall", "WIDE"));

        mvc.perform(put("/api/horses/{id}", horse)
                .header("Authorization", owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name",
                        "Updated horse",
                        "recordVersion", profile.get("recordVersion")
                                .asText()))))
                .andExpect(status().isOk());
        assertThat(confirm(order, command, owner.token(), 409).get("code")
                .asText())
                .isEqualTo("HORSE_VERSION_CONFLICT");
        assertThat(revisionCount(order)).isEqualTo(1);
    }

    /** Existing policy reference is required; no coverage is activated here. */
    @Test
    public void insuranceReferenceIsValidatedAndNotAPremium()
            throws Exception {
        JsonNode view = disclosure(order);

        confirm(order, command(view, Map.of("insurance", "EXISTING")),
                owner.token(), 409);
        confirm(order, command(view, Map.of("insurance", "EXISTING",
                "existingPolicyReference", "TEST-POLICY")), owner.token(), 200);
        assertThat(jdbc.queryForObject("SELECT CoverageCode FROM "
                + "dbo.InsuranceChoice",
                String.class)).isEqualTo("EXISTING_POLICY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.InsurancePolicy",
                Long.class)).isZero();
    }

    /** Accepted customer quote blocks direct service changes. */
    @Test
    public void acceptedQuoteRequiresControlledChange() throws Exception {
        JsonNode view = disclosure(order);

        jdbc.update("INSERT dbo.Quote (RevisionID,QuoteNumber,Currency,"
                + "QuoteStatus,IssuedAt,AcceptedAt) VALUES (?,?,'USD',"
                + "'ACCEPTED',DATEADD(second,-1,SYSUTCDATETIME()),"
                + "SYSUTCDATETIME())", view.get("revisionId").asLong(),
                TokenTools.randomToken().substring(0, 20));
        assertThat(confirm(order, command(view, Map.of("enhancedCare", true)),
                owner.token(), 409).get("code").asText())
                .isEqualTo("ORDER_CHANGE_REQUEST_REQUIRED");
        assertThat(revisionCount(order)).isEqualTo(1);
    }

    /**
     * Failure after nested revision writes rolls back all service artifacts.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void auditFailureRollsBackNestedRevisionAndSpecializations()
            throws Exception {
        ObjectNode command = command(disclosure(order), Map.of("airStall",
                "WIDE",
                "quarantinePackage", "STANDARD"));

        doThrow(new DataIntegrityViolationException("Artificial failure"))
                .when(audit).append(anyLong(), anyLong(), anyString());
        confirm(order, command, owner.token(), 409);
        assertThat(revisionCount(order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.StallChoice",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.QuarantineChoice",
                Long.class)).isZero();
    }

    /**
     * Different concurrent confirmations on one version have a single winner.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void concurrentServiceConfirmationHasOneWinner() throws Exception {
        ObjectNode first = command(disclosure(order), Map.of("enhancedCare",
                true));
        ObjectNode second = first.deepCopy();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        second.put("operationId", UUID.randomUUID().toString());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> compete(first, ready, start));
            var right = workers.submit(() -> compete(second, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(left.get(30, TimeUnit.SECONDS).getResponse()
                    .getStatus(),
                    right.get(30, TimeUnit.SECONDS).getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
        }
        assertThat(revisionCount(order)).isEqualTo(2);
    }

    private MvcResult compete(ObjectNode command, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Fixture start timed out.");
        }
        return mvc.perform(post(CUSTOMER + "/{id}/services/confirm", order)
                .header("Authorization", owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString())).andReturn();
    }

    /** Clinical and quarantine quantities come from Specialist proof only. */
    @Test
    public void specialistQuantitiesUseActualUnitsInsteadOfRouteKilometres()
            throws Exception {
        JsonNode view = disclosure(order);
        JsonNode receipt = confirm(order, command(view, Map.of(
                "veterinaryArrangement", "EQUITRANSIT", "quarantinePackage",
                "STANDARD")), owner.token(), 200);
        long revision = receipt.at("/revision/revisionId").asLong();

        approveCurrent();
        quarantine(revision, "REQUIRED", 3);
        jdbc.update("INSERT dbo.RevisionVeterinaryAssessment (RevisionID,"
                + "VisitCount,TestCount,DoseCount,CertificateCount,"
                        + "SpecialistID,"
                + "AssessedAt,ValidUntil,SourceReference) VALUES (?,2,3,1,1,?,"
                + "DATEADD(second,-1,SYSUTCDATETIME()),"
                + "DATEADD(day,30,SYSUTCDATETIME()),'Artificial clinical "
                        + "source')",
                revision, specialist.staffId());
        tariff("VETERINARY", "EXAM", "HORSE_VISIT", "VETERINARY", "80");
        tariff("VETERINARY", "TEST", "TEST_SAMPLE", "VETERINARY", "75");
        tariff("VETERINARY", "VACCINE", "DOSE", "VETERINARY", "45");
        tariff("VETERINARY", "CERTIFICATE", "CERTIFICATE", "VETERINARY", "120");
        tariff("QUARANTINE", "STANDARD", "HORSE_DAY",
                "DOCUMENTATION_QUARANTINE", "100");
        view = disclosure(order);
        assertThat(new BigDecimal(charge(view, "VETERINARY_EXAM")
                .get("amount").asText()))
                .isEqualByComparingTo("160.00");
        assertThat(new BigDecimal(charge(view, "VETERINARY_TEST")
                .get("amount").asText()))
                .isEqualByComparingTo("225.00");
        assertThat(charge(view, "VETERINARY_VACCINE").get("unit").asText())
                .isEqualTo("DOSE");
        assertThat(new BigDecimal(charge(view, "QUARANTINE").get("amount")
                .asText()))
                .isEqualByComparingTo("300.00");
        assertThat(new BigDecimal(view.get("knownSubtotal").asText()))
                .isEqualByComparingTo("850.00");
    }

    /** REQUIRED cannot carry NULL/zero; current assessment uniqueness holds. */
    @Test
    public void assessmentConstraintsPreventFalseExemption() throws Exception {
        long revision = disclosure(order).get("revisionId").asLong();

        approveCurrent();
        assertThatThrownBy(() -> quarantine(revision, "REQUIRED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> quarantine(revision, "REQUIRED", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        quarantine(revision, "NOT_REQUIRED", null);
        assertThatThrownBy(() -> quarantine(revision, "REQUIRED", 3))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Missing producer tables are pending, not waived or a server error. */
    @Test
    public void preMigrationAssessmentSourcesRemainUnknown() throws Exception {
        JsonNode before = disclosure(order);

        jdbc.execute("SAVE TRANSACTION ServiceSourceCompatibility");
        try {
            jdbc.execute("DROP TABLE dbo.RevisionQuarantineAssessment");
            jdbc.execute("DROP TABLE dbo.RevisionVeterinaryAssessment");
            assertThat(disclosure(order).at("/quarantine/applicability")
                    .asText())
                    .isEqualTo("UNKNOWN");
            assertThat(disclosure(order).get("totalEstimate").isNull())
                    .isTrue();
            assertThat(disclosure(order).get("recordVersion"))
                    .isEqualTo(before.get("recordVersion"));
        } finally {
            jdbc.execute("ROLLBACK TRANSACTION ServiceSourceCompatibility");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.RevisionQuarantineAssessment", Long.class))
                        .isZero();
    }

    /**
     * Monetary JSON uses decimal text to preserve values beyond JS integers.
     */
    @Test
    public void declaredValueKeepsExactSqlPrecisionAndNeverBecomesPremium()
            throws Exception {
        String value = "9007199254740993.01";
        ObjectNode body = command(disclosure(order), Map.of("insurance",
                "MORTALITY", "declaredValue", value));
        JsonNode receipt = confirm(order, body, owner.token(), 200);

        assertThat(receipt.at("/revision/snapshot/request/services/declaredVa"
                + "lue")
                .asText()).isEqualTo(value);
        assertThat(jdbc.queryForObject("SELECT DeclaredValue"
                + " FROM dbo.InsuranceChoice", BigDecimal.class))
                .isEqualByComparingTo(value);
        assertThat(charge(disclosure(order), "INSURANCE").get("amount")
                .isNull())
                .isTrue();
    }

    /** The catalogue batch retains ambiguity, not a highest-version guess. */
    @Test
    public void overlappingRatesStayPendingInTheBatchedCatalogue()
            throws Exception {
        approveCurrent();
        sourcedPlan(disclosure(order).get("revisionId").asLong());
        for (int version = 1; version <= 3; version++) {
            tariff("DOCUMENT_COORDINATION", "DEFAULT", "ROUTE_KM",
                    "DOCUMENTATION_QUARANTINE", "0.5");
            jdbc.update("UPDATE dbo.ServiceTariff SET VersionNo=?"
                    + " WHERE TariffID=?", version + 1,
                    fixtureTariffs.getLast());
        }
        JsonNode row = charge(disclosure(order), "DOCUMENT_COORDINATION");

        assertThat(row.get("amount").isNull()).isTrue();
        assertThat(row.get("blockers").toString()).contains("RATE_AMBIGUOUS");
    }

    /** Hostile decimal notation is rejected before any revision is written. */
    @Test
    public void rejectsUnboundedDeclaredValuesWithoutChangingOrder()
            throws Exception {
        JsonNode before = disclosure(order);
        long revisions = revisionCount(order);

        for (String value : List.of("1e2147483647", "1e-2147483647",
                "99999999999999999.99", "0.001", "-1", "NaN")) {
            confirm(order, command(before, Map.of("insurance", "MORTALITY",
                    "declaredValue", value)), owner.token(), 400);
        }
        assertThat(revisionCount(order)).isEqualTo(revisions);
        assertThat(disclosure(order).get("recordVersion"))
                .isEqualTo(before.get("recordVersion"));
    }

    private ObjectNode command(JsonNode view, Map<String, Object> preferences) {
        ObjectNode body = mapper.createObjectNode();

        body.put("recordVersion", view.get("recordVersion").asText());
        body.put("horseRecordVersion", view.get("horseRecordVersion").asText());
        body.put("operationId", UUID.randomUUID().toString());
        body.put("changeReason", "Customer updated requested services");
        body.set("services", mapper.valueToTree(preferences));
        return body;
    }

    private JsonNode confirm(long id, ObjectNode body, String token,
            int expected) throws Exception {
        return read(mvc.perform(post(CUSTOMER + "/{id}/services/confirm", id)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().is(expected)).andReturn());
    }

    private JsonNode disclosure(long id) throws Exception {
        return read(mvc.perform(get(CUSTOMER + "/{id}/services", id)
                .header("Authorization", owner.token()))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode charge(JsonNode view, String code) {
        for (JsonNode row : view.get("charges")) {
            if (code.equals(row.get("code").asText())) {
                return row;
            }
        }
        throw new IllegalStateException("Missing charge: " + code);
    }

    private long revisionCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.OrderRevision"
                + " WHERE OrderID=?", Long.class, id);
    }

    private long draftOrder() throws Exception {
        JsonNode draft = read(mvc.perform(post(CUSTOMER)
                .header("Authorization", owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("horseId", horse))))
                .andExpect(status().isCreated()).andReturn());
        long id = draft.get("orderId").asLong();

        fixtureOrders.add(id);
        return id;
    }

    private void approveCurrent() throws Exception {
        mvc.perform(post(MANAGER + "/{id}/decisions", order)
                .header("Authorization", manager.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(approval(order).toString())).andExpect(status()
                        .isOk());
    }

    private void fitPolicy(String slots, int maxWeight, int days) {
        Long id = jdbc.queryForObject("INSERT dbo.StallFitPolicy (PolicyCode,"
                + "VersionNo,SlotQuantity,MaxWeightKg,MaxHeightCm,MaxWidthCm,"
                + "EffectiveFrom,EffectiveTo,SourceURL,ReviewedBy,ReviewedAt,"
                        + "Status)"
                + " OUTPUT INSERTED.PolicyID VALUES (?,1,?,?,200,100,"
                + "DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(day,?,"
                        + "SYSUTCDATETIME()),"
                + "'https://example.invalid/test-only-fit',?,DATEADD(second,"
                        + "-1,SYSUTCDATETIME()),"
                + "'APPROVED')", Long.class, TokenTools.randomToken()
                        .substring(0, 20),
                slots, maxWeight, days, manager.staffId());

        fixturePolicies.add(id);
    }

    private void tariff(String code, String option, String unit, String group,
            String rate) {
        Long id = jdbc.queryForObject("INSERT dbo.ServiceTariff"
                + " (ServiceCode,OptionCode,UnitCode,CostGroup,UnitPrice,"
                        + "Currency,"
                + "VersionNo,EffectiveFrom,Status) OUTPUT INSERTED.TariffID"
                + " VALUES (?,?,?,?,?,'USD',1,DATEADD(day,-1,SYSUTCDATETIME()),"
                + "'ACTIVE')", Long.class, code, option, unit, group, rate);

        fixtureTariffs.add(id);
    }

    private void sourcedPlan(long revision) {
        Long plan = jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids TABLE"
                + " (ID BIGINT); INSERT dbo.TransportPlan (OrderID,RevisionID,"
                + "CreatedBy,VersionNo,FinancialGateStatus,"
                        + "OperationalGateStatus,"
                + "DocGateStatus,PlanStatus) OUTPUT INSERTED.PlanID INTO @ids"
                + " VALUES (?,?,?,1,'PENDING','PENDING','PENDING','CONFIRMED');"
                + " SELECT ID FROM @ids", Long.class, order, revision,
                        coordinator.staffId());

        jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=? WHERE "
                + "OrderID=?",
                plan, order);
        for (String mode : List.of("ROAD", "AIR")) {
            jdbc.update("INSERT dbo.TripLeg (PlanID,SequenceNo,TransportMode,"
                    + "StartPoint,EndPoint,DistanceKm,LegStatus,DistanceSource,"
                    + "DistanceConfirmedBy,DistanceConfirmedAt) VALUES"
                    + " (?,? ,?,'Test origin','Test destination',?,'PLANNED',"
                    + "'Artificial verified distance',?,DATEADD(second,-1,"
                            + "SYSUTCDATETIME()))",
                    plan, "ROAD".equals(mode) ? 1 : 2, mode,
                    "ROAD".equals(mode) ? 100 : 1000, coordinator.staffId());
        }
    }

    private void quarantine(long revision, String applicability, Integer days) {
        jdbc.update("INSERT dbo.RevisionQuarantineAssessment (RevisionID,"
                + "Applicability,RequiredDays,SpecialistID,AssessedAt,"
                        + "ValidUntil,"
                + "SourceReference) VALUES (?,?,?,?,DATEADD(second,-1,"
                        + "SYSUTCDATETIME()),"
                + "DATEADD(day,30,SYSUTCDATETIME()),'Artificial assessment')",
                revision, applicability, days, specialist.staffId());
    }
    private JsonNode managerCurrent(long id) throws Exception {
        return read(mvc.perform(get(MANAGER + "/{id}", id)
                .header("Authorization", manager.token()))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode customerCurrent(long id) throws Exception {
        return read(mvc.perform(get(CUSTOMER + "/{id}", id)
                .header("Authorization", owner.token()))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private ObjectNode approval(long id) throws Exception {
        JsonNode current = managerCurrent(id);
        ObjectNode command = mapper.createObjectNode();

        command.put("revisionId", current.get("currentRevisionId").asLong());
        command.put("recordVersion", current.get("recordVersion").asText());
        command.put("operationId", UUID.randomUUID().toString());
        command.put("decision", "APPROVED");
        command.put("specialistId", specialist.staffId());
        command.put("coordinatorId", coordinator.staffId());
        return command;
    }

    private long submittedOrder() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        JsonNode draft;
        long id;

        fields.put("horseId", horse);
        fields.put("originCountryCode", "US");
        fields.put("destinationCountryCode", "GB");
        fields.put("originAddress", "Pickup address");
        fields.put("destinationAddress", "Delivery address");
        fields.put("originTimezone", "America/New_York");
        fields.put("destinationTimezone", "Europe/London");
        fields.put("deliveryDeadline", OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(14).withNano(0).toString());
        draft = read(mvc.perform(post(CUSTOMER)
                .header("Authorization", owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(fields)))
                .andExpect(status().isCreated()).andReturn());
        id = draft.get("orderId").asLong();
        fixtureOrders.add(id);
        mvc.perform(post(CUSTOMER + "/{id}/submit", id)
                .header("Authorization", owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("recordVersion",
                        draft.get("recordVersion").asText(), "operationId",
                        UUID.randomUUID())))).andExpect(status().isOk());
        return id;
    }

    private Account account(String role) {
        UserAccount user = new UserAccount();
        Customer customer = new Customer();
        Long staff = null;

        user.setRole(roles.findByRoleCode(role).orElseThrow());
        user.setEmail(TokenTools.randomToken() + "@example.invalid");
        user.setPasswordHash(passwords.encode("ExamplePassword123!"));
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        users.saveAndFlush(user);
        fixtureUsers.add(user.getUserId());
        if ("CUSTOMER".equals(role)) {
            customer.setUser(user);
            customer.setFullName("Assessment customer fixture");
            customers.saveAndFlush(customer);
        } else {
            staff = jdbc.queryForObject("INSERT dbo.StaffProfile"
                    + " (UserID,FullName) OUTPUT INSERTED.StaffID VALUES (?,?)",
                    Long.class, user.getUserId(), "Assessment " + role);
        }
        return new Account(user.getUserId(), staff,
                "Bearer " + tokens.issue(user).getAccessToken());
    }

    /** Test-only actor, never production credentials. */
    private record Account(long userId, Long staffId, String token) {
    }
}
