/*
 * CustomerController
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Expose the authenticated customer profile.
 */
package com.equitransit.backend.controller;


import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.equitransit.backend.dto.response.CustomerResponse;
import com.equitransit.backend.service.CustomerService;


/** Exposes the personal profile represented by Figma C17. */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customers;

    /**
     * Creates the customer controller.
     * @param customers the profile service
     */
    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    /**
     * Returns the signed-in customer's profile.
     * @param jwt the validated access token
     * @return the profile belonging to the token subject
     */
    @GetMapping("/me")
    public CustomerResponse me(@AuthenticationPrincipal Jwt jwt) {
        return customers.getCurrentCustomer(Long.valueOf(jwt.getSubject()));
    }
}
