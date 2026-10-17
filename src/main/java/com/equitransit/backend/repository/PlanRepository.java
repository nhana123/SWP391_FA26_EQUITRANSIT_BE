/*
 * PlanRepository
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized JDBC adapter for F10 multimodal operational plans.
 */
package com.equitransit.backend.repository;

import com.equitransit.backend.dto.plan.PlanModels.AssignmentState;
import com.equitransit.backend.dto.plan.PlanModels.CarrierAcceptanceState;
import com.equitransit.backend.dto.plan.PlanModels.GateState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationView;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyView;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegInput;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegView;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationView;
import com.equitransit.backend.dto.plan.PlanModels.PlanRoadAssignmentRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentView;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.PlanSummaryView;
import com.equitransit.backend.dto.plan.PlanModels.PlanTripAssignmentView;
import com.equitransit.backend.dto.plan.PlanModels.ReservationState;
import com.equitransit.backend.dto.plan.PlanModels.StallSharing;
import com.equitransit.backend.dto.plan.PlanModels.TripRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized SQL adapter; all writes follow the parent order lock. */
@Repository
public class PlanRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param source shared transactional datasource */
    public PlanRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        this.jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return whether migration 011 and its triggers are deployed */
    public boolean ready() {
        return jdbc.getJdbcTemplate().queryForObject("SELECT CASE WHEN "
                + "OBJECT_ID('dbo.PlanOperation','U') IS NOT NULL AND "
                + "(SELECT COUNT(*) FROM sys.triggers WHERE name IN "
                + "('TR_Plan_DriverSchedule','TR_Resource_PlanActivation') "
                + "AND is_disabled=0)=2 AND EXISTS (SELECT 1 FROM "
                + "sys.check_constraints WHERE name='CK_PlanOperation_JSON' "
                + "AND is_disabled=0 AND is_not_trusted=0) THEN 1 ELSE 0 END",
                Integer.class) == 1;
    }

    /** @param order parent @param user actor @param op UUID @return replay */
    public List<Map<String, Object>> operation(long order, long user, UUID op) {
        return jdbc.queryForList("SELECT RequestHash,ReceiptJSON FROM "
                + "dbo.PlanOperation WHERE OrderID=:order AND UserID=:user "
                + "AND OperationID=:op", Map.of("order", order, "user", user,
                        "op", op.toString()));
    }

    /** @param order parent @param user actor @param op UUID @param hash input
     * @param receipt encoded JSON */
    public void saveOperation(long order, long user, UUID op, String hash,
            String receipt) {
        jdbc.update("INSERT dbo.PlanOperation (OrderID,UserID,OperationID,"
                + "RequestHash,ReceiptJSON) VALUES (:order,:user,:op,:hash,"
                + ":receipt)", Map.of("order", order, "user", user,
                        "op", op.toString(), "hash", hash, "receipt", receipt));
    }

    /** @param revision revision ID @return next version number */
    public int nextVersion(long revision) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(VersionNo),0)+1 FROM "
                + "dbo.TransportPlan WITH (UPDLOCK,HOLDLOCK) "
                + "WHERE RevisionID=:rev", Map.of("rev", revision),
                Integer.class);
    }

    /** @param order parent @param revision revision @param staff creator
     * @param version version @param start min start @param end max end
     * @return generated PlanID */
    public long insertPlan(long order, long revision, long staff, int version,
            OffsetDateTime start, OffsetDateTime end) {
        var args = new MapSqlParameterSource("order", order)
                .addValue("rev", revision).addValue("staff", staff)
                .addValue("ver", version).addValue("start", utc(start))
                .addValue("end", utc(end));

        return jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids TABLE "
                + "(ID BIGINT); INSERT dbo.TransportPlan (OrderID,RevisionID,"
                + "CreatedBy,VersionNo,PlannedStartTime,PlannedEndTime,"
                + "FinancialGateStatus,OperationalGateStatus,DocGateStatus,"
                + "PlanStatus) OUTPUT INSERTED.PlanID INTO @ids VALUES "
                + "(:order,:rev,:staff,:ver,:start,:end,'PENDING','PENDING',"
                + "'PENDING','DRAFT'); SELECT ID FROM @ids", args, Long.class);
    }

    /** @param plan plan ID @param leg leg input @param staff distance confirmer
     * @param now server UTC @return generated LegID */
    public long insertLeg(long plan, PlanLegInput leg, Long staff,
            OffsetDateTime now) {
        boolean sourced = leg.distanceSource() != null;
        var args = new MapSqlParameterSource("plan", plan)
                .addValue("seq", leg.sequenceNo())
                .addValue("mode", leg.transportMode().name())
                .addValue("start", leg.startPoint().trim())
                .addValue("end", leg.endPoint().trim())
                .addValue("km", leg.distanceKm().setScale(3,
                        RoundingMode.HALF_UP))
                .addValue("dep", utc(leg.plannedDeparture()))
                .addValue("arr", utc(leg.plannedArrival()))
                .addValue("stz", leg.startTimezone())
                .addValue("etz", leg.endTimezone())
                .addValue("sup", leg.supplierId())
                .addValue("flt", leg.plannedFlightNumber())
                .addValue("conn", leg.connectionBufferMinutes())
                .addValue("rest", leg.restDurationMinutes())
                .addValue("care", leg.careInstructions())
                .addValue("src", sourced ? leg.distanceSource().trim() : null)
                .addValue("by", sourced ? staff : null)
                .addValue("at", sourced ? utc(now) : null);

        return jdbc.queryForObject("SET NOCOUNT ON; DECLARE @ids TABLE "
                + "(ID BIGINT); INSERT dbo.TripLeg (PlanID,SequenceNo,"
                + "TransportMode,StartPoint,EndPoint,DistanceKm,"
                + "PlannedDeparture,PlannedArrival,LegStatus,StartTimezone,"
                + "EndTimezone,SupplierID,PlannedFlightNumber,"
                + "ConnectionBufferMinutes,RestDurationMinutes,"
                + "CareInstructions,DistanceSource,DistanceConfirmedBy,"
                + "DistanceConfirmedAt) OUTPUT INSERTED.LegID INTO @ids VALUES "
                + "(:plan,:seq,:mode,:start,:end,:km,:dep,:arr,'PLANNED',:stz,"
                + ":etz,:sup,:flt,:conn,:rest,:care,:src,:by,:at); "
                + "SELECT ID FROM @ids", args, Long.class);
    }

    /** @param order parent @param plan current plan ID */
    public void setCurrentPlan(long order, long plan) {
        jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=:plan,"
                + "UpdatedAt=SYSUTCDATETIME() WHERE OrderID=:order",
                Map.of("plan", plan, "order", order));
    }

    /** @param order parent order ID */
    public void touchOrder(long order) {
        jdbc.update("UPDATE dbo.TransportOrder SET UpdatedAt=SYSUTCDATETIME() "
                + "WHERE OrderID=:order", Map.of("order", order));
    }

    /** @param order parent @return currentPlanId or null */
    public Long currentPlanId(long order) {
        var rows = jdbc.query("SELECT CurrentPlanID FROM dbo.TransportOrder "
                + "WHERE OrderID=:order", Map.of("order", order),
                (r, i) -> number(r, "CurrentPlanID"));

        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** @param order parent @param plan plan ID @return summary or empty */
    public Optional<PlanSummaryView> findPlan(long order, long plan) {
        Long current = currentPlanId(order);

        return jdbc.query("SELECT * FROM dbo.TransportPlan WITH (UPDLOCK,"
                + "HOLDLOCK) WHERE OrderID=:order AND PlanID=:plan",
                Map.of("order", order, "plan", plan),
                (r, i) -> summary(r, current)).stream().findFirst();
    }

    /** @param order parent @param page zero-based @param size bounded
     * @return list of summaries */
    public List<PlanSummaryView> listPlans(long order, int page, int size) {
        Long current = currentPlanId(order);

        return jdbc.query("SELECT * FROM dbo.TransportPlan WHERE OrderID=:order"
                + " ORDER BY VersionNo DESC, PlanID DESC OFFSET :offset ROWS "
                + "FETCH NEXT :size ROWS ONLY",
                Map.of("order", order, "offset", (long) page * size,
                        "size", size),
                (r, i) -> summary(r, current));
    }

    /** @param order parent @return count of plans */
    public long countPlans(long order) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.TransportPlan "
                + "WHERE OrderID=:order", Map.of("order", order), Long.class);
    }

    /** @param plan plan ID @return ordered legs with nested assignments */
    public List<PlanLegView> legs(long plan) {
        var staffMap = jdbc.query("SELECT a.* FROM dbo.TripAssignment a "
                + "JOIN dbo.TripLeg l ON l.LegID=a.LegID WHERE l.PlanID=:plan "
                + "ORDER BY a.AssignmentID", Map.of("plan", plan),
                (r, i) -> new PlanTripAssignmentView(
                        r.getLong("AssignmentID"), r.getLong("LegID"),
                        r.getLong("StaffID"),
                        TripRole.valueOf(r.getString("RoleInTrip")),
                        time(r, "AssignedFrom"), time(r, "AssignedTo"),
                        AssignmentState.valueOf(r.getString("Status")),
                        (Integer) r.getObject("BufferMinutes"),
                        r.getString("ConflictNotes")));
        var stallMap = jdbc.query("SELECT a.* FROM dbo.StallAssignment a "
                + "JOIN dbo.TripLeg l ON l.LegID=a.LegID WHERE l.PlanID=:plan "
                + "ORDER BY a.StallAssignmentID", Map.of("plan", plan),
                (r, i) -> new PlanStallAssignmentView(
                        r.getLong("StallAssignmentID"), r.getLong("LegID"),
                        r.getLong("StallID"), r.getBigDecimal("SlotQuantity"),
                        StallSharing.valueOf(r.getString("SharingCode")),
                        r.getString("FitStatus"),
                        CarrierAcceptanceState.valueOf(r.getString(
                                "CarrierAcceptanceStatus")),
                        time(r, "AssignedFrom"), time(r, "AssignedTo"),
                        AssignmentState.valueOf(r.getString("Status")),
                        r.getString("PositionDescription"),
                        r.getString("CarrierApprovalFileKey"),
                        time(r, "CarrierApprovedAt"),
                        r.getString("CarrierContact")));
        var flightMap = jdbc.query("SELECT f.* FROM dbo.FlightReservation f "
                + "JOIN dbo.TripLeg l ON l.LegID=f.LegID WHERE l.PlanID=:plan "
                + "ORDER BY f.ReservationID", Map.of("plan", plan),
                (r, i) -> new PlanFlightReservationView(
                        r.getLong("ReservationID"), r.getLong("LegID"),
                        r.getLong("OfferID"), r.getString("AirlineName"),
                        r.getString("FlightNumber"),
                        r.getString("ReservationReference"),
                        ReservationState.valueOf(r.getString("Status")),
                        time(r, "ConfirmedAt"), r.getString("EvidenceFileKey"),
                        time(r, "ExpiresAt"), number(r, "ConfirmedBy"),
                        r.getString("SupplierContact"),
                        r.getBigDecimal("UsableCapacitySlots")));

        return jdbc.query("SELECT * FROM dbo.TripLeg WHERE PlanID=:plan "
                + "ORDER BY SequenceNo", Map.of("plan", plan), (r, i) -> {
                    long legId = r.getLong("LegID");

                    return new PlanLegView(legId, r.getLong("PlanID"),
                            number(r, "VehicleID"), r.getInt("SequenceNo"),
                            PlanLegMode.valueOf(r.getString("TransportMode")),
                            r.getString("StartPoint"), r.getString("EndPoint"),
                            r.getBigDecimal("DistanceKm"),
                            time(r, "PlannedDeparture"),
                            time(r, "PlannedArrival"),
                            LegState.valueOf(r.getString("LegStatus")),
                            r.getString("StartTimezone"),
                            r.getString("EndTimezone"),
                            number(r, "SupplierID"),
                            r.getString("PlannedFlightNumber"),
                            (Integer) r.getObject("ConnectionBufferMinutes"),
                            (Integer) r.getObject("RestDurationMinutes"),
                            r.getString("CareInstructions"),
                            r.getString("DistanceSource"),
                            number(r, "DistanceConfirmedBy"),
                            time(r, "DistanceConfirmedAt"),
                            staffMap.stream().filter(s -> s.legId() == legId)
                                    .toList(),
                            stallMap.stream().filter(s -> s.legId() == legId)
                                    .toList(),
                            flightMap.stream().filter(f -> f.legId() == legId)
                                    .toList());
                });
    }

    /** @param leg leg ID @param req road assignment @param start window start
     * @param end window end */
    public void assignRoad(long leg, PlanRoadAssignmentRequest req,
            OffsetDateTime start, OffsetDateTime end) {
        jdbc.update("UPDATE dbo.TripAssignment SET Status='CANCELLED' "
                + "WHERE LegID=:leg AND RoleInTrip='DRIVER' "
                + "AND Status NOT IN ('CANCELLED','CANCELED','INACTIVE')",
                Map.of("leg", leg));
        jdbc.update("UPDATE dbo.TripLeg SET VehicleID=:veh WHERE LegID=:leg",
                Map.of("veh", req.vehicleId(), "leg", leg));
        var args = new MapSqlParameterSource("leg", leg)
                .addValue("staff", req.driverStaffId())
                .addValue("from", utc(start)).addValue("to", utc(end))
                .addValue("buf", req.driverBufferMinutes())
                .addValue("notes", req.conflictNotes());

        jdbc.update("INSERT dbo.TripAssignment (LegID,StaffID,RoleInTrip,"
                + "AssignedFrom,AssignedTo,Status,BufferMinutes,ConflictNotes) "
                + "VALUES (:leg,:staff,'DRIVER',:from,:to,'CONFIRMED',:buf,"
                + ":notes)", args);
    }

    /** @param staff driver StaffID @param start window start @param end end
     * @param buffer buffer minutes @param excludeLeg leg to exclude
     * @return true if driver has an overlapping active assignment */
    public boolean driverOverlaps(long staff, OffsetDateTime start,
            OffsetDateTime end, int buffer, long excludeLeg) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TripAssignment a "
                + "WITH (UPDLOCK,HOLDLOCK) JOIN dbo.TripLeg l "
                + "ON l.LegID=a.LegID JOIN dbo.TransportPlan p "
                + "ON p.PlanID=l.PlanID WHERE a.StaffID=:staff "
                + "AND a.LegID<>:exclude AND a.Status NOT IN "
                + "('CANCELLED','CANCELED','INACTIVE') AND p.PlanStatus NOT IN "
                + "('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND "
                + "(a.AssignedFrom IS NULL OR a.AssignedTo IS NULL OR "
                + "a.BufferMinutes IS NULL OR ("
                + ":start<DATEADD(minute,CASE WHEN :buf>=a.BufferMinutes "
                + "THEN :buf ELSE a.BufferMinutes END,a.AssignedTo) AND "
                + "a.AssignedFrom<DATEADD(minute,CASE WHEN :buf>="
                + "a.BufferMinutes THEN :buf ELSE a.BufferMinutes END,:end)))",
                Map.of("staff", staff, "exclude", excludeLeg,
                        "start", utc(start), "end", utc(end), "buf", buffer),
                Integer.class) > 0;
    }

    /** @param leg leg ID @param req stall request @param slots slot quantity
     * @param sharing sharing code @param fit fit status @param start start
     * @param end end @param now server UTC */
    public void assignStall(long leg, PlanStallAssignmentRequest req,
            BigDecimal slots, String sharing, String fit, OffsetDateTime start,
            OffsetDateTime end, OffsetDateTime now) {
        jdbc.update("UPDATE dbo.StallAssignment SET Status='CANCELLED' "
                + "WHERE LegID=:leg AND Status NOT IN "
                + "('CANCELLED','CANCELED','INACTIVE')", Map.of("leg", leg));
        boolean accepted = req.carrierAcceptanceStatus()
                == CarrierAcceptanceState.ACCEPTED;
        var args = new MapSqlParameterSource("leg", leg)
                .addValue("stall", req.stallId()).addValue("slots", slots)
                .addValue("share", sharing).addValue("fit", fit)
                .addValue("acc", req.carrierAcceptanceStatus().name())
                .addValue("from", utc(start)).addValue("to", utc(end))
                .addValue("status", req.status().name())
                .addValue("pos", req.positionDescription())
                .addValue("key", req.carrierApprovalFileKey())
                .addValue("at", accepted ? utc(now) : null)
                .addValue("contact", req.carrierContact());

        jdbc.update("INSERT dbo.StallAssignment (LegID,StallID,SlotQuantity,"
                + "SharingCode,FitStatus,CarrierAcceptanceStatus,AssignedFrom,"
                + "AssignedTo,Status,PositionDescription,"
                + "CarrierApprovalFileKey,CarrierApprovedAt,CarrierContact) "
                + "VALUES (:leg,:stall,:slots,:share,:fit,:acc,:from,:to,"
                + ":status,:pos,:key,:at,:contact)", args);
    }

    /** @param revision revision ID @param offerId offer ID @param now UTC
     * @return matching valid AIRFARE offer row */
    public List<Map<String, Object>> airfareOffer(long revision, long offerId,
            OffsetDateTime now) {
        return jdbc.queryForList("SELECT OfferID,ServiceCode,Status,ValidUntil,"
                + "EvidenceFileKey FROM dbo.SupplierOffer WITH (HOLDLOCK) "
                + "WHERE OfferID=:id AND RevisionID=:rev",
                Map.of("id", offerId, "rev", revision));
    }

    /** @param leg leg ID @param req flight request @param staff confirmer
     * @param now server UTC */
    public void reserveFlight(long leg, PlanFlightReservationRequest req,
            long staff, OffsetDateTime now) {
        jdbc.update("UPDATE dbo.FlightReservation SET Status='CANCELLED' "
                + "WHERE LegID=:leg AND Status NOT IN ('CANCELLED','EXPIRED')",
                Map.of("leg", leg));
        boolean confirmed = req.status() == ReservationState.CONFIRMED;
        var args = new MapSqlParameterSource("leg", leg)
                .addValue("offer", req.offerId())
                .addValue("airline", req.airlineName().trim())
                .addValue("flight", req.flightNumber().trim())
                .addValue("ref", req.reservationReference())
                .addValue("status", req.status().name())
                .addValue("at", confirmed ? utc(now) : null)
                .addValue("key", req.evidenceFileKey())
                .addValue("exp", utc(req.expiresAt()))
                .addValue("by", confirmed ? staff : null)
                .addValue("contact", req.supplierContact())
                .addValue("slots", req.usableCapacitySlots());

        jdbc.update("INSERT dbo.FlightReservation (LegID,OfferID,AirlineName,"
                + "FlightNumber,ReservationReference,Status,ConfirmedAt,"
                + "EvidenceFileKey,ExpiresAt,ConfirmedBy,SupplierContact,"
                + "UsableCapacitySlots) VALUES (:leg,:offer,:airline,:flight,"
                + ":ref,:status,:at,:key,:exp,:by,:contact,:slots)", args);
        if (confirmed) {
            jdbc.update("UPDATE dbo.SupplierOffer SET Status='SELECTED' "
                    + "WHERE OfferID=:offer AND Status='RECEIVED'",
                    Map.of("offer", req.offerId()));
        }
    }

    /** @param revision revision ID @return quarantine choice row */
    public List<Map<String, Object>> quarantineChoice(long revision) {
        return jdbc.queryForList("SELECT q.QuarantineChoiceID,q.PackageCode,"
                + "q.RequiredDays,q.ConfirmedBy,q.DaysConfirmedAt "
                + "FROM dbo.QuarantineChoice q JOIN dbo.OrderServiceChoice c "
                + "ON c.ChoiceID=q.ChoiceID WHERE c.RevisionID=:rev "
                + "AND c.ServiceCode='QUARANTINE'", Map.of("rev", revision));
    }

    /** @param choiceId QuarantineChoiceID @param req request @param staff ID
     * @param now server UTC */
    public void reserveQuarantine(long choiceId,
            PlanQuarantineReservationRequest req, long staff,
            OffsetDateTime now) {
        jdbc.update("UPDATE dbo.QuarantineReservation SET Status='CANCELLED' "
                + "WHERE QuarantineChoiceID=:id AND Status NOT IN "
                + "('CANCELLED','EXPIRED')", Map.of("id", choiceId));
        boolean confirmed = req.status() == ReservationState.CONFIRMED;
        var args = new MapSqlParameterSource("id", choiceId)
                .addValue("name", req.facilityName().trim())
                .addValue("ref", req.reservationReference())
                .addValue("start", utc(req.startsAt()))
                .addValue("end", utc(req.endsAt()))
                .addValue("status", req.status().name())
                .addValue("sup", req.supplierId())
                .addValue("key", req.evidenceFileKey())
                .addValue("by", confirmed ? staff : null)
                .addValue("at", confirmed ? utc(now) : null)
                .addValue("exp", utc(req.expiresAt()))
                .addValue("contact", req.facilityContact());

        jdbc.update("INSERT dbo.QuarantineReservation (QuarantineChoiceID,"
                + "FacilityName,ReservationReference,StartsAt,EndsAt,Status,"
                + "SupplierID,EvidenceFileKey,ConfirmedBy,ConfirmedAt,"
                + "ExpiresAt,FacilityContact) VALUES (:id,:name,:ref,:start,"
                + ":end,:status,:sup,:key,:by,:at,:exp,:contact)", args);
    }

    /** @param revision revision ID @return quarantine reservations */
    public List<PlanQuarantineReservationView> quarantineReservations(
            long revision) {
        return jdbc.query("SELECT r.* FROM dbo.QuarantineReservation r "
                + "JOIN dbo.QuarantineChoice q ON q.QuarantineChoiceID="
                + "r.QuarantineChoiceID JOIN dbo.OrderServiceChoice c "
                + "ON c.ChoiceID=q.ChoiceID WHERE c.RevisionID=:rev "
                + "ORDER BY r.ReservationID DESC", Map.of("rev", revision),
                (r, i) -> new PlanQuarantineReservationView(
                        r.getLong("ReservationID"),
                        r.getLong("QuarantineChoiceID"),
                        r.getString("FacilityName"),
                        r.getString("ReservationReference"),
                        time(r, "StartsAt"), time(r, "EndsAt"),
                        ReservationState.valueOf(r.getString("Status")),
                        number(r, "SupplierID"),
                        r.getString("EvidenceFileKey"),
                        number(r, "ConfirmedBy"), time(r, "ConfirmedAt"),
                        time(r, "ExpiresAt"), r.getString("FacilityContact")));
    }

    /** @param revision revision ID @return insurance choice row */
    public List<Map<String, Object>> insuranceChoice(long revision) {
        return jdbc.queryForList("SELECT i.InsuranceChoiceID,i.CoverageCode,"
                + "i.DeclaredValue,i.ValueCurrency,i.ExistingPolicyReference "
                + "FROM dbo.InsuranceChoice i JOIN dbo.OrderServiceChoice c "
                + "ON c.ChoiceID=i.ChoiceID WHERE c.RevisionID=:rev "
                + "AND c.ServiceCode='INSURANCE'", Map.of("rev", revision));
    }

    /** @param choiceId InsuranceChoiceID @param req request @param staff ID
     * @param now server UTC */
    public void verifyInsurance(long choiceId, PlanInsurancePolicyRequest req,
            long staff, OffsetDateTime now) {
        jdbc.update("UPDATE dbo.InsurancePolicy SET Status='CANCELLED' "
                + "WHERE InsuranceChoiceID=:id AND Status NOT IN "
                + "('CANCELLED','EXPIRED')", Map.of("id", choiceId));
        boolean confirmed = req.status() == ReservationState.CONFIRMED;
        var args = new MapSqlParameterSource("id", choiceId)
                .addValue("insurer", req.insurerName().trim())
                .addValue("num", req.policyNumber().trim())
                .addValue("key", req.evidenceFileKey())
                .addValue("from", utc(req.coverageFrom()))
                .addValue("to", utc(req.coverageTo()))
                .addValue("status", req.status().name())
                .addValue("by", confirmed ? staff : null)
                .addValue("at", confirmed ? utc(now) : null)
                .addValue("terms", req.policyTerms());

        jdbc.update("INSERT dbo.InsurancePolicy (InsuranceChoiceID,"
                + "InsurerName,PolicyNumber,EvidenceFileKey,CoverageFrom,"
                + "CoverageTo,Status,VerifiedBy,VerifiedAt,PolicyTerms) "
                + "VALUES (:id,:insurer,:num,:key,:from,:to,:status,:by,:at,"
                + ":terms)", args);
    }

    /** @param revision revision ID @return insurance policies */
    public List<PlanInsurancePolicyView> insurancePolicies(long revision) {
        return jdbc.query("SELECT p.* FROM dbo.InsurancePolicy p "
                + "JOIN dbo.InsuranceChoice i ON i.InsuranceChoiceID="
                + "p.InsuranceChoiceID JOIN dbo.OrderServiceChoice c "
                + "ON c.ChoiceID=i.ChoiceID WHERE c.RevisionID=:rev "
                + "ORDER BY p.PolicyID DESC", Map.of("rev", revision),
                (r, i) -> new PlanInsurancePolicyView(
                        r.getLong("PolicyID"),
                        r.getLong("InsuranceChoiceID"),
                        r.getString("InsurerName"),
                        r.getString("PolicyNumber"),
                        r.getString("EvidenceFileKey"),
                        time(r, "CoverageFrom"), time(r, "CoverageTo"),
                        ReservationState.valueOf(r.getString("Status")),
                        number(r, "VerifiedBy"), time(r, "VerifiedAt"),
                        r.getString("PolicyTerms")));
    }

    /** @param order parent order @param start plan start @param end plan end
     * @return true if the order's horse is scheduled on an overlapping order */
    public boolean lockHorseAndCheckOverlap(long order, OffsetDateTime start,
            OffsetDateTime end) {
        var horses = jdbc.query("SELECT HorseID FROM dbo.TransportOrderDetail "
                + "WHERE OrderID=:order", Map.of("order", order),
                (r, i) -> number(r, "HorseID"));
        Long horse = horses.isEmpty() ? null : horses.getFirst();

        if (horse == null) {
            return true;
        }
        jdbc.queryForObject("SELECT HorseID FROM dbo.Horse WITH (UPDLOCK,"
                + "HOLDLOCK) WHERE HorseID=:horse", Map.of("horse", horse),
                Long.class);
        return jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.TransportOrderDetail d JOIN dbo.TransportOrder o "
                + "ON o.OrderID=d.OrderID JOIN dbo.TransportPlan p "
                + "ON p.PlanID=o.CurrentPlanID AND p.OrderID=o.OrderID "
                + "WHERE d.HorseID=:horse AND d.OrderID<>:order "
                + "AND o.OrderStatus NOT IN ('COMPLETED','DELIVERED',"
                + "'CANCELLED','CANCELED','CLOSED','REJECTED') "
                + "AND p.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT') "
                + "AND (p.PlannedStartTime IS NULL OR p.PlannedEndTime IS NULL "
                + "OR (:start<p.PlannedEndTime AND p.PlannedStartTime<:end))",
                Map.of("horse", horse, "order", order, "start", utc(start),
                        "end", utc(end)), Integer.class) > 0;
    }

    /** @param order parent @param plan plan ID @param quote accepted quote ID
     * @param now server UTC */
    public void confirmPlan(long order, long plan, long quote,
            OffsetDateTime now) {
        jdbc.update("UPDATE dbo.TransportPlan SET PlanStatus='SUPERSEDED' "
                + "WHERE OrderID=:order AND PlanID<>:plan AND PlanStatus IN "
                + "('DRAFT','PROPOSED','PLANNED','CONFIRMED')",
                Map.of("order", order, "plan", plan));
        jdbc.update("UPDATE dbo.TripLeg SET LegStatus='CONFIRMED' "
                + "WHERE PlanID=:plan AND LegStatus='PLANNED'",
                Map.of("plan", plan));
        jdbc.update("UPDATE dbo.TransportPlan SET AcceptedQuoteID=:quote,"
                + "OperationalGateStatus='PASS',GatesCheckedAt=:now,"
                + "PlanStatus='CONFIRMED' WHERE PlanID=:plan "
                + "AND OrderID=:order",
                Map.of("quote", quote, "now", utc(now), "plan", plan,
                        "order", order));
        jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=:plan,"
                + "OrderStatus=CASE WHEN OrderStatus='APPROVED' "
                + "THEN 'CONFIRMED' ELSE OrderStatus END,"
                + "UpdatedAt=SYSUTCDATETIME() WHERE OrderID=:order",
                Map.of("plan", plan, "order", order));
    }

    /** @param revision revision ID @return service code to arrangedBy map */
    public List<Map<String, Object>> serviceChoices(long revision) {
        return jdbc.queryForList("SELECT ServiceCode,OptionCode,ArrangedBy "
                + "FROM dbo.OrderServiceChoice WHERE RevisionID=:rev",
                Map.of("rev", revision));
    }

    private PlanSummaryView summary(ResultSet r, Long current)
            throws SQLException {
        long id = r.getLong("PlanID");

        return new PlanSummaryView(id, r.getLong("OrderID"),
                r.getLong("RevisionID"), r.getInt("VersionNo"),
                PlanState.valueOf(r.getString("PlanStatus")),
                current != null && current == id,
                number(r, "AcceptedQuoteID"), r.getLong("CreatedBy"),
                time(r, "PlannedStartTime"), time(r, "PlannedEndTime"),
                GateState.valueOf(r.getString("FinancialGateStatus")),
                GateState.valueOf(r.getString("OperationalGateStatus")),
                GateState.valueOf(r.getString("DocGateStatus")));
    }

    private Long number(ResultSet r, String field) throws SQLException {
        return r.getObject(field) == null ? null : r.getLong(field);
    }

    private OffsetDateTime time(ResultSet r, String field) throws SQLException {
        var value = r.getObject(field, LocalDateTime.class);

        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private LocalDateTime utc(OffsetDateTime time) {
        return time == null ? null : time.withOffsetSameInstant(ZoneOffset.UTC)
                .withNano(0).toLocalDateTime();
    }
}
