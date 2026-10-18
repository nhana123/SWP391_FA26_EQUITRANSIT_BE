/*
 * OperationalPlanPolicy
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Pure domain policy for F10 multimodal operational planning.
 */
package com.equitransit.backend.service.plan;

import com.equitransit.backend.dto.plan.PlanModels.AssignmentState;
import com.equitransit.backend.dto.plan.PlanModels.CarrierAcceptanceState;
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
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentView;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.ReservationState;
import com.equitransit.backend.dto.plan.PlanModels.TripRole;
import com.equitransit.backend.exception.order.OrderException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
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
        if (legs == null || legs.isEmpty() || legs.size() > 20) {
            throw badRequest("PLAN_LEGS_EMPTY", "Provide at least one leg.");
        }
        int expectedSeq = 1;
        OffsetDateTime previousArrival = null;
        String previousEnd = null;

        for (PlanLegInput leg : legs) {
            if (leg.sequenceNo() != expectedSeq++) {
                throw badRequest("PLAN_SEQUENCE_INVALID",
                        "Leg sequence numbers must be consecutive from 1.");
            }
            if (leg.distanceKm() == null || leg.distanceKm().signum() <= 0) {
                throw badRequest("PLAN_DISTANCE_INVALID",
                        "Leg distance must be positive.");
            }
            if (leg.distanceSource() == null
                    || leg.distanceSource().isBlank()) {
                throw badRequest("PLAN_DISTANCE_SOURCE_BLANK",
                        "Distance source cannot be blank when supplied.");
            }
            timezone(leg.startTimezone());
            timezone(leg.endTimezone());
            if (previousEnd != null && !point(previousEnd).equals(
                    point(leg.startPoint()))) {
                throw badRequest("ITINERARY_GAP_DETECTED",
                        "Consecutive legs must share the same endpoint.");
            }
            if (leg.transportMode() == PlanLegMode.AIR
                    && (leg.plannedFlightNumber() == null
                            || leg.plannedFlightNumber().isBlank()
                            || leg.supplierId() == null)) {
                throw badRequest("AIR_LEG_INCOMPLETE",
                        "AIR requires a flight number and carrier supplier.");
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
            previousEnd = leg.endPoint();
        }
    }

    /** @param req stall assignment command */
    public static void validateStallRequest(PlanStallAssignmentRequest req) {
        if (req.status() == AssignmentState.CANCELLED
                || req.status() == AssignmentState.INACTIVE) {
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
        reservationState(req.status());
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
                    || !req.expiresAt().isAfter(now)
                    || !req.expiresAt().isAfter(start)) {
                throw conflict("QUARANTINE_CONFIRMATION_INCOMPLETE",
                        "CONFIRMED quarantine requires reference, evidence "
                                + "and expiry after the booking start.");
            }
        }
    }

    /** @param req insurance command @param planStart min start @param planEnd
     * max end */
    public static void validateInsuranceRequest(PlanInsurancePolicyRequest req,
            OffsetDateTime planStart, OffsetDateTime planEnd) {
        reservationState(req.status());
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
        try {
            validateLegs(legs.stream().map(l -> new PlanLegInput(
                    l.sequenceNo(), l.transportMode(), l.startPoint(),
                    l.endPoint(), l.distanceKm(), l.distanceSource(),
                    l.plannedDeparture(), l.plannedArrival(),
                    l.startTimezone(), l.endTimezone(), l.supplierId(),
                    l.plannedFlightNumber(), l.connectionBufferMinutes(),
                    l.restDurationMinutes(), l.careInstructions())).toList());
        } catch (OrderException invalid) {
            blockers.add("ITINERARY_INVALID");
        }
        OffsetDateTime planStart = legs.getFirst().plannedDeparture();
        OffsetDateTime planEnd = legs.getLast().plannedArrival();

        for (PlanLegView leg : legs) {
            if (!text(leg.distanceSource())
                    || leg.distanceConfirmedBy() == null
                    || leg.distanceConfirmedAt() == null
                    || leg.distanceConfirmedAt().isAfter(now)) {
                blockers.add("LEG_DISTANCE_UNCONFIRMED");
            }
            if (leg.transportMode() == PlanLegMode.ROAD) {
                if (!leg.stallAssignments().isEmpty()
                        || !leg.flightReservations().isEmpty()) {
                    blockers.add("ROAD_LEG_HAS_AIR_RESOURCES");
                }
                if (leg.vehicleId() == null) {
                    blockers.add("ROAD_VEHICLE_MISSING");
                }
                boolean hasDriver = leg.staffAssignments().stream()
                        .anyMatch(a -> a.roleInTrip() == TripRole.DRIVER
                                && (a.status() == AssignmentState.CONFIRMED
                                || a.status() == AssignmentState.ACTIVE)
                                && covers(a.assignedFrom(), a.assignedTo(),
                                        leg.plannedDeparture(),
                                        leg.plannedArrival()));

                if (!hasDriver) {
                    blockers.add("ROAD_DRIVER_MISSING");
                }
            } else {
                if (leg.vehicleId() != null || leg.staffAssignments().stream()
                        .anyMatch(a -> a.roleInTrip() == TripRole.DRIVER)) {
                    blockers.add("AIR_LEG_HAS_ROAD_RESOURCES");
                }
                boolean stallOk = leg.stallAssignments().stream()
                        .anyMatch(s -> confirmedStall(leg, s, now));

                if (!stallOk) {
                    blockers.add("AIR_STALL_NOT_CONFIRMED");
                }
                boolean flightOk = leg.flightReservations().stream()
                        .anyMatch(f -> confirmedFlight(leg, f, now));

                if (!flightOk) {
                    blockers.add("AIR_FLIGHT_NOT_CONFIRMED");
                }
            }
        }
        if (quarantineRequiredDays == null) {
            blockers.add("QUARANTINE_ASSESSMENT_PENDING");
        }
        if (quarantineRequiredDays != null && quarantineRequiredDays > 0) {
            int reqDays = quarantineRequiredDays == null
                    ? 0 : quarantineRequiredDays;
            boolean qOk = reqDays > 0 && quarantine.stream().anyMatch(q ->
                    q.status() == ReservationState.CONFIRMED
                    && text(q.reservationReference())
                    && q.confirmedBy() != null
                    && q.confirmedAt() != null
                    && !q.confirmedAt().isAfter(now)
                    && text(q.evidenceFileKey())
                    && q.expiresAt() != null
                    && q.expiresAt().isAfter(now)
                    && q.startsAt() != null && q.endsAt() != null
                    && !q.startsAt().isBefore(planEnd)
                    && q.expiresAt().isAfter(q.startsAt())
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
                    && !p.verifiedAt().isAfter(now)
                    && text(p.policyNumber()) && text(p.insurerName())
                    && text(p.evidenceFileKey())
                    && p.coverageFrom() != null && p.coverageTo() != null
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

    /** @param leg AIR leg @param flight recorded booking @param now UTC
     * @return whether that booking covers all confirmed eligible stalls */
    public static boolean confirmedFlight(PlanLegView leg,
            PlanFlightReservationView flight, OffsetDateTime now) {
        var slots = leg.stallAssignments().stream()
                .filter(s -> confirmedStall(leg, s, now))
                .map(s -> s.slotQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return leg.transportMode() == PlanLegMode.AIR
                && flight.legId() == leg.legId()
                && flight.status() == ReservationState.CONFIRMED
                && text(flight.airlineName())
                && text(flight.reservationReference())
                && text(leg.plannedFlightNumber())
                && leg.plannedFlightNumber().equalsIgnoreCase(
                        flight.flightNumber())
                && flight.confirmedBy() != null && flight.confirmedAt() != null
                && !flight.confirmedAt().isAfter(now)
                && text(flight.evidenceFileKey()) && flight.expiresAt() != null
                && flight.expiresAt().isAfter(now)
                && flight.expiresAt().isAfter(leg.plannedDeparture())
                && slots.signum() > 0 && flight.usableCapacitySlots() != null
                && flight.usableCapacitySlots().compareTo(slots) >= 0;
    }

    private static boolean confirmedStall(PlanLegView leg,
            PlanStallAssignmentView stall, OffsetDateTime now) {
        return stall.legId() == leg.legId()
                && (stall.status() == AssignmentState.CONFIRMED
                        || stall.status() == AssignmentState.ACTIVE)
                && "SUITABLE".equals(stall.fitStatus())
                && stall.carrierAcceptanceStatus()
                        == CarrierAcceptanceState.ACCEPTED
                && text(stall.carrierApprovalFileKey())
                && stall.carrierApprovedAt() != null
                && !stall.carrierApprovedAt().isAfter(now)
                && covers(stall.assignedFrom(), stall.assignedTo(),
                        leg.plannedDeparture(), leg.plannedArrival())
                && stall.slotQuantity() != null
                && stall.slotQuantity().signum() > 0;
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static String point(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ")
                .toUpperCase(java.util.Locale.ROOT);
    }

    private static void timezone(String value) {
        if (value == null || !ZoneId.getAvailableZoneIds().contains(value)) {
            throw badRequest("PLAN_TIMEZONE_INVALID",
                    "Use a named IANA timezone for each endpoint.");
        }
    }

    private static boolean covers(OffsetDateTime from, OffsetDateTime to,
            OffsetDateTime start, OffsetDateTime end) {
        return from != null && to != null && start != null && end != null
                && !from.isAfter(start) && !to.isBefore(end);
    }

    private static void reservationState(
            ReservationState s) {
        if (s != ReservationState.HELD && s != ReservationState.CONFIRMED) {
            throw badRequest("RESERVATION_STATUS_INVALID",
                    "Use HELD or CONFIRMED to record new evidence.");
        }
    }

    private static OrderException badRequest(String code, String message) {
        return new OrderException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }
}
