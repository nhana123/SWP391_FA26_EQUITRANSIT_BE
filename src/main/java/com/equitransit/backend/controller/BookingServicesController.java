/*
 * BookingServicesController
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose owner-scoped service disclosure and confirmation contracts.
 */
package com.equitransit.backend.controller;


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
import com.equitransit.backend.dto.request.ServiceConfirmationRequest;
import com.equitransit.backend.dto.response.ServiceConfirmationResponse;
import com.equitransit.backend.dto.response.ServiceDisclosureResponse;
import com.equitransit.backend.service.BookingServicesService;
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
