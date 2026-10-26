/*
 * ResourceIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify SQL resources, availability, fit and concurrency.
 */
package com.equitransit.backend;

import com.equitransit.backend.dto.resource.ResourceExamples;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceMode;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAvailabilityRequest;
import com.equitransit.backend.entity.customer.Customer;
import com.equitransit.backend.entity.auth.UserAccount;
import com.equitransit.backend.entity.order.AirStallPreference;
import com.equitransit.backend.repository.auth.AccountRoleRepository;
import com.equitransit.backend.repository.customer.CustomerRepository;
import com.equitransit.backend.repository.auth.UserAccountRepository;
import com.equitransit.backend.repository.pricing.ServiceSourceRepository;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.service.pricing.StallFitEvaluator;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.service.resource.ResourceAvailabilityService;
import com.equitransit.backend.dto.horse.response.HorseResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;

/** Disposable native fixtures, guarded by the exact isolated catalog. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_RESOURCE_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_RESOURCE_TEST_ENABLED",
        matches = "true")
@Transactional
@SuppressWarnings("null")
public class ResourceIntegrationTest {

    private static final String BASE = "/api/manager/resources/";
    private final Map<String, Actor> actors = new HashMap<>();
    private final List<Long> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureOrders = new ArrayList<>();
    private final List<Long> fixturePlans = new ArrayList<>();
    private final Map<String, List<Long>> resources = new HashMap<>();
    private String passwordHash;
    private long order;
    private long revision;
    private final java.util.concurrent.ConcurrentLinkedQueue<String> failures =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

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
    private ResourceRepository repository;
    @Autowired
    private ServiceSourceRepository policies;
    @Autowired
    private StallFitEvaluator fit;
    @Autowired
    private ResourceAvailabilityService availability;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager tx;
    @Autowired
    private org.springframework.security.oauth2.jwt.JwtDecoder decoder;

    /** @throws Exception if the catalog guard fails */
    @BeforeEach
    public void setup() throws Exception {
        try (var connection = source.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo(
                    "EquiTransit_FigmaMigrationTest_20261013_143729");
        }
        assertThat(repository.ready()).isTrue();
        passwordHash = passwords.encode("TestPassword123!");
        actor("MANAGER");
    }

    /** Deletes only owned IDs, including committed race fixtures. */
    @AfterEach
    public void cleanup() {
        for (long plan : fixturePlans) {
            jdbc.update("DELETE dbo.StallAssignment WHERE LegID IN "
                    + "(SELECT LegID FROM dbo.TripLeg WHERE PlanID=?)", plan);
            jdbc.update("DELETE dbo.TripLeg WHERE PlanID=?", plan);
            jdbc.update("DELETE dbo.TransportPlan WHERE PlanID=?", plan);
        }
        for (long id : fixtureOrders) {
            String revisions = " IN (SELECT RevisionID FROM dbo.OrderRevision"
                    + " WHERE OrderID=?)";
            String choices = " IN (SELECT ChoiceID FROM dbo.OrderServiceChoice"
                    + " WHERE RevisionID" + revisions + ")";

            jdbc.update("DELETE dbo.OrderAssignment WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.OrderDecision WHERE RevisionID"
                    + revisions, id);
            for (String table : List.of("StallChoice", "QuarantineChoice",
                    "InsuranceChoice")) {
                jdbc.update("DELETE dbo." + table + " WHERE ChoiceID"
                        + choices, id);
            }
            jdbc.update("DELETE dbo.OrderServiceChoice WHERE RevisionID"
                    + revisions, id);
            jdbc.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=NULL,"
                    + "CurrentPlanID=NULL WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.TransportOrderDetail WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.OrderRevision WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.TransportOrder WHERE OrderID=?", id);
        }
        for (String kind : List.of("fit-policies", "vehicles", "stalls",
                "suppliers")) {
            String table = switch (kind) {
                case "fit-policies" -> "StallFitPolicy";
                case "vehicles" -> "FleetVehicle";
                case "stalls" -> "StallEquipment";
                default -> "Supplier";
            };
            String key = switch (kind) {
                case "fit-policies" -> "PolicyID";
                case "vehicles" -> "VehicleID";
                case "stalls" -> "StallID";
                default -> "SupplierID";
            };
            for (long id : resources.getOrDefault(kind, List.of())) {
                jdbc.update("DELETE dbo." + table + " WHERE " + key + "=?", id);
            }
        }
        for (long user : fixtureUsers) {
            jdbc.update("DELETE dbo.ResourceOperation WHERE UserID=?", user);
            jdbc.update("DELETE dbo.AuditEvent WHERE UserID=?", user);
            jdbc.update("DELETE dbo.Horse WHERE CustomerID IN "
                    + "(SELECT CustomerID FROM dbo.Customer WHERE UserID=?)",
                    user);
            jdbc.update("DELETE dbo.Customer WHERE UserID=?", user);
            jdbc.update("DELETE dbo.StaffProfile WHERE UserID=?", user);
            jdbc.update("DELETE dbo.[User] WHERE UserID=?", user);
        }
    }

    /** Typed create/read/archive, UUID replay and stale-version rejection. */
    @Test
    public void managerCatalogueAndStableRetries() throws Exception {
        for (String kind : List.of("vehicles", "stalls", "suppliers",
                "fit-policies")) {
            var input = body(kind);
            var first = create(kind, input);
            long id = first.get("id").asLong();

            assertThat(first.path("recordVersion").asText())
                    .matches("^[0-9a-fA-F]{16}$");

            assertThat(send(kind, input, actor("MANAGER").token(), 200))
                    .isEqualTo(first);
            var page = read(mvc.perform(get(BASE + kind)
                    .header("Authorization", actor("MANAGER").token()))
                    .andExpect(status().isOk()).andReturn());

            assertThat(page.at("/items/0/id").asLong()).isEqualTo(id);
            var update = replacement(first);
            var data = (ObjectNode) update.get("data");

            if (kind.equals("suppliers")) {
                data.put("active", false);
            } else {
                data.put("status", "INACTIVE");
            }
            assertThat(replace(kind, id, update, 200).get("recordVersion"))
                    .isNotEqualTo(first.get("recordVersion"));
            ((ObjectNode) update.get("command")).put("operationId",
                    UUID.randomUUID().toString());
            replace(kind, id, update, 409);
            mvc.perform(delete(BASE + kind + "/" + id)
                    .header("Authorization", actor("MANAGER").token()))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    /** Ordinary actors cannot enumerate or modify the Manager catalogue. */
    @Test
    public void rolesAndInputBoundsAreEnforced() throws Exception {
        for (String role : List.of("CUSTOMER", "COORDINATOR", "SPECIALIST",
                "DRIVER")) {
            mvc.perform(get(BASE + "vehicles")
                    .header("Authorization", actor(role).token()))
                    .andExpect(status().isForbidden());
            send("vehicles", body("vehicles"), actor(role).token(), 403);
        }
        mvc.perform(get(BASE + "vehicles?page=-1&size=101")
                .header("Authorization", actor("MANAGER").token()))
                .andExpect(status().isBadRequest());
        var invalid = body("stalls");

        ((ObjectNode) invalid.get("data")).put("weightLimitKg", -1);
        send("stalls", invalid, actor("MANAGER").token(), 400);
    }

    /** Approval is sourced/versioned and immediately consumed by F05. */
    @Test
    public void policyVersionsFeedRealF05Fit() throws Exception {
        var input = body("fit-policies");
        var draft = create("fit-policies", input);
        var now = OffsetDateTime.now(ZoneOffset.UTC);

        assertThat(policies.fitPolicies(now, now.plusDays(1))).isEmpty();
        var approval = replacement(draft);

        ((ObjectNode) approval.get("data")).put("status", "APPROVED");
        var approved = replace("fit-policies", draft.get("id").asLong(),
                approval, 200);
        var horse = mapper.readValue("{\"weightKg\":500,\"heightCm\":160,"
                + "\"bodyWidthCm\":70,\"measuredAt\":\"2026-01-01T00:00:00Z\"}",
                HorseResponse.class);

        assertThat(approved.get("reviewedBy").asLong())
                .isEqualTo(actor("MANAGER").staff());
        assertThat(fit.evaluate(AirStallPreference.WIDE, horse,
                policies.fitPolicies(now.plusSeconds(1), now.plusDays(1)), now)
                .status()).isEqualTo("ELIGIBLE");
        var forbidden = replacement(approved);

        ((ObjectNode) forbidden.get("data")).put("maxWeightKg", 400);
        replace("fit-policies", approved.get("id").asLong(), forbidden, 409);
        var newer = body("fit-policies");
        var data = (ObjectNode) newer.get("data");

        data.put("policyCode", approved.at("/data/policyCode").asText());
        data.put("status", "APPROVED");
        data.put("maxWeightKg", 400);
        assertThat(create("fit-policies", newer).get("version").asInt())
                .isEqualTo(2);
        assertThat(policies.fitPolicies(now.plusSeconds(1), now.plusDays(1)))
                .hasSize(1);
        assertThat(fit.evaluate(AirStallPreference.WIDE, horse,
                policies.fitPolicies(now.plusSeconds(1), now.plusDays(1)), now)
                .status()).isEqualTo("UNSUITABLE");
    }

    /** Missing positive limits/source cannot become an approved fit policy. */
    @Test
    public void incompletePolicyApprovalIsRejected() throws Exception {
        for (String field : List.of("maxWeightKg", "maxHeightCm",
                "maxWidthCm", "sourceUrl")) {
            var input = body("fit-policies");
            var data = (ObjectNode) input.get("data");

            data.put("status", "APPROVED");
            data.putNull(field);
            send("fit-policies", input, actor("MANAGER").token(), 400);
        }
        var relative = body("fit-policies");

        ((ObjectNode) relative.get("data")).put("sourceUrl", "/policy.pdf");
        send("fit-policies", relative, actor("MANAGER").token(), 400);
    }

    /** Inactive ownership blocks use, but does not prevent archival. */
    @Test
    public void inactiveSupplierCannotPreventArchival() throws Exception {
        var supplier = create("suppliers", body("suppliers"));
        var input = body("vehicles");

        ((ObjectNode) input.get("data")).put("ownerSupplierId",
                supplier.get("id").asLong());
        var vehicle = create("vehicles", input);
        var change = replacement(supplier);

        ((ObjectNode) change.get("data")).put("active", false);
        replace("suppliers", supplier.get("id").asLong(), change, 200);
        change = replacement(vehicle);
        replace("vehicles", vehicle.get("id").asLong(), change, 400);
        ((ObjectNode) change.get("data")).put("status", "INACTIVE");
        assertThat(replace("vehicles", vehicle.get("id").asLong(), change,
                200).at("/data/status").asText()).isEqualTo("INACTIVE");
    }

    /** F10 must keep a transaction and recheck fit, lineage and rowversion. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void strictConsumerChecksTransactionVersionAndOwnLeg()
            throws Exception {
        currentCase();
        var vehicle = create("vehicles", body("vehicles"));
        long id = vehicle.get("id").asLong();
        var start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withNano(0);
        var end = start.plusHours(1);
        long ownLeg = leg(plan(), "ROAD", id, start, end, 1);
        String version = vehicle.get("recordVersion").asText();
        var request = new ResourceAvailabilityRequest(order, revision,
                ResourceMode.ROAD, start, end, null, ownLeg);
        var context = org.springframework.security.core.context.
                SecurityContextHolder.getContext();

        context.setAuthentication(new org.springframework.security.oauth2.
                server.resource.authentication.JwtAuthenticationToken(
                        decoder.decode(actor("MANAGER").token().substring(7)),
                        List.of(new org.springframework.security.core.authority.
                                SimpleGrantedAuthority("ROLE_MANAGER"))));
        try {
            assertThatThrownBy(() -> availability.requireAvailable(
                    ResourceKind.VEHICLE, id, request, version))
                    .isInstanceOf(org.springframework.transaction.
                            IllegalTransactionStateException.class);
            var transaction = new TransactionTemplate(tx);
            var result = transaction.execute(state -> availability
                    .requireAvailable(ResourceKind.VEHICLE, id, request,
                            version));

            assertThat(result).isNotNull();
            if (result != null) {
                assertThat(result.status().name()).isEqualTo("AVAILABLE");
            }
            assertThatThrownBy(() -> transaction.execute(state -> availability
                    .requireAvailable(ResourceKind.VEHICLE, id, request,
                            "0000000000000000")))
                    .hasMessageContaining("Review resource evidence/version");
            var blocked = new ResourceAvailabilityRequest(order, revision,
                    ResourceMode.ROAD, start, end, null, null);

            assertThatThrownBy(() -> transaction.execute(state -> availability
                    .requireAvailable(ResourceKind.VEHICLE, id, blocked,
                            version)))
                    .hasMessageContaining("Review resource evidence/version");
            var foreign = new ResourceAvailabilityRequest(order, revision,
                    ResourceMode.ROAD, start, end, null, Long.MAX_VALUE);

            assertThatThrownBy(() -> transaction.execute(state -> availability
                    .requireAvailable(ResourceKind.VEHICLE, id, foreign,
                            version))).hasMessageContaining("not found");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder
                    .clearContext();
        }
    }

    /** A cancelled plan cannot reactivate into a newly occupied window. */
    @Test
    public void planReactivationRechecksResourceOverlap() throws Exception {
        currentCase();
        var vehicle = create("vehicles", body("vehicles"));
        long id = vehicle.get("id").asLong();
        var start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withNano(0);
        var end = start.plusHours(1);
        long previous = plan();

        leg(previous, "ROAD", id, start, end, 1);
        jdbc.update("UPDATE dbo.TransportPlan SET PlanStatus='CANCELLED' "
                + "WHERE PlanID=?", previous);
        leg(plan(2), "ROAD", id, start, end, 1);
        assertThatThrownBy(() -> jdbc.update("UPDATE dbo.TransportPlan "
                + "SET PlanStatus='DRAFT' WHERE PlanID=?", previous))
                .hasMessageContaining("overlapping assignment");
    }

    /** Maintenance and expiry block otherwise available road resources. */
    @Test
    public void maintenanceExpiryAndOwnership() throws Exception {
        currentCase();
        var vehicle = create("vehicles", body("vehicles"));
        long id = vehicle.get("id").asLong();
        var start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withNano(0);
        var end = start.plusHours(2);
        var available = probe("VEHICLE", id, start, end,
                actor("MANAGER").token(), 200);

        assertThat(available.get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(available.get("carrierConfirmed").asBoolean()).isFalse();
        Actor assigned = actor("COORDINATOR");

        actors.remove("COORDINATOR");
        probe("VEHICLE", id, start, end, actor("COORDINATOR").token(), 404);
        actors.put("COORDINATOR", assigned);
        probe("VEHICLE", id, start, end, actor("CUSTOMER").token(), 403);
        var change = replacement(vehicle);
        var window = (ObjectNode) change.at("/data/window");

        window.put("maintenanceFrom", start.minusHours(1).toString());
        window.putNull("maintenanceTo");
        vehicle = replace("vehicles", id, change, 200);
        assertThat(probe("VEHICLE", id, start, end,
                assigned.token(), 200).get("blockers").toString())
                .contains("MAINTENANCE");
        change = replacement(vehicle);
        window = (ObjectNode) change.at("/data/window");
        window.putNull("maintenanceFrom");
        window.put("documentExpiresAt", end.toString());
        replace("vehicles", id, change, 200);
        assertThat(probe("VEHICLE", id, start, end,
                assigned.token(), 200).get("blockers").toString())
                .contains("DOCUMENT_EXPIRED");
    }

    /** Fit needs both physical limits and an effective approved policy. */
    @Test
    public void stallFitRemainsPendingUntilPolicyIsApproved() throws Exception {
        currentCase();
        var stall = create("stalls", body("stalls"));
        long id = stall.get("id").asLong();
        var start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withNano(0);
        var end = start.plusHours(1);
        var pending = probe("STALL", id, start, end,
                actor("MANAGER").token(), 200);

        assertThat(pending.get("status").asText()).isEqualTo("PENDING");
        assertThat(pending.get("blockers").toString())
                .contains("FIT_POLICY_PENDING");
        var policy = body("fit-policies");

        ((ObjectNode) policy.get("data")).put("status", "APPROVED");
        create("fit-policies", policy);
        assertThat(probe("STALL", id, start, end,
                actor("COORDINATOR").token(), 200).get("status").asText())
                .isEqualTo("AVAILABLE");
        var change = replacement(stall);

        ((ObjectNode) change.get("data")).put("weightLimitKg", 400);
        replace("stalls", id, change, 200);
        assertThat(probe("STALL", id, start, end,
                actor("MANAGER").token(), 200).get("blockers").toString())
                .contains("PHYSICAL_FIT");
    }

    /** Half-open adjacency is free; a vehicle is never usable on AIR. */
    @Test
    public void vehicleScheduleAndModeGuard() throws Exception {
        currentCase();
        var vehicle = create("vehicles", body("vehicles"));
        long id = vehicle.get("id").asLong();
        long plan = plan();
        var start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withNano(0);
        var end = start.plusHours(1);

        leg(plan, "ROAD", id, start, end, 1);
        assertThat(probe("VEHICLE", id, end, end.plusHours(1),
                actor("MANAGER").token(), 200).get("status").asText())
                .isEqualTo("AVAILABLE");
        assertThat(probe("VEHICLE", id, end.minusSeconds(1), end.plusHours(1),
                actor("MANAGER").token(), 200).get("blockers").toString())
                .contains("OVERLAP");
        var change = replacement(vehicle);

        ((ObjectNode) change.at("/data/window")).put("bufferMinutes", 30);
        replace("vehicles", id, change, 200);
        assertThat(probe("VEHICLE", id, end.plusMinutes(29), end.plusHours(1),
                actor("MANAGER").token(), 200).get("blockers").toString())
                .contains("OVERLAP");
        assertThat(probe("VEHICLE", id, end.plusMinutes(30), end.plusHours(1),
                actor("MANAGER").token(), 200).get("status").asText())
                .isEqualTo("AVAILABLE");
        assertThatThrownBy(() -> leg(plan, "AIR", id, end,
                end.plusHours(1), 2)).hasMessageContaining("Vehicle mode");
    }

    /** The same physical stall has only one winner across transactions. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void concurrentStallAssignmentsHaveOneWinner() throws Exception {
        currentCase();
        var stall = create("stalls", body("stalls"));
        long id = stall.get("id").asLong();
        long plan = plan();
        var start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withNano(0);
        var end = start.plusHours(1);
        long first = leg(plan, "AIR", null, start, end, 1);

        currentCase();
        long second = leg(plan(), "AIR", null, start, end, 1);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> compete(first, id, start, end,
                    ready, go));
            var right = workers.submit(() -> compete(second, id, start, end,
                    ready, go));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(left.get(30, TimeUnit.SECONDS),
                    right.get(30, TimeUnit.SECONDS)))
                    .as(failures.toString())
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            go.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.StallAssignment WHERE StallID=?", Integer.class, id))
                .isEqualTo(1);
        var policy = body("fit-policies");

        ((ObjectNode) policy.get("data")).put("status", "APPROVED");
        create("fit-policies", policy);
        assertThat(probe("STALL", id, end, end.plusHours(1),
                actor("MANAGER").token(), 200).get("status").asText())
                .isEqualTo("AVAILABLE");
        assertThat(probe("STALL", id, start, end,
                actor("MANAGER").token(), 200).get("blockers").toString())
                .contains("OVERLAP");
    }

    private boolean compete(long leg, long stall, OffsetDateTime start,
            OffsetDateTime end, CountDownLatch ready, CountDownLatch go)
            throws Exception {
        ready.countDown();
        if (!go.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Fixture start timed out.");
        }
        var tx = new TransactionTemplate(
                new DataSourceTransactionManager(source));

        tx.setTimeout(15);
        try {
            tx.executeWithoutResult(state -> {
                jdbc.queryForObject("SELECT o.OrderID FROM dbo.TransportOrder "
                        + "o WITH (UPDLOCK,HOLDLOCK) JOIN dbo.TransportPlan p "
                        + "ON p.OrderID=o.OrderID JOIN dbo.TripLeg l ON "
                        + "l.PlanID=p.PlanID WHERE l.LegID=?", Long.class, leg);
                repository.lock();
                jdbc.update("INSERT dbo.StallAssignment (LegID,StallID,"
                        + "SlotQuantity,SharingCode,FitStatus,"
                        + "CarrierAcceptanceStatus,AssignedFrom,AssignedTo,"
                        + "Status) VALUES (?,?,1.5,'WIDE','PENDING',"
                        + "'PENDING',?,?,'HELD')", leg, stall,
                        start.toLocalDateTime(), end.toLocalDateTime());
            });
            return true;
        } catch (org.springframework.dao.DataAccessException error) {
            failures.add(error.getMostSpecificCause().getMessage());
            assertThat(error.getMostSpecificCause().getMessage())
                    .containsAnyOf("overlapping assignment", "deadlock");
            return false;
        } catch (OrderException error) {
            failures.add(error.getCode());
            assertThat(error.getCode()).isEqualTo("RESOURCE_BUSY");
            return false;
        }
    }

    private ObjectNode body(String kind) throws Exception {
        String example = switch (kind) {
            case "vehicles" -> ResourceExamples.VEHICLE;
            case "stalls" -> ResourceExamples.STALL;
            case "suppliers" -> ResourceExamples.SUPPLIER;
            default -> ResourceExamples.POLICY;
        };
        var result = mapper.createObjectNode();
        var data = (ObjectNode) mapper.readTree(example);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        if (kind.equals("vehicles")) {
            data.put("vehicleCode", "TEST-" + suffix);
            data.put("plateNumber", "TEST-" + suffix);
        } else if (kind.equals("stalls")) {
            data.put("stallCode", "TEST-" + suffix);
        } else if (kind.equals("fit-policies")) {
            data.put("policyCode", "TEST-" + suffix);
            data.put("effectiveFrom", OffsetDateTime.now(ZoneOffset.UTC)
                    .minusDays(1).withNano(0).toString());
            data.put("effectiveTo", OffsetDateTime.now(ZoneOffset.UTC)
                    .plusDays(30).withNano(0).toString());
        }
        if (data.has("window")) {
            ((ObjectNode) data.get("window")).put("bufferMinutes", 0);
        }
        result.set("command", mapper.valueToTree(Map.of("operationId",
                UUID.randomUUID().toString())));
        result.set("data", data);
        return result;
    }

    private ObjectNode replacement(JsonNode view) {
        var result = mapper.createObjectNode();

        result.set("command", mapper.valueToTree(Map.of("operationId",
                UUID.randomUUID().toString(), "recordVersion",
                view.get("recordVersion").asText())));
        result.set("data", view.get("data").deepCopy());
        return result;
    }

    private JsonNode create(String kind, ObjectNode body) throws Exception {
        JsonNode result = send(kind, body, actor("MANAGER").token(), 200);

        resources.computeIfAbsent(kind, key -> new ArrayList<>())
                .add(result.get("id").asLong());
        return result;
    }

    private JsonNode send(String kind, ObjectNode body, String token,
            int expected) throws Exception {
        return read(mvc.perform(post(BASE + kind)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().is(expected))
                .andReturn());
    }

    private JsonNode replace(String kind, long id, ObjectNode body,
            int expected) throws Exception {
        var result = mvc.perform(put(BASE + kind + "/" + id)
                .header("Authorization", actor("MANAGER").token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andReturn();

        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(expected);
        return read(result);
    }

    private JsonNode probe(String kind, long id, OffsetDateTime start,
            OffsetDateTime end, String token, int expected) throws Exception {
        return read(mvc.perform(post("/api/resources/" + kind + "/" + id
                + "/availability").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("orderId", order,
                        "revisionId", revision, "mode",
                        kind.equals("VEHICLE") ? "ROAD" : "AIR",
                        "start", start.toString(), "end", end.toString()))))
                .andExpect(status().is(expected)).andReturn());
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
                customer.setFullName("Resource test customer");
                customers.saveAndFlush(customer);
            } else {
                staff = jdbc.queryForObject("INSERT dbo.StaffProfile"
                        + " (UserID,FullName) OUTPUT INSERTED.StaffID"
                        + " VALUES (?,?)", Long.class, user.getUserId(),
                        "Resource test " + name);
            }
            return new Actor(user.getUserId(), staff,
                    "Bearer " + tokens.issue(user).getAccessToken());
        });
    }

    private void currentCase() throws Exception {
        Actor customer = actor("CUSTOMER");
        JsonNode horse = read(mvc.perform(post("/api/horses")
                .header("Authorization", customer.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Test horse",
                        "weightKg", 500, "heightCm", 160, "bodyWidthCm", 70,
                        "measuredAt", OffsetDateTime.now(ZoneOffset.UTC)
                                .minusDays(1).withNano(0).toString()))))
                .andExpect(status().isCreated()).andReturn());
        var fields = new HashMap<String, Object>();
        var now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);

        fields.put("horseId", horse.get("horseId").asLong());
        fields.put("originCountryCode", "VN");
        fields.put("destinationCountryCode", "GB");
        fields.put("originAddress", "Test origin");
        fields.put("destinationAddress", "Test destination");
        fields.put("originTimezone", "Asia/Ho_Chi_Minh");
        fields.put("destinationTimezone", "Europe/London");
        fields.put("importType", "TEMPORARY");
        fields.put("preferredDepartureAt", now.plusDays(7).toString());
        fields.put("deliveryDeadline", now.plusDays(14).toString());
        fields.put("services", Map.of("airStall", "WIDE"));
        var draft = read(mvc.perform(post("/api/transport-orders")
                .header("Authorization", customer.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(fields)))
                .andExpect(status().isCreated()).andReturn());

        order = draft.get("orderId").asLong();
        fixtureOrders.add(order);
        mvc.perform(post("/api/transport-orders/" + order + "/submit")
                .header("Authorization", customer.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("recordVersion",
                        draft.get("recordVersion").asText(), "operationId",
                        UUID.randomUUID().toString()))))
                .andExpect(status().isOk());
        var current = read(mvc.perform(get("/api/transport-orders/" + order)
                .header("Authorization", customer.token()))
                .andExpect(status().isOk()).andReturn());

        revision = current.get("currentRevisionId").asLong();
        mvc.perform(post("/api/manager/transport-orders/"
                + order + "/decisions")
                .header("Authorization", actor("MANAGER").token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("recordVersion",
                        current.get("recordVersion").asText(), "operationId",
                        UUID.randomUUID().toString(), "revisionId", revision,
                        "decision", "APPROVED",
                        "specialistId", actor("SPECIALIST").staff(),
                        "coordinatorId", actor("COORDINATOR").staff()))))
                .andExpect(status().isOk());
    }

    private long plan() {
        return plan(1);
    }

    private long plan(int version) {
        long id = jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids TABLE "
                + "(ID BIGINT); INSERT dbo.TransportPlan (OrderID,"
                + "RevisionID,CreatedBy,VersionNo,FinancialGateStatus,"
                + "OperationalGateStatus,DocGateStatus,PlanStatus)"
                + " OUTPUT INSERTED.PlanID INTO @ids VALUES (?,?,?,?,'PENDING',"
                + "'PENDING','PENDING','DRAFT'); SELECT ID FROM @ids",
                Long.class,
                order, revision, actor("COORDINATOR").staff(), version);

        fixturePlans.add(id);
        return id;
    }

    private long leg(long plan, String mode, Long vehicle,
            OffsetDateTime start, OffsetDateTime end, int sequence) {
        return jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids TABLE "
                + "(ID BIGINT); INSERT dbo.TripLeg (PlanID,VehicleID,"
                + "SequenceNo,TransportMode,StartPoint,EndPoint,DistanceKm,"
                + "PlannedDeparture,PlannedArrival,LegStatus) OUTPUT "
                + "INSERTED.LegID INTO @ids VALUES (?,?,?,?,'Origin',"
                + "'Destination',100,?,?,'PLANNED'); SELECT ID FROM @ids",
                Long.class, plan, vehicle, sequence, mode,
                start.toLocalDateTime(), end.toLocalDateTime());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    /** Test identity, never a real account or published credential. */
    private record Actor(long user, Long staff, String token) { }
}
