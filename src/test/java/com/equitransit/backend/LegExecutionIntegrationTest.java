package com.equitransit.backend;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import com.equitransit.backend.repository.LegExecutionRepository;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;


/** Native SQL tests roll back append-only rows rather than disabling guards. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_EXECUTION_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_EXECUTION_TEST_ENABLED",
        matches = "true")
@Transactional
public class LegExecutionIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource source;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private JwtTokenService tokens;
    @Autowired
    private LegExecutionRepository repository;
    private long order;
    private long plan;
    private long road;
    private long air;
    private long customer;
    private long manager;
    private long driver;
    private long coordinator;

    /** @throws Exception if the exact empty disposable test guard fails */
    @BeforeEach
    public void setup() throws Exception {
        try (var c = source.getConnection()) {
            assertThat(c.getCatalog()).isEqualTo(
                    "EquiTransit_FigmaMigrationTest_Execution_20261018");
        }
        assertThat(repository.ready()).isTrue();
        customer = account("CUSTOMER");
        long customerId = insert("Customer", "CustomerID",
                "UserID,FullName", "?,N'Fixture Customer'", customer);
        manager = account("MANAGER");
        driver = account("DRIVER");
        coordinator = account("COORDINATOR");
        long managerStaff = staff(manager);

        order = insert("TransportOrder", "OrderID",
                "CustomerID,OrderNumber,OrderStatus", "?,?,'IN_TRANSIT'",
                customerId, "EX-" + UUID.randomUUID().toString()
                        .substring(0, 12));
        long revision = insert("OrderRevision", "RevisionID",
                "OrderID,RevisionNo,OrderSnapshot", "?,1,'{}'", order);

        plan = insert("TransportPlan", "PlanID",
                "OrderID,RevisionID,CreatedBy,VersionNo,FinancialGateStatus,"
                        + "OperationalGateStatus,DocGateStatus,PlanStatus",
                "?,?,?,1,'PASS','PASS','PASS','IN_TRANSIT'", order, revision,
                managerStaff);
        jdbc.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=?,"
                + "CurrentPlanID=? WHERE OrderID=?", revision, plan, order);
        road = leg(1, "ROAD", "IN_TRANSIT", "'2020-01-01T08:00:00'");
        air = leg(2, "AIR", "CONFIRMED", "NULL");
        jdbc.update("INSERT dbo.TripAssignment (LegID,StaffID,RoleInTrip,"
                + "Status,AssignedFrom,AssignedTo) VALUES (?,?,'DRIVER',"
                + "'ACTIVE','2020-01-01T08:00:00','2020-01-01T10:00:00')",
                road, staff(driver));
        jdbc.update("INSERT dbo.OrderAssignment (OrderID,StaffID,"
                + "AssignmentRole) VALUES (?,?,'COORDINATOR')", order,
                staff(coordinator));
    }

    /** Proves rollback leaves no business data or append-only test facts. */
    @AfterTransaction
    public void noFixturesRemain() {
        for (String table : List.of("LegExecutionOperation", "LegMilestoneLog",
                "HorseWelfareLog", "IncidentReport", "TransportOrder",
                "TripLeg", "User", "AuditEvent")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.["
                    + table + "]", Integer.class)).as(table).isZero();
        }
    }

    /** Happy path retains planned times and leaves final handover to F14.
     * @throws Exception on HTTP failure */
    @Test
    public void completesOrderedRoadAirJourneyWithoutDeliveringOrder()
            throws Exception {
        write(driver, road, "complete", transition("2020-01-01T09:00:00"),
                200);
        write(coordinator, air, "start", transition("2020-01-01T09:10:00"),
                200);
        write(coordinator, air, "complete", transition("2020-01-01T12:00:00"),
                200);
        assertThat(jdbc.queryForObject("SELECT OrderStatus FROM "
                + "dbo.TransportOrder WHERE OrderID=?", String.class, order))
                .isEqualTo("IN_TRANSIT");
        assertThat(jdbc.queryForObject("SELECT PlannedArrival FROM dbo.TripLeg"
                + " WHERE LegID=?", java.sql.Timestamp.class, road).toString())
                .startsWith("2020-01-01 09:00:00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.LegMilestoneLog WHERE PlanID=?", Integer.class, plan))
                .isEqualTo(3);
    }

    /** @throws Exception on HTTP failure */
    @Test
    public void blocksFirstLegStartSkippedLegAndDriverAir() throws Exception {
        write(driver, road, "start", transition("2020-01-01T08:00:00"), 422);
        write(coordinator, air, "start", transition("2020-01-01T09:10:00"),
                422);
        write(driver, air, "start", transition("2020-01-01T09:10:00"), 403);
        long otherDriver = account("DRIVER");

        write(otherDriver, road, "complete", transition(
                "2020-01-01T09:00:00"), 403);
    }

    /** @throws Exception on HTTP failure */
    @Test
    public void enforcesOwnerAndPublicFactVisibility() throws Exception {
        var hidden = milestone();

        hidden.put("customerVisible", false);
        write(driver, road, "milestones", hidden, 200);
        write(driver, road, "milestones", milestone(), 200);
        var response = overview(customer, 200);

        assertThat(response.path("milestoneCount").asInt()).isEqualTo(1);
        assertThat(response.toString()).doesNotContain("secret-coordinator");
        overview(account("CUSTOMER"), 401);
        // Give a real Customer profile to test ownership rather than login.
        long outsider = account("CUSTOMER");
        insert("Customer", "CustomerID", "UserID,FullName", "?,'Other'",
                outsider);
        overview(outsider, 404);
        write(customer, road, "milestones", milestone(), 403);
    }

    /** @throws Exception on HTTP failure */
    @Test
    public void retryReturnsOriginalReceiptAndRejectsChangedPayload()
            throws Exception {
        var body = transition("2020-01-01T09:00:00");
        var first = write(driver, road, "complete", body, 200);

        assertThat(write(driver, road, "complete", body, 200)).isEqualTo(first);
        body.put("sourceReference", "Changed evidence");
        write(driver, road, "complete", body, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.LegExecutionOperation", Integer.class)).isEqualTo(1);
    }

    /** @throws Exception on HTTP failure */
    @Test
    public void staleVersionAndInvalidCoordinatesDoNotWrite()
            throws Exception {
        var stale = milestone();

        stale.withObject("command").put("recordVersion", "0000000000000000");
        write(driver, road, "milestones", stale, 409);
        var invalid = milestone();

        invalid.put("latitude", "90.000001");
        write(driver, road, "milestones", invalid, 422);
        var care = welfare();

        care.put("feedIntakeKg", "-1.00");
        write(driver, road, "welfare-logs", care, 400);
    }

    /** @throws Exception on HTTP failure */
    @Test
    public void airNeedsExternalSourceAndNonemptyReference() throws Exception {
        write(driver, road, "complete", transition("2020-01-01T09:00:00"),
                200);
        var request = transition("2020-01-01T09:10:00");

        request.put("sourceCode", "DRIVER_REPORT");
        write(coordinator, air, "start", request, 422);
        request.put("sourceCode", "CARRIER");
        request.remove("sourceReference");
        write(coordinator, air, "start", request, 400);
    }

    /** @throws Exception on HTTP failure */
    @Test
    public void lateFactsRetainEventTimeAndPrivateCorrectionCannotHidePublic()
            throws Exception {
        var visible = welfare();

        visible.put("customerVisible", true);
        visible.put("publicNote", "Routine care performed.");
        var first = write(driver, road, "welfare-logs", visible, 200);
        var correction = welfare();

        correction.put("supersedesLogId", first.path("entityId").asLong());
        write(driver, road, "welfare-logs", correction, 422);
        var late = milestone();

        late.put("occurredAt", "2020-01-01T08:10:00Z");
        write(driver, road, "milestones", late, 200);
        var response = overview(customer, 200);

        assertThat(response.path("lastUpdateAt").isNull()).isFalse();
        assertThat(response.path("milestones").get(0).path("occurredAt")
                .asText()).startsWith("2020-01-01T08:10:00");
        assertThat(response.toString()).doesNotContain("secret-coordinator",
                "careNotes", "behaviorNotes", "internalNotes");
    }

    /** @throws Exception on HTTP failure */
    @Test
    public void welfareCorrectionsRetainHistoryAndUrgentCreatesIncident()
            throws Exception {
        var first = write(driver, road, "welfare-logs", welfare(), 200);
        var correction = welfare();

        correction.put("supersedesLogId", first.path("entityId").asLong());
        correction.put("healthStatus", "URGENT");
        var next = write(driver, road, "welfare-logs", correction, 200);

        assertThat(next.path("incidentId").asLong()).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.HorseWelfareLog", Integer.class)).isEqualTo(2);
        assertThat(overview(customer, 200).path("welfareCount").asInt())
                .isZero();
        write(driver, road, "complete", transition("2020-01-01T09:00:00"),
                200);
        write(coordinator, air, "start", transition("2020-01-01T09:00:00"),
                200);
        var wrongLeg = welfare();

        wrongLeg.put("occurredAt", "2020-01-01T09:10:00Z");
        wrongLeg.put("supersedesLogId", first.path("entityId").asLong());
        write(coordinator, air, "welfare-logs", wrongLeg, 422);
    }

    /** @throws Exception on HTTP failure */
    @Test
    public void databaseRejectsUpdatingFacts() throws Exception {
        var receipt = write(driver, road, "welfare-logs", welfare(), 200);

        assertThatThrownBy(() -> jdbc.update("UPDATE dbo.HorseWelfareLog "
                + "SET CareNotes='overwrite' WHERE WelfareLogID=?",
                receipt.path("entityId").asLong()))
                .hasMessageContaining("append-only");
    }

    /** @throws Exception on HTTP failure */
    @Test
    public void databaseRejectsDeletingFacts() throws Exception {
        var receipt = write(driver, road, "welfare-logs", welfare(), 200);

        assertThatThrownBy(() -> jdbc.update("DELETE dbo.HorseWelfareLog "
                + "WHERE WelfareLogID=?", receipt.path("entityId").asLong()))
                .hasMessageContaining("append-only");
    }

    /** @throws Exception if a second connection can bypass the parent lock */
    @Test
    public void parentUpdateLockBlocksCompetingSession() throws Exception {
        write(driver, road, "milestones", milestone(), 200);
        try (var c = source.getConnection();
                var statement = c.createStatement()) {
            statement.execute("SET LOCK_TIMEOUT 1000");
            assertThatThrownBy(() -> {
                try (var rows = statement.executeQuery("SELECT OrderID "
                        + "FROM dbo.TransportOrder WITH (UPDLOCK,HOLDLOCK) "
                        + "WHERE OrderID=" + order)) {
                    rows.next();
                }
            })
                    .hasMessageContaining("Lock request time out");
        }
    }

    private JsonNode overview(long actor, int expected) throws Exception {
        var response = mvc.perform(get("/api/transport-orders/" + order
                + "/execution").header("Authorization", token(actor)))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(expected);
        return mapper.readTree(response.getContentAsString());
    }

    private JsonNode write(long actor, long leg, String action,
            ObjectNode body, int expected) throws Exception {
        var result = mvc.perform(post("/api/transport-orders/" + order
                + "/legs/" + leg + "/" + action)
                .header("Authorization", token(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body))).andReturn();
        var response = result.getResponse();

        assertThat(response.getStatus()).as("%s; cause=%s",
                response.getContentAsString(), result.getResolvedException())
                .isEqualTo(expected);
        return mapper.readTree(response.getContentAsString());
    }

    private ObjectNode transition(String when) {
        var body = mapper.createObjectNode();
        var command = body.putObject("command");

        command.put("recordVersion", java.util.HexFormat.of().formatHex(
                jdbc.queryForObject("SELECT RecordVersion FROM "
                        + "dbo.TransportOrder WHERE OrderID=?",
                        byte[].class, order)));
        command.put("operationId", UUID.randomUUID().toString());
        body.put("occurredAt", when + "Z");
        body.put("sourceCode", "CARRIER");
        body.put("sourceReference", "Native test carrier message");
        return body;
    }

    private ObjectNode milestone() {
        var body = transition("2020-01-01T08:15:00");

        body.put("milestoneType", "POSITION");
        body.put("latitude", "10.823100");
        body.put("longitude", "106.629700");
        body.put("customerVisible", true);
        body.put("publicNote", "Road progress updated.");
        body.put("internalNotes", "secret-coordinator");
        return body;
    }

    private ObjectNode welfare() {
        var body = transition("2020-01-01T08:20:00");

        body.put("healthStatus", "NORMAL");
        body.put("waterIntakeLiters", "2.50");
        body.put("feedIntakeKg", "1.00");
        body.put("customerVisible", false);
        body.put("careNotes", "secret-coordinator");
        return body;
    }

    private long account(String role) {
        long user = insert("User", "UserID", "RoleID,Email,PasswordHash,Status",
                "(SELECT RoleID FROM dbo.Role WHERE RoleCode=?),?,"
                        + "'fixture-hash','ACTIVE'", role,
                UUID.randomUUID() + "@example.invalid");

        if (!"CUSTOMER".equals(role)) {
            insert("StaffProfile", "StaffID", "UserID,FullName,StaffStatus",
                    "?,'Execution Fixture','ACTIVE'", user);
        }
        return user;
    }

    private long staff(long user) {
        return jdbc.queryForObject("SELECT StaffID FROM dbo.StaffProfile "
                + "WHERE UserID=?", Long.class, user);
    }

    private long leg(int sequence, String mode, String status, String start) {
        return insert("TripLeg", "LegID", "PlanID,SequenceNo,TransportMode,"
                + "LegStatus,StartPoint,EndPoint,DistanceKm,ActualDeparture,"
                + "PlannedDeparture,PlannedArrival", "?,?,?,?,'A','B',10,"
                + start + ",'2020-01-01T08:00:00','2020-01-01T09:00:00'",
                plan, sequence, mode, status);
    }

    private long insert(String table, String id, String columns,
            String values, Object... args) {
        return jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids TABLE "
                + "(ID BIGINT); INSERT dbo.[" + table + "] (" + columns
                + ") OUTPUT inserted." + id + " INTO @ids VALUES (" + values
                + "); SELECT ID FROM @ids;", Long.class, args);
    }

    private String token(long user) {
        return "Bearer " + tokens.issue(users.findById(user).orElseThrow())
                .getAccessToken();
    }
}
