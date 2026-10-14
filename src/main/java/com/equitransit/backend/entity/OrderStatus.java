/*
 * OrderStatus
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Type the existing transport-order API status vocabulary.
 */
package com.equitransit.backend.entity;


/**
 * Existing documented status codes; not permission to add transitions.
 * Legacy CANCELED remains readable alongside CANCELLED.
 */
public enum OrderStatus {
    DRAFT, SUBMITTED, APPROVED, CHANGES_REQUESTED, REJECTED,
    IN_PREPARATION, CONFIRMED, IN_TRANSIT, DELIVERED, COMPLETED,
    CANCELLED, CANCELED, CLOSED
}
