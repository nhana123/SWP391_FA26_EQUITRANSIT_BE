/*
 * OperationalAdjustmentService
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Business engine for operational adjustments, consent & evolution.
 */
package com.equitransit.backend.service.incident;

import com.equitransit.backend.dto.incident.AdjustmentApprovalStatus;
import com.equitransit.backend.dto.incident.CustomerDecision;
import com.equitransit.backend.dto.incident.request.AdjustmentApprovalRequest;
import com.equitransit.backend.dto.incident.request.CustomerAdjustmentDecisionRequest;
import com.equitransit.backend.dto.incident.request.OperationalAdjustmentCreateRequest;
import com.equitransit.backend.dto.incident.response.CustomerAdjustmentResponse;
import com.equitransit.backend.dto.incident.response.OperationalAdjustmentResponse;
import com.equitransit.backend.dto.incident.response.PlanEvolutionReceipt;
import com.equitransit.backend.entity.incident.OperationalAdjustment;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.incident.IncidentReportRepository;
import com.equitransit.backend.repository.incident.OperationalAdjustmentRepository;
import com.equitransit.backend.repository.incident.OperationalAdjustmentRepository.PlanRow;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrating adjustment proposals, customer consent and evolution.
 */
@Service
public class OperationalAdjustmentService {

    private final OperationalAdjustmentRepository adjustments;
    private final IncidentReportRepository incidents;
    private final LegExecutionRepository legs;
    private final CurrentActorService actors;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * Creates operational adjustment service.
     * @param adjustments adjustment repository
     * @param incidents incident repository
     * @param legs leg execution repository
     * @param actors actor identity service
     * @param mapper json object mapper
     * @param clock system clock
     */
    public OperationalAdjustmentService(
            OperationalAdjustmentRepository adjustments,
            IncidentReportRepository incidents,
            LegExecutionRepository legs,
            CurrentActorService actors,
            ObjectMapper mapper,
            Clock clock) {
        this.adjustments = adjustments;
        this.incidents = incidents;
        this.legs = legs;
        this.actors = actors;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Proposes an operational adjustment linked to a transport plan.
     * @param planId plan identifier
     * @param request adjustment proposal payload
     * @return created adjustment response
     */
    @Transactional
    public OperationalAdjustmentResponse proposeAdjustment(long planId,
            OperationalAdjustmentCreateRequest request) {
        CurrentActor actor = actors.current();

        if (actor.staffId() == null) {
            throw new AccessDeniedException("Staff profile required.");
        }
        var plan = adjustments.findPlan(planId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
                        "Transport plan not found: " + planId));

        validateProposedChangesJson(request.proposedChanges());
        if (request.incidentId() != null
                && incidents.findById(request.incidentId()).isEmpty()) {
            throw new OrderException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND",
                    "Incident report not found: " + request.incidentId());
        }

        var adj = new OperationalAdjustment();

        adj.setPlanId(planId);
        adj.setIncidentId(request.incidentId());
        adj.setChangeRequestId(request.changeRequestId());
        adj.setProposedBy(actor.staffId());
        adj.setProposedChanges(request.proposedChanges());
        adj.setAdditionalCostEstimate(request.additionalCostEstimate());
        adj.setProposedAt(OffsetDateTime.now(clock));

        if (request.additionalCostEstimate()
                .compareTo(BigDecimal.ZERO) > 0) {
            adj.setCustomerDecision(CustomerDecision.PENDING.name());
            adj.setApprovalStatus(
                    AdjustmentApprovalStatus.CUSTOMER_PENDING.name());
        } else {
            adj.setCustomerDecision(CustomerDecision.ACCEPTED.name());
            adj.setApprovalStatus(AdjustmentApprovalStatus.PROPOSED.name());
        }

        long id = adjustments.save(adj);

        adj.setAdjustmentId(id);
        return toStaffResponse(adj, plan.orderId());
    }

    /**
     * Lists adjustments proposed for a specific plan.
     * @param planId plan identifier
     * @return list of adjustment responses
     */
    @Transactional(readOnly = true)
    public List<OperationalAdjustmentResponse> listByPlan(long planId) {
        var plan = adjustments.findPlan(planId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
                        "Transport plan not found: " + planId));

        return adjustments.findByPlanId(planId).stream()
                .map(a -> toStaffResponse(a, plan.orderId())).toList();
    }

    /**
     * Reads a specific operational adjustment.
     * @param adjustmentId adjustment primary key
     * @return adjustment response
     */
    @Transactional(readOnly = true)
    public OperationalAdjustmentResponse getById(long adjustmentId) {
        var adj = adjustments.findById(adjustmentId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND,
                        "ADJUSTMENT_NOT_FOUND",
                        "Adjustment not found: " + adjustmentId));
        var plan = adjustments.findPlan(adj.getPlanId()).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
                        "Plan not found: " + adj.getPlanId()));

        return toStaffResponse(adj, plan.orderId());
    }

    /**
     * Records customer commercial consent on an operational adjustment.
     * @param orderId parent order identifier
     * @param adjustmentId adjustment primary key
     * @param request customer decision payload
     * @return updated customer adjustment response
     */
    @Transactional
    public CustomerAdjustmentResponse recordCustomerDecision(long orderId,
            long adjustmentId, CustomerAdjustmentDecisionRequest request) {
        CurrentActor actor = actors.current();

        verifyCustomerOrderOwnership(orderId, actor);
        var adj = adjustments.findById(adjustmentId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND,
                        "ADJUSTMENT_NOT_FOUND",
                        "Adjustment not found: " + adjustmentId));
        var plan = adjustments.findPlan(adj.getPlanId()).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
                        "Plan not found: " + adj.getPlanId()));

        if (plan.orderId() != orderId) {
            throw new OrderException(HttpStatus.NOT_FOUND,
                    "ADJUSTMENT_NOT_FOUND",
                    "Adjustment does not belong to order: " + orderId);
        }
        if (!CustomerDecision.PENDING.name()
                .equals(adj.getCustomerDecision())) {
            throw new OrderException(HttpStatus.CONFLICT, "ALREADY_DECIDED",
                    "Adjustment customer decision is already "
                            + adj.getCustomerDecision());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        String newStatus;

        if (request.decision() == CustomerDecision.ACCEPTED) {
            newStatus = AdjustmentApprovalStatus.PROPOSED.name();
        } else {
            newStatus = AdjustmentApprovalStatus.REJECTED.name();
        }

        adjustments.recordCustomerDecision(adjustmentId,
                request.decision().name(), actor.customerId(), now,
                request.responseNotes(), newStatus);

        adj.setCustomerDecision(request.decision().name());
        adj.setRespondedByCustomerId(actor.customerId());
        adj.setCustomerRespondedAt(now);
        adj.setCustomerResponseNotes(request.responseNotes());
        adj.setApprovalStatus(newStatus);
        return toCustomerResponse(adj, orderId);
    }

    /**
     * Lists customer-visible adjustments for an owned order.
     * @param orderId order identifier
     * @return list of redacted adjustment responses
     */
    @Transactional(readOnly = true)
    public List<CustomerAdjustmentResponse> listCustomerAdjustments(
            long orderId) {
        CurrentActor actor = actors.current();

        verifyCustomerOrderOwnership(orderId, actor);
        return adjustments.findByOrderId(orderId).stream()
                .map(a -> toCustomerResponse(a, orderId)).toList();
    }

    /**
     * Reads a customer-visible adjustment.
     * @param orderId order identifier
     * @param adjustmentId adjustment primary key
     * @return redacted customer adjustment response
     */
    @Transactional(readOnly = true)
    public CustomerAdjustmentResponse getCustomerAdjustment(long orderId,
            long adjustmentId) {
        CurrentActor actor = actors.current();

        verifyCustomerOrderOwnership(orderId, actor);
        var adj = adjustments.findById(adjustmentId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND,
                        "ADJUSTMENT_NOT_FOUND",
                        "Adjustment not found: " + adjustmentId));
        var plan = adjustments.findPlan(adj.getPlanId()).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
                        "Plan not found: " + adj.getPlanId()));

        if (plan.orderId() != orderId) {
            throw new OrderException(HttpStatus.NOT_FOUND,
                    "ADJUSTMENT_NOT_FOUND",
                    "Adjustment does not belong to order: " + orderId);
        }
        return toCustomerResponse(adj, orderId);
    }

    /**
     * Approves an adjustment and triggers operational plan evolution (BR-11).
     * @param adjustmentId adjustment primary key
     * @param request manager approval notes
     * @return receipt describing the newly evolved plan version
     */
    @Transactional
    public PlanEvolutionReceipt approveAdjustment(long adjustmentId,
            AdjustmentApprovalRequest request) {
        CurrentActor actor = actors.current();

        if (actor.staffId() == null) {
            throw new AccessDeniedException("Staff profile required.");
        }
        var adj = adjustments.findById(adjustmentId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND,
                        "ADJUSTMENT_NOT_FOUND",
                        "Adjustment not found: " + adjustmentId));

        if (AdjustmentApprovalStatus.APPLIED.name()
                .equals(adj.getApprovalStatus())) {
            throw new OrderException(HttpStatus.CONFLICT, "ALREADY_APPLIED",
                    "Adjustment is already applied.");
        }
        if (AdjustmentApprovalStatus.REJECTED.name()
                .equals(adj.getApprovalStatus())) {
            throw new OrderException(HttpStatus.CONFLICT, "ADJUSTMENT_REJECTED",
                    "Adjustment was rejected.");
        }

        if (adj.getAdditionalCostEstimate()
                .compareTo(BigDecimal.ZERO) > 0) {
            if (CustomerDecision.PENDING.name()
                    .equals(adj.getCustomerDecision())) {
                throw new OrderException(HttpStatus.CONFLICT,
                        "CUSTOMER_CONSENT_REQUIRED",
                        "Customer consent is required before approval.");
            }
            if (CustomerDecision.REJECTED.name()
                    .equals(adj.getCustomerDecision())) {
                throw new OrderException(HttpStatus.CONFLICT,
                        "CUSTOMER_REJECTED",
                        "Customer has rejected this adjustment proposal.");
            }
        }

        PlanRow oldPlan = adjustments.findPlan(adj.getPlanId())
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND,
                        "PLAN_NOT_FOUND",
                        "Plan not found: " + adj.getPlanId()));
        OffsetDateTime now = OffsetDateTime.now(clock);
        int newVersion = oldPlan.versionNo() + 1;

        long newPlanId = adjustments.insertEvolvedPlan(oldPlan.orderId(),
                oldPlan.revisionId(), actor.staffId(), newVersion,
                oldPlan.acceptedQuoteId(), oldPlan.plannedStartTime(),
                oldPlan.plannedEndTime(), now);

        int legCount = adjustments.cloneLegsToNewPlan(oldPlan.planId(),
                newPlanId);

        adjustments.supersedePlan(oldPlan.planId());
        adjustments.updateOrderCurrentPlan(oldPlan.orderId(), newPlanId);

        if (adj.getChangeRequestId() != null) {
            adjustments.updateOrderChangeRequest(adj.getChangeRequestId(),
                    "APPLIED", actor.staffId(), now,
                    "Applied via OperationalAdjustment #" + adjustmentId);
        }
        adjustments.recordManagerDecision(adjustmentId,
                AdjustmentApprovalStatus.APPLIED.name(), actor.staffId(), now);

        return new PlanEvolutionReceipt(
                adjustmentId,
                oldPlan.orderId(),
                oldPlan.planId(),
                newPlanId,
                newVersion,
                legCount,
                AdjustmentApprovalStatus.APPLIED.name(),
                now,
                "Operational adjustment applied. Plan evolved to version "
                        + newVersion + "."
        );
    }

    /**
     * Rejects an operational adjustment proposal.
     * @param adjustmentId adjustment primary key
     * @param request manager rejection payload
     * @return updated adjustment response
     */
    @Transactional
    public OperationalAdjustmentResponse rejectAdjustment(long adjustmentId,
            AdjustmentApprovalRequest request) {
        CurrentActor actor = actors.current();

        if (actor.staffId() == null) {
            throw new AccessDeniedException("Staff profile required.");
        }
        var adj = adjustments.findById(adjustmentId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND,
                        "ADJUSTMENT_NOT_FOUND",
                        "Adjustment not found: " + adjustmentId));
        var plan = adjustments.findPlan(adj.getPlanId()).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
                        "Plan not found: " + adj.getPlanId()));
        OffsetDateTime now = OffsetDateTime.now(clock);

        adjustments.recordManagerDecision(adjustmentId,
                AdjustmentApprovalStatus.REJECTED.name(),
                actor.staffId(), now);
        if (adj.getChangeRequestId() != null) {
            adjustments.updateOrderChangeRequest(adj.getChangeRequestId(),
                    "REJECTED", actor.staffId(), now,
                    request.approvalNotes());
        }
        adj.setApprovalStatus(AdjustmentApprovalStatus.REJECTED.name());
        adj.setApprovedBy(actor.staffId());
        adj.setDecidedAt(now);
        return toStaffResponse(adj, plan.orderId());
    }

    private void verifyCustomerOrderOwnership(long orderId,
            CurrentActor actor) {
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

    private void validateProposedChangesJson(String json) {
        if (json == null || json.isBlank()) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "INVALID_PROPOSED_CHANGES",
                    "ProposedChanges must not be blank.");
        }
        try {
            mapper.readTree(json);
        } catch (Exception e) {
            throw new OrderException(HttpStatus.BAD_REQUEST, "INVALID_JSON",
                    "ProposedChanges must be valid JSON: " + e.getMessage());
        }
    }

    private OperationalAdjustmentResponse toStaffResponse(
            OperationalAdjustment a, long orderId) {
        String propName = lookupStaffName(a.getProposedBy());
        String appName = a.getApprovedBy() != null
                ? lookupStaffName(a.getApprovedBy()) : null;

        return new OperationalAdjustmentResponse(
                a.getAdjustmentId() == null ? 0L : a.getAdjustmentId(),
                a.getPlanId() == null ? 0L : a.getPlanId(),
                orderId,
                a.getIncidentId(),
                a.getChangeRequestId(),
                a.getProposedBy() == null ? 0L : a.getProposedBy(),
                propName,
                a.getApprovedBy(),
                appName,
                a.getProposedChanges(),
                a.getAdditionalCostEstimate(),
                a.getApprovalStatus(),
                a.getProposedAt(),
                a.getDecidedAt(),
                a.getCustomerDecision(),
                a.getRespondedByCustomerId(),
                a.getCustomerRespondedAt(),
                a.getCustomerResponseNotes()
        );
    }

    private CustomerAdjustmentResponse toCustomerResponse(
            OperationalAdjustment a, long orderId) {
        String summary = extractCustomerSummary(a.getProposedChanges());

        return new CustomerAdjustmentResponse(
                a.getAdjustmentId() == null ? 0L : a.getAdjustmentId(),
                orderId,
                a.getAdditionalCostEstimate(),
                a.getCustomerDecision(),
                a.getApprovalStatus(),
                summary,
                a.getProposedAt(),
                a.getCustomerResponseNotes(),
                a.getCustomerRespondedAt()
        );
    }

    private String extractCustomerSummary(String json) {
        if (json == null || json.isBlank()) {
            return "Operational adjustment proposed.";
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (root.hasNonNull("summary")) {
                return root.get("summary").asText();
            }
            if (root.hasNonNull("reason")) {
                return root.get("reason").asText();
            }
        } catch (Exception ignored) {
            // fallback
        }
        return "Operational adjustment proposed.";
    }

    private String lookupStaffName(Long staffId) {
        if (staffId == null) {
            return null;
        }
        return legs.findStaffProfile(staffId)
                .map(s -> s.fullName())
                .orElse("Staff #" + staffId);
    }
}
