/*
 * OperationalPlanIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify F10 multimodal operational planning, locks and concurrency.
 */
package com.equitransit.backend;

import com.equitransit.backend.dto.resource.ResourceExamples;
import com.equitransit.backend.entity.Customer;
import com.equitransit.backend.entity.UserAccount;
import com.equitransit.backend.repository.AccountRoleRepository;
import com.equitransit.backend.repository.CustomerRepository;
import com.equitransit.backend.repository.PlanRepository;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;

/** Native SQL integration suite for F10 multimodal operational planning. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_PLAN_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_PLAN_TEST_ENABLED", matches = "true")
public class OperationalPlanIntegrationTest {

    private final Map<String, Actor> actors = new HashMap<>();
    private final List<Long> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureOrders = new ArrayList<>();
    private final List<Long> fixtureTariffs = new ArrayList<>();
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
    private PlanRepository repository;

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

            jdbc.update("DELETE dbo.PlanOperation WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.AuditEvent WHERE EntityID=?", id);
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
            jdbc.update("DELETE dbo.QuoteItem WHERE QuoteID" + quote, id);
            jdbc.update("DELETE dbo.QuotePaymentSchedule WHERE QuoteID"
                    + quote, id);
            jdbc.update("DELETE dbo.Quote WHERE RevisionID" + rev, id);
            jdbc.update("DELETE dbo.SupplierOffer WHERE RevisionID" + rev, id);
            jdbc.update("DELETE dbo.RevisionQuarantineAssessment WHERE "
                    + "RevisionID" + rev, id);
            jdbc.update("DELETE dbo.RevisionVeterinaryAssessment WHERE "
                    + "RevisionID" + rev, id);
            jdbc.update("DELETE dbo.OrderAssignment WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.OrderDecision WHERE RevisionID" + rev, id);
            for (String table : List.of("StallChoice", "QuarantineChoice",
                    "InsuranceChoice")) {
                jdbc.update("DELETE dbo." + table + " WHERE ChoiceID"
                        + choice, id);
            }
            jdbc.update("DELETE dbo.OrderServiceChoice WHERE RevisionID"
                    + rev, id);
            jdbc.update("DELETE dbo.TransportOrderDetail WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.OrderRevision WHERE OrderID=?", id);
            jdbc.update("DELETE dbo.TransportOrder WHERE OrderID=?", id);
        }
        for (long tariff : fixtureTariffs) {
            jdbc.update("DELETE dbo.ServiceTariff WHERE TariffID=?", tariff);
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

    /** Full multimodal draft -> F05/F06 km -> assignments -> confirm. */
    @Test
    public void fullMultimodalLifecycleSuppliesF05KmAndConfirmsPlan()
            throws Exception {
        long horseId = createHorse();
        var case1 = createApprovedCase(horseId);
        long order = case1.orderId();
        long rev = case1.revisionId();
        var start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withNano(0);
        var mid = start.plusHours(2);
        var airDep = mid.plusHours(1);
        var end = airDep.plusHours(8);
        var draftReq = Map.of("command", command(order), "revisionId", rev,
                "setAsCurrentDraft", true, "legs", List.of(
                        Map.of("sequenceNo", 1, "transportMode", "ROAD",
                                "startPoint", "Hanoi", "endPoint", "Noi Bai",
                                "distanceKm", "50.000", "distanceSource",
                                "Google Maps", "plannedDeparture",
                                start.toString(), "plannedArrival",
                                mid.toString()),
                        Map.of("sequenceNo", 2, "transportMode", "AIR",
                                "startPoint", "Noi Bai", "endPoint",
                                "London Heathrow", "distanceKm", "9200.000",
                                "distanceSource", "IATA Route",
                                "plannedDeparture", airDep.toString(),
                                "plannedArrival", end.toString(),
                                "plannedFlightNumber", "VN0051",
                                "connectionBufferMinutes", 60)));
        var plan = postJson(base(order), draftReq,
                actor("COORDINATOR").token(), 200);
        long planId = plan.get("planId").asLong();
        long roadLeg = plan.at("/legs/0/legId").asLong();
        long airLeg = plan.at("/legs/1/legId").asLong();

        assertThat(plan.get("versionNo").asInt()).isEqualTo(1);
        assertThat(plan.get("currentPlan").asBoolean()).isTrue();
        var vehicle = createResource("vehicles");
        var stall = createResource("stalls");
        var policy = body("fit-policies");

        ((ObjectNode) policy.get("data")).put("status", "APPROVED");
        createResource("fit-policies", policy);
        postJson(base(order) + "/" + planId + "/legs/" + roadLeg
                + "/road-assignment", Map.of("command", command(order),
                        "vehicleId", vehicle.get("id").asLong(),
                        "vehicleRecordVersion",
                        vehicle.get("recordVersion").asText(),
                        "driverStaffId", actor("DRIVER").staff(),
                        "driverBufferMinutes", 30),
                actor("COORDINATOR").token(), 200);
        postJson(base(order) + "/" + planId + "/legs/" + roadLeg
                + "/stall-assignment", Map.of("command", command(order),
                        "stallId", stall.get("id").asLong(),
                        "stallRecordVersion",
                        stall.get("recordVersion").asText(),
                        "carrierAcceptanceStatus", "ACCEPTED",
                        "status", "CONFIRMED",
                        "carrierApprovalFileKey", "proof.pdf"),
                actor("COORDINATOR").token(), 409);
        postJson(base(order) + "/" + planId + "/legs/" + airLeg
                + "/stall-assignment", Map.of("command", command(order),
                        "stallId", stall.get("id").asLong(),
                        "stallRecordVersion",
                        stall.get("recordVersion").asText(),
                        "carrierAcceptanceStatus", "ACCEPTED",
                        "status", "CONFIRMED",
                        "carrierApprovalFileKey", "proof.pdf"),
                actor("COORDINATOR").token(), 200);
        long offerId = createAirfareOffer(order, rev);

        postJson(base(order) + "/" + planId + "/legs/" + airLeg
                + "/flight-reservation", Map.of("command", command(order),
                        "offerId", offerId, "airlineName", "VN Cargo",
                        "flightNumber", "VN0051", "reservationReference",
                        "AWB-123", "status", "HELD", "evidenceFileKey",
                        "flight.pdf", "expiresAt", end.plusDays(5).toString(),
                        "usableCapacitySlots", "3.0"),
                actor("COORDINATOR").token(), 200);
        long quoteId = createAcceptedQuote(order, rev);

        postJson(base(order) + "/" + planId + "/confirm",
                Map.of("command", command(order), "acceptedQuoteId", quoteId),
                actor("COORDINATOR").token(), 409);
        postJson(base(order) + "/" + planId + "/legs/" + airLeg
                + "/flight-reservation", Map.of("command", command(order),
                        "offerId", offerId, "airlineName", "VN Cargo",
                        "flightNumber", "VN0051", "reservationReference",
                        "AWB-123", "status", "CONFIRMED", "evidenceFileKey",
                        "flight.pdf", "expiresAt", end.plusDays(5).toString(),
                        "usableCapacitySlots", "3.0"),
                actor("COORDINATOR").token(), 200);
        postJson(base(order) + "/" + planId + "/quarantine-reservation",
                Map.of("command", command(order), "revisionId", rev,
                        "facilityName", "Quarantine A", "reservationReference",
                        "Q-1", "startsAt", end.toString(), "endsAt",
                        end.plusDays(5).toString(), "status", "CONFIRMED",
                        "evidenceFileKey", "q.pdf", "expiresAt",
                        end.plusDays(20).toString()),
                actor("COORDINATOR").token(), 409);
        postJson(base(order) + "/" + planId + "/quarantine-reservation",
                Map.of("command", command(order), "revisionId", rev,
                        "facilityName", "Quarantine A", "reservationReference",
                        "Q-1", "startsAt", end.toString(), "endsAt",
                        end.plusDays(14).toString(), "status", "CONFIRMED",
                        "evidenceFileKey", "q.pdf", "expiresAt",
                        end.plusDays(20).toString()),
                actor("COORDINATOR").token(), 200);
        postJson(base(order) + "/" + planId + "/insurance-policy",
                Map.of("command", command(order), "revisionId", rev,
                        "insurerName", "EquiInsure", "policyNumber", "POL-1",
                        "evidenceFileKey", "ins.pdf", "coverageFrom",
                        start.minusHours(1).toString(), "coverageTo",
                        end.plusDays(15).toString(), "status", "CONFIRMED"),
                actor("COORDINATOR").token(), 200);
        var confirmCmd = Map.of("command", command(order),
                "acceptedQuoteId", quoteId);
        var confirmed = postJson(base(order) + "/" + planId + "/confirm",
                confirmCmd, actor("COORDINATOR").token(), 200);

        assertThat(confirmed.get("planStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.get("operationalGateStatus").asText())
                .isEqualTo("PASS");
        assertThat(postJson(base(order) + "/" + planId + "/confirm",
                confirmCmd, actor("COORDINATOR").token(), 200))
                .isEqualTo(confirmed);
        postJson(base(order) + "/" + planId + "/insurance-policy",
                Map.of("command", command(order), "revisionId", rev,
                        "insurerName", "EquiInsure", "policyNumber", "POL-2",
                        "evidenceFileKey", "ins.pdf", "coverageFrom",
                        start.minusHours(1).toString(), "coverageTo",
                        end.plusDays(15).toString(), "status", "CONFIRMED"),
                actor("COORDINATOR").token(), 409);
        var v2 = postJson(base(order), Map.of("command", command(order),
                "revisionId", rev, "setAsCurrentDraft", false, "legs",
                List.of(Map.of("sequenceNo", 1, "transportMode", "ROAD",
                        "startPoint", "Hanoi", "endPoint", "Noi Bai",
                        "distanceKm", "55.000", "distanceSource", "Google Maps",
                        "plannedDeparture", start.plusDays(30).toString(),
                        "plannedArrival", mid.plusDays(30).toString()))),
                actor("MANAGER").token(), 200);

        assertThat(v2.get("versionNo").asInt()).isEqualTo(2);
        var disclosure = read(mvc.perform(get("/api/transport-orders/"
                + order + "/services")
                .header("Authorization", actor("CUSTOMER").token()))
                .andExpect(status().isOk()).andReturn());

        assertThat(disclosure.toString()).contains(
                "\"quantity\":\"50\"", "\"quantity\":\"9250\"",
                "\"quantity\":\"13800\"");
    }

    /** Concurrent road assignments for the same driver/vehicle serialize. */
    @Test
    public void concurrentDriverAndVehicleAssignmentsHaveOneWinner()
            throws Exception {
        long h1 = createHorse();
        long h2 = createHorse();
        var c1 = createApprovedCase(h1);
        var c2 = createApprovedCase(h2);
        var start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(9).withNano(0);
        var end = start.plusHours(3);
        var p1 = postJson(base(c1.orderId()), Map.of("command",
                command(c1.orderId()), "revisionId", c1.revisionId(),
                "setAsCurrentDraft", true, "legs", List.of(Map.of(
                        "sequenceNo", 1, "transportMode", "ROAD",
                        "startPoint", "A", "endPoint", "B", "distanceKm",
                        "40.000", "distanceSource", "Maps", "plannedDeparture",
                        start.toString(), "plannedArrival", end.toString()))),
                actor("COORDINATOR").token(), 200);
        var p2 = postJson(base(c2.orderId()), Map.of("command",
                command(c2.orderId()), "revisionId", c2.revisionId(),
                "setAsCurrentDraft", true, "legs", List.of(Map.of(
                        "sequenceNo", 1, "transportMode", "ROAD",
                        "startPoint", "A", "endPoint", "B", "distanceKm",
                        "40.000", "distanceSource", "Maps", "plannedDeparture",
                        start.toString(), "plannedArrival", end.toString()))),
                actor("COORDINATOR").token(), 200);
        var v1 = createResource("vehicles");
        var v2 = createResource("vehicles");
        long driver = actor("DRIVER").staff();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var f1 = pool.submit(() -> raceRoad(c1.orderId(),
                    p1.get("planId").asLong(), p1.at("/legs/0/legId").asLong(),
                    v1.get("id").asLong(), v1.get("recordVersion").asText(),
                    driver, ready, go));
            var f2 = pool.submit(() -> raceRoad(c2.orderId(),
                    p2.get("planId").asLong(), p2.at("/legs/0/legId").asLong(),
                    v2.get("id").asLong(), v2.get("recordVersion").asText(),
                    driver, ready, go));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(f1.get(30, TimeUnit.SECONDS),
                    f2.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
    }

    /** Same horse on two active orders cannot confirm overlapping plans. */
    @Test
    public void sameHorseCannotBeConfirmedOnOverlappingOrders()
            throws Exception {
        long sharedHorse = createHorse();
        var c1 = createApprovedCase(sharedHorse);
        var c2 = createApprovedCase(sharedHorse);
        var start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(11).withNano(0);
        var end = start.plusHours(4);
        long p1 = createReadyRoadPlan(c1.orderId(), c1.revisionId(), start,
                end, createResource("vehicles"), actor("DRIVER").staff());
        long p2 = createReadyRoadPlan(c2.orderId(), c2.revisionId(),
                start.plusHours(1), end.plusHours(2),
                createResource("vehicles"), createExtraDriver());
        long q1 = createAcceptedQuote(c1.orderId(), c1.revisionId());
        long q2 = createAcceptedQuote(c2.orderId(), c2.revisionId());

        postJson(base(c1.orderId()) + "/" + p1 + "/confirm",
                Map.of("command", command(c1.orderId()),
                        "acceptedQuoteId", q1),
                actor("COORDINATOR").token(), 200);
        var blocked = postJson(base(c2.orderId()) + "/" + p2 + "/confirm",
                Map.of("command", command(c2.orderId()),
                        "acceptedQuoteId", q2),
                actor("COORDINATOR").token(), 409);

        assertThat(blocked.get("code").asText())
                .isEqualTo("HORSE_SCHEDULE_OVERLAP");
    }

    /** Two 1.5-slot SHARED orders share a 3.0-slot stall on the same flight. */
    @Test
    public void sharedAirStallAllowsTwoWideOrdersOnSameFlightAndBlocksOverflow()
            throws Exception {
        var c1 = createApprovedCase(createHorse(), "WIDE");
        var c2 = createApprovedCase(createHorse(), "WIDE");
        var c3 = createApprovedCase(createHorse(), "WIDE");
        var dep = OffsetDateTime.now(ZoneOffset.UTC).plusDays(12).withNano(0);
        var arr = dep.plusHours(8);
        var airLegs = List.of(Map.of("sequenceNo", 1, "transportMode", "AIR",
                "startPoint", "Noi Bai", "endPoint", "London Heathrow",
                "distanceKm", "9200.000", "distanceSource", "IATA Route",
                "plannedDeparture", dep.toString(), "plannedArrival",
                arr.toString(), "plannedFlightNumber", "VN0051"));
        var p1 = postJson(base(c1.orderId()), Map.of("command",
                command(c1.orderId()), "revisionId", c1.revisionId(),
                "setAsCurrentDraft", true, "legs", airLegs),
                actor("COORDINATOR").token(), 200);
        var p2 = postJson(base(c2.orderId()), Map.of("command",
                command(c2.orderId()), "revisionId", c2.revisionId(),
                "setAsCurrentDraft", true, "legs", airLegs),
                actor("COORDINATOR").token(), 200);
        var p3 = postJson(base(c3.orderId()), Map.of("command",
                command(c3.orderId()), "revisionId", c3.revisionId(),
                "setAsCurrentDraft", true, "legs", airLegs),
                actor("COORDINATOR").token(), 200);
        var candidates = read(mvc.perform(get(base(c1.orderId())
                + "/shared-stall-candidates")
                .header("Authorization", actor("COORDINATOR").token()))
                .andExpect(status().isOk()).andReturn());

        assertThat(candidates.toString()).contains(
                "\"candidateOrderId\":" + c2.orderId(),
                "\"candidateOrderId\":" + c3.orderId(),
                "\"combinedSlotQuantity\":3.0");
        var stall = createResource("stalls");
        var policy = body("fit-policies");

        ((ObjectNode) policy.get("data")).put("status", "APPROVED");
        createResource("fit-policies", policy);
        long stallId = stall.get("id").asLong();
        String stallVer = stall.get("recordVersion").asText();

        postJson(base(c1.orderId()) + "/" + p1.get("planId").asLong()
                + "/legs/" + p1.at("/legs/0/legId").asLong()
                + "/stall-assignment", Map.of("command", command(c1.orderId()),
                        "stallId", stallId, "stallRecordVersion", stallVer,
                        "carrierAcceptanceStatus", "ACCEPTED",
                        "status", "CONFIRMED",
                        "carrierApprovalFileKey", "proof-1.pdf"),
                actor("COORDINATOR").token(), 200);
        var shared = postJson(base(c2.orderId()) + "/"
                + p2.get("planId").asLong() + "/legs/"
                + p2.at("/legs/0/legId").asLong() + "/stall-assignment",
                Map.of("command", command(c2.orderId()), "stallId", stallId,
                        "stallRecordVersion", stallVer,
                        "carrierAcceptanceStatus", "ACCEPTED",
                        "status", "CONFIRMED",
                        "carrierApprovalFileKey", "proof-2.pdf"),
                actor("COORDINATOR").token(), 200);

        assertThat(shared.at("/legs/0/stallAssignments/0/sharingCode")
                .asText()).isEqualTo("SHARED");
        assertThat(shared.at("/legs/0/stallAssignments/0/slotQuantity")
                .asText()).isEqualTo("1.5");
        postJson(base(c3.orderId()) + "/" + p3.get("planId").asLong()
                + "/legs/" + p3.at("/legs/0/legId").asLong()
                + "/stall-assignment", Map.of("command", command(c3.orderId()),
                        "stallId", stallId, "stallRecordVersion", stallVer,
                        "carrierAcceptanceStatus", "ACCEPTED",
                        "status", "CONFIRMED",
                        "carrierApprovalFileKey", "proof-3.pdf"),
                actor("COORDINATOR").token(), 409);
    }

    private int raceRoad(long orderId, long planId, long legId, long vehicleId,
            String vehicleVer, long driverId, CountDownLatch ready,
            CountDownLatch go) throws Exception {
        var cmd = command(orderId);

        ready.countDown();
        assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(post(base(orderId) + "/" + planId + "/legs/" + legId
                + "/road-assignment")
                .header("Authorization", actor("COORDINATOR").token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("command", cmd,
                        "vehicleId", vehicleId, "vehicleRecordVersion",
                        vehicleVer, "driverStaffId", driverId,
                        "driverBufferMinutes", 30))))
                .andReturn().getResponse().getStatus();
    }

    private long createReadyRoadPlan(long order, long rev, OffsetDateTime start,
            OffsetDateTime end, JsonNode vehicle, long driverId)
            throws Exception {
        var plan = postJson(base(order), Map.of("command", command(order),
                "revisionId", rev, "setAsCurrentDraft", true, "legs",
                List.of(Map.of("sequenceNo", 1, "transportMode", "ROAD",
                        "startPoint", "A", "endPoint", "B", "distanceKm",
                        "50.000", "distanceSource", "Maps", "plannedDeparture",
                        start.toString(), "plannedArrival", end.toString()))),
                actor("COORDINATOR").token(), 200);
        long planId = plan.get("planId").asLong();
        long legId = plan.at("/legs/0/legId").asLong();

        postJson(base(order) + "/" + planId + "/legs/" + legId
                + "/road-assignment", Map.of("command", command(order),
                        "vehicleId", vehicle.get("id").asLong(),
                        "vehicleRecordVersion",
                        vehicle.get("recordVersion").asText(),
                        "driverStaffId", driverId, "driverBufferMinutes", 15),
                actor("COORDINATOR").token(), 200);
        postJson(base(order) + "/" + planId + "/quarantine-reservation",
                Map.of("command", command(order), "revisionId", rev,
                        "facilityName", "Quarantine A", "reservationReference",
                        "Q-1", "startsAt", end.toString(), "endsAt",
                        end.plusDays(14).toString(), "status", "CONFIRMED",
                        "evidenceFileKey", "q.pdf", "expiresAt",
                        end.plusDays(20).toString()),
                actor("COORDINATOR").token(), 200);
        postJson(base(order) + "/" + planId + "/insurance-policy",
                Map.of("command", command(order), "revisionId", rev,
                        "insurerName", "EquiInsure", "policyNumber", "POL-1",
                        "evidenceFileKey", "ins.pdf", "coverageFrom",
                        start.minusHours(1).toString(), "coverageTo",
                        end.plusDays(15).toString(), "status", "CONFIRMED"),
                actor("COORDINATOR").token(), 200);
        return planId;
    }

    private long createAirfareOffer(long order, long rev) {
        return jdbc.queryForObject("DECLARE @ids TABLE (ID BIGINT); "
                + "INSERT dbo.SupplierOffer (RevisionID,ProviderName,"
                + "OfferReference,ServiceCode,UnitCode,Quantity,"
                + "UnitPrice,RetailUnitPrice,Currency,ValidUntil,"
                + "Status,IncludesStall,EvidenceFileKey,RecordedBy,RecordedAt) "
                + "OUTPUT INSERTED.OfferID INTO @ids VALUES (?,'VN Cargo',"
                + "'OFF-' + CONVERT(varchar(20),?),'AIRFARE','FLIGHT',1.0000,"
                + "5000.0000,6000.00,'USD',DATEADD(day,20,SYSUTCDATETIME()),"
                + "'SELECTED',1,'offer.pdf',?,SYSUTCDATETIME()); "
                + "SELECT ID FROM @ids", Long.class, rev, order,
                actor("COORDINATOR").staff());
    }

    private long createAcceptedQuote(long order, long rev) {
        long id = jdbc.queryForObject("DECLARE @ids TABLE (ID BIGINT); "
                + "INSERT dbo.Quote (RevisionID,"
                + "QuoteNumber,VersionNo,Currency,TotalQuotedAmount,"
                + "QuoteStatus,PreparedBy,SubmittedAt,ValidUntil,IssuedBy,"
                + "IssuedAt,AcceptedByCustomerID,AcceptedAt,TermsVersion,"
                + "TermsText,CancellationTerms) OUTPUT INSERTED.QuoteID "
                + "INTO @ids VALUES (?,'Q-' + CONVERT(varchar(20),?) + '-1',"
                + "1,'USD',1000.00,'ACCEPTED',?,SYSUTCDATETIME(),"
                + "DATEADD(day,10,SYSUTCDATETIME()),?,SYSUTCDATETIME(),"
                + "(SELECT CustomerID FROM dbo.TransportOrder WHERE OrderID=?),"
                + "SYSUTCDATETIME(),'V1','Terms','Cancel'); "
                + "SELECT ID FROM @ids", Long.class, rev, order,
                actor("COORDINATOR").staff(), actor("MANAGER").staff(), order);

        jdbc.update("INSERT dbo.QuotePaymentSchedule (QuoteID,SequenceNo,"
                + "PaymentStage,AmountDue,Currency,DueAt) VALUES "
                + "(?,1,'FULL',1000.00,'USD',DATEADD(day,5,SYSUTCDATETIME()))",
                id);
        return id;
    }

    private long createHorse() throws Exception {
        var horse = postJson("/api/horses", Map.of("name", "Plan test horse",
                "weightKg", 500, "heightCm", 160, "bodyWidthCm", 70,
                "measuredAt", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
                        .withNano(0).toString()),
                actor("CUSTOMER").token(), 201);

        return horse.get("horseId").asLong();
    }

    private CaseFixture createApprovedCase(long horseId) throws Exception {
        return createApprovedCase(horseId, "WIDE");
    }

    private CaseFixture createApprovedCase(long horseId, String airStall)
            throws Exception {
        var now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        var fields = new HashMap<String, Object>();

        fields.put("horseId", horseId);
        fields.put("originCountryCode", "VN");
        fields.put("destinationCountryCode", "GB");
        fields.put("originAddress", "Test origin");
        fields.put("destinationAddress", "Test destination");
        fields.put("originTimezone", "Asia/Ho_Chi_Minh");
        fields.put("destinationTimezone", "Europe/London");
        fields.put("importType", "TEMPORARY");
        fields.put("preferredDepartureAt", now.plusDays(7).toString());
        fields.put("deliveryDeadline", now.plusDays(30).toString());
        fields.put("services", Map.of("roadArrangement", "EQUITRANSIT",
                "airStall", airStall, "quarantinePackage", "STANDARD",
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
        return new CaseFixture(order, rev);
    }

    private JsonNode createResource(String kind) throws Exception {
        return createResource(kind, body(kind));
    }

    private JsonNode createResource(String kind, ObjectNode body)
            throws Exception {
        var res = postJson("/api/manager/resources/" + kind, body,
                actor("MANAGER").token(), 200);

        resources.computeIfAbsent(kind, k -> new ArrayList<>())
                .add(res.get("id").asLong());
        return res;
    }

    private ObjectNode body(String kind) throws Exception {
        String ex = switch (kind) {
            case "vehicles" -> ResourceExamples.VEHICLE;
            case "stalls" -> ResourceExamples.STALL;
            default -> ResourceExamples.POLICY;
        };
        var result = mapper.createObjectNode();
        var data = (ObjectNode) mapper.readTree(ex);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        if (kind.equals("vehicles")) {
            data.put("vehicleCode", "V-" + suffix);
            data.put("plateNumber", "P-" + suffix);
        } else if (kind.equals("stalls")) {
            data.put("stallCode", "S-" + suffix);
        } else {
            data.put("policyCode", "POL-" + suffix);
            data.put("effectiveFrom", OffsetDateTime.now(ZoneOffset.UTC)
                    .minusDays(1).withNano(0).toString());
            data.put("effectiveTo", OffsetDateTime.now(ZoneOffset.UTC)
                    .plusDays(60).withNano(0).toString());
        }
        if (data.has("window")) {
            ((ObjectNode) data.get("window")).put("bufferMinutes", 0);
        }
        result.set("command", mapper.valueToTree(Map.of("operationId",
                UUID.randomUUID().toString())));
        result.set("data", data);
        return result;
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
                + "OUTPUT INSERTED.StaffID VALUES (?,'Extra Driver')",
                Long.class, user.getUserId());
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
                customer.setFullName("Plan test customer");
                customers.saveAndFlush(customer);
            } else {
                staff = jdbc.queryForObject("INSERT dbo.StaffProfile "
                        + "(UserID,FullName) OUTPUT INSERTED.StaffID "
                        + "VALUES (?,?)", Long.class, user.getUserId(),
                        "Plan test " + name);
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

    private String base(long order) {
        return "/api/transport-orders/" + order + "/plans";
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

    private record CaseFixture(long orderId, long revisionId) { }
}
