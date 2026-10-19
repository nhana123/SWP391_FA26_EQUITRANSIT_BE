/*
 * OperationalAdjustmentController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller for coordinator proposals and manager approvals.
 */
package com.equitransit.backend.controller.incident;

import com.equitransit.backend.dto.incident.request.AdjustmentApprovalRequest;
import com.equitransit.backend.dto.incident.request.OperationalAdjustmentCreateRequest;
import com.equitransit.backend.dto.incident.response.OperationalAdjustmentResponse;
import com.equitransit.backend.dto.incident.response.PlanEvolutionReceipt;
import com.equitransit.backend.service.incident.OperationalAdjustmentService;
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
 * Controller for proposing operational adjustments and manager approval.
 */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Operational adjustment coordination and approval")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class OperationalAdjustmentController {

    private final OperationalAdjustmentService adjustments;

    /**
     * Creates operational adjustment controller.
     * @param adjustments operational adjustment service
     */
    public OperationalAdjustmentController(
            OperationalAdjustmentService adjustments) {
        this.adjustments = adjustments;
    }

    /**
     * Proposes an operational adjustment linked to a transport plan.
     * @param planId plan identifier
     * @param request proposal payload
     * @return created adjustment response
     */
    @PostMapping(value = "/api/coordinator/plans/{planId}/adjustments",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Propose an operational adjustment to a plan")
    public ResponseEntity<OperationalAdjustmentResponse> proposeAdjustment(
            @PathVariable @Positive long planId,
            @RequestBody @Valid OperationalAdjustmentCreateRequest request) {
        var response = adjustments.proposeAdjustment(planId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists adjustments proposed for a specific plan.
     * @param planId plan identifier
     * @return list of adjustments
     */
    @GetMapping(value = "/api/coordinator/plans/{planId}/adjustments")
    @Operation(summary = "List adjustments for a transport plan")
    public List<OperationalAdjustmentResponse> listPlanAdjustments(
            @PathVariable @Positive long planId) {
        return adjustments.listByPlan(planId);
    }

    /**
     * Reads a specific operational adjustment.
     * @param adjustmentId adjustment primary key
     * @return adjustment details
     */
    @GetMapping(value = "/api/coordinator/adjustments/{adjustmentId}")
    @Operation(summary = "Read operational adjustment details")
    public OperationalAdjustmentResponse getAdjustment(
            @PathVariable @Positive long adjustmentId) {
        return adjustments.getById(adjustmentId);
    }

    /**
     * Approves an adjustment and executes plan evolution (BR-11).
     * @param adjustmentId adjustment primary key
     * @param request approval remarks
     * @return plan evolution receipt
     */
    @PostMapping(value = "/api/manager/adjustments/{adjustmentId}/approve",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Manager approves adjustment and evolves plan",
            description = "Fails with 409 Conflict if additional cost > 0 "
                    + "and customer has not accepted.")
    public PlanEvolutionReceipt approveAdjustment(
            @PathVariable @Positive long adjustmentId,
            @RequestBody(required = false) @Valid
            AdjustmentApprovalRequest request) {
        var req = request != null ? request
                : new AdjustmentApprovalRequest(null);

        return adjustments.approveAdjustment(adjustmentId, req);
    }

    /**
     * Rejects an operational adjustment proposal.
     * @param adjustmentId adjustment primary key
     * @param request rejection remarks
     * @return updated adjustment response
     */
    @PostMapping(value = "/api/manager/adjustments/{adjustmentId}/reject",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Manager rejects an operational adjustment")
    public OperationalAdjustmentResponse rejectAdjustment(
            @PathVariable @Positive long adjustmentId,
            @RequestBody(required = false) @Valid
            AdjustmentApprovalRequest request) {
        var req = request != null ? request
                : new AdjustmentApprovalRequest(null);

        return adjustments.rejectAdjustment(adjustmentId, req);
    }
}
