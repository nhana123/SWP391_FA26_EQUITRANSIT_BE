/*
 * CustomerHandoverController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller for customer delivery feedback and confirmation.
 */
package com.equitransit.backend.controller.handover;

import com.equitransit.backend.dto.handover.request.CustomerHandoverDecisionRequest;
import com.equitransit.backend.dto.handover.response.CustomerHandoverResponse;
import com.equitransit.backend.service.handover.TripHandoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller allowing customers to view delivery receipts and submit feedback.
 */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer delivery handovers")
@RequestMapping(value = "/api/customer/transport-orders/{orderId}/handovers",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerHandoverController {

    private final TripHandoverService handovers;

    /**
     * Creates customer handover controller.
     * @param handovers trip handover service
     */
    public CustomerHandoverController(TripHandoverService handovers) {
        this.handovers = handovers;
    }

    /**
     * Reads all delivery handovers for owned transport order.
     * @param orderId parent order key
     * @return redacted list of delivery handovers
     */
    @GetMapping
    @Operation(summary = "Read delivery handovers for customer order",
            description = "Returns redacted delivery records with supplier "
                    + "costs isolated.")
    public ResponseEntity<List<CustomerHandoverResponse>> getHandovers(
            @PathVariable @Positive long orderId) {
        var list = handovers.getCustomerHandovers(orderId);
        return ResponseEntity.ok(list);
    }

    /**
     * Reads one delivery handover for owned transport order.
     * @param orderId parent order key
     * @param handoverId handover PK
     * @return redacted customer delivery response
     */
    @GetMapping("/{handoverId}")
    @Operation(summary = "Read one delivery handover by ID",
            description = "Returns redacted delivery record for customer.")
    public ResponseEntity<CustomerHandoverResponse> getHandover(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long handoverId) {
        var response = handovers.getCustomerHandoverById(orderId, handoverId);
        return ResponseEntity.ok(response);
    }

    /**
     * Submits customer delivery acceptance, feedback, or exception dispute.
     * @param orderId parent order key
     * @param handoverId handover PK
     * @param request customer decision payload
     * @return updated delivery response
     */
    @PutMapping(value = "/{handoverId}/decision",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit delivery acceptance or dispute decision",
            description = "Customer decides ACCEPTED, EXCEPTION, "
                    + "or NOT_DELIVERED.")
    public ResponseEntity<CustomerHandoverResponse> submitDecision(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long handoverId,
            @RequestBody @Valid CustomerHandoverDecisionRequest request) {
        var response = handovers.recordCustomerDecision(orderId, handoverId,
                request);
        return ResponseEntity.ok(response);
    }
}
