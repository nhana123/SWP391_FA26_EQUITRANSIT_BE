/*
 * ActualCostController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller for internal actual cost recording & profitability.
 */
package com.equitransit.backend.controller.handover;

import com.equitransit.backend.dto.handover.request.ActualCostCreateRequest;
import com.equitransit.backend.dto.handover.response.ActualCostResponse;
import com.equitransit.backend.dto.handover.response.PlanProfitabilityResponse;
import com.equitransit.backend.service.handover.ActualCostTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller allowing coordinators to track internal costs and margins.
 */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Actual cost tracking & profitability")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class ActualCostController {

    private final ActualCostTrackingService costs;

    /**
     * Creates actual cost controller.
     * @param costs actual cost tracking service
     */
    public ActualCostController(ActualCostTrackingService costs) {
        this.costs = costs;
    }

    /**
     * Records an internal actual supplier expenditure incurred on a plan.
     * @param planId operational plan PK
     * @param request cost payload
     * @return created actual cost response
     */
    @PostMapping(value = "/api/coordinator/plans/{planId}/actual-costs",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Record internal actual supplier cost",
            description = "Logs actual invoice expenditure on plan or leg. "
                    + "Strictly isolated from customer views.")
    public ResponseEntity<ActualCostResponse> recordActualCost(
            @PathVariable @Positive long planId,
            @RequestBody @Valid ActualCostCreateRequest request) {
        var response = costs.recordActualCost(planId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Reads all internal actual costs logged for a plan.
     * @param planId operational plan PK
     * @return list of actual costs
     */
    @GetMapping("/api/coordinator/plans/{planId}/actual-costs")
    @Operation(summary = "Read actual costs for plan",
            description = "Lists all internal supplier costs logged on plan.")
    public ResponseEntity<List<ActualCostResponse>> getActualCosts(
            @PathVariable @Positive long planId) {
        var list = costs.getActualCostsForPlan(planId);
        return ResponseEntity.ok(list);
    }

    /**
     * Calculates profitability comparing customer quote to actual costs.
     * @param planId operational plan PK
     * @return profitability summary
     */
    @GetMapping("/api/manager/plans/{planId}/profitability")
    @Operation(summary = "Read plan profitability summary",
            description = "Executive report comparing quote to actual costs. "
                    + "Restricted to MANAGER role.")
    public ResponseEntity<PlanProfitabilityResponse> getProfitability(
            @PathVariable @Positive long planId) {
        var response = costs.getPlanProfitability(planId);
        return ResponseEntity.ok(response);
    }
}
