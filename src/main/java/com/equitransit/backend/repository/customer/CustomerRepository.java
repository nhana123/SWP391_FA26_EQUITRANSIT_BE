/*
 * CustomerRepository
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Read customer profiles.
 */
package com.equitransit.backend.repository.customer;


import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.equitransit.backend.entity.customer.Customer;


/** Reads customer profiles belonging to login accounts. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Finds the customer profile of an authenticated account.
     * @param userId the authenticated account identifier
     * @return the matching profile
     */
    Optional<Customer> findByUserUserId(Long userId);
}
