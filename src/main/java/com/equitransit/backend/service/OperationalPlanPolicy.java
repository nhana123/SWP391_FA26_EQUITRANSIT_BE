/*
 * OperationalPlanPolicy
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Pure domain policy for F10 multimodal operational planning.
 */
package com.equitransit.backend.service;

import com.equitransit.backend.dto.plan.PlanModels.AssignmentState;
import com.equitransit.backend.dto.plan.PlanModels.CarrierAcceptanceState;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyView;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegInput;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegView;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationView;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.ReservationState;
import com.equitransit.backend.dto.plan.PlanModels.TripRole;
import com.equitransit.backend.exception.OrderException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Pure domain validation for F10 legs, reservations and confirmation gates. */
public final class OperationalPlanPolicy {

    private OperationalPlanPolicy() { }

    /** @param status plan state @return whether mutations are permitted */
    public static boolean editable(PlanState status) {
        return status == PlanState.DRAFT || status == PlanState.PROPOSED
                || status == PlanState.PLANNED;
    }

    /** @param legs ordered leg inputs */
    public static void validateLegs(List<PlanLegInput> legs) {
        if (legs == null || legs.isEmpty()) {
            throw badRequest("PLAN_LEGS_EMPTY", "Provide at least one leg.");
        }
        int expectedSeq = 1;
        OffsetDateTime previousArrival = null;

        for (PlanLegInput leg : legs) {
            if (leg.estimatedAirfare() != null
                    && (leg.transportMode() != PlanLegMode.AIR
                        || leg.estimatedAirfare().signum() <= 0
                        || leg.estimatedAirfare().scale() > 2
                        || leg.estimatedAirfare().setScale(4)
                                .precision() > 18)) {
                throw badRequest("PLAN_AIRFARE_INVALID",
                        "Provide a positive AIR-only monetary estimate.");
            }
            if (leg.sequenceNo() != expectedSeq++) {
                throw badRequest("PLAN_SEQUENCE_INVALID",
                        "Leg sequence numbers must be consecutive from 1.");
            }
            if (leg.distanceKm() == null || leg.distanceKm().signum() <= 0) {
                throw badRequest("PLAN_DISTANCE_INVALID",
                        "Leg distance must be positive.");
            }
            if (leg.distanceSource() != null
                    && leg.distanceSource().isBlank()) {
                throw badRequest("PLAN_DISTANCE_SOURCE_BLANK",
                        "Distance source cannot be blank when supplied.");
            }
            var dep = leg.plannedDeparture().withNano(0);
            var arr = leg.plannedArrival().withNano(0);

            if (!dep.isBefore(arr) || dep.getYear() < 1900
                    || arr.getYear() > 9997) {
                throw badRequest("PLAN_WINDOW_INVALID",
                        "Leg arrival must be strictly after departure.");
            }
            if (leg.transportMode() == PlanLegMode.ROAD
                    && leg.plannedFlightNumber() != null) {
                throw badRequest("PLAN_MODE_INVALID",
                        "ROAD legs cannot have a planned flight number.");
            }
            if (previousArrival != null) {
                int conn = leg.connectionBufferMinutes() == null
                        ? 0 : leg.connectionBufferMinutes();
                int rest = leg.restDurationMinutes() == null
                        ? 0 : leg.restDurationMinutes();
                OffsetDateTime minDep = previousArrival.plusMinutes(
                        (long) conn + rest);

                if (dep.isBefore(minDep)) {
                    throw badRequest("PLAN_CONNECTION_OVERLAP",
                            "Subsequent leg departs before connection/rest.");
                }
            }
            previousArrival = arr;
        }
    }

    /** @param req stall assignment command */
    public static void validateStallRequest(PlanStallAssignmentRequest req) {
        if (req.status() == AssignmentState.CANCELLED
                || req.status() == AssignmentState.INACTIVE
                || req.status() == AssignmentState.COMPLETED) {
            throw badRequest("STALL_ASSIGNMENT_STATUS_INVALID",
                    "Use HELD or CONFIRMED when assigning a stall.");
        }
        if (req.carrierAcceptanceStatus() == CarrierAcceptanceState.ACCEPTED
                && (req.carrierApprovalFileKey() == null
                        || req.carrierApprovalFileKey().isBlank())) {
            throw conflict("CARRIER_PROOF_REQUIRED",
                    "Accepted carrier status requires an approval file key.");
        }
        if ((req.status() == AssignmentState.CONFIRMED
                || req.status() == AssignmentState.ACTIVE)
                && req.carrierAcceptanceStatus()
                        != CarrierAcceptanceState.ACCEPTED) {
            throw conflict("CARRIER_ACCEPTANCE_REQUIRED",
                    "Stall assignment cannot be CONFIRMED without carrier "
                            + "ACCEPTED status.");
        }
    }

    /** @param req flight command @param requiredSlots horse stall slots
     * @param now server UTC */
    public static void validateFlightRequest(PlanFlightReservationRequest req,
            BigDecimal requiredSlots, OffsetDateTime now) {
        if (req.status() == ReservationState.CANCELLED
                || req.status() == ReservationState.EXPIRED) {
            throw badRequest("FLIGHT_STATUS_INVALID",
                    "Use HELD or CONFIRMED when recording a reservation.");
        }
        if (requiredSlots != null
                && req.usableCapacitySlots().compareTo(requiredSlots) < 0) {
            throw conflict("FLIGHT_CAPACITY_INSUFFICIENT",
                    "Flight usable capacity is less than required stall slots");
        }
        if (req.expiresAt() != null && !req.expiresAt().isAfter(now)) {
            throw conflict("FLIGHT_RESERVATION_EXPIRED",
                    "Flight reservation expiry must be in the future.");
        }
        if (req.status() == ReservationState.CONFIRMED) {
            if (req.reservationReference() == null
                    || req.reservationReference().isBlank()
                    || req.evidenceFileKey() == null
                    || req.evidenceFileKey().isBlank()
                    || req.expiresAt() == null) {
                throw conflict("FLIGHT_CONFIRMATION_INCOMPLETE",
                        "CONFIRMED flight requires reference, evidence file "
                                + "key, and future expiry.");
            }
        }
    }

    /** @param req quarantine command @param requiredDays Specialist days
     * @param now server UTC */
    public static void validateQuarantineRequest(
            PlanQuarantineReservationRequest req, Integer requiredDays,
            OffsetDateTime now) {
        if (requiredDays == null || requiredDays <= 0) {
            throw conflict("QUARANTINE_ASSESSMENT_PENDING",
                    "Specialist must confirm required quarantine days first.");
        }
        var start = req.startsAt().withNano(0);
        var end = req.endsAt().withNano(0);

        if (!start.isBefore(end)) {
            throw badRequest("QUARANTINE_WINDOW_INVALID",
                    "Quarantine end must be after start.");
        }
        long reservedDays = Duration.between(start, end).toDays();

        if (reservedDays < requiredDays) {
            throw conflict("QUARANTINE_DAYS_INSUFFICIENT",
                    "Quarantine reservation cannot reduce compulsory days.");
        }
        if (req.status() == ReservationState.CONFIRMED) {
            if (req.reservationReference() == null
                    || req.reservationReference().isBlank()
                    || req.evidenceFileKey() == null
                    || req.evidenceFileKey().isBlank()
                    || req.expiresAt() == null
                    || !req.expiresAt().isAfter(now)) {
                throw conflict("QUARANTINE_CONFIRMATION_INCOMPLETE",
                        "CONFIRMED quarantine requires reference, evidence "
                                + "and future expiry.");
            }
        }
    }

    /** @param req insurance command @param planStart min start @param planEnd
     * max end */
    public static void validateInsuranceRequest(PlanInsurancePolicyRequest req,
            OffsetDateTime planStart, OffsetDateTime planEnd) {
        var from = req.coverageFrom().withNano(0);
        var to = req.coverageTo().withNano(0);

        if (!from.isBefore(to)) {
            throw badRequest("INSURANCE_WINDOW_INVALID",
                    "Insurance coverageTo must be after coverageFrom.");
        }
        if (planStart != null && from.isAfter(planStart)) {
            throw conflict("INSURANCE_COVERAGE_GAP",
                    "Insurance coverage must start at or before plan start.");
        }
        if (planEnd != null && to.isBefore(planEnd)) {
            throw conflict("INSURANCE_COVERAGE_GAP",
                    "Insurance coverage must extend through plan end.");
        }
        if (req.status() == ReservationState.CONFIRMED
                && (req.evidenceFileKey() == null
                        || req.evidenceFileKey().isBlank())) {
            throw conflict("INSURANCE_EVIDENCE_REQUIRED",
                    "CONFIRMED insurance policy requires an evidence file key");
        }
    }

    /** @param legs plan legs @param quarantine quarantine reservations
     * @param insurance insurance policies @param choices service choices
     * @param quarantineRequiredDays specialist days @param now server UTC
     * @return list of operational blocker codes; empty when ready to confirm */
    public static List<String> confirmationBlockers(List<PlanLegView> legs,
            List<PlanQuarantineReservationView> quarantine,
            List<PlanInsurancePolicyView> insurance,
            List<Map<String, Object>> choices, Integer quarantineRequiredDays,
            OffsetDateTime now) {
        var blockers = new ArrayList<String>();

        if (legs.isEmpty()) {
            blockers.add("NO_LEGS");
            return blockers;
        }
        boolean customerRoad = choices.stream().anyMatch(c ->
                "ROAD_TRANSPORT".equals(c.get("ServiceCode"))
                        && "CUSTOMER".equals(c.get("ArrangedBy")));
        OffsetDateTime planStart = legs.getFirst().plannedDeparture();
        OffsetDateTime planEnd = legs.getLast().plannedArrival();

        for (PlanLegView leg : legs) {
            if (leg.distanceSource() == null
                    || leg.distanceConfirmedBy() == null
                    || leg.distanceConfirmedAt() == null) {
                blockers.add("LEG_DISTANCE_UNCONFIRMED");
            }
            if (leg.transportMode() == PlanLegMode.ROAD) {
                if (!leg.stallAssignments().isEmpty()
                        || !leg.flightReservations().isEmpty()) {
                    blockers.add("ROAD_LEG_HAS_AIR_RESOURCES");
                }
                if (!customerRoad) {
                    if (leg.vehicleId() == null) {
                        blockers.add("ROAD_VEHICLE_MISSING");
                    }
                    boolean hasDriver = leg.staffAssignments().stream()
                            .anyMatch(a -> a.roleInTrip() == TripRole.DRIVER
                                    && (a.status() == AssignmentState.CONFIRMED
                                    || a.status() == AssignmentState.ACTIVE));

                    if (!hasDriver) {
                        blockers.add("ROAD_DRIVER_MISSING");
                    }
                }
            } else {
                if (leg.vehicleId() != null || leg.staffAssignments().stream()
                        .anyMatch(a -> a.roleInTrip() == TripRole.DRIVER)) {
                    blockers.add("AIR_LEG_HAS_ROAD_RESOURCES");
                }
                boolean stallOk = leg.stallAssignments().stream().anyMatch(s ->
                        (s.status() == AssignmentState.CONFIRMED
                                || s.status() == AssignmentState.ACTIVE)
                        && "SUITABLE".equals(s.fitStatus())
                        && s.carrierAcceptanceStatus()
                                == CarrierAcceptanceState.ACCEPTED
                        && s.carrierApprovalFileKey() != null
                        && s.carrierApprovedAt() != null);

                if (!stallOk) {
                    blockers.add("AIR_STALL_NOT_CONFIRMED");
                }
                boolean flightOk = leg.flightReservations().stream().anyMatch(
                        f -> f.status() == ReservationState.CONFIRMED
                        && f.reservationReference() != null
                        && f.confirmedBy() != null
                        && f.confirmedAt() != null
                        && f.evidenceFileKey() != null
                        && f.expiresAt() != null
                        && f.expiresAt().isAfter(now));

                if (!flightOk) {
                    blockers.add("AIR_FLIGHT_NOT_CONFIRMED");
                }
            }
        }
        boolean quarantineSelected = choices.stream().anyMatch(c ->
                "QUARANTINE".equals(c.get("ServiceCode")));

        if (quarantineSelected || (quarantineRequiredDays != null
                && quarantineRequiredDays > 0)) {
            int reqDays = quarantineRequiredDays == null
                    ? 0 : quarantineRequiredDays;
            boolean qOk = reqDays > 0 && quarantine.stream().anyMatch(q ->
                    q.status() == ReservationState.CONFIRMED
                    && q.reservationReference() != null
                    && q.confirmedBy() != null
                    && q.confirmedAt() != null
                    && q.evidenceFileKey() != null
                    && q.expiresAt() != null
                    && q.expiresAt().isAfter(now)
                    && Duration.between(q.startsAt(), q.endsAt()).toDays()
                            >= reqDays);

            if (!qOk) {
                blockers.add("QUARANTINE_NOT_CONFIRMED");
            }
        }
        boolean insuranceRequired = choices.stream().anyMatch(c ->
                "INSURANCE".equals(c.get("ServiceCode"))
                        && !"NONE".equals(c.get("OptionCode")));

        if (insuranceRequired) {
            boolean iOk = insurance.stream().anyMatch(p ->
                    p.status() == ReservationState.CONFIRMED
                    && p.verifiedBy() != null
                    && p.verifiedAt() != null
                    && p.evidenceFileKey() != null
                    && (planStart == null
                            || !p.coverageFrom().isAfter(planStart))
                    && (planEnd == null
                            || !p.coverageTo().isBefore(planEnd)));

            if (!iOk) {
                blockers.add("INSURANCE_NOT_VERIFIED");
            }
        }
        return blockers.stream().distinct().toList();
    }

    private static OrderException badRequest(String code, String message) {
        return new OrderException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }
}
