package com.equitransit.backend.controller;

import com.equitransit.backend.dto.payment.BookingFlowStatus;
import com.equitransit.backend.service.BookingFlowService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner/live Manager/assigned Coordinator booking overview. */
@RestController
@Validated
@PreAuthorize("isAuthenticated()")
@RequestMapping(value = "/api/transport-orders", produces = "application/json")
public class BookingFlowController {
    private final BookingFlowService service;

    /** @param service domain access and financial gates */
    public BookingFlowController(BookingFlowService service) {
        this.service = service;
    }

    /** @param orderId parent @return current commercial booking gates */
    @GetMapping("/{orderId}/booking-flow-status")
    @Operation(summary = "Read current quote/deposit and assignment gates")
    public BookingFlowStatus status(@Positive @PathVariable long orderId) {
        return service.status(orderId);
    }
}
