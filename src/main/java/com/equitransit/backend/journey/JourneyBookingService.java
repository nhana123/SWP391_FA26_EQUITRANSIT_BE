package com.equitransit.backend.journey;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.equitransit.backend.journey.JourneyAccess.number;
import static com.equitransit.backend.journey.JourneyPolicy.require;

/** Multi-horse booking, tariff snapshot and reconciled financial lifecycle. */
@Service
@Transactional
public class JourneyBookingService {
    private final JourneyStore db;
    private final JourneyAccess access;
    private final Clock clock;

    /**
     * @param db SQL store @param access authorization @param clock UTC clock
     */
    public JourneyBookingService(JourneyStore db, JourneyAccess access,
            Clock clock) {
        this.db = db;
        this.access = access;
        this.clock = clock;
    }

    /** @param req pricing selections @return estimate, not an issued quote */
    @Transactional(readOnly = true)
    public Map<String, Object> estimate(JourneyModels.Estimate req) {
        access.role("CUSTOMER", "MANAGER", "COORDINATOR");
        var rates = db.rows("SELECT TariffID,ServiceCode,UnitCode,UnitPrice"
                + " FROM dbo.ServiceTariff WHERE TariffID IN (:ids)"
                + " AND Currency='USD' AND Status='ACTIVE'"
                + " AND EffectiveFrom<=:now"
                + " AND (EffectiveTo IS NULL OR EffectiveTo>:now)",
                Map.of("ids", JourneyPolicy.distinct(req.tariffIds()),
                        "now", LocalDateTime.now(clock)));
        return JourneyPolicy.price(rates, req);
    }

    /** @return active retail rates only, never supplier purchase prices */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> tariffs() {
        access.role("CUSTOMER", "MANAGER", "COORDINATOR");
        return db.rows("SELECT TOP(500) TariffID,ServiceCode,OptionCode,"
                + "UnitCode,UnitPrice,Currency FROM dbo.ServiceTariff"
                + " WHERE Status='ACTIVE' AND Currency='USD'"
                + " AND EffectiveFrom<=:now"
                + " AND (EffectiveTo IS NULL OR EffectiveTo>:now)"
                + " ORDER BY ServiceCode,OptionCode,TariffID",
                Map.of("now", LocalDateTime.now(clock)));
    }

    /** @param req many owned horses and shared journey @return saved booking */
    public Map<String, Object> create(JourneyModels.BookingRequest req) {
        var actor = access.role("CUSTOMER");
        return access.operation("BOOK_CREATE", req.operationId(), req, () -> {
            var ids = JourneyPolicy.distinct(req.horseIds());
            require(ids.size() == req.estimate().horseCount(),
                    "HORSE_COUNT_MISMATCH");
            require(!req.pickupTo().isBefore(req.pickupFrom())
                    && req.deliveryBy().isAfter(req.pickupTo())
                    && req.pickupFrom().isAfter(LocalDateTime.now(clock)),
                    "BOOKING_DATES_INVALID");
            var horses = db.rows("SELECT * FROM dbo.Horse WITH (HOLDLOCK)"
                    + " WHERE HorseID IN (:ids) AND CustomerID=:owner",
                    Map.of("ids", ids, "owner", actor.customerId()));
            require(horses.size() == ids.size(), "HORSE_OWNERSHIP_REQUIRED");
            var price = estimate(req.estimate());
            var total = (BigDecimal) price.get("total");
            var args = new HashMap<String, Object>();
            args.put("owner", actor.customerId());
            args.put("origin", req.originCountry());
            args.put("dest", req.destinationCountry());
            args.put("fromAddress", req.originAddress());
            args.put("toAddress", req.destinationAddress());
            args.put("from", req.pickupFrom());
            args.put("to", req.pickupTo());
            args.put("by", req.deliveryBy());
            args.put("km", req.estimate().distanceKm());
            args.put("mode", req.estimate().mode());
            args.put("total", total);
            args.put("deposit", total.multiply(new BigDecimal("0.30"))
                    .setScale(2, RoundingMode.HALF_UP));
            args.put("price", access.encode(price));
            args.put("transit", access.encode(JourneyPolicy.transit(
                    req.transitCountries(), req.originCountry(),
                    req.destinationCountry())));
            long id = db.insert("INSERT journey.Booking(CustomerID,"
                    + "OriginCountry,DestinationCountry,OriginAddress,"
                    + "DestinationAddress,PickupFrom,PickupTo,DeliveryBy,"
                    + "DistanceKm,TransportMode,Currency,TotalAmount,"
                    + "DepositAmount,PriceSnapshot,TransitCountriesJSON)"
                    + " VALUES (:owner,:origin,"
                    + ":dest,:fromAddress,:toAddress,:from,:to,:by,:km,:mode,"
                    + "'USD',:total,:deposit,:price,:transit)", args);
            for (var horse : horses) {
                long bh = db.insert("INSERT journey.BookingHorse(BookingID,"
                        + "HorseID,HorseSnapshot) VALUES "
                                + "(:id,:horse,:snapshot)",
                        Map.of("id", id, "horse", horse.get("HorseID"),
                                "snapshot", access.encode(horse)));
                seedRequirements(bh, req);
            }
            return get(id);
        });
    }

    /** @param id booking key @return owned or assigned detail */
    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        var row = new HashMap<>(access.booking(id));
        row.put("horses", db.rows("SELECT BookingHorseID,HorseID,Status,"
                + "HorseSnapshot FROM journey.BookingHorse WHERE BookingID=:id"
                + " ORDER BY BookingHorseID", Map.of("id", id)));
        row.put("paidAmount", paid(id));
        row.put("extraAmount", extras(id));
        row.put("tracking", db.rows("SELECT h.BookingHorseID,t.TripID,"
                + "t.Status,t.StartsAt,t.EndsAt,m.DeliveredAt,m.ReceiverName"
                + " FROM journey.BookingHorse h JOIN journey.Manifest m"
                + " ON m.BookingHorseID=h.BookingHorseID JOIN journey.Trip t"
                + " ON t.TripID=m.TripID WHERE h.BookingID=:id",
                Map.of("id", id)));
        return row;
    }

    /** @return current customer's bookings or assigned staff queue */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        var a = access.role("CUSTOMER", "MANAGER", "COORDINATOR", "SPECIALIST");
        var args = new HashMap<String, Object>();
        args.put("owner", a.customerId());
        args.put("staff", a.staffId());
        args.put("role", a.roleCode());
        return db.rows("SELECT TOP (100) BookingID,Status,TotalAmount,"
                + "DepositAmount,Version FROM journey.Booking WHERE "
                + "(:role='CUSTOMER' AND CustomerID=:owner) OR :role='MANAGER'"
                + " OR (:role='COORDINATOR' AND CoordinatorID=:staff)"
                + " OR (:role='SPECIALIST' AND SpecialistID=:staff)"
                + " ORDER BY BookingID DESC", args);
    }

    /** @param id booking key @param req assignments @return approved receipt */
    public Map<String, Object> approve(long id, JourneyModels.Approval req) {
        access.role("MANAGER");
        return access.operation("BOOKING_APPROVE:" + id,
                req.command().operationId(), req, () -> {
                    var row = access.booking(id);
                    access.version(row, req.command().version());
                    require("SUBMITTED".equals(row.get("Status")),
                            "BOOKING_NOT_SUBMITTED");
                    require(req.routeReviewed(),
                            "TRANSIT_ROUTE_REVIEW_REQUIRED");
                    staff(req.specialistId(), "SPECIALIST");
                    staff(req.coordinatorId(), "COORDINATOR");
                    var total = (BigDecimal) row.get("TotalAmount");
                    var deposit = total.multiply(BigDecimal.valueOf(
                            req.depositPercent())).divide(new BigDecimal("100"),
                                    2, RoundingMode.HALF_UP);
                    require(deposit.signum() > 0, "DEPOSIT_TOO_SMALL");
                    db.write("UPDATE journey.Booking SET SpecialistID=:s,"
                            + "CoordinatorID=:c,DepositAmount=:deposit,"
                            + "RouteReviewed=1,"
                            + "Status='AWAITING_DEPOSIT',Version=Version+1"
                            + " WHERE BookingID=:id", Map.of("id", id,
                                    "s", req.specialistId(),
                                    "c", req.coordinatorId(),
                                    "deposit", deposit));
                    return get(id);
                });
    }

    /** @param id booking key @param req reconciled bank receipt
     * @return updated financial state */
    public Map<String, Object> pay(long id, JourneyModels.PaymentRequest req) {
        var a = access.role("MANAGER");
        return access.operation("BOOKING_PAYMENT:" + id,
                req.command().operationId(), req, () -> {
                    var row = access.booking(id);
                    access.version(row, req.command().version());
                    require(List.of("AWAITING_DEPOSIT", "APPROVED",
                            "AWAITING_FINAL_PAYMENT")
                            .contains(row.get("Status")),
                            "BOOKING_NOT_PAYABLE");
                    var due = ((BigDecimal) row.get("TotalAmount"))
                            .add(extras(id)).subtract(paid(id));
                    require(req.amount().scale() <= 2
                            && req.amount().compareTo(due) <= 0,
                            "PAYMENT_EXCEEDS_OBLIGATION");
                    require(db.rows("SELECT PaymentID FROM journey.Payment"
                            + " WHERE BankReference=:ref", Map.of("ref",
                                    req.bankReference())).isEmpty(),
                            "BANK_REFERENCE_ALREADY_RECONCILED");
                    db.insert("INSERT journey.Payment(BookingID,Amount,"
                            + "BankReference,ReconciledBy) VALUES (:id,:amount,"
                            + ":ref,:staff)", Map.of("id", id, "amount",
                                    req.amount(), "ref", req.bankReference(),
                                    "staff", a.staffId()));
                    var cumulative = paid(id);
                    String status = cumulative.compareTo((BigDecimal)
                            row.get("DepositAmount")) >= 0 ? "APPROVED"
                                    : "AWAITING_DEPOSIT";
                    if (allDelivered(id)) {
                        status = cumulative.compareTo(((BigDecimal)
                                row.get("TotalAmount")).add(extras(id))) >= 0
                                        ? "APPROVED"
                                        : "AWAITING_FINAL_PAYMENT";
                    }
                    db.write("UPDATE journey.Booking SET Status=:status,"
                            + "Version=Version+1 WHERE BookingID=:id",
                            Map.of("id", id, "status", status));
                    return get(id);
                });
    }

    /** @param id booking key @return total reconciled receipts */
    public BigDecimal paid(long id) {
        return (BigDecimal) access.one("SELECT COALESCE(SUM(Amount),0) AS N"
                + " FROM journey.Payment WHERE BookingID=:id",
                Map.of("id", id)).get("N");
    }

    /** @param id booking key @return manager-approved customer extras */
    public BigDecimal extras(long id) {
        return (BigDecimal) access.one("SELECT COALESCE(SUM(Amount),0) AS N"
                + " FROM journey.ExtraCharge WHERE BookingID=:id",
                Map.of("id", id)).get("N");
    }

    /** @param id booking key @return every horse has delivery evidence */
    public boolean allDelivered(long id) {
        return number(access.one("SELECT COUNT(*) AS N"
                + " FROM journey.BookingHorse WHERE BookingID=:id"
                + " AND Status<>'DELIVERED'", Map.of("id", id)), "N") == 0;
    }

    /** @param id selected staff key @param role required live role */
    public void staff(long id, String role) {
        var row = access.one("SELECT s.StaffID FROM dbo.StaffProfile s"
                + " JOIN dbo.[User] u ON u.UserID=s.UserID"
                + " JOIN dbo.[Role] r ON r.RoleID=u.RoleID"
                + " WHERE s.StaffID=:id AND u.Status='ACTIVE'"
                + " AND s.StaffStatus='ACTIVE'"
                + " AND r.RoleCode=:role", Map.of("id", id, "role", role));
        require(number(row, "StaffID") == id, "STAFF_ROLE_REQUIRED");
    }

    /** @param row database row @param key timestamp column @return UTC time */
    public static LocalDateTime time(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Timestamp t ? t.toLocalDateTime()
                : (LocalDateTime) value;
    }

    private void seedRequirements(long horse,
            JourneyModels.BookingRequest req) {
        var transit = JourneyPolicy.transit(req.transitCountries(),
                req.originCountry(), req.destinationCountry());
        var rules = Map.of("PRE_EXPORT", List.of("EXPORT_HEALTH", "PASSPORT"),
                "ARRIVAL", List.of("IMPORT_CUSTOMS"), "QUARANTINE_RELEASE",
                List.of("QUARANTINE_RELEASE_CERTIFICATE"));
        rules.forEach((phase, defaults) -> {
            var configured = db.rows("SELECT DISTINCT DocumentType,"
                    + "TransitCountryCode"
                    + " FROM dbo.ComplianceRuleVersion WHERE"
                    + " OriginCountryCode=:origin"
                    + " AND DestinationCountryCode=:dest"
                    + " AND PhaseCode=:phase AND (TransitCountryCode IS NULL"
                    + " OR TransitCountryCode IN (:transit))"
                    + " AND EffectiveFrom<=:start"
                    + " AND (EffectiveTo IS NULL OR EffectiveTo>:end)",
                    Map.of("origin", req.originCountry(), "dest",
                            req.destinationCountry(), "phase", phase,
                            "start", req.pickupFrom(),
                            "end", req.deliveryBy(),
                            "transit", transit.isEmpty()
                                    ? List.of("--") : transit));
            var types = new java.util.LinkedHashSet<>(configured.isEmpty()
                    ? defaults : configured.stream().map(r -> {
                        String type = r.get("DocumentType").toString();
                        return r.get("TransitCountryCode") == null ? type
                                : type.substring(0, Math.min(56, type.length()))
                                    + "_TR_" + r.get("TransitCountryCode");
                    }).toList());
            if ("PRE_EXPORT".equals(phase)) {
                for (var country : transit) {
                    if (configured.stream().noneMatch(r ->
                            country.equals(r.get("TransitCountryCode")))) {
                        types.add("TRANSIT_PERMIT_" + country);
                    }
                }
            }
            types.forEach(type -> db.insert("INSERT journey.Requirement"
                    + "(BookingHorseID,Phase,TypeCode)"
                    + " VALUES (:horse,:phase,:type)", Map.of("horse", horse,
                            "phase", phase, "type", type)));
        });
    }
}
