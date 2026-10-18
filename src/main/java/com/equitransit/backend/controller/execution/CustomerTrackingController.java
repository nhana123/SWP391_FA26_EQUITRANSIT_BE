/*
 * CustomerTrackingController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller serving customer journey tracking timeline.
 */
package com.equitransit.backend.controller.execution;

import com.equitransit.backend.dto.execution.response.JourneyTimelineResponse;
import com.equitransit.backend.service.execution.CustomerTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Controller delivering safe public tracking timeline for order customers. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer journey tracking")
public class CustomerTrackingController {

    private final CustomerTrackingService tracking;

    /**
     * Creates customer tracking controller.
     * @param tracking tracking service
     */
    public CustomerTrackingController(CustomerTrackingService tracking) {
        this.tracking = tracking;
    }

    /**
     * Customer views live journey tracking timeline with public milestones.
     *
     * @param orderId target transport order ID
     * @return journey tracking timeline
     */
    @Operation(summary = "View customer journey timeline with active welfare")
    @GetMapping(value = {
            "/api/customer/transport-orders/{orderId}/tracking",
            "/api/transport-orders/{orderId}/tracking"
    }, produces = MediaType.APPLICATION_JSON_VALUE)
    public JourneyTimelineResponse getTrackingTimeline(
            @PathVariable @Positive long orderId) {
        return tracking.getTrackingTimeline(orderId);
    }
}
