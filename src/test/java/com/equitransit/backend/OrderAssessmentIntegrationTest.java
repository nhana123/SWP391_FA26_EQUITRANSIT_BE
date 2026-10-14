/*
 * OrderAssessmentIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify assessment authorization and transactions on SQL Server.
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.dto.request.AssessmentRequest;
import com.equitransit.backend.entity.Customer;
import com.equitransit.backend.entity.UserAccount;
import com.equitransit.backend.repository.AccountRoleRepository;
import com.equitransit.backend.repository.AssessmentAuditRepository;
import com.equitransit.backend.repository.CustomerRepository;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.security.LiveJwtConverter;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.service.OrderAssessmentService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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


/** Full-schema test copy only; rollback or explicit fixture ID cleanup. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_ASSESSMENT_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_ASSESSMENT_TEST_ENABLED",
        matches = "true")
@Transactional
public class OrderAssessmentIntegrationTest {

    private static final String MANAGER = "/api/manager/transport-orders";
    private static final String STAFF = "/api/staff/transport-orders";
    private static final String CUSTOMER = "/api/transport-orders";

    private final List<Long> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureOrders = new ArrayList<>();

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
    @Autowired
    private JwtDecoder decoder;
    @Autowired
    private LiveJwtConverter converter;
    @Autowired
    private OrderAssessmentService assessments;
    @MockitoSpyBean
    private AssessmentAuditRepository audit;

    private Account owner;
    private Account other;
    private Account manager;
    private Account specialist;
    private Account replacement;
    private Account coordinator;
    private long horse;
    private long order;

    /**
     * Guards the database before any fixture write.
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
        replacement = account("SPECIALIST");
        coordinator = account("COORDINATOR");
        horse = read(mvc.perform(post("/api/horses")
                .header("Authorization", owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Assessment horse\"}"))
                .andExpect(status().isCreated()).andReturn())
                .get("horseId").asLong();
        order = submittedOrder();
    }

    /** Deletes only this test's explicitly recorded committed fixture IDs. */
    @AfterEach
    public void cleanup() {
        reset(audit);
        SecurityContextHolder.clearContext();
        for (long id : fixtureOrders) {
            jdbc.update("DELETE dbo.AuditEvent WHERE EntityType="
                    + "'ORDER_ASSESSMENT' AND EntityID=?", id);
            jdbc.update("DELETE dbo.OrderAssignment WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.Quote WHERE RevisionID IN"
                    + " (SELECT RevisionID FROM dbo.OrderRevision"
                    + " WHERE OrderID=?)", id);
            jdbc.update("DELETE dbo.OrderDecision WHERE RevisionID IN"
                    + " (SELECT RevisionID FROM dbo.OrderRevision"
                    + " WHERE OrderID=?)", id);
            jdbc.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=NULL,"
                    + "CurrentPlanID=NULL WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.OrderServiceChoice WHERE RevisionID IN"
                    + " (SELECT RevisionID FROM dbo.OrderRevision"
                    + " WHERE OrderID=?)", id);
            jdbc.update("DELETE dbo.OrderChangeRequest WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.TransportOrderDetail WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.OrderRevision WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.TransportOrder WHERE OrderID=?", id);
        }
        for (long user : fixtureUsers) {
            jdbc.update("DELETE dbo.Horse WHERE CustomerID IN"
                    + " (SELECT CustomerID FROM dbo.Customer WHERE UserID=?)",
                    user);
            jdbc.update("DELETE dbo.Customer WHERE UserID=?", user);
            jdbc.update("DELETE dbo.StaffProfile WHERE UserID=?", user);
            jdbc.update("DELETE dbo.[User] WHERE UserID=?", user);
        }
        fixtureOrders.clear();
        fixtureUsers.clear();
    }

    /** Approval records StaffID and creates the two active case roles. */
    @Test
    public void approvalIsAtomicAndDoesNotApproveDepartureOrMoney()
            throws Exception {
        ObjectNode command = approval(order);
        JsonNode receipt = decide(order, command, manager.token(), 200);

        assertThat(receipt.get("orderStatus").asText()).isEqualTo("APPROVED");
        assertThat(receipt.at("/decision/managerId").asLong())
                .isEqualTo(manager.staffId());
        assertThat(receipt.get("assignments")).hasSize(2);
        assertThat(count("OrderAssignment", order)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Quote",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TransportPlan",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.PaymentRequest", Long.class))
                .isZero();
        mvc.perform(get(STAFF + "/{id}", order)
                .header("Authorization", specialist.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("assignments.length()").value(1));
    }

    /** Both HTTP and direct service proxies enforce Manager-only commands. */
    @Test
    public void enforcesRolesAndRejectsIdentitySpoofing() throws Exception {
        ObjectNode command = approval(order);

        for (Account actor : List.of(owner, specialist, coordinator)) {
            decide(order, command, actor.token(), 403);
        }
        mvc.perform(get(MANAGER)).andExpect(status().isUnauthorized());
        SecurityContextHolder.getContext().setAuthentication(converter.convert(
                decoder.decode(owner.token().substring(7))));
        assertThatThrownBy(() -> assessments.decide(order,
                mapper.treeToValue(command, AssessmentRequest.class)))
                .isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.clearContext();
        command.put("managerId", owner.userId());
        decide(order, command, manager.token(), 400);
        assertThat(decisionCount(order)).isZero();
    }

/**
 * Profiles and active database roles are checked instead of trusted IDs.
 */
    @Test
    public void refusesWrongRoleInactiveAndUnprofiledAccounts()
            throws Exception {
        ObjectNode command = approval(order);

        command.put("specialistId", coordinator.staffId());
        decide(order, command, manager.token(), 409);
        command.put("specialistId", specialist.staffId());
        jdbc.update("UPDATE dbo.[User] SET Status='INACTIVE' WHERE UserID=?",
                specialist.userId());
        decide(order, command, manager.token(), 409);
        jdbc.update("UPDATE dbo.[User] SET Status='ACTIVE' WHERE UserID=?",
                specialist.userId());
        jdbc.update("DELETE dbo.StaffProfile WHERE UserID=?", manager.userId());
        decide(order, command, manager.token(), 401);
        assertThat(decisionCount(order)).isZero();
    }

    /** Each negative outcome needs a public reason and forbids assignments. */
    @Test
    public void clarificationIsPublicAndRevisionHistoryRemainsImmutable()
            throws Exception {
        ObjectNode command = approval(order);
        long originalRevision = command.get("revisionId").asLong();
        ObjectNode update;
        JsonNode current;
        JsonNode reviewed;

        command.put("decision", "CHANGES_REQUESTED");
        command.remove(List.of("specialistId", "coordinatorId"));
        decide(order, command, manager.token(), 400);
        command.put("reason", "Please confirm receiver contact");
        decide(order, command, manager.token(), 200);
        mvc.perform(get(CUSTOMER + "/{id}/assessment", order)
                .header("Authorization", owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("reason")
                        .value("Please confirm receiver contact"))
                .andExpect(jsonPath("managerId").doesNotExist());
        current = customerCurrent(order);
        update = current.get("draft").deepCopy();
        update.put("recordVersion", current.get("recordVersion").asText());
        update.put("operationId", UUID.randomUUID().toString());
        update.put("changeReason", "Added receiver contact");
        update.put("receiverName", "New receiver");
        mvc.perform(post(CUSTOMER + "/{id}/revisions", order)
                .header("Authorization", owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(update.toString())).andExpect(status().isOk());
        reviewed = managerCurrent(order);
        assertThat(reviewed.get("currentRevisionId").asLong())
                .isNotEqualTo(originalRevision);
        assertThat(reviewed.get("decision").isNull()).isTrue();
        assertThat(decisionCount(order)).isEqualTo(1);
        assertThat(count("OrderAssignment", order)).isZero();
        mvc.perform(get(CUSTOMER + "/{id}/assessment", order)
                .header("Authorization", other.token()))
                .andExpect(status().isNotFound());
    }

    /** A decision cannot apply to an older revision or stale parent version. */
    @Test
    public void refusesStaleVersionRevisionAndRepeatDecision()
            throws Exception {
        ObjectNode command = approval(order);
        ObjectNode invalid = command.deepCopy();

        invalid.put("revisionId", command.get("revisionId").asLong() + 10000);
        decide(order, invalid, manager.token(), 409);
        invalid = command.deepCopy();
        invalid.put("recordVersion", "0000000000000000");
        decide(order, invalid, manager.token(), 409);
        decide(order, command, manager.token(), 200);
        invalid = approval(order);
        decide(order, invalid, manager.token(), 409);
        assertThat(decisionCount(order)).isEqualTo(1);
    }

/**
 * A stable retry returns the recorded receipt without duplicate history.
 */
    @Test
    public void retriesSafelyAndRejectsDifferentInput() throws Exception {
        ObjectNode command = approval(order);
        JsonNode first = decide(order, command, manager.token(), 200);
        JsonNode retry = decide(order, command, manager.token(), 200);

        assertThat(retry).isEqualTo(first);
        command.put("reason", "Different input");
        decide(order, command, manager.token(), 409);
        assertThat(decisionCount(order)).isEqualTo(1);
        assertThat(count("OrderAssignment", order)).isEqualTo(2);
        assertThat(auditCount(order)).isEqualTo(1);
    }

    /** Reassignment revokes live access and retains original metadata. */
    @Test
    public void reassignmentRevokesOldCaseAccessAndKeepsHistory()
            throws Exception {
        JsonNode approved =
                decide(order, approval(order), manager.token(), 200);
        ObjectNode command = assignment(approved);
        JsonNode first;

        command.put("staffId", replacement.staffId());
        first = reassign(command, 200);
        assertThat(reassign(command, 200)).isEqualTo(first);
        assertThat(count("OrderAssignment", order)).isEqualTo(3);
        assertThat(activeAssignments(order)).isEqualTo(2);
        mvc.perform(get(STAFF + "/{id}", order)
                .header("Authorization", specialist.token()))
                .andExpect(status().isNotFound());
        mvc.perform(get(STAFF + "/{id}", order)
                .header("Authorization", replacement.token()))
                .andExpect(status().isOk());
        mvc.perform(get(STAFF).header("Authorization", specialist.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(0));
        mvc.perform(get(MANAGER + "/{id}/assignments", order)
                .header("Authorization", manager.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].endedAt").exists());
    }

    /** Staff cannot see other cases, internal commands or other-role notes. */
    @Test
    public void staffCaseAccessUsesLiveRoleAndAssignment() throws Exception {
        mvc.perform(get(STAFF + "/{id}", order)
                .header("Authorization", specialist.token()))
                .andExpect(status().isNotFound());
        decide(order, approval(order), manager.token(), 200);
        mvc.perform(get(STAFF + "/{id}", order)
                .header("Authorization", replacement.token()))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE dbo.[User] SET RoleID=(SELECT RoleID"
                + " FROM dbo.[Role] WHERE RoleCode='COORDINATOR')"
                + " WHERE UserID=?", specialist.userId());
        mvc.perform(get(STAFF + "/{id}", order)
                .header("Authorization", specialist.token()))
                .andExpect(status().isNotFound());
    }

/**
 * Current approval is invalidated when the customer submits a new version.
 */
    @Test
    public void customerCanReviseApprovedButUncommittedRequest()
            throws Exception {
        JsonNode approval =
                decide(order, approval(order), manager.token(), 200);
        JsonNode current = customerCurrent(order);
        ObjectNode command = current.get("draft").deepCopy();

        command.put("recordVersion", approval.get("recordVersion").asText());
        command.put("operationId", UUID.randomUUID().toString());
        command.put("changeReason", "Updated route before commitment");
        command.put("receiverName", "Updated receiver");
        mvc.perform(post(CUSTOMER + "/{id}/revisions", order)
                .header("Authorization", owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString())).andExpect(status().isOk());
        mvc.perform(get(STAFF + "/{id}", order)
                .header("Authorization", specialist.token()))
                .andExpect(status().isNotFound());
        decide(order, approval(order), manager.token(), 200);
        assertThat(decisionCount(order)).isEqualTo(2);
        assertThat(activeAssignments(order)).isEqualTo(2);
        assertThat(count("OrderAssignment", order)).isEqualTo(4);
    }

    /** Several approved cases may share staff; workload is not capacity. */
    @Test
    public void directoryShowsMultipleCasesAndDoesNotExposeCredentials()
            throws Exception {
        long second;

        decide(order, approval(order), manager.token(), 200);
        second = submittedOrder();

        decide(second, approval(second), manager.token(), 200);
        mvc.perform(get(MANAGER + "/staff").param("role", "SPECIALIST")
                .header("Authorization", manager.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("items[0].activeCases").value(2))
                .andExpect(jsonPath("items[0].availabilityStatus")
                        .value("NOT_CONFIGURED"))
                .andExpect(jsonPath("items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("items[0].userId").doesNotExist());
        mvc.perform(get(MANAGER).param("size", "101")
                .header("Authorization", manager.token()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(MANAGER + "/staff").param("role", "DRIVER")
                .header("Authorization", manager.token()))
                .andExpect(status().isBadRequest());
    }

/**
 * Internal approval notes do not appear in customer assessment responses.
 */
    @Test
    public void customerViewRedactsInternalApprovalAndAssignmentNotes()
            throws Exception {
        ObjectNode command = approval(order);

        command.put("reason", "Internal assessment note");
        command.put("assignmentNote", "Staff-only preparation instructions");
        decide(order, command, manager.token(), 200);
        mvc.perform(get(CUSTOMER + "/{id}/assessment", order)
                .header("Authorization", owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("reason").isEmpty())
                .andExpect(jsonPath("assignments").doesNotExist());
    }

/**
 * Rejection ends case access; it does not activate any downstream gates.
 */
    @Test
    public void rejectionIsFinalForTheReviewedRevision() throws Exception {
        ObjectNode command = approval(order);

        command.put("decision", "REJECTED");
        command.put("reason", "Route not supported");
        command.remove(List.of("specialistId", "coordinatorId"));
        decide(order, command, manager.token(), 200);
        assertThat(customerCurrent(order).get("orderStatus").asText())
                .isEqualTo("REJECTED");
        assertThat(count("OrderAssignment", order)).isZero();
    }

    /** Ending responsibility is idempotent and immediately revokes access. */
    @Test
    public void endAssignmentRetainsHistoryAndReturnsStableRetry()
            throws Exception {
        JsonNode approved =
                decide(order, approval(order), manager.token(), 200);
        ObjectNode command = assignment(approved);
        String first;

        command.remove("staffId");
        first = end(command);
        assertThat(end(command)).isEqualTo(first);
        assertThat(activeAssignments(order)).isEqualTo(1);
        assertThat(count("OrderAssignment", order)).isEqualTo(2);
        mvc.perform(get(STAFF + "/{id}", order)
                .header("Authorization", specialist.token()))
                .andExpect(status().isNotFound());
    }

    /** Failure after decision/team writes rolls back the entire action. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void auditFailureRollsBackDecisionAssignmentsAndParent()
            throws Exception {
        ObjectNode command = approval(order);

        doThrow(new DataIntegrityViolationException("Fixture failure"))
                .when(audit).append(anyLong(), anyLong(), anyString(),
                        anyString());
        decide(order, command, manager.token(), 409);
        assertThat(decisionCount(order)).isZero();
        assertThat(activeAssignments(order)).isZero();
        assertThat(customerCurrent(order).get("orderStatus").asText())
                .isEqualTo("SUBMITTED");
        assertThat(customerCurrent(order).get("recordVersion").asText())
                .isEqualTo(command.get("recordVersion").asText());
    }

    /** Distinct concurrent decisions on one version cannot both commit. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void concurrentDecisionHasOneWinner() throws Exception {
        ObjectNode first = approval(order);
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
                    .getStatus(), right.get(30, TimeUnit.SECONDS).getResponse()
                    .getStatus())).containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
        }
        assertThat(decisionCount(order)).isEqualTo(1);
        assertThat(activeAssignments(order)).isEqualTo(2);
        assertThat(auditCount(order)).isEqualTo(1);
    }

    /** Protected customer commitments cannot be rewritten by staff changes. */
    @Test
    public void acceptedQuoteBlocksReassignmentWithoutChangingHistory()
            throws Exception {
        JsonNode approved =
                decide(order, approval(order), manager.token(), 200);
        ObjectNode command = assignment(approved);
        long revision = approved.at("/decision/revisionId").asLong();

        jdbc.update("INSERT dbo.Quote (RevisionID,QuoteNumber,Currency,"
                + "QuoteStatus,IssuedAt,AcceptedAt) VALUES (?,?,'USD',"
                + "'ACCEPTED',DATEADD(second,-1,SYSUTCDATETIME()),"
                + "SYSUTCDATETIME())", revision,
                TokenTools.randomToken().substring(0, 20));
        command.put("staffId", replacement.staffId());
        assertThat(reassign(command, 409).get("code").asText())
                .isEqualTo("ORDER_CHANGE_REQUEST_REQUIRED");
        assertThat(count("OrderAssignment", order)).isEqualTo(2);
        assertThat(customerCurrent(order).get("recordVersion").asText())
                .isEqualTo(approved.get("recordVersion").asText());
    }

    /** Case deadlines are UTC metadata, not a staff travel reservation. */
    @Test
    public void deadlineAndInternalInstructionsAreCapturedWithoutPublicLeak()
            throws Exception {
        ObjectNode command = approval(order);
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.ofHours(7))
                .plusDays(2).withNano(0);
        JsonNode result;

        command.put("dueAt", deadline.toString());
        command.put("assignmentNote", "Preparation notes");
        result = decide(order, command, manager.token(), 200);
        assertThat(result.at("/assignments/0/dueAt").asText())
                .isEqualTo(deadline.withOffsetSameInstant(ZoneOffset.UTC)
                        .toString());
        assertThat(result.at("/assignments/0/assignedBy").asLong())
                .isEqualTo(manager.staffId());
        assertThat(result.at("/assignments/0/assignmentNote").asText())
                .isEqualTo("Preparation notes");
    }

    /** Concurrent retries return one identical result and one set of rows. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void concurrentIdenticalDecisionIsIdempotent() throws Exception {
        ObjectNode command = approval(order);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> compete(command, ready, start));
            var right = workers.submit(() -> compete(command, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(read(left.get(30, TimeUnit.SECONDS)))
                    .isEqualTo(read(right.get(30, TimeUnit.SECONDS)));
        } finally {
            start.countDown();
        }
        assertThat(decisionCount(order)).isEqualTo(1);
        assertThat(activeAssignments(order)).isEqualTo(2);
        assertThat(auditCount(order)).isEqualTo(1);
    }

    /** Reassignment competition preserves one active holder per case role. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void concurrentReassignmentHasOneWinner() throws Exception {
        JsonNode approved =
                decide(order, approval(order), manager.token(), 200);
        ObjectNode first = assignment(approved);
        ObjectNode second = first.deepCopy();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        second.put("operationId", UUID.randomUUID().toString());
        second.put("staffId", replacement.staffId());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> competeAssignment(first,
                    ready, start));
            var right = workers.submit(() -> competeAssignment(second,
                    ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(left.get(30, TimeUnit.SECONDS).getResponse()
                    .getStatus(), right.get(30, TimeUnit.SECONDS).getResponse()
                    .getStatus())).containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
        }
        assertThat(activeAssignments(order)).isEqualTo(2);
        assertThat(count("OrderAssignment", order)).isEqualTo(3);
    }

    private MvcResult competeAssignment(ObjectNode command,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Fixture start timed out.");
        }
        return mvc.perform(put(MANAGER + "/{id}/assignments/SPECIALIST", order)
                .header("Authorization", manager.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString())).andReturn();
    }

    private MvcResult compete(ObjectNode command, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Fixture start timed out.");
        }
        return mvc.perform(post(MANAGER + "/{id}/decisions", order)
                .header("Authorization", manager.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString())).andReturn();
    }

    private String end(ObjectNode command) throws Exception {
        return mvc.perform(post(MANAGER + "/{id}/assignments/SPECIALIST/end",
                order).header("Authorization", manager.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode reassign(ObjectNode command, int expected)
            throws Exception {
        return read(mvc.perform(put(MANAGER + "/{id}/assignments/SPECIALIST",
                order).header("Authorization", manager.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString())).andExpect(status().is(expected))
                .andReturn());
    }

    private ObjectNode assignment(JsonNode current) {
        ObjectNode command = mapper.createObjectNode();

        command.put("recordVersion", current.get("recordVersion").asText());
        command.put("operationId", UUID.randomUUID().toString());
        command.put("staffId", specialist.staffId());
        command.put("reason", "Manager workload adjustment");
        return command;
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

    private JsonNode decide(long id, ObjectNode command, String token,
            int expected) throws Exception {
        return read(mvc.perform(post(MANAGER + "/{id}/decisions", id)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.toString())).andExpect(status().is(expected))
                .andReturn());
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

    private long count(String table, long id) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo." + table
                + " WHERE OrderID=?", Long.class, id);
    }

    private long activeAssignments(long id) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*)"
                + " FROM dbo.OrderAssignment"
                + " WHERE OrderID=? AND EndedAt IS NULL", Long.class, id);
    }

    private long decisionCount(long id) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*)"
                + " FROM dbo.OrderDecision d"
                + " JOIN dbo.OrderRevision r ON r.RevisionID=d.RevisionID"
                + " WHERE r.OrderID=?", Long.class, id);
    }

    private long auditCount(long id) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.AuditEvent"
                + " WHERE EntityType='ORDER_ASSESSMENT' AND EntityID=?",
                Long.class, id);
    }

    /** Test-only identity with no production data or raw secret. */
    private record Account(long userId, Long staffId, String token) {
    }
}
