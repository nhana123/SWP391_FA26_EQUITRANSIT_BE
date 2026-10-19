/*
 * CoordinatorHandoverController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller for coordinator/manager handover operations.
 */
package com.equitransit.backend.controller.handover;

import com.equitransit.backend.dto.handover.request.FinalDeliveryHandoverCreateRequest;
import com.equitransit.backend.dto.handover.request.IntermediateHandoverCreateRequest;
import com.equitransit.backend.dto.handover.response.OperationalCompletionReceipt;
import com.equitransit.backend.dto.handover.response.TripHandoverResponse;
import com.equitransit.backend.service.handover.TripHandoverService;
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
 * Controller allowing coordinators to record and inspect handovers.
 */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Coordinator trip handovers")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class CoordinatorHandoverController {

    private final TripHandoverService handovers;

    /**
     * Creates coordinator handover controller.
     * @param handovers trip handover service
     */
    public CoordinatorHandoverController(TripHandoverService handovers) {
        this.handovers = handovers;
    }

    /**
     * Records an intermediate handover on a multimodal leg.
     * @param legId transport leg key
     * @param request intermediate handover payload
     * @return created handover response
     */
    @PostMapping(value = "/api/coordinator/legs/{legId}/intermediate-handover",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Record intermediate leg handover by coordinator",
            description = "Logs custody transfer at cargo hub or quarantine.")
    public ResponseEntity<TripHandoverResponse> recordIntermediate(
            @PathVariable @Positive long legId,
            @RequestBody @Valid IntermediateHandoverCreateRequest request) {
        var response = handovers
                .recordIntermediateByCoordinator(legId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Records final delivery on behalf of destination staff.
     * @param legId final transport leg key
     * @param request final delivery payload
     * @return operational completion receipt
     */
    @PostMapping(value = "/api/coordinator/legs/{legId}/final-delivery",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Record final delivery by coordinator",
            description = "Validates clearance and authorized recipient.")
    public ResponseEntity<OperationalCompletionReceipt> recordFinalDelivery(
            @PathVariable @Positive long legId,
            @RequestBody @Valid FinalDeliveryHandoverCreateRequest request) {
        var receipt = handovers
                .recordFinalDeliveryByCoordinator(legId, request);
        return ResponseEntity.ok(receipt);
    }

    /**
     * Reads all handovers for a leg.
     * @param legId transport leg key
     * @return list of handovers
     */
    @GetMapping("/api/coordinator/legs/{legId}/handovers")
    @Operation(summary = "Read handovers for leg by coordinator",
            description = "Inspects intermediate and final handovers on leg.")
    public ResponseEntity<List<TripHandoverResponse>> getHandoversForLeg(
            @PathVariable @Positive long legId) {
        var list = handovers.getHandoversForLeg(legId);
        return ResponseEntity.ok(list);
    }

    /**
     * Reads one handover by ID.
     * @param handoverId handover PK
     * @return handover details
     */
    @GetMapping("/api/coordinator/handovers/{handoverId}")
    @Operation(summary = "Read handover details by ID",
            description = "Full staff-visible projection of handover event.")
    public ResponseEntity<TripHandoverResponse> getHandoverById(
            @PathVariable @Positive long handoverId) {
        var response = handovers.getHandoverById(handoverId);
        return ResponseEntity.ok(response);
    }
}
