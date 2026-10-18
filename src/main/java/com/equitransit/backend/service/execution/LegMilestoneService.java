/*
 * LegMilestoneService
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Business engine for discrete road and air leg milestone progression.
 */
package com.equitransit.backend.service.execution;

import com.equitransit.backend.dto.execution.AirMilestoneType;
import com.equitransit.backend.dto.execution.RoadMilestoneType;
import com.equitransit.backend.dto.execution.request.AirMilestoneCreateRequest;
import com.equitransit.backend.dto.execution.request.EstimatedArrivalUpdateRequest;
import com.equitransit.backend.dto.execution.request.RoadMilestoneCreateRequest;
import com.equitransit.backend.dto.execution.response.LegMilestoneResponse;
import com.equitransit.backend.entity.execution.LegMilestoneLog;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.execution.LegMilestoneLogRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.DomainAccessPolicy;
import com.equitransit.backend.service.execution.validation.MilestoneSequenceValidator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates mode-specific milestone logging, transitions & validation. */
@Service
public class LegMilestoneService {

    private final LegMilestoneLogRepository milestones;
    private final LegExecutionRepository legs;
    private final MilestoneSequenceValidator sequenceValidator;
    private final CurrentActorService actors;
    private final DomainAccessPolicy access;
    private final Clock clock;

    /**
     * Creates milestone service.
     *
     * @param milestones milestone repository
     * @param legs leg and order execution repository
     * @param sequenceValidator sequence progression validator
     * @param actors current actor resolver
     * @param access domain access policy
     * @param clock system UTC clock
     */
    public LegMilestoneService(LegMilestoneLogRepository milestones,
            LegExecutionRepository legs,
            MilestoneSequenceValidator sequenceValidator,
            CurrentActorService actors,
            DomainAccessPolicy access, Clock clock) {
        this.milestones = milestones;
        this.legs = legs;
        this.sequenceValidator = sequenceValidator;
        this.actors = actors;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Assigned driver records a road transport milestone event.
     *
     * @param legId trip leg PK
     * @param req road milestone payload
     * @return recorded milestone response
     */
    @Transactional
    public LegMilestoneResponse recordRoadMilestone(long legId,
            RoadMilestoneCreateRequest req) {
        CurrentActor actor = actors.current();
        var leg = requireLeg(legId);

        if (!"ROAD".equalsIgnoreCase(leg.transportMode())) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "INVALID_TRANSPORT_MODE",
                    "Target leg is not a road transport leg.");
        }
        if (!"DRIVER".equals(actor.roleCode()) || actor.staffId() == null) {
            throw new AccessDeniedException(
                    "Only assigned drivers can log road milestones.");
        }
        var assignment = legs.findDriverAssignment(legId, actor.staffId());
        if (assignment.isEmpty()) {
            throw new AccessDeniedException(
                    "Driver is not assigned to this road leg.");
        }
        validatePriorLegCompletion(leg);

        var existing = milestones.findByLegId(legId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime occurred = req.occurredAt() != null
                ? req.occurredAt() : now;

        sequenceValidator.validateRoadMilestone(existing, req.milestoneType(),
                occurred, now);

        var entity = new LegMilestoneLog();
        entity.setLegId(legId);
        entity.setReportedBy(actor.staffId());
        entity.setMilestoneType(req.milestoneType().name());
        entity.setOccurredAt(occurred);
        entity.setRecordedAt(now);
        entity.setLatitude(req.latitude());
        entity.setLongitude(req.longitude());
        entity.setNotes(req.notes());
        entity.setSourceCode("DRIVER_APP");
        entity.setLocationDescription(req.locationDescription());
        entity.setDelayReason(req.delayReason());
        entity.setEvidenceFileKey(req.evidenceFileKey());

        long milestoneId = milestones.save(entity);
        entity.setMilestoneId(milestoneId);

        applyRoadTransitions(leg, req.milestoneType(), occurred);

        return toResponse(entity);
    }

    /**
     * Coordinator records an air transport milestone with partner source.
     *
     * @param legId trip leg PK
     * @param req air milestone payload
     * @return recorded milestone response
     */
    @Transactional
    public LegMilestoneResponse recordAirMilestone(long legId,
            AirMilestoneCreateRequest req) {
        CurrentActor actor = actors.current();
        var leg = requireLeg(legId);

        if (!"AIR".equalsIgnoreCase(leg.transportMode())) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "INVALID_TRANSPORT_MODE",
                    "Target leg is not an air transport leg.");
        }
        if (!"COORDINATOR".equals(actor.roleCode())
                && !"MANAGER".equals(actor.roleCode())) {
            throw new AccessDeniedException(
                    "Only coordinators or managers can log air milestones.");
        }
        validatePriorLegCompletion(leg);

        var existing = milestones.findByLegId(legId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime occurred = req.occurredAt() != null
                ? req.occurredAt() : now;

        sequenceValidator.validateAirMilestone(existing, req.milestoneType(),
                occurred, now);

        var entity = new LegMilestoneLog();
        entity.setLegId(legId);
        entity.setReportedBy(actor.staffId());
        entity.setMilestoneType(req.milestoneType().name());
        entity.setOccurredAt(occurred);
        entity.setRecordedAt(now);
        entity.setLatitude(req.latitude());
        entity.setLongitude(req.longitude());
        entity.setNotes(req.notes());
        entity.setSourceCode(req.sourceCode().trim());
        entity.setSourceReference(req.sourceReference().trim());
        entity.setLocationDescription(req.locationDescription());
        entity.setEvidenceFileKey(req.evidenceFileKey());

        long milestoneId = milestones.save(entity);
        entity.setMilestoneId(milestoneId);

        applyAirTransitions(leg, req.milestoneType(), occurred);

        return toResponse(entity);
    }

    /**
     * Coordinator updates estimated arrival time and delay reason on a leg.
     *
     * @param legId trip leg PK
     * @param req estimated arrival payload
     * @return updated milestone response
     */
    @Transactional
    public LegMilestoneResponse updateEstimatedArrival(long legId,
            EstimatedArrivalUpdateRequest req) {
        CurrentActor actor = actors.current();
        var leg = requireLeg(legId);

        if (!"COORDINATOR".equals(actor.roleCode())
                && !"MANAGER".equals(actor.roleCode())) {
            throw new AccessDeniedException(
                    "Only coordinators or managers can update arrival times.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (req.estimatedArrivalAt().isBefore(now)) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "INVALID_ESTIMATED_ARRIVAL",
                    "EstimatedArrivalAt cannot be in the past.");
        }

        var entity = new LegMilestoneLog();
        entity.setLegId(legId);
        entity.setReportedBy(actor.staffId());
        entity.setMilestoneType("DELAYED");
        entity.setOccurredAt(now);
        entity.setRecordedAt(now);
        entity.setEstimatedArrivalAt(req.estimatedArrivalAt());
        entity.setDelayReason(req.delayReason().trim());
        entity.setNotes("Estimated arrival updated: " + req.delayReason());

        long milestoneId = milestones.save(entity);
        entity.setMilestoneId(milestoneId);

        return toResponse(entity);
    }

    /**
     * Retrieves all milestone logs for a given leg.
     *
     * @param legId trip leg PK
     * @return list of milestones
     */
    @Transactional(readOnly = true)
    public List<LegMilestoneResponse> getLegMilestones(long legId) {
        requireLeg(legId);
        return milestones.findByLegId(legId).stream()
                .map(this::toResponse)
                .toList();
    }

    private LegExecutionRepository.LegRow requireLeg(long legId) {
        return legs.findLegById(legId).orElseThrow(() -> new OrderException(
                HttpStatus.NOT_FOUND, "LEG_NOT_FOUND",
                "Trip leg #" + legId + " was not found."));
    }

    private void validatePriorLegCompletion(
            LegExecutionRepository.LegRow leg) {
        if (leg.sequenceNo() > 1) {
            var allLegs = legs.findLegsByPlanId(leg.planId());
            for (var prev : allLegs) {
                if (prev.sequenceNo() < leg.sequenceNo()
                        && !"COMPLETED".equalsIgnoreCase(prev.legStatus())) {
                    throw new OrderException(HttpStatus.CONFLICT,
                            "PREVIOUS_LEG_INCOMPLETE",
                            "Prior leg #" + prev.sequenceNo()
                                    + " must be COMPLETED before progressing.");
                }
            }
        }
    }

    private void applyRoadTransitions(LegExecutionRepository.LegRow leg,
            RoadMilestoneType type, OffsetDateTime occurred) {
        if (type == RoadMilestoneType.DEPARTED) {
            legs.updateLegDeparture(leg.legId(), "IN_PROGRESS", occurred);
            legs.updatePlanStatus(leg.planId(), "IN_TRANSIT");
            legs.findOrderByLegId(leg.legId()).ifPresent(o ->
                    legs.updateOrderStatus(o.orderId(), "IN_TRANSIT"));
        } else if (type == RoadMilestoneType.ARRIVED) {
            legs.updateLegArrival(leg.legId(), "COMPLETED", occurred);
        }
    }

    private void applyAirTransitions(LegExecutionRepository.LegRow leg,
            AirMilestoneType type, OffsetDateTime occurred) {
        if (type == AirMilestoneType.CARGO_TENDERED
                || type == AirMilestoneType.CUSTOMS_CLEARED_OUT
                || type == AirMilestoneType.AIRCRAFT_LOADED) {
            if (!"IN_PROGRESS".equalsIgnoreCase(leg.legStatus())) {
                legs.updateLegStatus(leg.legId(), "IN_PROGRESS");
            }
        } else if (type == AirMilestoneType.WHEELS_UP) {
            legs.updateLegDeparture(leg.legId(), "IN_PROGRESS", occurred);
            legs.updatePlanStatus(leg.planId(), "IN_TRANSIT");
            legs.findOrderByLegId(leg.legId()).ifPresent(o ->
                    legs.updateOrderStatus(o.orderId(), "IN_TRANSIT"));
        } else if (type == AirMilestoneType.WHEELS_DOWN) {
            if (leg.actualArrival() == null) {
                legs.updateLegArrival(leg.legId(), "IN_PROGRESS", occurred);
            }
        } else if (type == AirMilestoneType.HANDOVER_READY
                || type == AirMilestoneType.CUSTOMS_CLEARED_IN) {
            if (type == AirMilestoneType.HANDOVER_READY) {
                legs.updateLegArrival(leg.legId(), "COMPLETED", occurred);
            }
        }
    }

    private LegMilestoneResponse toResponse(LegMilestoneLog m) {
        String staffName = legs.findStaffName(m.getReportedBy());

        return new LegMilestoneResponse(
                m.getMilestoneId(),
                m.getLegId(),
                m.getReportedBy(),
                staffName,
                m.getMilestoneType(),
                m.getOccurredAt(),
                m.getRecordedAt(),
                m.getLatitude(),
                m.getLongitude(),
                m.getNotes(),
                m.getSourceCode(),
                m.getSourceReference(),
                m.getLocationDescription(),
                m.getEstimatedArrivalAt(),
                m.getDelayReason(),
                m.getEvidenceFileKey());
    }
}
