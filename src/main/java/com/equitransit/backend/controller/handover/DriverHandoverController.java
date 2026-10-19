/*
 * DriverHandoverController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller for driver intermediate and final handovers.
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
 * Controller allowing assigned road drivers to record custody handovers.
 */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Driver trip handovers")
@RequestMapping(value = "/api/driver/legs/{legId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class DriverHandoverController {

    private final TripHandoverService handovers;

    /**
     * Creates driver handover controller.
     * @param handovers trip handover service
     */
    public DriverHandoverController(TripHandoverService handovers) {
        this.handovers = handovers;
    }

    /**
     * Records an intermediate handover to another carrier or quarantine.
     * @param legId transport leg key
     * @param request handover payload
     * @return created handover response
     */
    @PostMapping(value = "/intermediate-handover",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Record intermediate leg handover",
            description = "Driver completes current leg and primes next leg. "
                    + "Plan and order remain active.")
    public ResponseEntity<TripHandoverResponse> recordIntermediate(
            @PathVariable @Positive long legId,
            @RequestBody @Valid IntermediateHandoverCreateRequest request) {
        var response = handovers.recordIntermediateByDriver(legId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Records final delivery of horse to the authorized consignee.
     * @param legId final transport leg key
     * @param request delivery payload
     * @return operational completion receipt
     */
    @PostMapping(value = "/final-delivery",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Record final destination delivery",
            description = "Enforces quarantine clearance and receiver match. "
                    + "Marks leg, plan and order DELIVERED.")
    public ResponseEntity<OperationalCompletionReceipt> recordFinalDelivery(
            @PathVariable @Positive long legId,
            @RequestBody @Valid FinalDeliveryHandoverCreateRequest request) {
        var receipt = handovers.recordFinalDeliveryByDriver(legId, request);
        return ResponseEntity.ok(receipt);
    }

    /**
     * Reads all handover events recorded on this leg.
     * @param legId transport leg key
     * @return list of handovers
     */
    @GetMapping("/handovers")
    @Operation(summary = "Read handovers for leg",
            description = "Lists all intermediate or final handovers on leg.")
    public ResponseEntity<List<TripHandoverResponse>> getHandovers(
            @PathVariable @Positive long legId) {
        var list = handovers.getHandoversForLeg(legId);
        return ResponseEntity.ok(list);
    }
}
