/*
 * ServiceConfirmationResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.dto.response;


/**
 * Explicit customer service contract, no supplier costs or secrets.
 * @param order completion state; GET current after historic retry
 * @param revision new immutable revision, null for draft-only selection
 */
public record ServiceConfirmationResponse(
        OrderResponse order, OrderRevisionResponse revision) {
}
