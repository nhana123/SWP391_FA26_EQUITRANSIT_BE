/*
 * CustomerAdjustmentController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller for customer commercial consent on adjustments.
 */
package com.equitransit.backend.controller.incident;

import com.equitransit.backend.dto.incident.request.CustomerAdjustmentDecisionRequest;
import com.equitransit.backend.dto.incident.response.CustomerAdjustmentResponse;
import com.equitransit.backend.service.incident.OperationalAdjustmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller allowing customers to review adjustments and record consent.
 */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer operational adjustment consent")
@RequestMapping(value = "/api/customer/transport-orders/{orderId}/adjustments",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerAdjustmentController {

    private final OperationalAdjustmentService adjustments;

    /**
     * Creates customer adjustment controller.
     * @param adjustments operational adjustment service
     */
    public CustomerAdjustmentController(
            OperationalAdjustmentService adjustments) {
        this.adjustments = adjustments;
    }

    /**
     * Lists adjustments visible to the owning customer.
     * @param orderId transport order identifier
     * @return list of redacted adjustment responses
     */
    @GetMapping
    @Operation(summary = "List customer-facing adjustments for an order",
            description = "Internal supplier costs and notes are redacted.")
    public List<CustomerAdjustmentResponse> listAdjustments(
            @PathVariable @Positive long orderId) {
        return adjustments.listCustomerAdjustments(orderId);
    }

    /**
     * Reads a specific customer-facing adjustment.
     * @param orderId transport order identifier
     * @param adjustmentId adjustment primary key
     * @return redacted adjustment response
     */
    @GetMapping("/{adjustmentId}")
    @Operation(summary = "Read customer-facing adjustment details")
    public CustomerAdjustmentResponse getAdjustment(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long adjustmentId) {
        return adjustments.getCustomerAdjustment(orderId, adjustmentId);
    }

    /**
     * Records customer decision on a proposed operational adjustment.
     * @param orderId transport order identifier
     * @param adjustmentId adjustment primary key
     * @param request customer decision payload (ACCEPTED or REJECTED)
     * @return updated customer adjustment response
     */
    @PostMapping(value = "/{adjustmentId}/decision",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Record customer consent decision on an adjustment",
            description = "Accepting or rejecting the additional cost "
                    + "for the proposed route/plan change.")
    public CustomerAdjustmentResponse recordDecision(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long adjustmentId,
            @RequestBody @Valid CustomerAdjustmentDecisionRequest request) {
        return adjustments.recordCustomerDecision(orderId, adjustmentId,
                request);
    }
}
