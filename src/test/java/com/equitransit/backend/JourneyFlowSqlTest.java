package com.equitransit.backend;

import com.equitransit.backend.journey.JourneyAccess;
import com.equitransit.backend.journey.JourneyBookingService;
import com.equitransit.backend.journey.JourneyDocumentService;
import com.equitransit.backend.journey.JourneyModels;
import com.equitransit.backend.journey.JourneyStore;
import com.equitransit.backend.journey.JourneyTripService;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.repository.plan.PlanRepository;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceBlocker;
import com.equitransit.backend.exception.order.OrderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Native SQL happy/blocked paths; every business fixture rolls back. */
@EnabledIfEnvironmentVariable(named = "EQ_JOURNEY_SQL", matches = "true")
public class JourneyFlowSqlTest {
    private JourneyStore db;
    private JourneyAccess access;
    private JourneyBookingService bookings;
    private JourneyTripService trips;
    private JourneyDocumentService documents;
    private AtomicReference<CurrentActor> actor;
    private AtomicReference<Instant> instant;
    private final Map<String, CurrentActor> staff = new java.util.HashMap<>();

    /** Two customers, three horses, one trip; no unpaid/uncleared bypass. */
    @Test
    public void sharedTripFromBookingToSettlement() {
        var ds = new DriverManagerDataSource(
                System.getenv("EQ_JOURNEY_DB_URL"));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.executeWithoutResult(status -> {
            db = new JourneyStore(new NamedParameterJdbcTemplate(ds));
            var name = db.rows("SELECT DB_NAME() AS N", Map.of())
                    .getFirst().get("N").toString();
            assertThat(name).isIn("EquiTransit_Demo", "EquiTransit_Test");
            var actors = mock(CurrentActorService.class);
            actor = new AtomicReference<>();
            when(actors.current()).thenAnswer(inv -> actor.get());
            var clock = mock(Clock.class);
            instant = new AtomicReference<>(
                    Instant.parse("2030-01-01T10:00:00Z"));
            when(clock.instant()).thenAnswer(inv -> instant.get());
            when(clock.getZone()).thenReturn(ZoneOffset.UTC);
            access = new JourneyAccess(db, actors,
                    new ObjectMapper().findAndRegisterModules());
            bookings = new JourneyBookingService(db, access, clock);
            trips = new JourneyTripService(db, access, bookings, clock);
            documents = new JourneyDocumentService(db, access, clock);
            db.lock();
            for (String role : List.of("MANAGER", "SPECIALIST", "COORDINATOR",
                    "DRIVER")) {
                var s = db.rows("SELECT TOP(1) s.UserID,s.StaffID"
                        + " FROM dbo.StaffProfile s JOIN dbo.[User] u"
                        + " ON u.UserID=s.UserID JOIN dbo.[Role] r"
                        + " ON r.RoleID=u.RoleID WHERE r.RoleCode=:role"
                        + " AND u.Status='ACTIVE'", Map.of("role", role))
                        .getFirst();
                staff.put(role, new CurrentActor(n(s, "UserID"), role,
                        null, n(s, "StaffID")));
            }
            var customerA = customer();
            var customerB = customer();
            long tariff = db.insert("INSERT dbo.ServiceTariff(ServiceCode,"
                    + "OptionCode,CostGroup,UnitCode,UnitPrice,Currency,"
                    + "VersionNo,"
                    + "EffectiveFrom,Status) VALUES ('ROAD_TRANSPORT',:code,"
                    + "'TRANSPORT','KM',2,'USD',1,'20290101','ACTIVE')",
                    Map.of("code", UUID.randomUUID().toString()));
            actor.set(customerA);
            var req = bookingRequest(customerA, tariff, 2);
            var a = bookings.create(req);
            long bookingA = n(a, "BookingID");
            assertThat(n(bookings.create(req), "BookingID"))
                    .isEqualTo(bookingA);
            actor.set(customerB);
            assertThatThrownBy(() -> bookings.get(bookingA))
                    .hasMessageContaining("Booking access required");
            assertThatThrownBy(() -> bookings.create(req))
                    .hasMessageContaining("HORSE_OWNERSHIP_REQUIRED");
            var b = bookings.create(bookingRequest(customerB, tariff, 1));
            long bookingB = n(b, "BookingID");
            actor.set(staff.get("MANAGER"));
            for (long id : List.of(bookingA, bookingB)) {
                bookings.approve(id, new JourneyModels.Approval(bcmd(id),
                        staff.get("SPECIALIST").staffId(),
                        staff.get("COORDINATOR").staffId(), 30, true));
                var pay = new JourneyModels.PaymentRequest(bcmd(id),
                        new BigDecimal("60.00"), UUID.randomUUID().toString());
                bookings.pay(id, pay);
                bookings.pay(id, pay);
                assertThat(bookings.paid(id)).isEqualByComparingTo("60");
                assertThatThrownBy(() -> bookings.pay(id,
                        new JourneyModels.PaymentRequest(bcmd(id),
                                new BigDecimal("60.00"), pay.bankReference())))
                        .hasMessageContaining(
                                "BANK_REFERENCE_ALREADY_RECONCILED");
            }
            long vehicle = db.insert("INSERT dbo.FleetVehicle(VehicleCode,"
                    + "PlateNumber,HorseCapacity,ClimateSystemType,"
                    + "OperationalStatus,DocumentExpiresAt) VALUES"
                    + " (:code,:plate,3,'AIR_RIDE','AVAILABLE','20310101')",
                    Map.of("code", UUID.randomUUID().toString()
                            .substring(0, 20), "plate", UUID.randomUUID()
                                    .toString().substring(0, 12)));
            actor.set(staff.get("COORDINATOR"));
            var trip = trips.create(new JourneyModels.TripRequest(
                    UUID.randomUUID(), "ROAD", vehicle, null, 3,
                    staff.get("DRIVER").staffId(), "VN", "SG",
                    LocalDateTime.parse("2030-01-02T10:00:00"),
                    LocalDateTime.parse("2030-01-03T10:00:00"), List.of("MY")));
            long tripId = n(trip, "TripID");
            var horseIds = db.rows("SELECT BookingHorseID FROM"
                    + " journey.BookingHorse WHERE BookingID IN (:ids)",
                    Map.of("ids", List.of(bookingA, bookingB))).stream()
                    .map(h -> n(h, "BookingHorseID")).toList();
            trips.manifest(tripId, new JourneyModels.ManifestRequest(
                    tcmd(tripId), horseIds));
            trips.transition(tripId, "submit", tcmd(tripId));
            actor.set(staff.get("MANAGER"));
            trips.transition(tripId, "approve", tcmd(tripId));
            var legacyResources = new ResourceRepository(ds,
                    new ObjectMapper().findAndRegisterModules());
            var windowFrom = java.time.OffsetDateTime.parse(
                    "2030-01-02T10:00:00Z");
            var windowTo = java.time.OffsetDateTime.parse(
                    "2030-01-03T10:00:00Z");
            assertThat(legacyResources.candidateSchedules(ResourceKind.VEHICLE,
                    List.of(vehicle), windowFrom, windowTo, 0).get(vehicle))
                    .contains(ResourceBlocker.OVERLAP);
            assertThat(new PlanRepository(ds).driverOverlaps(
                    staff.get("DRIVER").staffId(), windowFrom, windowTo, 0, 0))
                    .isTrue();
            actor.set(staff.get("DRIVER"));
            instant.set(Instant.parse("2030-01-02T10:00:00Z"));
            assertThatThrownBy(() -> trips.transition(tripId, "depart",
                    tcmd(tripId)))
                    .hasMessageContaining("DRIVER_CHECKLIST_REQUIRED");
            trips.safety(tripId, new JourneyModels.SafetyRequest(tcmd(tripId),
                    true, true, true, true));
            assertThatThrownBy(() -> trips.transition(tripId, "depart",
                    tcmd(tripId)))
                    .hasMessageContaining("BALANCE_PAYMENT_REQUIRED");
            actor.set(staff.get("MANAGER"));
            for (long id : List.of(bookingA, bookingB)) {
                bookings.pay(id, new JourneyModels.PaymentRequest(bcmd(id),
                        new BigDecimal("140.00"),
                        UUID.randomUUID().toString()));
            }
            actor.set(staff.get("DRIVER"));
            assertThatThrownBy(() -> trips.transition(tripId, "depart",
                    tcmd(tripId)))
                    .hasMessageContaining("PRE_EXPORT_CLEARANCE_REQUIRED");
            Map<Long, UUID> files = new java.util.HashMap<>();
            boolean rejectedOnce = false;
            for (var owner : List.of(customerA, customerB)) {
                long booking = owner.equals(customerA) ? bookingA : bookingB;
                actor.set(owner);
                for (var r : documents.list(booking)) {
                    long rid = n(r, "RequirementID");
                    var result = documents.submit(rid,
                            document(n(r, "Version")));
                    long reviewVersion = n(r, "Version") + 1;
                    if (!rejectedOnce) {
                        actor.set(staff.get("SPECIALIST"));
                        documents.review(rid, new JourneyModels.ReviewRequest(
                                command(reviewVersion),
                                n(result, "submissionId"), "REJECTED",
                                "Replace expired vaccination record"));
                        actor.set(owner);
                        var feedback = documents.list(booking).stream()
                                .filter(d -> n(d, "RequirementID") == rid)
                                .findFirst().orElseThrow();
                        assertThat(feedback.get("ReviewNotes"))
                                .isEqualTo(
                                        "Replace expired vaccination record");
                        result = documents.submit(rid,
                                document(reviewVersion + 1));
                        reviewVersion += 2;
                        rejectedOnce = true;
                    }
                    files.put(n(r, "BookingHorseID"), UUID.fromString(
                            result.get("fileKey").toString()));
                    actor.set(staff.get("SPECIALIST"));
                    documents.review(rid, new JourneyModels.ReviewRequest(
                            command(reviewVersion),
                            n(result, "submissionId"), "APPROVED", null));
                    actor.set(owner);
                }
            }
            actor.set(staff.get("SPECIALIST"));
            for (long horse : horseIds) {
                long booking = n(access.one("SELECT BookingID FROM"
                        + " journey.BookingHorse WHERE BookingHorseID=:id",
                        Map.of("id", horse)), "BookingID");
                for (String phase : List.of("PRE_EXPORT", "ARRIVAL")) {
                    documents.clear(horse, phase, bcmd(booking));
                }
            }
            actor.set(staff.get("DRIVER"));
            trips.transition(tripId, "depart", tcmd(tripId));
            long first = horseIds.getFirst();
            assertThatThrownBy(() -> trips.deliver(tripId,
                    new JourneyModels.DeliveryRequest(tcmd(tripId), first,
                            "Receiver", files.get(first),
                            LocalDateTime.parse("2030-01-02T10:00:00"))))
                    .hasMessageContaining("QUARANTINE_RELEASE_REQUIRED");
            actor.set(staff.get("SPECIALIST"));
            for (long horse : horseIds) {
                long booking = n(access.one("SELECT BookingID"
                        + " FROM journey.BookingHorse WHERE BookingHorseID=:id",
                        Map.of("id", horse)), "BookingID");
                documents.clear(horse, "QUARANTINE_RELEASE", bcmd(booking));
            }
            actor.set(staff.get("DRIVER"));
            assertThatThrownBy(() -> trips.deliver(tripId,
                    new JourneyModels.DeliveryRequest(tcmd(tripId), first,
                            "Receiver", files.get(first),
                            LocalDateTime.parse("2030-01-02T09:59:00"))))
                    .isInstanceOfSatisfying(
                            OrderException.class, e -> assertThat(
                                    e.getStatus().value()).isEqualTo(422));
            for (long horse : horseIds) {
                trips.deliver(tripId, new JourneyModels.DeliveryRequest(
                        tcmd(tripId), horse, "Test receiver", files.get(horse),
                        LocalDateTime.parse("2030-01-02T10:00:00")));
            }
            actor.set(staff.get("MANAGER"));
            trips.extra(tripId, new JourneyModels.ExtraRequest(tcmd(tripId),
                    bookingA, new BigDecimal("50.00"), "Emergency care"));
            assertThatThrownBy(() -> trips.transition(tripId, "close",
                    tcmd(tripId)))
                    .hasMessageContaining("SETTLEMENT_PAYMENT_REQUIRED");
            bookings.pay(bookingA, new JourneyModels.PaymentRequest(
                    bcmd(bookingA),
                    new BigDecimal("50.00"), UUID.randomUUID().toString()));
            var close = tcmd(tripId);
            assertThat(trips.transition(tripId, "close", close).get("Status"))
                    .isEqualTo("COMPLETED");
            assertThat(trips.transition(tripId, "close", close).get("Status"))
                    .isEqualTo("COMPLETED");
            assertThat(bookings.get(bookingA).get("Status"))
                    .isEqualTo("COMPLETED");
            assertThat(bookings.get(bookingB).get("Status"))
                    .isEqualTo("COMPLETED");
            status.setRollbackOnly();
        });
    }

    private CurrentActor customer() {
        long role = n(db.rows("SELECT RoleID FROM dbo.[Role] WHERE"
                + " RoleCode='CUSTOMER'", Map.of()).getFirst(), "RoleID");
        long user = db.insert("INSERT dbo.[User](RoleID,Email,PasswordHash)"
                + " VALUES (:role,:email,'not-a-login-hash')",
                Map.of("role", role,
                        "email", UUID.randomUUID() + "@journey.test"));
        long id = db.insert("INSERT dbo.Customer(UserID,FullName)"
                + " VALUES (:user,'Journey test')", Map.of("user", user));
        return new CurrentActor(user, "CUSTOMER", id, null);
    }

    private JourneyModels.BookingRequest bookingRequest(CurrentActor owner,
            long tariff, int count) {
        var horses = new ArrayList<Long>();
        for (int i = 0; i < count; i++) {
            horses.add(db.insert("INSERT dbo.Horse(CustomerID,Name)"
                    + " VALUES (:owner,'Journey fixture')",
                    Map.of("owner", owner.customerId())));
        }
        return new JourneyModels.BookingRequest(UUID.randomUUID(), "VN", "SG",
                "Origin", "Destination",
                LocalDateTime.parse("2030-01-02T09:00:00"),
                LocalDateTime.parse("2030-01-02T11:00:00"),
                LocalDateTime.parse("2030-01-04T10:00:00"), horses,
                new JourneyModels.Estimate("ROAD", new BigDecimal("100"), count,
                        List.of(tariff)), List.of("MY"));
    }

    private JourneyModels.DocumentRequest document(long version) {
        return new JourneyModels.DocumentRequest(command(version), "test.pdf",
                "application/pdf", Base64.getEncoder().encodeToString(
                        "%PDF-1.7 test".getBytes(java.nio.charset.
                                StandardCharsets.UTF_8)),
                LocalDateTime.parse("2030-02-01T00:00:00"));
    }

    private JourneyModels.Command bcmd(long id) {
        return command(n(access.booking(id), "Version"));
    }

    private JourneyModels.Command tcmd(long id) {
        return command(n(access.trip(id), "Version"));
    }

    private JourneyModels.Command command(long version) {
        return new JourneyModels.Command(UUID.randomUUID(), version);
    }

    private long n(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
