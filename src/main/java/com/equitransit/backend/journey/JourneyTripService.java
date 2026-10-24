package com.equitransit.backend.journey;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import com.equitransit.backend.exception.OrderException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.equitransit.backend.journey.JourneyAccess.number;
import static com.equitransit.backend.journey.JourneyBookingService.time;
import static com.equitransit.backend.journey.JourneyPolicy.require;

/** Shared-trip manifest and departure/delivery/settlement orchestration. */
@Service
@Transactional
public class JourneyTripService {
    private final JourneyStore db;
    private final JourneyAccess access;
    private final JourneyBookingService bookings;
    private final Clock clock;

    /** @param db SQL @param access identities @param bookings finance
     * @param clock authoritative UTC clock */
    public JourneyTripService(JourneyStore db, JourneyAccess access,
            JourneyBookingService bookings, Clock clock) {
        this.db = db;
        this.access = access;
        this.bookings = bookings;
        this.clock = clock;
    }

    /** @param req resource/time selection @return draft trip */
    public Map<String, Object> create(JourneyModels.TripRequest req) {
        var a = access.role("COORDINATOR");
        return access.operation("TRIP_CREATE", req.operationId(), req, () -> {
            require(req.endsAt().isAfter(req.startsAt())
                    && req.startsAt().isAfter(LocalDateTime.now(clock)),
                    "TRIP_DATES_INVALID");
            bookings.staff(req.driverId(), "DRIVER");
            int capacity = req.capacity();
            if ("ROAD".equals(req.mode())) {
                require(req.vehicleId() != null, "VEHICLE_REQUIRED");
                var v = access.one("SELECT * FROM dbo.FleetVehicle"
                        + " WHERE VehicleID=:id", Map.of("id",
                                req.vehicleId()));
                require(List.of("ACTIVE", "AVAILABLE")
                        .contains(v.get("OperationalStatus")),
                        "VEHICLE_UNAVAILABLE");
                require(v.get("DocumentExpiresAt") != null
                        && time(v, "DocumentExpiresAt").isAfter(req.endsAt()),
                        "VEHICLE_DOCUMENTS_EXPIRED");
                capacity = ((Number) v.get("HorseCapacity")).intValue();
                require(capacity > 0 && capacity <= 100,
                        "CAPACITY_NOT_SUPPORTED");
            } else {
                require(req.supplierId() != null, "AIR_SUPPLIER_REQUIRED");
                access.one("SELECT SupplierID FROM dbo.Supplier"
                        + " WHERE SupplierID=:id AND IsActive=1",
                        Map.of("id", req.supplierId()));
            }
            var p = new HashMap<String, Object>();
            p.put("vehicle", req.vehicleId());
            p.put("supplier", req.supplierId());
            p.put("capacity", capacity);
            p.put("mode", req.mode());
            p.put("driver", req.driverId());
            p.put("coordinator", a.staffId());
            p.put("origin", req.originCountry());
            p.put("dest", req.destinationCountry());
            p.put("start", req.startsAt());
            p.put("end", req.endsAt());
            p.put("transit", access.encode(JourneyPolicy.transit(
                    req.transitCountries(), req.originCountry(),
                    req.destinationCountry())));
            long id = db.insert("INSERT journey.Trip(VehicleID,SupplierID,"
                    + "Capacity,TransportMode,DriverID,CoordinatorID,"
                    + "OriginCountry,DestinationCountry,StartsAt,EndsAt,"
                    + "TransitCountriesJSON)"
                    + " VALUES (:vehicle,:supplier,:capacity,:mode,:driver,"
                    + ":coordinator,:origin,:dest,:start,:end,:transit)", p);
            return get(id);
        });
    }

    /** @param id assigned trip @return manifest for authorized staff only */
    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        var row = new HashMap<>(access.trip(id));
        row.put("manifest", manifest(id));
        return row;
    }

    /** @return assigned staff trip summaries */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        var a = access.role("MANAGER", "COORDINATOR", "DRIVER");
        var p = new HashMap<String, Object>();
        p.put("staff", a.staffId());
        p.put("role", a.roleCode());
        return db.rows("SELECT TOP (100) TripID,Status,StartsAt,EndsAt,Version"
                + " FROM journey.Trip WHERE :role='MANAGER'"
                + " OR (:role='COORDINATOR' AND CoordinatorID=:staff)"
                + " OR (:role='DRIVER' AND DriverID=:staff)"
                + " ORDER BY TripID DESC", p);
    }

    /** @param id trip @param req complete manifest replacement
     * @return updated draft */
    public Map<String, Object> manifest(long id,
            JourneyModels.ManifestRequest req) {
        access.role("COORDINATOR");
        return access.operation("TRIP_MANIFEST:" + id,
                req.command().operationId(), req, () -> {
                    var trip = access.trip(id);
                    access.version(trip, req.command().version());
                    require("DRAFT".equals(trip.get("Status")),
                            "TRIP_NOT_DRAFT");
                    var ids = JourneyPolicy.distinct(req.bookingHorseIds());
                    require(ids.size() <= number(trip, "Capacity"),
                            "CAPACITY_EXCEEDED");
                    var rows = db.rows("SELECT h.*,b.OriginCountry,"
                            + "b.DestinationCountry,b.TransportMode,"
                            + "b.TransitCountriesJSON,b.RouteReviewed,"
                            + "b.PickupFrom,b.PickupTo,b.DeliveryBy,"
                            + "b.Status AS BookingStatus,b.CoordinatorID"
                            + " FROM journey.BookingHorse h JOIN "
                                    + "journey.Booking b"
                            + " ON b.BookingID=h.BookingID"
                            + " WHERE h.BookingHorseID IN (:ids)",
                            Map.of("ids", ids));
                    require(rows.size() == ids.size(),
                            "MANIFEST_HORSE_MISSING");
                    require(rows.stream().map(h -> h.get("HorseID"))
                            .distinct().count() == rows.size(),
                            "PHYSICAL_HORSE_DUPLICATED");
                    for (var horse : rows) {
                        require("APPROVED".equals(horse.get("BookingStatus"))
                                && "PLANNED".equals(horse.get("Status")),
                                "BOOKING_NOT_READY_FOR_PLANNING");
                        require(number(horse, "CoordinatorID")
                                == access.actor().staffId(),
                                "BOOKING_COORDINATOR_REQUIRED");
                        for (String key : List.of("OriginCountry",
                                "DestinationCountry", "TransportMode",
                                "TransitCountriesJSON")) {
                            require(trip.get(key).equals(horse.get(key)),
                                    "ROUTE_MISMATCH");
                        }
                        require(Boolean.TRUE.equals(horse.get("RouteReviewed")),
                                "TRANSIT_ROUTE_REVIEW_REQUIRED");
                        require(!time(trip, "StartsAt").isBefore(
                                time(horse, "PickupFrom"))
                                && !time(trip, "StartsAt").isAfter(
                                        time(horse, "PickupTo"))
                                && !time(trip, "EndsAt").isAfter(
                                        time(horse, "DeliveryBy")),
                                "BOOKING_WINDOW_CONFLICT");
                        require(db.rows("SELECT ManifestID FROM "
                                + "journey.Manifest"
                                + " WHERE BookingHorseID=:horse AND Active=1"
                                + " AND TripID<>:trip", Map.of("horse",
                                        horse.get("BookingHorseID"), "trip",
                                                id))
                                .isEmpty(), "HORSE_ALREADY_ASSIGNED");
                        require(db.rows("SELECT m.ManifestID"
                                + " FROM journey.Manifest m"
                                + " JOIN journey.BookingHorse h ON"
                                + " h.BookingHorseID=m.BookingHorseID"
                                + " WHERE h.HorseID=:horse AND m.Active=1"
                                + " AND m.TripID<>:trip", Map.of("horse",
                                        horse.get("HorseID"), "trip", id))
                                .isEmpty(), "PHYSICAL_HORSE_ALREADY_ASSIGNED");
                    }
                    db.write("DELETE journey.Manifest WHERE TripID=:id",
                            Map.of("id", id));
                    for (int i = 0; i < ids.size(); i++) {
                        db.insert("INSERT "
                                + "journey.Manifest(TripID,BookingHorseID,"
                                + "SlotNo) VALUES (:id,:horse,:slot)",
                                Map.of("id", id, "horse", ids.get(i),
                                        "slot", i + 1));
                    }
                    state(id, "DRAFT");
                    return get(id);
                });
    }

    /** @param id trip @param action lifecycle operation @param cmd retry input
     * @return updated trip */
    public Map<String, Object> transition(long id, String action,
            JourneyModels.Command cmd) {
        String role = switch (action) {
            case "submit", "reopen" -> "COORDINATOR";
            case "approve", "close" -> "MANAGER";
            case "depart" -> "DRIVER";
            default -> throw new IllegalArgumentException("Unknown action.");
        };
        access.role(role);
        return access.operation("TRIP_" + action + ":" + id,
                cmd.operationId(), cmd, () -> {
                    var trip = access.trip(id);
                    access.version(trip, cmd.version());
                    var horses = manifest(id);
                    require(!horses.isEmpty(), "MANIFEST_REQUIRED");
                    if ("reopen".equals(action)) {
                        require(List.of("PENDING_APPROVAL", "SCHEDULED")
                                .contains(trip.get("Status")),
                                "TRIP_CANNOT_REOPEN");
                        db.write("DELETE journey.DepartureCheck"
                                + " WHERE TripID=:id", Map.of("id", id));
                        state(id, "DRAFT");
                        return get(id);
                    }
                    String expected = switch (action) {
                        case "submit" -> "DRAFT";
                        case "approve" -> "PENDING_APPROVAL";
                        case "depart" -> "SCHEDULED";
                        default -> "ARRIVED";
                    };
                    require(expected.equals(trip.get("Status")),
                            "TRIP_STATE_CONFLICT");
                    if (List.of("submit", "approve", "depart")
                            .contains(action)) {
                        resources(trip);
                    }
                    if ("depart".equals(action)) {
                        require(!db.rows("SELECT TripID FROM"
                                + " journey.DepartureCheck WHERE TripID=:id"
                                + " AND DriverID=:driver AND CheckedAt>=:since",
                                Map.of("id", id, "driver", trip.get("DriverID"),
                                        "since", LocalDateTime.now(clock)
                                                .minusHours(24))).isEmpty(),
                                "DRIVER_CHECKLIST_REQUIRED");
                        require(!LocalDateTime.now(clock).isBefore(
                                time(trip, "StartsAt")), "DEPARTURE_TOO_EARLY");
                        for (var h : horses) {
                            long booking = number(h, "BookingID");
                            var b = access.one("SELECT TotalAmount,Status"
                                    + " FROM journey.Booking WHERE "
                                            + "BookingID=:id",
                                    Map.of("id", booking));
                            require("APPROVED".equals(b.get("Status"))
                                    && bookings.paid(booking).compareTo(
                                            (BigDecimal) b.get("TotalAmount"))
                                            >= 0, "BALANCE_PAYMENT_REQUIRED");
                            cleared(number(h, "BookingHorseID"), "PRE_EXPORT");
                        }
                        db.write("UPDATE journey.Trip SET ActualDeparture=:now"
                                + " WHERE TripID=:id", Map.of("id", id,
                                        "now", LocalDateTime.now(clock)));
                        db.write("UPDATE h SET Status='IN_TRANSIT'"
                                + " FROM journey.BookingHorse h JOIN"
                                + " journey.Manifest m ON m.BookingHorseID="
                                + "h.BookingHorseID WHERE m.TripID=:id",
                                Map.of("id", id));
                    }
                    if ("close".equals(action)) {
                        settle(horses, id);
                        db.write("UPDATE journey.Manifest SET Active=0"
                                + " WHERE TripID=:id", Map.of("id", id));
                    }
                    state(id, switch (action) {
                        case "submit" -> "PENDING_APPROVAL";
                        case "approve" -> "SCHEDULED";
                        case "depart" -> "IN_TRANSIT";
                        default -> "COMPLETED";
                    });
                    return get(id);
                });
    }

    /** @param id trip @param req per-horse receiver evidence @return trip */
    public Map<String, Object> deliver(long id,
            JourneyModels.DeliveryRequest req) {
        access.role("DRIVER");
        return access.operation("TRIP_DELIVER:" + id,
                req.command().operationId(), req, () -> {
                    var t = access.trip(id);
                    access.version(t, req.command().version());
                    require("IN_TRANSIT".equals(t.get("Status")),
                            "TRIP_NOT_IN_TRANSIT");
                    if (req.occurredAt().isBefore(time(t, "ActualDeparture"))) {
                        throw new OrderException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "HANDOVER_TIME_BEFORE_DEPARTURE",
                                "Delivery cannot precede departure.");
                    }
                    require(!req.occurredAt().isAfter(LocalDateTime.now(clock)),
                            "HANDOVER_TIME_IN_FUTURE");
                    var h = access.one("SELECT h.* FROM journey.Manifest m"
                            + " JOIN journey.BookingHorse h ON"
                            + " h.BookingHorseID=m.BookingHorseID"
                            + " WHERE m.TripID=:trip AND "
                                    + "m.BookingHorseID=:horse"
                            + " AND m.DeliveredAt IS NULL", Map.of("trip", id,
                                    "horse", req.bookingHorseId()));
                    cleared(req.bookingHorseId(), "ARRIVAL");
                    cleared(req.bookingHorseId(), "QUARANTINE_RELEASE");
                    access.one("SELECT FileKey FROM journey.FileObject"
                            + " WHERE FileKey=:file AND BookingID=:booking",
                            Map.of("file", req.evidenceKey().toString(),
                                    "booking", h.get("BookingID")));
                    db.write("UPDATE journey.Manifest SET DeliveredAt=:at,"
                            + "ReceiverName=:receiver,EvidenceKey=:file"
                            + " WHERE TripID=:id AND BookingHorseID=:horse",
                            Map.of("id", id, "horse", req.bookingHorseId(),
                                    "at", req.occurredAt(), "receiver",
                                    req.receiverName(), "file",
                                    req.evidenceKey().toString()));
                    db.write("UPDATE journey.BookingHorse SET "
                            + "Status='DELIVERED'"
                            + " WHERE BookingHorseID=:id",
                            Map.of("id", req.bookingHorseId()));
                    boolean finished = manifest(id).stream()
                            .allMatch(m -> m.get("DeliveredAt") != null);
                    state(id, finished ? "ARRIVED" : "IN_TRANSIT");
                    return get(id);
                });
    }

    /** @param id trip @param req approved extra expense @return updated trip */
    public Map<String, Object> extra(long id, JourneyModels.ExtraRequest req) {
        var actor = access.role("MANAGER");
        return access.operation("TRIP_EXTRA:" + id,
                req.command().operationId(), req, () -> {
                    var trip = access.trip(id);
                    access.version(trip, req.command().version());
                    require(List.of("IN_TRANSIT", "ARRIVED")
                            .contains(trip.get("Status")),
                                    "TRIP_EXTRA_NOT_ALLOWED");
                    require(req.amount().scale() <= 2, "INVALID_MONEY_SCALE");
                    access.one("SELECT DISTINCT h.BookingID"
                            + " FROM journey.Manifest m JOIN "
                                    + "journey.BookingHorse h"
                            + " ON h.BookingHorseID=m.BookingHorseID"
                            + " WHERE m.TripID=:trip AND h.BookingID=:booking",
                            Map.of("trip", id, "booking", req.bookingId()));
                    db.insert("INSERT journey.ExtraCharge(TripID,BookingID,"
                            + "Amount,Reason,ApprovedBy) VALUES "
                                    + "(:trip,:booking,"
                            + ":amount,:reason,:staff)", Map.of("trip", id,
                                    "booking", req.bookingId(), "amount",
                                    req.amount(), "reason", req.reason(),
                                    "staff", actor.staffId()));
                    db.write("UPDATE journey.Booking SET Version=Version+1,"
                            + "Status=CASE WHEN EXISTS (SELECT 1 FROM"
                            + " journey.BookingHorse h WHERE h.BookingID=:id"
                            + " AND h.Status<>'DELIVERED') THEN Status"
                            + " ELSE 'AWAITING_FINAL_PAYMENT' END"
                            + " WHERE BookingID=:id", Map.of("id",
                                    req.bookingId()));
                    state(id, trip.get("Status").toString());
                    return get(id);
                });
    }

    /** @param id trip @param req driver checklist @return updated trip */
    public Map<String, Object> safety(long id,
            JourneyModels.SafetyRequest req) {
        var actor = access.role("DRIVER");
        return access.operation("TRIP_SAFETY:" + id,
                req.command().operationId(), req, () -> {
                    var trip = access.trip(id);
                    access.version(trip, req.command().version());
                    require("SCHEDULED".equals(trip.get("Status")),
                            "TRIP_NOT_SCHEDULED");
                    require(req.vehicleSafe() && req.waterReady()
                            && req.beddingReady() && req.horsesReceived(),
                            "DRIVER_CHECKLIST_INCOMPLETE");
                    db.write("DELETE journey.DepartureCheck WHERE TripID=:id",
                            Map.of("id", id));
                    db.write("INSERT journey.DepartureCheck(TripID,DriverID,"
                            + "VehicleSafe,WaterReady,BeddingReady,"
                            + "HorsesReceived,CheckedAt) VALUES"
                            + " (:id,:driver,1,1,1,1,:now)", Map.of("id", id,
                                    "driver", actor.staffId(), "now",
                                    LocalDateTime.now(clock)));
                    state(id, "SCHEDULED");
                    return get(id);
                });
    }

    private List<Map<String, Object>> manifest(long id) {
        return db.rows("SELECT m.*,h.BookingID,h.HorseID FROM "
                + "journey.Manifest m"
                + " JOIN journey.BookingHorse h"
                + " ON h.BookingHorseID=m.BookingHorseID WHERE m.TripID=:id"
                + " ORDER BY m.SlotNo", Map.of("id", id));
    }

    private void state(long id, String status) {
        db.write("UPDATE journey.Trip SET Status=:status,Version=Version+1"
                + " WHERE TripID=:id", Map.of("id", id, "status", status));
    }

    private void cleared(long horse, String phase) {
        require(!db.rows("SELECT BookingHorseID FROM journey.Clearance"
                + " WHERE BookingHorseID=:horse AND Phase=:phase"
                + " AND ValidUntil>:now", Map.of("horse", horse, "phase", phase,
                        "now", LocalDateTime.now(clock))).isEmpty(),
                "QUARANTINE_RELEASE".equals(phase)
                        ? "QUARANTINE_RELEASE_REQUIRED"
                        : phase + "_CLEARANCE_REQUIRED");
    }

    private void settle(List<Map<String, Object>> horses, long closingTrip) {
        for (long id : horses.stream().map(h -> number(h, "BookingID"))
                .distinct().sorted().toList()) {
            var b = access.one("SELECT TotalAmount FROM journey.Booking"
                    + " WHERE BookingID=:id", Map.of("id", id));
            require(bookings.paid(id).compareTo(((BigDecimal)
                    b.get("TotalAmount")).add(bookings.extras(id))) >= 0,
                    "SETTLEMENT_PAYMENT_REQUIRED");
            boolean otherTripsClosed = db.rows("SELECT t.TripID"
                    + " FROM journey.Trip t JOIN journey.Manifest m"
                    + " ON m.TripID=t.TripID JOIN journey.BookingHorse h"
                    + " ON h.BookingHorseID=m.BookingHorseID"
                    + " WHERE h.BookingID=:id AND t.TripID<>:trip"
                    + " AND t.Status NOT IN ('COMPLETED','CANCELLED')",
                    Map.of("id", id, "trip", closingTrip)).isEmpty();
            if (bookings.allDelivered(id) && otherTripsClosed) {
                db.write("UPDATE journey.Booking SET Status='COMPLETED',"
                        + "Version=Version+1 WHERE BookingID=:id",
                        Map.of("id", id));
            }
        }
    }

    private void resources(Map<String, Object> t) {
        bookings.staff(number(t, "DriverID"), "DRIVER");
        var horses = manifest(number(t, "TripID"));
        require(!horses.isEmpty(), "MANIFEST_REQUIRED");
        if (t.get("VehicleID") != null) {
            var v = access.one("SELECT * FROM dbo.FleetVehicle"
                    + " WHERE VehicleID=:id", Map.of("id", t.get("VehicleID")));
            require(number(v, "HorseCapacity") >= horses.size(),
                    "CAPACITY_EXCEEDED");
            require(List.of("ACTIVE", "AVAILABLE")
                    .contains(v.get("OperationalStatus")),
                    "VEHICLE_UNAVAILABLE");
            require(v.get("DocumentExpiresAt") != null
                    && time(v, "DocumentExpiresAt").isAfter(time(t, "EndsAt")),
                    "VEHICLE_DOCUMENTS_EXPIRED");
            require(v.get("MaintenanceFrom") == null
                    || !time(v, "MaintenanceFrom").isBefore(time(t, "EndsAt"))
                    || (v.get("MaintenanceTo") != null
                        && !time(v, "MaintenanceTo")
                                .isAfter(time(t, "StartsAt"))),
                    "VEHICLE_MAINTENANCE_CONFLICT");
        }
        var p = new HashMap<String, Object>();
        p.put("id", t.get("TripID"));
        p.put("vehicle", t.get("VehicleID"));
        p.put("driver", t.get("DriverID"));
        p.put("start", t.get("StartsAt"));
        p.put("end", t.get("EndsAt"));
        require(db.rows("SELECT TripID FROM journey.Trip WHERE TripID<>:id"
                + " AND Status IN ('PENDING_APPROVAL','SCHEDULED','IN_TRANSIT')"
                + " AND (VehicleID=:vehicle OR DriverID=:driver)"
                + " AND StartsAt<:end AND EndsAt>:start", p).isEmpty(),
                "TRIP_RESOURCE_CONFLICT");
        require(db.rows("SELECT l.LegID FROM dbo.TripLeg l"
                + " JOIN dbo.TransportPlan p ON p.PlanID=l.PlanID"
                + " LEFT JOIN dbo.TripAssignment a ON a.LegID=l.LegID"
                + " WHERE (l.VehicleID=:vehicle OR a.StaffID=:driver)"
                + " AND l.LegStatus NOT IN ('CANCELLED','CANCELED','REJECTED')"
                + " AND p.PlanStatus NOT IN"
                + " ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')"
                + " AND l.PlannedDeparture<:end AND l.PlannedArrival>:start", p)
                .isEmpty(), "LEGACY_RESOURCE_CONFLICT");
        require(db.rows("SELECT d.HorseID FROM journey.Manifest m"
                + " JOIN journey.BookingHorse h"
                + " ON h.BookingHorseID=m.BookingHorseID"
                + " JOIN dbo.TransportOrderDetail d ON d.HorseID=h.HorseID"
                + " JOIN dbo.TransportOrder o ON o.OrderID=d.OrderID"
                + " JOIN dbo.TransportPlan p ON p.PlanID=o.CurrentPlanID"
                + " WHERE m.TripID=:id AND p.PlanStatus IN"
                + " ('CONFIRMED','ACTIVE','IN_TRANSIT')"
                + " AND (p.PlannedStartTime IS NULL OR p.PlannedEndTime IS NULL"
                + " OR (p.PlannedStartTime<:end AND p.PlannedEndTime>:start))",
                p).isEmpty(), "LEGACY_HORSE_CONFLICT");
    }
}
