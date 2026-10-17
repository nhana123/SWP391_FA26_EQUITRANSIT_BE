/*
 * OperationalPlanController
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Build and confirm a multimodal operational plan.
 */
package com.equitransit.backend.controller;

import com.equitransit.backend.dto.plan.PlanModels.PlanConfirmRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanDetailResponse;
import com.equitransit.backend.dto.plan.PlanModels.PlanDraftRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanPageResponse;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanRoadAssignmentRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentRequest;
import com.equitransit.backend.service.OperationalPlanService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin F10 planning controller; service owns roles, locks and invariants. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Multimodal operational planning")
@RequestMapping(value = "/api/transport-orders/{orderId}/plans",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class OperationalPlanController {

    private final OperationalPlanService service;

    /** @param service transactional operational planning service */
    public OperationalPlanController(OperationalPlanService service) {
        this.service = service;
    }

    /** @param orderId parent @param page zero-based @param size bounded
     * @return paginated plan summaries */
    @GetMapping
    public PlanPageResponse list(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(schema = @Schema(example = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @Parameter(schema = @Schema(example = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(orderId, page, size);
    }

    /** @param orderId parent @param planId plan ID @return full plan detail */
    @GetMapping("/{planId}")
    public PlanDetailResponse get(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "311") @PathVariable @Positive long planId) {
        return service.get(orderId, planId);
    }

    /** @param orderId parent @param body draft legs command
     * @return created versioned draft plan */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlanDetailResponse createDraft(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Valid @RequestBody PlanDraftRequest body) {
        return service.createDraft(orderId, body);
    }

    /** @param orderId parent @param planId plan ID @param legId ROAD leg ID
     * @param body vehicle and driver assignment @return updated plan */
    @PostMapping(value = "/{planId}/legs/{legId}/road-assignment",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlanDetailResponse assignRoad(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "311") @PathVariable @Positive long planId,
            @Parameter(example = "401") @PathVariable @Positive long legId,
            @Valid @RequestBody PlanRoadAssignmentRequest body) {
        return service.assignRoad(orderId, planId, legId, body);
    }

    /** @param orderId parent @param planId plan ID @param legId AIR leg ID
     * @param body stall equipment assignment @return updated plan */
    @PostMapping(value = "/{planId}/legs/{legId}/stall-assignment",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlanDetailResponse assignStall(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "311") @PathVariable @Positive long planId,
            @Parameter(example = "402") @PathVariable @Positive long legId,
            @Valid @RequestBody PlanStallAssignmentRequest body) {
        return service.assignStall(orderId, planId, legId, body);
    }

    /** @param orderId parent @param planId plan ID @param legId AIR leg ID
     * @param body flight reservation command @return updated plan */
    @PostMapping(value = "/{planId}/legs/{legId}/flight-reservation",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlanDetailResponse reserveFlight(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "311") @PathVariable @Positive long planId,
            @Parameter(example = "402") @PathVariable @Positive long legId,
            @Valid @RequestBody PlanFlightReservationRequest body) {
        return service.reserveFlight(orderId, planId, legId, body);
    }

    /** @param orderId parent @param planId plan ID @param body quarantine cmd
     * @return updated plan */
    @PostMapping(value = "/{planId}/quarantine-reservation",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlanDetailResponse reserveQuarantine(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "311") @PathVariable @Positive long planId,
            @Valid @RequestBody PlanQuarantineReservationRequest body) {
        return service.reserveQuarantine(orderId, planId, body);
    }

    /** @param orderId parent @param planId plan ID @param body insurance cmd
     * @return updated plan */
    @PostMapping(value = "/{planId}/insurance-policy",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlanDetailResponse verifyInsurance(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "311") @PathVariable @Positive long planId,
            @Valid @RequestBody PlanInsurancePolicyRequest body) {
        return service.verifyInsurance(orderId, planId, body);
    }

    /** @param orderId parent @param planId plan ID @param body confirm command
     * @return confirmed plan detail */
    @PostMapping(value = "/{planId}/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlanDetailResponse confirm(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "311") @PathVariable @Positive long planId,
            @Valid @RequestBody PlanConfirmRequest body) {
        return service.confirm(orderId, planId, body);
    }
}
