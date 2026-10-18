/*
 * CustomerService
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Return the authenticated customer profile without secrets.
 */
package com.equitransit.backend.service.customer;


import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.entity.customer.Customer;
import com.equitransit.backend.dto.customer.response.CustomerResponse;
import com.equitransit.backend.exception.ApiException;
import com.equitransit.backend.repository.customer.CustomerRepository;
import com.equitransit.backend.security.CurrentActorService;


/** Reads the profile of the authenticated customer only. */
@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final CurrentActorService actors;

    /**
     * Creates the profile service.
     * @param customers the profile repository
     * @param actors the trusted current identity resolver
     */
    public CustomerService(CustomerRepository customers,
            CurrentActorService actors) {
        this.customers = customers;
        this.actors = actors;
    }

    /**
     * Resolves a customer by authenticated account identity.
     * @return the safe customer profile
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@domainAccessPolicy.isCustomer()")
    public CustomerResponse getCurrentCustomer() {
        Customer customer = customers.findByUserUserId(
                actors.current().userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Customer profile was not found."));
        CustomerResponse response = new CustomerResponse();

        response.setCustomerId(customer.getCustomerId());
        response.setUserId(customer.getUser().getUserId());
        response.setEmail(customer.getUser().getEmail());
        response.setFullName(customer.getFullName());
        response.setPhone(customer.getPhone());
        response.setAddress(customer.getAddress());
        return response;
    }
}
