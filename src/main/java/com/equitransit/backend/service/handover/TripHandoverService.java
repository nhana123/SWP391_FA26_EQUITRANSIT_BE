/*
 * TripHandoverService
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Multimodal custody transfers, delivery gates & feedback.
 */
package com.equitransit.backend.service.handover;

import com.equitransit.backend.dto.handover.HandoverType;
import com.equitransit.backend.dto.handover.request.
        CustomerHandoverDecisionRequest;
import com.equitransit.backend.dto.handover.request.
        FinalDeliveryHandoverCreateRequest;
import com.equitransit.backend.dto.handover.request.
        IntermediateHandoverCreateRequest;
import com.equitransit.backend.dto.handover.response.CustomerHandoverResponse;
import com.equitransit.backend.dto.handover.response.
        OperationalCompletionReceipt;
import com.equitransit.backend.dto.handover.response.TripHandoverResponse;
import com.equitransit.backend.entity.handover.TripHandover;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.execution.LegExecutionRepository.
        LegRow;
import com.equitransit.backend.repository.execution.LegExecutionRepository.
        OrderScopeRow;
import com.equitransit.backend.repository.handover.TripHandoverRepository;
import com.equitransit.backend.repository.handover.TripHandoverRepository.
        OrderReceiverRow;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates multimodal trip custody transfers, delivery gates & feedback.
 */
@Service
public class TripHandoverService {

    private final TripHandoverRepository handovers;
    private final LegExecutionRepository legs;
    private final FinalDeliveryValidator validator;
    private final CurrentActorService actors;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * Creates handover service.
     *
     * @param handovers handover repository
     * @param legs leg and order repository
     * @param validator final delivery prerequisite validator
     * @param actors live actor resolver
     * @param mapper JSON object mapper
     * @param clock system UTC clock
     */
    public TripHandoverService(TripHandoverRepository handovers,
            LegExecutionRepository legs,
            FinalDeliveryValidator validator,
            CurrentActorService actors,
            ObjectMapper mapper,
            Clock clock) {
        this.handovers = handovers;
        this.legs = legs;
        this.validator = validator;
        this.actors = actors;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Records an intermediate handover executed by the assigned driver.
     *
     * @param legId transport leg key
     * @param request intermediate handover payload
     * @return staff-facing handover response
     */
    @Transactional
    public TripHandoverResponse recordIntermediateByDriver(long legId,
            IntermediateHandoverCreateRequest request) {
        CurrentActor actor = actors.current();
        verifyDriverAssignment(legId, actor);
        return processIntermediateHandover(legId, request, actor.staffId());
    }

    /**
     * Records an intermediate handover executed by coordinator or manager.
     *
     * @param legId transport leg key
     * @param request intermediate handover payload
     * @return staff-facing handover response
     */
    @Transactional
    public TripHandoverResponse recordIntermediateByCoordinator(long legId,
            IntermediateHandoverCreateRequest request) {
        CurrentActor actor = actors.current();
        if (actor.staffId() == null) {
            throw new AccessDeniedException("Staff profile required.");
        }
        return processIntermediateHandover(legId, request, actor.staffId());
    }

    /**
     * Records final destination delivery executed by the assigned driver.
     *
     * @param legId final transport leg key
     * @param request final delivery payload
     * @return operational completion receipt
     */
    @Transactional
    public OperationalCompletionReceipt recordFinalDeliveryByDriver(long legId,
            FinalDeliveryHandoverCreateRequest request) {
        CurrentActor actor = actors.current();
        verifyDriverAssignment(legId, actor);
        return processFinalDelivery(legId, request, actor.staffId());
    }

    /**
     * Records final destination delivery executed by coordinator or manager.
     *
     * @param legId final transport leg key
     * @param request final delivery payload
     * @return operational completion receipt
     */
    @Transactional
    public OperationalCompletionReceipt recordFinalDeliveryByCoordinator(
            long legId, FinalDeliveryHandoverCreateRequest request) {
        CurrentActor actor = actors.current();
        if (actor.staffId() == null) {
            throw new AccessDeniedException("Staff profile required.");
        }
        return processFinalDelivery(legId, request, actor.staffId());
    }

    /**
     * Staff reads all handovers logged on a leg.
     *
     * @param legId transport leg key
     * @return list of handover records
     */
    @Transactional(readOnly = true)
    public List<TripHandoverResponse> getHandoversForLeg(long legId) {
        LegRow leg = findLegOrThrow(legId);
        OrderScopeRow order = findOrderScopeOrThrow(legId);

        return handovers.findByLegId(legId).stream()
                .map(h -> toStaffResponse(h, order.orderId(), leg.planId()))
                .toList();
    }

    /**
     * Staff reads one handover record by ID.
     *
     * @param handoverId handover PK
     * @return staff-facing handover response
     */
    @Transactional(readOnly = true)
    public TripHandoverResponse getHandoverById(long handoverId) {
        TripHandover h = findHandoverOrThrow(handoverId);
        LegRow leg = findLegOrThrow(h.getLegId());
        OrderScopeRow order = findOrderScopeOrThrow(h.getLegId());

        return toStaffResponse(h, order.orderId(), leg.planId());
    }

    /**
     * Customer reads delivery handovers for their owned order.
     *
     * @param orderId parent order key
     * @return redacted list of delivery handovers
     */
    @Transactional(readOnly = true)
    public List<CustomerHandoverResponse> getCustomerHandovers(long orderId) {
        verifyCustomerOrderOwnership(orderId);
        return handovers.findByOrderId(orderId).stream()
                .map(h -> toCustomerResponse(h, orderId))
                .toList();
    }

    /**
     * Customer reads one delivery handover by ID.
     *
     * @param orderId parent order key
     * @param handoverId handover PK
     * @return redacted customer delivery response
     */
    @Transactional(readOnly = true)
    public CustomerHandoverResponse getCustomerHandoverById(long orderId,
            long handoverId) {
        verifyCustomerOrderOwnership(orderId);
        TripHandover h = findHandoverOrThrow(handoverId);
        OrderScopeRow order = findOrderScopeOrThrow(h.getLegId());
        if (order.orderId() != orderId) {
            throw new AccessDeniedException("Handover does not belong to order "
                    + orderId);
        }
        return toCustomerResponse(h, orderId);
    }

    /**
     * Customer records acceptance or dispute on a delivery handover.
     *
     * @param orderId parent order key
     * @param handoverId handover PK
     * @param request customer response payload
     * @return updated customer delivery response
     */
    @Transactional
    public CustomerHandoverResponse recordCustomerDecision(long orderId,
            long handoverId, CustomerHandoverDecisionRequest request) {
        CurrentActor actor = actors.current();
        verifyCustomerOrderOwnership(orderId);
        TripHandover h = findHandoverOrThrow(handoverId);
        OrderScopeRow order = findOrderScopeOrThrow(h.getLegId());
        if (order.orderId() != orderId) {
            throw new AccessDeniedException("Handover does not belong to order "
                    + orderId);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        handovers.updateCustomerDecision(handoverId,
                request.decision().name(), request.feedback(), now,
                actor.customerId());

        h.setCustomerDecision(request.decision().name());
        h.setCustomerFeedback(request.feedback());
        h.setCustomerRespondedAt(now);
        h.setCustomerConfirmedAt(now);
        h.setRespondedByCustomerId(actor.customerId());

        return toCustomerResponse(h, orderId);
    }

    // Internal processing engines

    private TripHandoverResponse processIntermediateHandover(long legId,
            IntermediateHandoverCreateRequest request, long staffId) {
        if (!request.handoverType().isIntermediate()) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "INVALID_HANDOVER_TYPE",
                    "Expected intermediate handover type, received: "
                            + request.handoverType());
        }

        validatePastOrPresent(request.handoverTime());
        validateEvidenceJson(request.evidenceFilesJson());

        LegRow leg = findLegOrThrow(legId);
        OrderScopeRow order = findOrderScopeOrThrow(legId);
        validateAfterDeparture(request.handoverTime(),
                leg.actualDeparture());

        var h = new TripHandover();
        h.setLegId(legId);
        h.setRecordedBy(staffId);
        h.setHandoverType(request.handoverType().name());
        h.setHandoverTime(request.handoverTime());
        h.setReceivedByName(request.receivedByName().trim());
        h.setReceiverAuthority(request.receiverAuthority());
        h.setReceiverSignatureFileKey(request.receiverSignatureFileKey());
        h.setHorseConditionStatus(request.horseConditionStatus().name());
        h.setOutcome(request.outcome().name());
        h.setExceptionNotes(request.exceptionNotes());
        h.setPhotoFileKey(request.photoFileKey());
        h.setLocationDescription(request.locationDescription());
        h.setEvidenceFilesJson(request.evidenceFilesJson());

        long id = handovers.insert(h);
        h.setHandoverId(id);

        // Mark current leg completed and prime next leg
        handovers.completeLeg(legId, request.handoverTime());
        handovers.activateNextLeg(leg.planId(), leg.sequenceNo() + 1);

        // Plan and order remain ACTIVE / IN_TRANSIT (intermediate rule)
        return toStaffResponse(h, order.orderId(), leg.planId());
    }

    private OperationalCompletionReceipt processFinalDelivery(long legId,
            FinalDeliveryHandoverCreateRequest request, long staffId) {
        validatePastOrPresent(request.handoverTime());
        validateEvidenceJson(request.evidenceFilesJson());

        LegRow leg = findLegOrThrow(legId);
        OrderScopeRow order = findOrderScopeOrThrow(legId);

        if (!handovers.isLastLegOfPlan(legId, leg.planId())) {
            throw new OrderException(HttpStatus.BAD_REQUEST, "NOT_FINAL_LEG",
                    "Final delivery can only be executed on the final "
                            + "destination leg of the plan.");
        }
        validateAfterDeparture(request.handoverTime(),
                leg.actualDeparture());

        OrderReceiverRow expectedReceiver = handovers.findOrderReceiver(
                order.orderId()).orElse(null);

        validator.validateFinalDelivery(order.orderId(), request,
                expectedReceiver);

        var h = new TripHandover();
        h.setLegId(legId);
        h.setRecordedBy(staffId);
        h.setHandoverType(HandoverType.FINAL_RECEIVER.name());
        h.setHandoverTime(request.handoverTime());
        h.setReceivedByName(request.receivedByName().trim());
        h.setReceiverAuthority(request.receiverAuthority());
        h.setReceiverSignatureFileKey(request.receiverSignatureFileKey());
        h.setHorseConditionStatus(request.horseConditionStatus().name());
        h.setOutcome(request.outcome().name());
        h.setExceptionNotes(request.exceptionNotes());
        h.setPhotoFileKey(request.photoFileKey());
        h.setLocationDescription(request.locationDescription());
        h.setEvidenceFilesJson(request.evidenceFilesJson());

        long id = handovers.insert(h);
        h.setHandoverId(id);

        OffsetDateTime now = OffsetDateTime.now(clock);
        handovers.completeLeg(legId, request.handoverTime());
        handovers.completePlan(leg.planId());
        handovers.deliverOrder(order.orderId(), now);

        return new OperationalCompletionReceipt(
                id,
                order.orderId(),
                leg.planId(),
                legId,
                "DELIVERED",
                "COMPLETED",
                now,
                h.getReceivedByName(),
                h.getHorseConditionStatus(),
                true
        );
    }

    private void verifyDriverAssignment(long legId, CurrentActor actor) {
        if (actor.staffId() == null) {
            throw new AccessDeniedException("Driver staff profile required.");
        }
        var assignment = legs.findDriverAssignment(legId, actor.staffId())
                .or(() -> legs.findCompletedDriverAssignment(legId,
                        actor.staffId()));
        if (assignment.isEmpty()) {
            throw new AccessDeniedException("Driver is not actively assigned "
                    + "to leg: " + legId);
        }
    }

    private void verifyCustomerOrderOwnership(long orderId) {
        CurrentActor actor = actors.current();
        if (actor.customerId() == null) {
            throw new AccessDeniedException("Customer profile required.");
        }
        var order = legs.findOrderByOrderId(orderId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                        "Transport order not found: " + orderId));

        if (order.customerId() != actor.customerId()) {
            throw new AccessDeniedException("Access to order " + orderId
                    + " is forbidden.");
        }
    }

    private LegRow findLegOrThrow(long legId) {
        return legs.findLegById(legId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "LEG_NOT_FOUND",
                        "Transport leg not found: " + legId));
    }

    private OrderScopeRow findOrderScopeOrThrow(long legId) {
        return legs.findOrderByLegId(legId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                        "Parent order not found for leg: " + legId));
    }

    private TripHandover findHandoverOrThrow(long handoverId) {
        return handovers.findById(handoverId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "HANDOVER_NOT_FOUND",
                        "Trip handover not found: " + handoverId));
    }

    private void validatePastOrPresent(OffsetDateTime time) {
        if (time == null) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "TIMESTAMP_REQUIRED", "HandoverTime is required.");
        }
        OffsetDateTime threshold = OffsetDateTime.now(clock).plusMinutes(5);
        if (time.isAfter(threshold)) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "FUTURE_TIMESTAMP_NOT_PERMITTED",
                    "HandoverTime cannot be in the future: " + time);
        }
    }

    private void validateAfterDeparture(OffsetDateTime occurredAt,
            OffsetDateTime actualDeparture) {
        if (actualDeparture != null && occurredAt.isBefore(actualDeparture)) {
            throw new OrderException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "HANDOVER_TIME_BEFORE_DEPARTURE",
                    "Handover time cannot precede the actual leg departure.");
        }
    }

    private void validateEvidenceJson(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            mapper.readTree(json);
        } catch (Exception e) {
            throw new OrderException(HttpStatus.BAD_REQUEST, "INVALID_JSON",
                    "EvidenceFilesJSON must be valid JSON: " + e.getMessage());
        }
    }

    private TripHandoverResponse toStaffResponse(TripHandover h, long orderId,
            long planId) {
        String staffName = legs.findStaffName(h.getRecordedBy());

        return new TripHandoverResponse(
                h.getHandoverId() == null ? 0L : h.getHandoverId(),
                h.getLegId(),
                orderId,
                planId,
                h.getRecordedBy(),
                staffName,
                h.getHandoverType(),
                h.getHandoverTime(),
                h.getReceivedByName(),
                h.getReceiverAuthority(),
                h.getReceiverSignatureFileKey(),
                h.getHorseConditionStatus(),
                h.getOutcome(),
                h.getExceptionNotes(),
                h.getPhotoFileKey(),
                h.getLocationDescription(),
                h.getCustomerConfirmedAt(),
                h.getCustomerDecision(),
                h.getCustomerFeedback(),
                h.getCustomerRespondedAt(),
                h.getRespondedByCustomerId(),
                h.getEvidenceFilesJson()
        );
    }

    private CustomerHandoverResponse toCustomerResponse(TripHandover h,
            long orderId) {
        return new CustomerHandoverResponse(
                h.getHandoverId() == null ? 0L : h.getHandoverId(),
                orderId,
                h.getHandoverType(),
                h.getHandoverTime(),
                h.getReceivedByName(),
                h.getHorseConditionStatus(),
                h.getOutcome(),
                h.getPhotoFileKey(),
                h.getReceiverSignatureFileKey(),
                h.getLocationDescription(),
                h.getCustomerDecision(),
                h.getCustomerFeedback(),
                h.getCustomerRespondedAt()
        );
    }
}
