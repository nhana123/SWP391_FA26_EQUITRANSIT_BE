/*
 * StaffAdministrationIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify native staff transactions, live tokens and Manager races.
 */
package com.equitransit.backend;

import com.equitransit.backend.entity.UserAccount;
import com.equitransit.backend.repository.AccountRoleRepository;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.
        TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.put;

/** Schema-only disposable clone; existing shared test rows remain untouched. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_STAFF_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_STAFF_TEST_ENABLED", matches = "true")
@Transactional
public class StaffAdministrationIntegrationTest {
    private static final String BASE = "/api/manager/staff";
    private final List<Long> committedUsers = new ArrayList<>();
    private Actor manager;
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
    private AccountRoleRepository roles;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private JwtTokenService tokens;
    @Autowired
    private EntityManager entities;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager tx;

    /** @throws Exception if not connected to the exact disposable catalog */
    @BeforeEach
    public void setup() throws Exception {
        try (var connection = source.getConnection()) {
            assertThat(connection.getCatalog()).isIn(
                    "EquiTransit_StaffProvisioningTest_20261018",
                    "EquiTransit_FigmaMigrationTest_Staff_20261018");
        }
        for (String role : List.of("MANAGER", "SPECIALIST", "COORDINATOR",
                "DRIVER", "CUSTOMER")) {
            jdbc.update("IF NOT EXISTS (SELECT 1 FROM dbo.Role WHERE "
                    + "RoleCode=?) INSERT dbo.Role(RoleCode) VALUES (?)",
                    role, role);
        }
        manager = actor("MANAGER");
    }

    /** Rollback ordinary fixtures; delete only owned committed race users. */
    @AfterEach
    public void cleanup() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        for (long id : committedUsers) {
            jdbc.update("DELETE dbo.AuditEvent WHERE UserID=?", id);
            jdbc.update("DELETE dbo.StaffAdminOperation WHERE UserID=?", id);
        }
        for (long id : committedUsers) {
            jdbc.update("DELETE dbo.StaffProfile WHERE UserID=?", id);
            jdbc.update("DELETE dbo.[User] WHERE UserID=?", id);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[User]",
                Integer.class)).isZero();
    }

    /** All four staff roles except Manager and Customer receive 403. */
    @Test
    public void managerOnlyAndStrictRequestContracts() throws Exception {
        for (String role : List.of("SPECIALIST", "COORDINATOR", "DRIVER",
                "CUSTOMER")) {
            var other = actor(role);
            expect(mvc.perform(get(BASE).header("Authorization", other.token))
                    .andReturn(), 403);
            expect(send(BASE, createBody("DRIVER"), other.token), 403);
        }
        expect(send(BASE, createBody("CUSTOMER"), manager.token), 400);
        expect(send(BASE, createBody("ADMIN"), manager.token), 400);
        var body = createBody("DRIVER");
        ((ObjectNode) body.get("command")).remove("recordVersion");
        expect(send(BASE, body, manager.token), 400);
        expect(mvc.perform(get(BASE + "?size=101")
                .header("Authorization", manager.token)).andReturn(), 400);
    }

    /** Create/update/retry/CAS/uniqueness, encoded credential and redaction. */
    @Test
    public void provisionUpdateAndReplayAreAtomicAndPrivate() throws Exception {
        var body = createBody("SPECIALIST");
        var first = expect(send(BASE, body, manager.token), 200);
        long staff = first.get("staffId").asLong();
        assertThat(expect(send(BASE, body, manager.token), 200))
                .isEqualTo(first);
        body.put("initialPassword", "ChangedPassword!2026");
        assertThat(expect(send(BASE, body, manager.token), 409).toString())
                .contains("IDEMPOTENCY_CONFLICT");
        body.set("command", command(actorVersion()));
        assertThat(expect(send(BASE, body, manager.token), 409).toString())
                .contains("STAFF_DUPLICATE");
        var duplicateEmail = createBody("DRIVER");
        ((ObjectNode) duplicateEmail.get("profile")).put("email",
                first.get("email").asText().toUpperCase(java.util.Locale.ROOT));
        expect(send(BASE, duplicateEmail, manager.token), 409);
        var duplicateCode = createBody("DRIVER");
        ((ObjectNode) duplicateCode.get("profile")).put("employeeCode",
                first.get("employeeCode").asText());
        expect(send(BASE, duplicateCode, manager.token), 409);
        var update = mapper.createObjectNode();
        update.set("command", command(first.get("recordVersion").asText()));
        update.set("profile", body.get("profile").deepCopy());
        ((ObjectNode) update.get("profile")).put("role", "COORDINATOR")
                .put("department", "Operations");
        var changed = expect(mvc.perform(put(BASE + "/" + staff)
                .header("Authorization", manager.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update.toString())).andReturn(), 200);
        assertThat(changed.get("role").asText()).isEqualTo("COORDINATOR");
        assertThat(changed.get("recordVersion").asText())
                .isNotEqualTo(first.get("recordVersion").asText());
        update.set("command", command(first.get("recordVersion").asText()));
        expect(mvc.perform(put(BASE + "/" + staff)
                .header("Authorization", manager.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update.toString())).andReturn(), 409);
        var digest = jdbc.queryForObject("SELECT PasswordHash FROM dbo.[User]"
                + " WHERE UserID=?", String.class,
                first.get("userId").asLong());
        assertThat(passwords.matches("ExamplePass!2026", digest)).isTrue();
        var audits = jdbc.queryForList("SELECT ChangeSnapshot FROM "
                + "dbo.AuditEvent WHERE EntityType='STAFF_PROFILE' "
                + "AND EntityID=?", String.class, staff);
        assertThat(audits).hasSize(2);
        assertThat(audits.toString()).contains("[REDACTED]", "COORDINATOR")
                .doesNotContain("ExamplePass!2026", digest);
        assertThat(first.toString()).doesNotContain("Password", "Hash");
        var page = expect(mvc.perform(get(BASE
                + "?role=COORDINATOR&status=ACTIVE&page=0&size=1")
                .header("Authorization", manager.token)).andReturn(), 200);
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        assertThat(page.get("items").size()).isEqualTo(1);
    }

    /** Real account state revokes already-issued JWT; history survives. */
    @Test
    public void disableClosesAssignmentsAndRevokesBearer() throws Exception {
        var specialist = actor("SPECIALIST");
        var order = order();
        jdbc.update("INSERT dbo.OrderAssignment(OrderID,StaffID,"
                + "AssignmentRole,AssignedBy) VALUES (?,?,'SPECIALIST',?)",
                order.get("orderId").asLong(), specialist.staff, manager.staff);
        var current = view(specialist.staff);
        assertThat(current.get("activeAssignments").asInt()).isEqualTo(1);
        var body = mapper.createObjectNode();
        body.set("command", command(current.get("recordVersion").asText()));
        body.set("profile", createBody("COORDINATOR").get("profile"));
        assertThat(expect(mvc.perform(put(BASE + "/" + specialist.staff)
                .header("Authorization", manager.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andReturn(), 409).toString())
                .contains("STAFF_ASSIGNMENT_ROLE_CONFLICT");
        var status = statusBody(current, "LOCKED");
        var locked = expect(send(BASE + "/" + specialist.staff + "/status",
                status, manager.token), 200);
        assertThat(locked.get("activeAssignments").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.OrderAssignment WHERE StaffID=? AND EndedAt IS NOT NULL",
                Integer.class, specialist.staff)).isEqualTo(1);
        entities.clear();
        expect(mvc.perform(get("/api/staff/cases")
                .header("Authorization", specialist.token)).andReturn(), 401);
        var active = expect(send(BASE + "/" + specialist.staff + "/status",
                statusBody(locked, "ACTIVE"), manager.token), 200);
        assertThat(active.get("activeAssignments").asInt()).isZero();
    }

    /** Disable must not remove the driver while a real leg is in transit. */
    @Test
    public void inTransitDriverRequiresReassignment() throws Exception {
        var driver = actor("DRIVER");
        var order = order();
        long plan = jdbc.queryForObject("DECLARE @ids TABLE(ID bigint); "
                + "INSERT dbo.TransportPlan(OrderID,"
                + "RevisionID,CreatedBy,VersionNo,FinancialGateStatus,"
                + "OperationalGateStatus,DocGateStatus,PlanStatus) OUTPUT "
                + "INSERTED.PlanID INTO @ids VALUES (?,?,?,1,'PENDING',"
                + "'PENDING','PENDING','DRAFT'); "
                + "SELECT ID FROM @ids", Long.class,
                order.get("orderId").asLong(),
                order.get("currentRevisionId").asLong(), manager.staff);
        long leg = jdbc.queryForObject("DECLARE @ids TABLE(ID bigint); "
                + "INSERT dbo.TripLeg(PlanID,SequenceNo,"
                + "TransportMode,StartPoint,EndPoint,DistanceKm,"
                + "PlannedDeparture,PlannedArrival,LegStatus) OUTPUT "
                + "INSERTED.LegID INTO @ids VALUES "
                + "(?,1,'ROAD','A','B',100,'2027-02-01T01:00:00',"
                + "'2027-02-01T03:00:00','IN_TRANSIT'); SELECT ID FROM @ids",
                Long.class, plan);
        jdbc.update("INSERT dbo.TripAssignment(LegID,StaffID,RoleInTrip,"
                + "AssignedFrom,AssignedTo,Status) VALUES (?,?,'DRIVER',"
                + "'2027-02-01T01:00:00','2027-02-01T03:00:00','ACTIVE')",
                leg, driver.staff);
        assertThat(expect(send(BASE + "/" + driver.staff + "/status",
                statusBody(view(driver.staff), "INACTIVE"), manager.token),
                409).toString()).contains("DRIVER_IN_TRANSIT");
        jdbc.update("UPDATE dbo.TripLeg SET LegStatus='PLANNED' WHERE LegID=?",
                leg);
        expect(send(BASE + "/" + driver.staff + "/status",
                statusBody(view(driver.staff), "INACTIVE"), manager.token),
                200);
        assertThat(jdbc.queryForObject("SELECT Status FROM dbo.TripAssignment"
                + " WHERE LegID=?", String.class, leg)).isEqualTo("INACTIVE");
    }

    /** Committed concurrent self-disable: exactly one Manager survives. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void concurrentManagersCannotDisableTheLastManager()
            throws Exception {
        var second = actor("MANAGER");
        var firstBody = statusBody(view(manager.staff), "LOCKED");
        var secondBody = statusBody(view(second.staff), "INACTIVE");
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> race(manager, firstBody, ready, go));
            var other = pool.submit(() -> race(second, secondBody, ready, go));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS),
                    other.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            go.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[User] u "
                + "JOIN dbo.StaffProfile s ON s.UserID=u.UserID "
                + "WHERE u.Status='ACTIVE' AND s.StaffStatus='ACTIVE'",
                Integer.class)).isEqualTo(1);
    }

    private int race(Actor actor, ObjectNode body, CountDownLatch ready,
            CountDownLatch go) throws Exception {
        ready.countDown();
        assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
        var result = send(BASE + "/" + actor.staff + "/status", body,
                actor.token);
        if (result.getResponse().getStatus() == 409) {
            assertThat(result.getResponse().getContentAsString())
                    .contains("LAST_ACTIVE_MANAGER_PROTECTED");
        }
        return result.getResponse().getStatus();
    }

    private Actor actor(String role) {
        return new TransactionTemplate(tx).execute(state -> {
            var user = new UserAccount();
            user.setEmail(UUID.randomUUID() + "@example.invalid");
            user.setRole(roles.findByRoleCode(role).orElseThrow());
            user.setPasswordHash(passwords.encode("ExamplePass!2026"));
            user.setStatus("ACTIVE");
            user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
            users.saveAndFlush(user);
            committedUsers.add(user.getUserId());
            long staff = 0;
            if (!role.equals("CUSTOMER")) {
                staff = jdbc.queryForObject("INSERT dbo.StaffProfile(UserID,"
                        + "FullName) OUTPUT INSERTED.StaffID VALUES (?,?)",
                        Long.class, user.getUserId(), "Staff test " + role);
            } else {
                jdbc.update("INSERT dbo.Customer(UserID,FullName) VALUES "
                        + "(?,'Staff test customer')", user.getUserId());
            }
            return new Actor(user.getUserId(), staff,
                    "Bearer " + tokens.issue(user).getAccessToken());
        });
    }

    private JsonNode order() throws Exception {
        var customer = actor("CUSTOMER");
        var horse = expect(send("/api/horses", mapper.createObjectNode()
                .put("name", "Staff test horse"), customer.token), 201);
        var body = mapper.createObjectNode()
                .put("horseId", horse.get("horseId").asLong())
                .put("originCountryCode", "VN")
                .put("destinationCountryCode", "GB")
                .put("originAddress", "Staff test origin")
                .put("destinationAddress", "Staff test destination")
                .put("originTimezone", "Asia/Ho_Chi_Minh")
                .put("destinationTimezone", "Europe/London")
                .put("preferredDepartureAt", "2027-02-01T08:00:00+07:00")
                .put("deliveryDeadline", "2027-02-08T08:00:00+07:00")
                .put("importType", "TEMPORARY");
        var draft = expect(send("/api/transport-orders", body, customer.token),
                201);
        long id = draft.get("orderId").asLong();
        expect(send("/api/transport-orders/" + id + "/submit",
                command(draft.get("recordVersion").asText()), customer.token),
                200);
        return expect(mvc.perform(get("/api/transport-orders/" + id)
                .header("Authorization", customer.token)).andReturn(), 200);
    }

    private ObjectNode createBody(String role) throws Exception {
        var profile = mapper.createObjectNode()
                .put("email", UUID.randomUUID() + "@example.invalid")
                .put("fullName", "Staff test")
                .put("employeeCode", "TEST-" + UUID.randomUUID())
                .put("role", role).put("phone", "+84901234567");
        var result = mapper.createObjectNode().put("initialPassword",
                "ExamplePass!2026");
        result.set("profile", profile);
        result.set("command", command(actorVersion()));
        return result;
    }

    private String actorVersion() throws Exception {
        return view(manager.staff).get("recordVersion").asText();
    }

    private JsonNode view(long staff) throws Exception {
        return expect(mvc.perform(get(BASE + "/" + staff)
                .header("Authorization", manager.token)).andReturn(), 200);
    }

    private ObjectNode command(String version) {
        assertThat(version).matches("^[0-9a-fA-F]{16}$");
        return mapper.createObjectNode().put("recordVersion", version)
                .put("operationId", UUID.randomUUID().toString());
    }

    private ObjectNode statusBody(JsonNode view, String status) {
        var result = mapper.createObjectNode().put("status", status)
                .put("reason", "Staff test status transition");
        result.set("command", command(view.get("recordVersion").asText()));
        return result;
    }

    private MvcResult send(String url, ObjectNode body, String token)
            throws Exception {
        return mvc.perform(post(url).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andReturn();
    }

    private JsonNode expect(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(status);
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    /** Separate domain and authentication identities used by the fixture. */
    private record Actor(long user, long staff, String token) { }
}
