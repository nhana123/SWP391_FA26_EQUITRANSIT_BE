/*
 * TransportOrderIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify immutable submissions through HTTP and real SQL Server.
 */
package com.equitransit.backend;


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
import org.springframework.transaction.support.
        TransactionSynchronizationManager;
import com.equitransit.backend.entity.Customer;
import com.equitransit.backend.entity.UserAccount;
import com.equitransit.backend.repository.AccountRoleRepository;
import com.equitransit.backend.repository.CustomerRepository;
import com.equitransit.backend.repository.OrderRepository;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.security.TokenTools;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;


/** Rollback fixtures; committed concurrency fixtures use ID-scoped cleanup. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_ORDER_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_ORDER_TEST_ENABLED", matches = "true")
@Transactional
public class TransportOrderIntegrationTest {

    private static final String API = "/api/transport-orders";

    private final List<Long> fixtureUsers = new ArrayList<>();

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
    private OrderRepository orderRepository;

    private String ownerToken;
    private String otherToken;
    private String managerToken;
    private long managerUser;
    private long horse;

    /**
     * Refuses all fixture writes outside the restored full test schema.
     * @throws Exception if metadata or fixture creation fails
     */
    @BeforeEach
    public void prepare()
            throws Exception {
        String database;

        try (var connection = dataSource.getConnection()) {
            database = connection.getCatalog();
        }
        assertThat(database).startsWith("EquiTransit_FigmaMigrationTest_");
        ownerToken = account("CUSTOMER");
        otherToken = account("CUSTOMER");
        managerToken = account("MANAGER");
        managerUser = fixtureUsers.getLast();
        horse = createHorse(ownerToken);
    }

    /**
     * Removes only committed concurrency/failure fixtures from the test
     * copy.
     */
    @AfterEach
    public void cleanup() {
        reset(orderRepository);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            for (Long user : fixtureUsers) {
                jdbc.update("DELETE dbo.AuditEvent WHERE UserID=?"
                        + " AND EntityType='ORDER_ACTIVITY'", user);
                cleanupOrders(user);
                jdbc.update("DELETE dbo.Horse WHERE CustomerID IN"
                        + " (SELECT CustomerID FROM dbo.Customer"
                        + " WHERE UserID=?)",
                        user);
                jdbc.update("DELETE dbo.Customer WHERE UserID=?", user);
                jdbc.update("DELETE dbo.[User] WHERE UserID=?", user);
            }
        }
    }

    /**
     * Saving a draft creates no fake revision or current horse snapshot.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void partialDraftHasNoSubmittedArtifacts()
            throws Exception {
        JsonNode draft = create(Map.of(), ownerToken);
        long id = draft.get("orderId").asLong();

        assertThat(draft.get("orderStatus").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("currentRevisionId").isNull()).isTrue();
        assertThat(count("OrderRevision", id)).isZero();
        assertThat(count("TransportOrderDetail", id)).isZero();
        assertThat(draft.get("recordVersion").asText()).matches("[0-9a-f]{16}");
    }

    /**
     * Draft replacements compare the observed version and remain unsubmitted.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void draftUpdateRejectsAStaleVersion()
            throws Exception {
        JsonNode draft = create(Map.of(), ownerToken);
        long id = draft.get("orderId").asLong();
        ObjectNode replacement = revisionBody(draft);

        replacement.remove(List.of("operationId", "changeReason"));
        replacement.put("horseId", horse);
        mvc.perform(put(API + "/{id}/draft", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(replacement.toString())).andExpect(status().isOk());
        mvc.perform(put(API + "/{id}/draft", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(replacement.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ORDER_VERSION_CONFLICT"));
        assertThat(count("OrderRevision", id)).isZero();
    }

    /**
     * Own lists and all nested operations reject foreign request IDs.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void ownershipAppliesToListsAndHistory()
            throws Exception {
        JsonNode draft = create(complete(), ownerToken);
        long id = draft.get("orderId").asLong();

        mvc.perform(get(API).header("Authorization", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(0));
        for (String suffix : List.of("", "/revisions", "/revisions/1")) {
            mvc.perform(get(API + "/" + id + suffix)
                    .header("Authorization", otherToken))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post(API + "/{id}/submit", id)
                .header("Authorization", otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody(draft, UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    /**
     * Client identity, status, prices and non-owned horses cannot enter a
     * draft.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void spoofedOrForeignInputsAreRejected()
            throws Exception {
        long foreignHorse = createHorse(otherToken);

        for (String body : List.of("{\"customerId\":1}",
                "{\"orderStatus\":\"APPROVED\"}",
                "{\"horseIds\":[1,2]}",
                "{\"currency\":\"EUR\"}",
                "{\"services\":{\"unitPrice\":0}}",
                "{\"services\":{\"quarantineDays\":0}}")) {
            mvc.perform(post(API).header("Authorization", ownerToken)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(API).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        Map.of("horseId", foreignHorse))))
                .andExpect(status().isNotFound());
    }

    /**
     * Only an authenticated active customer may use request endpoints.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void authenticationAndRoleAreRequired()
            throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(get(API).header("Authorization", managerToken))
                .andExpect(status().isForbidden());
        mvc.perform(post(API).header("Authorization", managerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    /**
     * A submitted revision captures one horse and unpriced requested inputs.
     * @throws Exception if an HTTP or SQL assertion fails
     */
    @Test
    public void submissionCapturesOneHorseAndServices()
            throws Exception {
        Map<String, Object> fields = complete();
        JsonNode draft;
        JsonNode revision;
        long id;

        fields.put("services", Map.of("quarantinePackage", "STANDARD",
                "groomingDays", 3, "airStall", "WIDE"));
        draft = create(fields, ownerToken);
        id = draft.get("orderId").asLong();
        revision = submit(draft, UUID.randomUUID());
        assertThat(revision.get("revisionNo").asInt()).isEqualTo(1);
        assertThat(revision.at("/snapshot/horse/horseId").asLong())
                .isEqualTo(horse);
        assertThat(revision.at("/snapshot/request/originCountryCode").asText())
                .isEqualTo("US");
        assertThat(revision.at("/snapshot/services").size()).isEqualTo(3);
        assertThat(count("TransportOrderDetail", id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.OrderServiceChoice WHERE RevisionID=?",
                Integer.class, revision.get("revisionId").asLong()))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.QuarantineChoice", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.QuarantineChoice WHERE RequiredDays IS NOT NULL",
                Integer.class)).isZero();
    }

    /**
     * An identical operation UUID cannot duplicate a submitted revision.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void successfulSubmitIsIdempotent()
            throws Exception {
        JsonNode draft = create(complete(), ownerToken);
        UUID operation = UUID.randomUUID();
        JsonNode first = submit(draft, operation);
        JsonNode retry = submit(draft, operation);

        assertThat(retry).isEqualTo(first);
        assertThat(count("OrderRevision", draft.get("orderId").asLong()))
                .isEqualTo(1);
    }

    /**
     * Reusing the UUID with a different command is a conflict, not a replay.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void changedRetryInputDoesNotCreateAnotherVersion()
            throws Exception {
        JsonNode draft = create(complete(), ownerToken);
        UUID operation = UUID.randomUUID();
        long id = draft.get("orderId").asLong();
        JsonNode current;
        ObjectNode command;

        submit(draft, operation);
        current = current(id);
        command = revisionBody(current);
        command.put("operationId", operation.toString());
        mvc.perform(post(API + "/{id}/revisions", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ORDER_RETRY_CONFLICT"));
        assertThat(count("OrderRevision", id)).isEqualTo(1);
    }

    /**
     * Incomplete input and unassessed one-slot intent leave the draft intact.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void failedSubmissionLeavesNoSnapshots()
            throws Exception {
        JsonNode draft = create(Map.of(), ownerToken);
        long id = draft.get("orderId").asLong();
        Map<String, Object> fields = complete();
        JsonNode oneSlot;

        mvc.perform(post(API + "/{id}/submit", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody(draft, UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        assertThat(count("OrderRevision", id)).isZero();
        fields.put("services", Map.of("airStall", "SHARED_ONE"));
        oneSlot = create(fields, ownerToken);
        mvc.perform(post(API + "/{id}/submit", oneSlot.get("orderId").asLong())
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody(oneSlot, UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code")
                        .value("AIR_STALL_ASSESSMENT_REQUIRED"));
        assertThat(count("OrderRevision", oneSlot.get("orderId").asLong()))
                .isZero();
    }

    /**
     * Database failure after revision insertion rolls back every artifact.
     * @throws Exception if an HTTP or SQL assertion fails
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void partialPersistenceFailureRollsBackSubmission()
            throws Exception {
        JsonNode draft = create(complete(), ownerToken);
        long id = draft.get("orderId").asLong();

        doThrow(new DataIntegrityViolationException("Synthetic test failure"))
                .when(orderRepository).saveIntents(anyLong(), anyList());
        mvc.perform(post(API + "/{id}/submit", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody(draft, UUID.randomUUID())))
                .andExpect(status().isConflict());
        assertThat(count("OrderRevision", id)).isZero();
        assertThat(count("TransportOrderDetail", id)).isZero();
        assertThat(current(id).get("currentRevisionId").isNull()).isTrue();
    }

    /**
     * Revision history remains unchanged after horse/master/request updates.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void revisedRequestDoesNotOverwriteHistoricalSnapshot()
            throws Exception {
        JsonNode draft = create(complete(), ownerToken);
        JsonNode first = submit(draft, UUID.randomUUID());
        long id = draft.get("orderId").asLong();
        JsonNode current = current(id);
        ObjectNode command = revisionBody(current);
        JsonNode second;

        command.put("receiverName", "New receiver");
        updateHorseMaster();
        second = revision(id, command);
        assertThat(second.get("revisionNo").asInt()).isEqualTo(2);
        assertThat(second.at("/snapshot/horse/weightKg").asInt())
                .isEqualTo(650);
        assertThat(count("TransportOrderDetail", id)).isEqualTo(1);
        mvc.perform(get(API + "/{id}/revisions/{revision}", id,
                first.get("revisionId").asLong())
                .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("snapshot.request.receiverName")
                        .doesNotExist())
                .andExpect(jsonPath("snapshot.horse.weightKg").doesNotExist());
        assertThat(current(id).get("currentRevisionId").asLong())
                .isEqualTo(second.get("revisionId").asLong());
    }

    /**
     * Acceptance requires a proposal; intake cannot overwrite the contract.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void acceptedOrderOnlyAllowsAnUnappliedChangeRequest()
            throws Exception {
        JsonNode draft = create(complete(), ownerToken);
        JsonNode first = submit(draft, UUID.randomUUID());
        long id = draft.get("orderId").asLong();
        long revision = first.get("revisionId").asLong();
        ObjectNode command;
        MvcResult proposal;
        JsonNode receipt;

        quote(revision, true);
        command = revisionBody(current(id));
        command.put("receiverName", "Proposed receiver");
        mvc.perform(post(API + "/{id}/revisions", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code")
                        .value("ORDER_CHANGE_REQUEST_REQUIRED"));
        proposal = mvc.perform(post(API + "/{id}/change-requests", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("status").value("PENDING")).andReturn();
        receipt = mapper.readTree(proposal.getResponse().getContentAsString());
        mvc.perform(post(API + "/{id}/change-requests", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("changeRequestId")
                        .value(receipt.get("changeRequestId").asLong()));
        assertThat(current(id).at("/draft/receiverName").isNull()).isTrue();
        assertThat(count("OrderRevision", id)).isEqualTo(1);
        assertThat(count("OrderChangeRequest", id)).isEqualTo(1);
        command.put("operationId", UUID.randomUUID().toString());
        mvc.perform(post(API + "/{id}/change-requests", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ORDER_CHANGE_PENDING"));
    }

    /**
     * Preparation loses stale readiness without moving the current plan
     * pointer.
     * @throws Exception if an HTTP or SQL assertion fails
     */
    @Test
    public void newRevisionInvalidatesUnacceptedPreparation()
            throws Exception {
        JsonNode draft = create(complete(), ownerToken);
        JsonNode first = submit(draft, UUID.randomUUID());
        long id = draft.get("orderId").asLong();
        long revision = first.get("revisionId").asLong();
        long quote = quote(revision, false);
        long plan = plan(id, revision, false);

        revision(id, revisionBody(current(id)));
        assertThat(jdbc.queryForObject("SELECT QuoteStatus FROM dbo.Quote"
                + " WHERE QuoteID=?", String.class, quote))
                .isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject("SELECT DocGateStatus"
                + " FROM dbo.TransportPlan"
                + " WHERE PlanID=?", String.class, plan)).isEqualTo("PENDING");
        assertThat(current(id).get("currentPlanId").asLong()).isEqualTo(plan);
        assertThat(jdbc.queryForObject("SELECT RevisionID"
                + " FROM dbo.TransportPlan"
                + " WHERE PlanID=?", Long.class, plan)).isEqualTo(revision);
    }

    /**
     * Operational authorization independently blocks a direct route rewrite.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void authorizedPlanProtectsTheOrderWithoutPaymentAssumptions()
            throws Exception {
        JsonNode draft = create(complete(), ownerToken);
        JsonNode first = submit(draft, UUID.randomUUID());
        long id = draft.get("orderId").asLong();

        plan(id, first.get("revisionId").asLong(), true);
        mvc.perform(post(API + "/{id}/revisions", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionBody(current(id)).toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code")
                        .value("ORDER_CHANGE_REQUEST_REQUIRED"));
        assertThat(count("OrderRevision", id)).isEqualTo(1);
    }

    /**
     * Two concurrent identical commands must receive the same single revision.
     * @throws Exception if synchronization or HTTP assertions fail
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void concurrentSubmitRetriesCreateExactlyOneRevision()
            throws Exception {
        JsonNode draft = create(complete(), ownerToken);
        long id = draft.get("orderId").asLong();
        String body = submitBody(draft, UUID.randomUUID());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> compete(id, body, ready, start));
            var second = workers.submit(() -> compete(id, body, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS))
                    .isEqualTo(second.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
        assertThat(count("OrderRevision", id)).isEqualTo(1);
        assertThat(count("TransportOrderDetail", id)).isEqualTo(1);
    }

    /**
     * Distinct simultaneous commands cannot both submit the same old version.
     * @throws Exception if synchronization or HTTP assertions fail
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void concurrentDistinctCommandsRespectVersion()
            throws Exception {
        JsonNode draft = create(complete(), ownerToken);
        long id = draft.get("orderId").asLong();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        String firstBody = submitBody(draft, UUID.randomUUID());
        String secondBody = submitBody(draft, UUID.randomUUID());

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> attempt(
                    id, firstBody, ready, start));
            var second = workers.submit(() -> attempt(
                    id, secondBody, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS).getResponse()
                    .getStatus(), second.get(30, TimeUnit.SECONDS).getResponse()
                    .getStatus())).containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
        }
        assertThat(count("OrderRevision", id)).isEqualTo(1);
    }

    private String compete(long id, String body, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        MvcResult result = attempt(id, body, ready, start);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getContentAsString();
    }

    private MvcResult attempt(long id, String body, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Test start timed out.");
        }
        return mvc.perform(post(API + "/{id}/submit", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private void updateHorseMaster() throws Exception {
        MvcResult result = mvc.perform(get("/api/horses/{id}", horse)
                .header("Authorization", ownerToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode profile = mapper.readTree(
                result.getResponse().getContentAsString());

        mvc.perform(put("/api/horses/{id}", horse)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Updated",
                        "weightKg", 650, "recordVersion",
                        profile.get("recordVersion").asText()))))
                .andExpect(status().isOk());
    }

    private JsonNode revision(long id, ObjectNode command) throws Exception {
        MvcResult result = mvc.perform(post(API + "/{id}/revisions", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString()))
                .andExpect(status().isOk()).andReturn();

        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private ObjectNode revisionBody(JsonNode current) {
        ObjectNode body = current.get("draft").deepCopy();

        body.put("recordVersion", current.get("recordVersion").asText());
        body.put("operationId", UUID.randomUUID().toString());
        body.put("changeReason", "Updated customer request");
        return body;
    }

    private JsonNode current(long id) throws Exception {
        MvcResult result = mvc.perform(get(API + "/{id}", id)
                .header("Authorization", ownerToken))
                .andExpect(status().isOk()).andReturn();

        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode submit(JsonNode draft, UUID operation) throws Exception {
        MvcResult result = mvc.perform(post(API + "/{id}/submit",
                draft.get("orderId").asLong())
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody(draft, operation)))
                .andExpect(status().isOk()).andReturn();

        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String submitBody(JsonNode draft, UUID operation) throws Exception {
        return mapper.writeValueAsString(Map.of("recordVersion",
                draft.get("recordVersion").asText(), "operationId", operation));
    }

    private JsonNode create(Map<String, Object> body, String token)
            throws Exception {
        MvcResult result = mvc.perform(post(API).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();

        return mapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * Core routing is required only at submit; unknown receiver/event remain
     * valid nullable intent.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void submissionRequiresCoreRoutingButNotOptionalDetails()
            throws Exception {
        JsonNode draft;
        long id;
        JsonNode revision;

        for (String key : List.of("horseId", "originCountryCode",
                "destinationCountryCode", "preferredDepartureAt",
                "deliveryDeadline", "importType")) {
            Map<String, Object> fields = complete();
            JsonNode partial;
            long partialId;

            fields.remove(key);
            partial = create(fields, ownerToken);
            partialId = partial.get("orderId").asLong();
            mvc.perform(post(API + "/{id}/submit", partialId)
                    .header("Authorization", ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(submitBody(partial, UUID.randomUUID())))
                    .andExpect(status().isBadRequest());
            assertThat(count("OrderRevision", partialId)).isZero();
        }
        draft = create(complete(), ownerToken);
        id = draft.get("orderId").asLong();
        revision = mapper.readTree(mvc.perform(post(
                API + "/{id}/submit", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody(draft, UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());

        assertThat(revision.at("/snapshot/request/receiverName").isNull())
                .isTrue();
        assertThat(revision.at("/snapshot/request/eventStartAt").isNull())
                .isTrue();
    }

    private Map<String, Object> complete() {
        Map<String, Object> fields = new LinkedHashMap<>();

        fields.put("horseId", horse);
        fields.put("originCountryCode", "US");
        fields.put("destinationCountryCode", "GB");
        fields.put("originAddress", "Pickup address");
        fields.put("destinationAddress", "Delivery address");
        fields.put("originTimezone", "America/New_York");
        fields.put("destinationTimezone", "Europe/London");
        fields.put("importType", "TEMPORARY");
        fields.put("preferredDepartureAt", OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(7).withNano(0).toString());
        fields.put("deliveryDeadline", OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(14).withNano(0).toString());
        return fields;
    }

    private long count(String table, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo." + table
                + " WHERE OrderID=?", Long.class, id);
    }

    private long quote(long revision, boolean accepted) {
        return jdbc.queryForObject("INSERT dbo.Quote"
                + " (RevisionID,QuoteNumber,Currency,QuoteStatus,"
                + "IssuedAt,AcceptedAt)"
                + " OUTPUT INSERTED.QuoteID VALUES (?,?,'USD',?,"
                + "DATEADD(second,-1,SYSUTCDATETIME()),?)", Long.class,
                revision, TokenTools.randomToken().substring(0, 20),
                accepted ? "ACCEPTED" : "ISSUED", accepted
                        ? LocalDateTime.now(ZoneOffset.UTC).withNano(0) : null);
    }

    private long plan(long order, long revision, boolean authorized) {
        Long staff = jdbc.queryForObject("INSERT dbo.StaffProfile"
                + " (UserID,FullName) OUTPUT INSERTED.StaffID VALUES (?,?)",
                Long.class, managerUser, "Rollback coordinator fixture");
        Long plan = jdbc.queryForObject("SET NOCOUNT ON;"
                + " DECLARE @ids TABLE (ID BIGINT); INSERT dbo.TransportPlan"
                + " (OrderID,RevisionID,CreatedBy,VersionNo,"
                + "FinancialGateStatus,"
                + "OperationalGateStatus,DocGateStatus,PlanStatus,"
                + "GatesCheckedAt,DepartureAuthorizedAt)"
                + " OUTPUT INSERTED.PlanID INTO @ids"
                + " VALUES (?,?,?,1,'READY','READY','READY',?,"
                + "SYSUTCDATETIME(),?); SELECT ID FROM @ids;",
                Long.class, order, revision, staff,
                authorized ? "AUTHORIZED" : "DRAFT", authorized
                        ? LocalDateTime.now(ZoneOffset.UTC).withNano(0) : null);

        jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=?"
                + " WHERE OrderID=?",
                plan, order);
        return plan;
    }

    private long createHorse(String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/horses")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rollback order horse\"}"))
                .andExpect(status().isCreated()).andReturn();

        return mapper.readTree(result.getResponse().getContentAsString())
                .get("horseId").asLong();
    }

    private String account(String role) {
        UserAccount user = new UserAccount();
        Customer customer = new Customer();

        user.setRole(roles.findByRoleCode(role).orElseThrow());
        user.setEmail(TokenTools.randomToken() + "@example.invalid");
        user.setPasswordHash(passwords.encode("ExamplePassword123!"));
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        users.saveAndFlush(user);
        fixtureUsers.add(user.getUserId());
        if ("CUSTOMER".equals(role)) {
            customer.setUser(user);
            customer.setFullName("Rollback order fixture");
            customers.saveAndFlush(customer);
        }
        return "Bearer " + tokens.issue(user).getAccessToken();
    }

    private void cleanupOrders(long user) {
        String scope = " IN (SELECT OrderID FROM dbo.TransportOrder"
                + " WHERE CustomerID IN (SELECT CustomerID FROM dbo.Customer"
                + " WHERE UserID=?))";

        jdbc.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=NULL,"
                + "CurrentPlanID=NULL WHERE OrderID" + scope, user);
        jdbc.update("DELETE dbo.OrderServiceChoice WHERE RevisionID IN"
                + " (SELECT RevisionID FROM dbo.OrderRevision WHERE OrderID"
                + scope + ")", user);
        jdbc.update("DELETE dbo.OrderChangeRequest WHERE OrderID"
                + scope, user);
        jdbc.update("DELETE dbo.TransportOrderDetail WHERE OrderID"
                + scope, user);
        jdbc.update("DELETE dbo.OrderRevision WHERE OrderID" + scope, user);
        jdbc.update("DELETE dbo.TransportOrder WHERE OrderID" + scope, user);
    }
}
