/*
 * MilestoneSequenceValidator
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: State machine and progression validation for multimodal milestones.
 */
package com.equitransit.backend.service.execution.validation;

import com.equitransit.backend.dto.execution.AirMilestoneType;
import com.equitransit.backend.dto.execution.RoadMilestoneType;
import com.equitransit.backend.entity.execution.LegMilestoneLog;
import com.equitransit.backend.exception.order.OrderException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Validates discrete milestone progressions for ROAD and AIR legs. */
@Component
public class MilestoneSequenceValidator {

    private static final Map<AirMilestoneType, Integer> AIR_RANKS = Map.of(
            AirMilestoneType.CARGO_TENDERED, 1,
            AirMilestoneType.CUSTOMS_CLEARED_OUT, 2,
            AirMilestoneType.AIRCRAFT_LOADED, 3,
            AirMilestoneType.WHEELS_UP, 4,
            AirMilestoneType.WHEELS_DOWN, 5,
            AirMilestoneType.UNLOADED, 6,
            AirMilestoneType.CUSTOMS_CLEARED_IN, 7,
            AirMilestoneType.HANDOVER_READY, 8);

    /** Creates sequence validator. */
    public MilestoneSequenceValidator() { }

    /**
     * Validates a candidate road milestone against prior leg history.
     *
     * @param existing prior milestones logged on this leg
     * @param candidate proposed new road milestone type
     * @param occurredAt event occurrence time
     * @param now current server time
     */
    public void validateRoadMilestone(List<LegMilestoneLog> existing,
            RoadMilestoneType candidate, OffsetDateTime occurredAt,
            OffsetDateTime now) {
        validateTimeNotFuture(occurredAt, now);

        if (!existing.isEmpty()) {
            var last = existing.getLast();
            validateTimeMonotonic(last.getOccurredAt(), occurredAt);

            boolean alreadyArrived = existing.stream().anyMatch(
                    m -> "ARRIVED".equalsIgnoreCase(m.getMilestoneType()));

            if (alreadyArrived) {
                throw new OrderException(HttpStatus.CONFLICT,
                        "INVALID_MILESTONE_PROGRESSION",
                        "Cannot log milestones after ARRIVED has been "
                                + "reached.");
            }
        }

        if (existing.isEmpty()) {
            if (candidate != RoadMilestoneType.DEPARTED) {
                throw new OrderException(HttpStatus.CONFLICT,
                        "INVALID_MILESTONE_PROGRESSION",
                        "The initial milestone on a road leg must be "
                                + "DEPARTED.");
            }
            return;
        }

        boolean hasDeparted = existing.stream().anyMatch(
                m -> "DEPARTED".equalsIgnoreCase(m.getMilestoneType()));

        if (!hasDeparted && candidate != RoadMilestoneType.DEPARTED) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "INVALID_MILESTONE_PROGRESSION",
                    "Road progression must begin with DEPARTED.");
        }

        if (candidate == RoadMilestoneType.DEPARTED && hasDeparted) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "INVALID_MILESTONE_PROGRESSION",
                    "DEPARTED has already been logged for this road leg.");
        }
    }

    /**
     * Validates a candidate air milestone against prior leg history.
     *
     * @param existing prior milestones logged on this leg
     * @param candidate proposed new air milestone type
     * @param occurredAt event occurrence time
     * @param now current server time
     */
    public void validateAirMilestone(List<LegMilestoneLog> existing,
            AirMilestoneType candidate, OffsetDateTime occurredAt,
            OffsetDateTime now) {
        validateTimeNotFuture(occurredAt, now);

        int candidateRank = AIR_RANKS.getOrDefault(candidate, 0);

        if (!existing.isEmpty()) {
            var last = existing.getLast();
            validateTimeMonotonic(last.getOccurredAt(), occurredAt);

            int lastRank = 0;
            try {
                var lastType = AirMilestoneType.valueOf(
                        last.getMilestoneType().toUpperCase(
                                java.util.Locale.ROOT));
                lastRank = AIR_RANKS.getOrDefault(lastType, 0);
            } catch (IllegalArgumentException ignored) {
                // Ignore unexpected prior legacy type strings
            }

            if (candidateRank <= lastRank) {
                throw new OrderException(HttpStatus.CONFLICT,
                        "INVALID_MILESTONE_PROGRESSION",
                        "Air milestone " + candidate + " violates progression "
                                + "after " + last.getMilestoneType() + ".");
            }
        } else {
            if (candidate != AirMilestoneType.CARGO_TENDERED
                    && candidate != AirMilestoneType.CUSTOMS_CLEARED_OUT) {
                throw new OrderException(HttpStatus.CONFLICT,
                        "INVALID_MILESTONE_PROGRESSION",
                        "Air leg must begin with CARGO_TENDERED or "
                                + "CUSTOMS_CLEARED_OUT.");
            }
        }
    }

    private void validateTimeNotFuture(OffsetDateTime occurredAt,
            OffsetDateTime now) {
        if (occurredAt != null && occurredAt.isAfter(now.plusMinutes(5))) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "FUTURE_TIMESTAMP_NOT_PERMITTED",
                    "OccurredAt cannot be in the future.");
        }
    }

    private void validateTimeMonotonic(OffsetDateTime previous,
            OffsetDateTime current) {
        if (previous != null && current != null && current.isBefore(previous)) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "INVALID_MILESTONE_PROGRESSION",
                    "OccurredAt cannot precede previous milestone on this "
                            + "leg.");
        }
    }
}
