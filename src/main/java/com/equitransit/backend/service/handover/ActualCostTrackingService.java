/*
 * ActualCostTrackingService
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Business engine for internal actual supplier expenses & profit.
 */
package com.equitransit.backend.service.handover;

import com.equitransit.backend.dto.handover.request.ActualCostCreateRequest;
import com.equitransit.backend.dto.handover.response.ActualCostResponse;
import com.equitransit.backend.dto.handover.response.PlanProfitabilityResponse;
import com.equitransit.backend.entity.handover.ActualCost;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.handover.ActualCostRepository;
import com.equitransit.backend.repository.handover.TripHandoverRepository;
import com.equitransit.backend.repository.handover.TripHandoverRepository.PlanRow;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks internal actual supplier expenditures and executive profit margins.
 */
@Service
public class ActualCostTrackingService {

    private final ActualCostRepository costs;
    private final TripHandoverRepository handovers;
    private final LegExecutionRepository legs;
    private final CurrentActorService actors;
    private final Clock clock;

    /**
     * Creates actual cost tracking service.
     *
     * @param costs actual cost repository
     * @param handovers handover & plan repository
     * @param legs leg & staff repository
     * @param actors live actor resolver
     * @param clock system UTC clock
     */
    public ActualCostTrackingService(ActualCostRepository costs,
            TripHandoverRepository handovers,
            LegExecutionRepository legs,
            CurrentActorService actors,
            Clock clock) {
        this.costs = costs;
        this.handovers = handovers;
        this.legs = legs;
        this.actors = actors;
        this.clock = clock;
    }

    /**
     * Records an internal actual supplier cost incurred on a plan.
     *
     * @param planId operational plan PK
     * @param request cost payload
     * @return created actual cost response
     */
    @Transactional
    public ActualCostResponse recordActualCost(long planId,
            ActualCostCreateRequest request) {
        CurrentActor actor = actors.current();
        if (actor.staffId() == null) {
            throw new AccessDeniedException("Staff profile required.");
        }

        findPlanOrThrow(planId);

        if (request.legId() != null) {
            if (!costs.legBelongsToPlan(request.legId(), planId)) {
                throw new OrderException(HttpStatus.BAD_REQUEST,
                        "LEG_NOT_IN_PLAN",
                        "Leg " + request.legId() + " does not belong to "
                                + "plan " + planId);
            }
        }

        if (request.amount().compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "NEGATIVE_COST_AMOUNT",
                    "Cost amount cannot be negative: " + request.amount());
        }

        validatePastOrPresent(request.incurredAt());

        var cost = new ActualCost();
        cost.setPlanId(planId);
        cost.setLegId(request.legId());
        cost.setCostCode(request.costCode().name());
        cost.setAmount(request.amount());
        cost.setCurrency(request.currency().toUpperCase(java.util.Locale.ROOT));
        cost.setIncurredAt(request.incurredAt());
        cost.setDescription(request.description());
        cost.setRecordedBy(actor.staffId());
        cost.setEvidenceFileKey(request.evidenceFileKey());

        long id = costs.insert(cost);
        cost.setCostId(id);

        return toResponse(cost);
    }

    /**
     * Reads all internal actual costs logged for a transport plan.
     *
     * @param planId operational plan PK
     * @return list of actual cost responses
     */
    @Transactional(readOnly = true)
    public List<ActualCostResponse> getActualCostsForPlan(long planId) {
        findPlanOrThrow(planId);
        return costs.findByPlanId(planId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Calculates plan profitability comparing customer quote to actual costs.
     * Restricted to MANAGER role.
     *
     * @param planId operational plan PK
     * @return executive profitability analysis
     */
    @Transactional(readOnly = true)
    public PlanProfitabilityResponse getPlanProfitability(long planId) {
        CurrentActor actor = actors.current();
        if (!"MANAGER".equals(actor.roleCode())) {
            throw new AccessDeniedException("Manager role required.");
        }

        PlanRow plan = findPlanOrThrow(planId);

        BigDecimal quoted = costs.findOrderTotalQuotedAmount(plan.orderId());
        BigDecimal actual = costs.findTotalActualCost(planId);
        BigDecimal grossProfit = quoted.subtract(actual);

        BigDecimal marginPercent = BigDecimal.ZERO;
        if (quoted.compareTo(BigDecimal.ZERO) > 0) {
            marginPercent = grossProfit.divide(quoted, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        int count = costs.countByPlanId(planId);

        return new PlanProfitabilityResponse(
                planId,
                plan.orderId(),
                "EUR",
                quoted,
                actual,
                grossProfit,
                marginPercent,
                count
        );
    }

    private PlanRow findPlanOrThrow(long planId) {
        return handovers.findPlan(planId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
                        "Operational plan not found: " + planId));
    }

    private void validatePastOrPresent(OffsetDateTime time) {
        if (time == null) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "TIMESTAMP_REQUIRED", "IncurredAt timestamp is required.");
        }
        OffsetDateTime threshold = OffsetDateTime.now(clock).plusMinutes(5);
        if (time.isAfter(threshold)) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "FUTURE_TIMESTAMP_NOT_PERMITTED",
                    "IncurredAt cannot be in the future: " + time);
        }
    }

    private ActualCostResponse toResponse(ActualCost c) {
        String staffName = c.getRecordedBy() != null
                ? legs.findStaffName(c.getRecordedBy()) : null;

        return new ActualCostResponse(
                c.getCostId() == null ? 0L : c.getCostId(),
                c.getPlanId(),
                c.getLegId(),
                c.getCostCode(),
                c.getAmount(),
                c.getCurrency(),
                c.getIncurredAt(),
                c.getDescription(),
                c.getRecordedBy(),
                staffName,
                c.getEvidenceFileKey()
        );
    }
}
