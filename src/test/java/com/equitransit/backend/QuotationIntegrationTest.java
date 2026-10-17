/*
 * QuotationIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
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
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/** Real SQL fixtures stay in the exact opt-in test database and are removed. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_QUOTE_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_QUOTE_TEST_ENABLED", matches = "true")
public class QuotationIntegrationTest {

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

            jdbc.update("DELETE dbo.AuditEvent WHERE EntityID=? AND"
                    + " EntityType IN ('ORDER_QUOTATION','ORDER_ASSESSMENT',"
                    + "'SERVICE_SELECTION')", order);
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
     * Full lifecycle freezes retail prices, terms and payment obligations.
     * @throws Exception if API calls fail
     */
    @Test
    public void lifecycleAndRetryDoNotDuplicateObligations() throws Exception {
        JsonNode draft = post(base() + "/quotes", coordinator, prepare(), 200);
        long id = draft.at("/quote/quoteId").asLong();

        assertThat(draft.at("/quote/total").asText()).isEqualTo("260.00");
        action(id, "APPROVE", manager, 200);
        action(id, "ISSUE", manager, 200);
        jdbc.update("UPDATE dbo.ServiceTariff SET UnitPrice=UnitPrice+1"
                + " WHERE TariffID IN (?,?,?)", fixtureTariffs.toArray());
        ObjectNode accept = actionBody("ACCEPT");
        JsonNode first = post(base() + "/quotes/" + id + "/actions", owner,
                accept, 200);
        JsonNode replay = post(base() + "/quotes/" + id + "/actions", owner,
                accept, 200);

        assertThat(first).isEqualTo(replay);
        assertThat(first.at("/quote/status").asText()).isEqualTo("ACCEPTED");
        assertThat(count("QuotePaymentSchedule", "QuoteID", id)).isEqualTo(1);
        assertThat(first.toString()).doesNotContain("purchaseUnitPrice",
                "PreparedBy", "SourceReference", "supplierCost", "margin");
        post(base() + "/quotes", manager, prepare(), 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TransportPlan"
                + " WHERE OrderID=? AND DepartureAuthorizedAt IS NOT NULL",
                Integer.class, order)).isZero();
    }

    /**
     * Pending quantities have no zero QuoteItem and block approval/issue.
     * @throws Exception if API calls fail
     */
    @Test
    public void pendingPricesCannotBeApprovedOrIssued() throws Exception {
        ObjectNode body = prepare();

        body.set("assessments", mapper.createArrayNode());
        JsonNode draft = post(base() + "/quotes", manager, body, 200);
        long id = draft.at("/quote/quoteId").asLong();

        assertThat(draft.at("/quote/total").isNull()).isTrue();
        assertThat(draft.get("blockers").size()).isGreaterThan(0);
        action(id, "APPROVE", manager, 409);
        action(id, "ISSUE", manager, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.QuoteItem"
                + " WHERE QuoteID=? AND Amount=0", Integer.class, id)).isZero();
    }

    /**
     * Live role, ownership and assignment enforce write/read boundaries.
     * @throws Exception if API calls fail
     */
    @Test
    public void rolesOwnershipAndAssignmentAreEnforced() throws Exception {
        post(base() + "/quotes", owner, prepare(), 403);
        post(base() + "/quotes", specialist, prepare(), 403);
        JsonNode draft = post(base() + "/quotes", manager, prepare(), 200);
        long id = draft.at("/quote/quoteId").asLong();

        get(base() + "/quotes/" + id, owner, 404);
        action(id, "APPROVE", coordinator, 403);
        action(id, "APPROVE", manager, 200);
        action(id, "ISSUE", manager, 200);
        get(base() + "/quotes/" + id, other, 404);
        action(id, "ACCEPT", other, 404);
        action(id, "ACCEPT", manager, 403);
        jdbc.update("UPDATE dbo.OrderAssignment SET EndedAt=SYSUTCDATETIME()"
                + " WHERE OrderID=? AND StaffID=?", order, coordinatorId);
        post(base() + "/quotes", coordinator, prepare(), 403);
    }

    /**
     * Manager-rejected internal drafts must not appear in customer history.
     * @throws Exception if API calls fail
     */
    @Test
    public void rejectedUnissuedDraftRemainsPrivate() throws Exception {
        long id = post(base() + "/quotes", manager, prepare(), 200)
                .at("/quote/quoteId").asLong();

        action(id, "REJECT", manager, 200);
        JsonNode list = get(base() + "/quotes", owner, 200);

        assertThat(list.get("items")).isEmpty();
        assertThat(list.get("totalElements").asLong()).isZero();
        get(base() + "/quotes/" + id, owner, 404);
    }

    /**
     * Replacement version supersedes old offers but preserves captured items.
     * @throws Exception if API calls fail
     */
    @Test
    public void replacementSupersedesAndDoesNotRewriteHistory()
            throws Exception {
        JsonNode first = post(base() + "/quotes", manager, prepare(), 200);
        long firstId = first.at("/quote/quoteId").asLong();

        action(firstId, "APPROVE", manager, 200);
        action(firstId, "ISSUE", manager, 200);
        JsonNode second = post(base() + "/quotes", manager, prepare(), 200);

        assertThat(second.at("/quote/version").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT QuoteStatus FROM dbo.Quote"
                + " WHERE QuoteID=?", String.class, firstId))
                .isEqualTo("SUPERSEDED");
        assertThat(count("QuoteItem", "QuoteID", firstId)).isEqualTo(3);
        action(firstId, "ACCEPT", owner, 409);
    }

    /**
     * Expired quotations and revision mismatches cannot be accepted.
     * @throws Exception if API calls fail
     */
    @Test
    public void expiredAndStaleRevisionAcceptanceAreRejected()
            throws Exception {
        long id = issued();

        jdbc.update("UPDATE dbo.Quote SET IssuedAt=DATEADD(day,-2,"
                + "SYSUTCDATETIME()),ValidUntil=DATEADD(day,-1,"
                + "SYSUTCDATETIME()) WHERE QuoteID=?", id);
        action(id, "ACCEPT", owner, 409);
        action(id, "EXPIRE", manager, 200);
        assertThat(jdbc.queryForObject("SELECT QuoteStatus FROM dbo.Quote"
                + " WHERE QuoteID=?", String.class, id)).isEqualTo("EXPIRED");
        long fresh = issued();

        jdbc.update("UPDATE dbo.TransportOrder SET OrderStatus='SUBMITTED'"
                + " WHERE OrderID=?", order);
        action(fresh, "ACCEPT", owner, 409);
    }

    /**
     * Payment schedule amounts must cover the exact rounded total.
     * @throws Exception if API calls fail
     */
    @Test
    public void mismatchedScheduleAndChangedRatesBlockApproval()
            throws Exception {
        ObjectNode body = prepare();

        ((ObjectNode) body.at("/paymentSchedule/0")).put("amountDue", "1.00");
        long id = post(base() + "/quotes", manager, body, 200)
                .at("/quote/quoteId").asLong();

        action(id, "APPROVE", manager, 409);
        long next = post(base() + "/quotes", manager, prepare(), 200)
                .at("/quote/quoteId").asLong();

        jdbc.update("UPDATE dbo.ServiceTariff SET UnitPrice=UnitPrice+1"
                + " WHERE TariffID=?", fixtureTariffs.getFirst());
        action(next, "APPROVE", manager, 409);
    }

    /**
     * Concurrent preparation with one version creates exactly one quote.
     * @throws Exception if thread coordination fails
     */
    @Test
    public void competingPrepareHasOneWinner() throws Exception {
        ObjectNode first = prepare();
        ObjectNode second = first.deepCopy();

        ((ObjectNode) second.get("command")).put("operationId",
                UUID.randomUUID().toString());
        assertThat(race(base() + "/quotes", manager, first, second))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Quote"
                + " WHERE RevisionID=?", Integer.class, revision)).isEqualTo(1);
    }

    /**
     * Concurrent accept with one version cannot create two commitments.
     * @throws Exception if thread coordination fails
     */
    @Test
    public void competingAcceptHasOneWinner() throws Exception {
        long id = issued();
        ObjectNode first = actionBody("ACCEPT");
        ObjectNode second = first.deepCopy();

        ((ObjectNode) second.get("command")).put("operationId",
                UUID.randomUUID().toString());
        assertThat(race(base() + "/quotes/" + id + "/actions", owner,
                first, second)).containsExactlyInAnyOrder(200, 409);
        assertThat(count("QuotePaymentSchedule", "QuoteID", id)).isEqualTo(1);
    }

    /**
     * Same operation UUID replays; changed input never reuses the receipt.
     * @throws Exception if API calls fail
     */
    @Test
    public void prepareReplayIsStableAndChangedInputConflicts()
            throws Exception {
        ObjectNode body = prepare();
        JsonNode first = post(base() + "/quotes", manager, body, 200);

        assertThat(post(base() + "/quotes", manager, body, 200))
                .isEqualTo(first);
        body.put("termsVersion", "CHANGED");
        post(base() + "/quotes", manager, body, 409);
    }

    /**
     * Supplier purchase price and retail price remain distinct and private.
     * @throws Exception if API calls fail
     */
    @Test
    public void supplierOfferIsEvidencedImmutableAndIdempotent()
            throws Exception {
        ObjectNode offer = mapper.createObjectNode();

        offer.set("command", command());
        offer.put("revisionId", revision);
        offer.put("serviceCode", "AIRFARE");
        offer.put("providerName", "Artificial test supplier");
        offer.put("offerReference", "TEST-SOURCE-ONLY");
        offer.put("unitCode", "SHIPMENT");
        offer.put("quantity", "1");
        offer.put("purchaseUnitPrice", "900.00");
        offer.put("retailUnitPrice", "1200.00");
        offer.put("validUntil", time(5));
        offer.put("evidenceFileKey", "test-only/source-proof.pdf");
        offer.put("includesStall", true);
        offer.set("includedCharges", mapper.valueToTree(
                List.of("AIRPORT_HANDLING", "TERMINAL_HANDLING")));
        post(base() + "/supplier-offers", owner, offer, 403);
        JsonNode first = post(base() + "/supplier-offers", coordinator,
                offer, 200);

        assertThat(post(base() + "/supplier-offers", coordinator, offer, 200))
                .isEqualTo(first);
        long id = first.get("offerId").asLong();

        assertThat(jdbc.queryForObject("SELECT UnitPrice FROM"
                + " dbo.SupplierOffer WHERE OfferID=?", String.class, id))
                .startsWith("900");
        assertThat(jdbc.queryForObject("SELECT RetailUnitPrice FROM"
                + " dbo.SupplierOffer WHERE OfferID=?", String.class, id))
                .startsWith("1200");
        assertThat(first.toString()).doesNotContain("purchaseUnitPrice",
                "retailUnitPrice");
        ((ObjectNode) offer.get("command")).put("operationId",
                UUID.randomUUID().toString());
        offer.set("command", command());
        offer.put("validUntil", time(-1));
        post(base() + "/supplier-offers", coordinator, offer, 409);
        offer.put("validUntil", time(5));
        offer.put("purchaseUnitPrice", "100000000000000.00");
        post(base() + "/supplier-offers", coordinator, offer, 409);
    }

    /**
     * Offset timestamps and plain monetary input normalize to captured UTC.
     * @throws Exception if API calls fail
     */
    @Test
    public void offsetScheduleAndPlainMoneyCanBeApproved() throws Exception {
        ObjectNode body = prepare();
        ObjectNode stage = (ObjectNode) body.at("/paymentSchedule/0");
        OffsetDateTime due = OffsetDateTime.parse(stage.get("dueAt").asText());

        stage.put("dueAt", due.withOffsetSameInstant(
                ZoneOffset.ofHours(7)).toString());
        stage.put("amountDue", "260");
        long id = post(base() + "/quotes", manager, body, 200)
                .at("/quote/quoteId").asLong();

        action(id, "APPROVE", manager, 200);
        action(id, "ISSUE", manager, 200);
    }

    /**
     * A different current revision cannot accept a previous issued quote.
     * @throws Exception if API calls fail
     */
    @Test
    public void changedCurrentRevisionBlocksAcceptance() throws Exception {
        long id = issued();
        long replacement = jdbc.queryForObject("INSERT dbo.OrderRevision"
                + " (OrderID,RevisionNo,OrderSnapshot,SubmittedAt)"
                + " OUTPUT INSERTED.RevisionID SELECT OrderID,2,OrderSnapshot,"
                + "SYSUTCDATETIME() FROM dbo.OrderRevision WHERE RevisionID=?",
                Long.class, revision);

        jdbc.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=?"
                + " WHERE OrderID=?", replacement, order);
        action(id, "ACCEPT", owner, 409);
    }

    /**
     * Accept protects F03 direct rewrite without authorizing the journey.
     * @throws Exception if API calls fail
     */
    @Test
    public void acceptedQuoteProtectsDirectOrderRevision() throws Exception {
        long id = issued();

        action(id, "ACCEPT", owner, 200);
        JsonNode current = get(base(), owner, 200);
        ObjectNode request = ((ObjectNode) current.get("draft")).deepCopy();

        request.put("recordVersion", current.get("recordVersion").asText());
        request.put("operationId", UUID.randomUUID().toString());
        request.put("changeReason", "Artificial direct rewrite attempt");
        post(base() + "/revisions", owner, request, 409);
    }

    /**
     * Accept and direct revision use one parent lock and one version winner.
     * @throws Exception if thread coordination fails
     */
    @Test
    public void acceptanceRacingRevisionCannotCommitBoth() throws Exception {
        long id = issued();
        ObjectNode accept = actionBody("ACCEPT");
        JsonNode current = get(base(), owner, 200);
        ObjectNode revise = ((ObjectNode) current.get("draft")).deepCopy();

        revise.put("recordVersion", current.get("recordVersion").asText());
        revise.put("operationId", UUID.randomUUID().toString());
        revise.put("changeReason", "Artificial concurrent revision");
        assertThat(race(base() + "/quotes/" + id + "/actions",
                base() + "/revisions", owner, accept, revise))
                .containsExactlyInAnyOrder(200, 409);
    }

    /**
     * Schema enforces plan/accepted-quote revision lineage as a second guard.
     * @throws Exception if API calls fail
     */
    @Test
    public void planCannotReferenceAQuoteOfAnotherRevision() throws Exception {
        long id = issued();

        action(id, "ACCEPT", owner, 200);
        long replacement = jdbc.queryForObject("INSERT dbo.OrderRevision"
                + " (OrderID,RevisionNo,OrderSnapshot,SubmittedAt)"
                + " OUTPUT INSERTED.RevisionID SELECT OrderID,2,OrderSnapshot,"
                + "SYSUTCDATETIME() FROM dbo.OrderRevision WHERE RevisionID=?",
                Long.class, revision);

        assertThatThrownBy(() -> jdbc.update("UPDATE dbo.TransportPlan"
                + " SET RevisionID=?,AcceptedQuoteID=? WHERE OrderID=?",
                replacement, id, order))
                .isInstanceOf(DataAccessException.class);
    }

    private List<Integer> race(String path, String token, ObjectNode first,
            ObjectNode second) throws Exception {
        return race(path, path, token, first, second);
    }

    private List<Integer> race(String firstPath, String secondPath,
            String token, ObjectNode first, ObjectNode second)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return status(firstPath, token, first);
            });
            var b = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return status(secondPath, token, second);
            });

            start.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS),
                    b.get(30, TimeUnit.SECONDS));
        }
    }

    private int status(String path, String token, JsonNode body)
            throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
                .andReturn().getResponse().getStatus();
    }

    /**
     * F03 invalidates every unaccepted quote state through the shared policy.
     * @throws Exception if API calls fail
     */
    @Test
    public void revisionSupersedesAllUnacceptedQuoteStates() throws Exception {
        long draft = post(base() + "/quotes", manager, prepare(), 200)
                .at("/quote/quoteId").asLong();
        long approved = post(base() + "/quotes", manager, prepare(), 200)
                .at("/quote/quoteId").asLong();

        action(approved, "APPROVE", manager, 200);
        long issued = issued();

        // Restore concurrent historical preparation states for this fixture.
        jdbc.update("UPDATE dbo.Quote SET QuoteStatus='DRAFT' WHERE QuoteID=?",
                draft);
        jdbc.update("UPDATE dbo.Quote SET QuoteStatus='APPROVED'"
                + " WHERE QuoteID=?", approved);
        JsonNode current = get(base(), owner, 200);
        ObjectNode request = ((ObjectNode) current.get("draft")).deepCopy();

        request.put("recordVersion", current.get("recordVersion").asText());
        request.put("operationId", UUID.randomUUID().toString());
        request.put("changeReason", "Replace the commercial request");
        post(base() + "/revisions", owner, request, 200);
        for (long id : List.of(draft, approved, issued)) {
            assertThat(quoteStatus(id)).isEqualTo("SUPERSEDED");
        }
        assertThat(post(base() + "/revisions", owner, request, 200)
                .get("revisionId")).isNotNull();
        assertThat(get(base() + "/services", owner, 200)
                .get("totalEstimate").isNull()).isTrue();
    }

    /**
     * A failed outer write rolls revision and quote invalidation back together.
     * @throws Exception if API calls fail
     */
    @Test
    public void supersessionRollsBackWithTheRevisionTransaction()
            throws Exception {
        long id = issued();
        JsonNode current = get(base(), owner, 200);
        ObjectNode request = ((ObjectNode) current.get("draft")).deepCopy();

        request.put("recordVersion", current.get("recordVersion").asText());
        request.put("operationId", UUID.randomUUID().toString());
        request.put("changeReason", "Artificial rollback fixture");
        assertThatThrownBy(() -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    try {
                        post(base() + "/revisions", owner, request, 200);
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                    throw new IllegalStateException("Artificial rollback");
                })).isInstanceOf(IllegalStateException.class);
        assertThat(quoteStatus(id)).isEqualTo("ISSUED");
        assertThat(get(base(), owner, 200).get("currentRevisionId").asLong())
                .isEqualTo(revision);
        action(id, "ACCEPT", owner, 200);
    }

    /**
     * F05 confirmation inherits atomic supersession and safe UUID replay.
     * @throws Exception if API calls fail
     */
    @Test
    public void serviceConfirmationSupersedesIssuedQuote() throws Exception {
        long id = issued();
        ObjectNode request = serviceConfirmation();
        JsonNode first = post(base() + "/services/confirm", owner, request,
                200);

        assertThat(quoteStatus(id)).isEqualTo("SUPERSEDED");
        assertThat(post(base() + "/services/confirm", owner, request, 200))
                .isEqualTo(first);
        action(id, "ACCEPT", owner, 409);
    }

    /**
     * Accepted commitments cannot be superseded through service confirmation.
     * @throws Exception if API calls fail
     */
    @Test
    public void serviceConfirmationCannotSupersedeAcceptedQuote()
            throws Exception {
        long id = issued();

        action(id, "ACCEPT", owner, 200);
        post(base() + "/services/confirm", owner, serviceConfirmation(), 409);
        assertThat(quoteStatus(id)).isEqualTo("ACCEPTED");
    }

    /**
     * F05 shows F06 quantities, retail rates and NOT_REQUIRED evidence exactly.
     * @throws Exception if API calls fail
     */
    @Test
    public void disclosureUsesCommercialRetailSnapshot() throws Exception {
        assertThat(get(base() + "/services", owner, 200)
                .get("totalEstimate").isNull()).isTrue();
        ObjectNode offer = mapper.createObjectNode();

        offer.set("command", command());
        offer.put("revisionId", revision);
        offer.put("serviceCode", "GOVERNMENT_DUTY");
        offer.put("providerName", "Artificial supplier");
        offer.put("offerReference", "ARTIFICIAL-RETAIL-SOURCE");
        offer.put("unitCode", "ASSESSMENT");
        offer.put("quantity", "0.015");
        offer.put("purchaseUnitPrice", "0.50");
        offer.put("retailUnitPrice", "1.00");
        offer.put("validUntil", time(5));
        offer.put("evidenceFileKey", "test-only/source.pdf");
        offer.put("includesStall", false);
        offer.set("includedCharges", mapper.createArrayNode());
        long source = post(base() + "/supplier-offers", coordinator, offer,
                200).get("offerId").asLong();

        assertThat(get(base() + "/services", owner, 200)
                .get("totalEstimate").isNull()).isTrue();
        ObjectNode body = prepare();
        var evidence = mapper.createArrayNode();

        for (JsonNode item : body.get("assessments")) {
            if (!"GOVERNMENT_DUTY".equals(item.get("code").asText())) {
                evidence.add(item);
            }
        }
        evidence.add(mapper.valueToTree(Map.of("code", "GOVERNMENT_DUTY",
                "status", "PRICED", "quantity", "0.015", "offerId", source,
                "sourceReference", "Artificial assessed scope", "validUntil",
                time(4), "reason", "Artificial commercial assessment")));
        body.set("assessments", evidence);
        ((ObjectNode) body.at("/paymentSchedule/0"))
                .put("amountDue", "260.02");
        JsonNode quote = post(base() + "/quotes", manager, body, 200);
        long id = quote.at("/quote/quoteId").asLong();

        assertCommercialDisclosure(quote);
        action(id, "APPROVE", manager, 200);
        action(id, "ISSUE", manager, 200);
        jdbc.update("UPDATE dbo.ServiceTariff SET UnitPrice=3"
                + " WHERE TariffID=?", fixtureTariffs.getFirst());
        assertCommercialDisclosure(quote);
        get(base() + "/services", other, 404);
        action(id, "ACCEPT", owner, 200);
        jdbc.update("UPDATE dbo.Quote SET IssuedAt=DATEADD(day,-2,"
                + "SYSUTCDATETIME()),ValidUntil=DATEADD(day,-1,"
                + "SYSUTCDATETIME()) WHERE QuoteID=?", id);
        assertCommercialDisclosure(quote);
    }

    /**
     * Pending, rejected and expired commercial versions do not fake estimates.
     * @throws Exception if API calls fail
     */
    @Test
    public void disclosureExcludesUnusableCommercialVersions()
            throws Exception {
        ObjectNode request = prepare();

        request.set("assessments", mapper.createArrayNode());
        long id = post(base() + "/quotes", manager, request, 200)
                .at("/quote/quoteId").asLong();
        assertThat(get(base() + "/services", owner, 200)
                .get("totalEstimate").isNull()).isTrue();
        action(id, "REJECT", manager, 200);
        assertThat(get(base() + "/services", owner, 200)
                .get("totalEstimate").isNull()).isTrue();
        long issued = issued();

        jdbc.update("UPDATE dbo.Quote SET IssuedAt=DATEADD(day,-2,"
                + "SYSUTCDATETIME()),ValidUntil=DATEADD(day,-1,"
                + "SYSUTCDATETIME()) WHERE QuoteID=?", issued);
        assertThat(get(base() + "/services", owner, 200)
                .get("totalEstimate").isNull()).isTrue();
    }

    private void assertCommercialDisclosure(JsonNode quote) throws Exception {
        JsonNode view = get(base() + "/services", owner, 200);

        assertThat(view.get("totalEstimate").asText())
                .as("Commercial disclosure: %s", view).isEqualTo("260.02");
        assertThat(view.get("payable").asBoolean()).isFalse();
        assertThat(view.toString()).doesNotContain("purchaseUnitPrice",
                "supplierCost", "internalNotes", "ARTIFICIAL-RETAIL-SOURCE",
                "test-only/source.pdf");
        for (JsonNode line : quote.get("items")) {
            JsonNode row = null;

            for (JsonNode charge : view.get("charges")) {
                if (charge.get("code").equals(line.get("code"))) {
                    row = charge;
                }
            }
            assertThat(row).isNotNull();
            for (String field : List.of("quantity", "unitRate", "amount")) {
                assertThat(row.get(field)).isEqualTo(line.get(field));
            }
        }
    }

    private ObjectNode serviceConfirmation() throws Exception {
        JsonNode view = get(base() + "/services", owner, 200);
        ObjectNode body = mapper.createObjectNode();

        body.put("recordVersion", view.get("recordVersion").asText());
        body.put("horseRecordVersion",
                view.get("horseRecordVersion").asText());
        body.put("operationId", UUID.randomUUID().toString());
        body.put("changeReason", "Customer changed requested services");
        body.set("services", view.get("selected"));
        return body;
    }

    private String quoteStatus(long id) {
        return jdbc.queryForObject("SELECT QuoteStatus FROM dbo.Quote"
                + " WHERE QuoteID=?", String.class, id);
    }

    /**
     * Publication and replacement retain historical amounts and UUID receipts.
     * @throws Exception if API calls fail
     */
    @Test
    public void managerPublishesVersionedRetailPrices() throws Exception {
        ObjectNode body = tariffBody("GROOMING", "1.25");
        JsonNode first = publishTariff(body);

        assertThat(post(tariffs(), manager, body, 200)).isEqualTo(first);
        assertThat(get(base() + "/services/rates", owner, 200).toString())
                .contains("1.25").doesNotContain("Artificial price proof");
        ObjectNode replacement = tariffBody("GROOMING", "2.50");

        replacement.put("recordVersion", first.get("recordVersion").asText());
        JsonNode next = putTariff(first.get("tariffId").asLong(), replacement,
                200);

        fixtureTariffs.add(next.get("tariffId").asLong());
        assertThat(next.get("version").asInt()).isEqualTo(2);
        assertThat(putTariff(first.get("tariffId").asLong(), replacement, 200))
                .isEqualTo(next);
        assertThat(get(tariffs() + "/" + first.get("tariffId").asLong(),
                manager, 200).get("unitPrice").asText()).isEqualTo("1.25");
        assertThat(get(tariffs() + "?page=0&size=1", manager, 200)
                .get("items").size()).isEqualTo(1);
        replacement.put("unitPrice", "2.75");
        putTariff(first.get("tariffId").asLong(), replacement, 409);
    }

    /**
     * Only active Managers may publish, never customers or assigned staff.
     * @throws Exception if API calls fail
     */
    @Test
    public void tariffPublicationEnforcesRolesAndInput() throws Exception {
        ObjectNode body = tariffBody("GROOMING", "1.25");

        for (String actor : List.of(owner, coordinator, specialist)) {
            post(tariffs(), actor, body, 403);
            get(tariffs(), actor, 403);
        }
        body.put("unitPrice", "-1.00");
        post(tariffs(), manager, body, 400);
        body.put("unitPrice", "1.234");
        post(tariffs(), manager, body, 400);
        body.put("unitPrice", "100000000000000.00");
        post(tariffs(), manager, body, 409);
        body.put("unitPrice", "1.25");
        body.put("sourceReference", " ");
        post(tariffs(), manager, body, 400);
        body.put("sourceReference", "Artificial price proof");
        body.put("product", "BASIC_CARE");
        post(tariffs(), manager, body, 409);
        body.put("product", "DOCUMENT_REVIEW");
        post(tariffs(), manager, body, 409);
        body.put("product", "GROOMING");
        body.put("validUntil", time(-1));
        post(tariffs(), manager, body, 409);
    }

    /**
     * Stale replacements and duplicate active publications cannot overlap.
     * @throws Exception if API calls fail
     */
    @Test
    public void tariffVersionsRejectStaleAndDuplicateRates() throws Exception {
        JsonNode first = publishTariff(tariffBody("GROOMING", "1.25"));
        ObjectNode next = tariffBody("GROOMING", "2.00");

        post(tariffs(), manager, next, 409);
        next.put("recordVersion", "0000000000000000");
        putTariff(first.get("tariffId").asLong(), next, 409);
        next.put("recordVersion", first.get("recordVersion").asText());
        next.put("product", "BOARDING");
        putTariff(first.get("tariffId").asLong(), next, 409);
        assertThat(get(tariffs() + "/" + first.get("tariffId").asLong(),
                manager, 200).get("recordVersion"))
                .isEqualTo(first.get("recordVersion"));
    }

    /**
     * Preview computes changed preferences without persisting a single write.
     * @throws Exception if API calls fail
     */
    @Test
    public void servicePreviewIsReadOnlyAndUsesPublishedRates()
            throws Exception {
        publishTariff(tariffBody("GROOMING", "1.25"));
        JsonNode before = get(base(), owner, 200);
        ObjectNode preview = previewBody();
        ObjectNode choices = (ObjectNode) preview.get("services");

        choices.put("groomingDays", 3);
        JsonNode result = post(base() + "/services/preview", owner, preview,
                200);
        JsonNode grooming = null;

        for (JsonNode line : result.get("charges")) {
            if ("GROOMING".equals(line.get("code").asText())) {
                grooming = line;
            }
        }
        assertThat(grooming).isNotNull();
        assertThat(grooming.get("quantity").asText()).isEqualTo("3");
        assertThat(grooming.get("unitRate").asText()).startsWith("1.25");
        assertThat(grooming.get("amount").asText()).isEqualTo("3.75");
        assertThat(result.get("payable").asBoolean()).isFalse();
        assertThat(result.get("totalEstimate").isNull()).isTrue();
        assertThat(get(base(), owner, 200)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Quote"
                + " WHERE RevisionID=?", Integer.class, revision)).isZero();
        assertThat(result.get("selected").get("groomingDays").asInt())
                .isEqualTo(3);
        assertThat(get(base() + "/services", owner, 200)
                .at("/selected/groomingDays").isNull()).isTrue();
    }

    /**
     * Owner/version checks and no client prices apply to read-only estimates.
     * @throws Exception if API calls fail
     */
    @Test
    public void previewRejectsForeignStaleAndUntrustedInputs()
            throws Exception {
        ObjectNode body = previewBody();

        post(base() + "/services/preview", other, body, 404);
        get(base() + "/services/rates", other, 404);
        body.put("recordVersion", "0000000000000000");
        post(base() + "/services/preview", owner, body, 409);
        body = previewBody();
        body.put("horseRecordVersion", "0000000000000000");
        post(base() + "/services/preview", owner, body, 409);
        body = previewBody();
        ((ObjectNode) body.get("services")).put("unitPrice", "0.01");
        post(base() + "/services/preview", owner, body, 400);
        body = previewBody();
        ((ObjectNode) body.get("services")).put("airStall", "SHARED_ONE");
        post(base() + "/services/preview", owner, body, 409);
    }

    /**
     * Updating current rates never changes issued quote snapshots or totals.
     * @throws Exception if API calls fail
     */
    @Test
    public void publishedRateChangeDoesNotRewriteIssuedQuote()
            throws Exception {
        long quote = issued();
        JsonNode first = get(tariffs() + "/" + fixtureTariffs.getFirst(),
                manager, 200);
        ObjectNode next = tariffBody("ROAD_TRANSPORT", "3.00");

        next.put("recordVersion", first.get("recordVersion").asText());
        JsonNode changed = putTariff(first.get("tariffId").asLong(), next,
                200);

        fixtureTariffs.add(changed.get("tariffId").asLong());
        assertThat(get(base() + "/quotes/" + quote, owner, 200)
                .at("/quote/total").asText()).isEqualTo("260.00");
        assertThat(get(base() + "/services", owner, 200)
                .get("totalEstimate").asText()).isEqualTo("260.00");
    }

    /**
     * Competing replacement writes have exactly one version winner.
     * @throws Exception if thread coordination fails
     */
    @Test
    public void competingTariffReplacementsHaveOneWinner() throws Exception {
        JsonNode first = publishTariff(tariffBody("GROOMING", "1.25"));
        long id = first.get("tariffId").asLong();
        ObjectNode a = tariffBody("GROOMING", "2.00");
        ObjectNode b = tariffBody("GROOMING", "3.00");

        a.put("recordVersion", first.get("recordVersion").asText());
        b.put("recordVersion", first.get("recordVersion").asText());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var one = executor.submit(() -> {
                start.await();
                return tariffPutStatus(id, a);
            });
            var two = executor.submit(() -> {
                start.await();
                return tariffPutStatus(id, b);
            });

            start.countDown();
            assertThat(List.of(one.get(30, TimeUnit.SECONDS),
                    two.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
            fixtureTariffs.add(jdbc.queryForObject("SELECT MAX(TariffID)"
                    + " FROM dbo.ServiceTariff WHERE ServiceCode='GROOMING'",
                    Long.class));
        }
    }

    /**
     * A catalog write rolled back by its caller leaves no rate or receipt.
     * @throws Exception if API calls fail
     */
    @Test
    public void tariffPublicationRollsBackAtomically() throws Exception {
        ObjectNode body = tariffBody("GROOMING", "1.25");

        assertThatThrownBy(() -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    try {
                        post(tariffs(), manager, body, 200);
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                    throw new IllegalStateException("Artificial rollback");
                })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.ServiceTariff"
                + " WHERE ServiceCode='GROOMING'", Integer.class)).isZero();
        publishTariff(body);
    }

    private int tariffPutStatus(long id, ObjectNode body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(tariffs() + "/" + id)
                .header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andReturn().getResponse()
                .getStatus();
    }

    private ObjectNode previewBody() throws Exception {
        JsonNode view = get(base() + "/services", owner, 200);
        ObjectNode body = mapper.createObjectNode();

        body.set("recordVersion", view.get("recordVersion"));
        body.set("horseRecordVersion", view.get("horseRecordVersion"));
        body.set("services", view.get("selected"));
        return body;
    }

    /** Manager changes units while retaining the same service identity. */
    @Test
    public void publishesShipmentUnitsForAllFlexibleProducts()
            throws Exception {
        var history = get(tariffs() + "?page=0&size=100", manager, 200);

        for (String product : List.of("AIR_STALL", "ENHANCED_CARE",
                "DOCUMENT_COORDINATION")) {
            var body = tariffBody(product, "100.00");
            JsonNode previous = null;

            for (var row : history.path("items")) {
                if (product.equals(row.path("product").asText())) {
                    previous = row;
                }
            }
            body.put("unitCode", "SHIPMENT");
            JsonNode result;

            if (previous == null) {
                result = publishTariff(body);
            } else {
                body.put("recordVersion", previous.path("recordVersion")
                        .asText());
                result = putTariff(previous.path("tariffId").asLong(), body,
                        200);
                fixtureTariffs.add(result.path("tariffId").asLong());
                assertThat(result.path("version").asInt())
                        .isEqualTo(previous.path("version").asInt() + 1);
            }
            assertThat(result.path("unit").asText())
                    .isEqualTo("SHIPMENT");
            assertThat(get(tariffs() + "/" + result.path("tariffId").asLong(),
                    manager, 200).path("unit").asText())
                    .isEqualTo("SHIPMENT");
            var legacyUpdate = tariffBody(product, "101.00");

            legacyUpdate.put("recordVersion", result.path("recordVersion")
                    .asText());
            var successor = putTariff(result.path("tariffId").asLong(),
                    legacyUpdate, 200);

            fixtureTariffs.add(successor.path("tariffId").asLong());
            assertThat(successor.path("unit").asText()).isEqualTo("SHIPMENT");
        }
        var invalid = tariffBody("GROOMING", "100.00");

        invalid.put("unitCode", "SHIPMENT");
        assertThat(post(tariffs(), manager, invalid, 400).path("code").asText())
                .isEqualTo("TARIFF_UNIT_INVALID");
        var rates = get(base() + "/services/rates", owner, 200);

        assertThat(rates.toString()).contains("allowedUnits", "SHIPMENT");
    }

    /** Offer scope uses existing JSON; legacy retries remain idempotent. */
    @Test
    public void supplierOfferCoverageIsPersistedWithoutSchemaChanges()
            throws Exception {
        for (String scope : List.of("ENTIRE_ROUTE", "PARTIAL_ROUTE",
                "UNKNOWN")) {
            ObjectNode body = (ObjectNode) mapper.readTree(
                    com.equitransit.backend.dto.quote.QuoteApiExamples.OFFER);

            body.set("command", command());
            body.put("revisionId", revision);
            body.put("offerReference", "COVERAGE-" + scope);
            body.put("validUntil", time(5));
            body.put("includesStall", true);
            if (scope.equals("UNKNOWN")) {
                body.remove("bundleCoverage");
            } else {
                body.put("bundleCoverage", scope);
            }
            JsonNode receipt = post(base() + "/supplier-offers", coordinator,
                    body, 200);

            assertThat(post(base() + "/supplier-offers", coordinator, body,
                    200)).isEqualTo(receipt);
            assertThat(jdbc.queryForObject("SELECT JSON_VALUE(IncludedCharges,"
                    + "'$.coverage') FROM dbo.SupplierOffer WHERE OfferID=?",
                    String.class, receipt.path("offerId").asLong()))
                    .isEqualTo(scope);
        }
    }

    private String tariffs() {
        return "/api/manager/service-tariffs";
    }

    private ObjectNode tariffBody(String product, String price) {
        ObjectNode body = mapper.createObjectNode();

        body.put("product", product);
        body.put("unitPrice", price);
        body.put("sourceReference", "Artificial price proof");
        body.put("operationId", UUID.randomUUID().toString());
        return body;
    }

    private JsonNode publishTariff(ObjectNode body) throws Exception {
        JsonNode value = post(tariffs(), manager, body, 200);

        fixtureTariffs.add(value.get("tariffId").asLong());
        return value;
    }

    private JsonNode putTariff(long id, ObjectNode body, int expected)
            throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.put(tariffs()
                + "/" + id).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andReturn().getResponse();

        assertThat(response.getStatus()).as(response.getContentAsString())
                .isEqualTo(expected);
        return mapper.readTree(response.getContentAsString());
    }

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
}
