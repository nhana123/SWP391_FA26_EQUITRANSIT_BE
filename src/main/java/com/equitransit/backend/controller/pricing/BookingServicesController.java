/*
 * BookingServicesController
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose owner-scoped service disclosure and confirmation contracts.
 */
package com.equitransit.backend.controller.pricing;

import com.equitransit.backend.dto.pricing.ServiceRateResponse;

import com.equitransit.backend.dto.pricing.ServicePreviewRequest;


import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.equitransit.backend.dto.pricing.request.ServiceConfirmationRequest;
import com.equitransit.backend.dto.pricing.response.ServiceConfirmationResponse;
import com.equitransit.backend.dto.pricing.response.ServiceDisclosureResponse;
import com.equitransit.backend.service.pricing.BookingServicesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * Thin adapter; owner, measurement, price and revision rules stay in service.
 */
@RestController
@Validated
@RequestMapping(value = "/api/transport-orders/{id}/services",
        produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Booking services")
public class BookingServicesController {

    private final BookingServicesService services;

    /** @param services authorized service-selection facade */
    public BookingServicesController(BookingServicesService services) {
        this.services = services;
    }

    /**
     * Publishes all charges; expanding Show all never creates new fees.
     * @param id owned order key
     * @return seven-section breakdown, pending states and measured options
     */
    @GetMapping
    @Operation(summary = "Read my booking services and all charges",
            description = "USD estimates, not payable amounts. Missing source "
                    + "quantity/rate is null and pending, not zero. Air "
                + "options "
                    + "use captured horse measurements and approved policies; "
                    + "measurement eligibility is not carrier confirmation. "
                    + "A usable current-revision F06 quotation supplies its "
                    + "retail snapshot and commercial assessments; PRICED "
                    + "quote rows are shown as ESTIMATED here. Supplier "
                    + "purchase costs and internal evidence stay private.")
    public ServiceDisclosureResponse get(@PathVariable @Positive long id) {
        return services.get(id);
    }

    /**
     * @param id owned order
     * @return all supported public retail options, including unselected ones
     */
    @GetMapping("/rates")
    @Operation(summary = "Read service option unit rates for my order",
            description = "Missing rate is null, not zero. Included services "
                    + "cannot be sold twice. Rates alone do not confirm "
                    + "quantities, legal applicability or carrier acceptance.")
    public java.util.List<
            ServiceRateResponse> rates(
                    @PathVariable @Positive long id) {
        return services.rates(id);
    }

    /**
     * @param id owned order
     * @param request full unsaved preferences with observed versions
     * @return read-only nonpayable estimate; no revision or quote is created
     */
    @PostMapping("/preview")
    @Operation(summary = "Preview unsaved service choices",
            description = "Read-only calculation. Never saves preferences, "
                    + "allocates space or creates a quotation. Client must "
                    + "discard responses for older selections. Missing "
                    + "source quantities or evidence stay pending.")
    public ServiceDisclosureResponse preview(@PathVariable @Positive long id,
            @RequestBody @Valid
            ServicePreviewRequest request) {
        return services.preview(id, request);
    }

    /**
     * Saves choices on a draft or creates a submitted replacement revision.
     * @param id owned order key
     * @param request complete preferences and observed order/horse versions
     * @return completion receipt, no payment or booking activation
     */
    @PostMapping("/confirm")
    @Operation(summary = "Confirm my requested service choices",
            description = "Full preference replacement. Supply recordVersion, "
                    + "horseRecordVersion and operationId. Submitted changes "
                    + "require changeReason and create an immutable revision. "
                    + "Reuse UUID and exact input on retry, then GET current.")
    public ServiceConfirmationResponse confirm(@PathVariable @Positive long id,
            @RequestBody @Valid ServiceConfirmationRequest request) {
        return services.confirm(id, request);
    }
}
