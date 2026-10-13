/*
 * CustomerService
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Return the authenticated customer profile without secrets.
 */
package com.equitransit.backend.service;


import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.entity.Customer;
import com.equitransit.backend.dto.response.CustomerResponse;
import com.equitransit.backend.exception.ApiException;
import com.equitransit.backend.repository.CustomerRepository;


/** Reads the profile of the authenticated customer only. */
@Service
public class CustomerService {

    private final CustomerRepository customers;

    /**
     * Creates the profile service.
     * @param customers the profile repository
     */
    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /**
     * Resolves a customer by authenticated account identity.
     * @param userId the identity from a validated access token
     * @return the safe customer profile
     */
    @Transactional(readOnly = true)
    public CustomerResponse getCurrentCustomer(Long userId) {
        Customer customer = customers.findByUserUserId(userId)
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
