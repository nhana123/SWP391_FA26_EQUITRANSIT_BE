/*
 * CustomerController
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Expose the authenticated customer profile.
 */
package com.equitransit.backend.controller.customer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.equitransit.backend.dto.customer.request.UpdateCustomerRequest;
import com.equitransit.backend.dto.customer.response.CustomerResponse;
import com.equitransit.backend.service.customer.CustomerService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/** Exposes the personal profile represented by Figma C17. */
@RestController
@RequestMapping(value = "/api/customers/me",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customer profile")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customers;

    /**
     * Creates the customer controller.
     * 
     * @param customers the profile service
     */
    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    /**
     * Returns the signed-in customer's profile.
     * 
     * @return the profile belonging to the token subject
     */
    @GetMapping
    @Operation(summary = "Read my customer profile")
    public CustomerResponse me() {
        return customers.getCurrentCustomer();
    }

    /**
     * Updates the signed-in customer's profile.
     * 
     * @param request the updated profile fields
     * @return the updated customer profile
     */
    @PutMapping
    @Operation(summary = "Update my customer profile")
    public CustomerResponse updateMe(
            @Valid @RequestBody UpdateCustomerRequest request) {
        return customers.updateCurrentCustomer(request);
    }
}
