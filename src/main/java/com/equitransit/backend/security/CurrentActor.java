/*
 * CurrentActor
 * Author: EquiTransit development team
 * Date: 2026-10-13
 * Copyright: EquiTransit project contributors.
 * Purpose: Represent validated account and domain identity without secrets.
 */
package com.equitransit.backend.security;


/**
 * Immutable identity resolved from security context and database profiles.
 * @param userId the account primary key
 * @param roleCode the current database role
 * @param customerId the customer key, null for staff
 * @param staffId the staff key, null for customers
 */
public record CurrentActor(Long userId, String roleCode,
        Long customerId, Long staffId) {
}
