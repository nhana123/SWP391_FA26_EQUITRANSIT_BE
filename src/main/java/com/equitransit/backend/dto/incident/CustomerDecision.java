/*
 * CustomerDecision
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Customer commercial consent decision on operational adjustments.
 */
package com.equitransit.backend.dto.incident;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Customer commercial consent decision for adjustments with additional costs.
 */
@Schema(description = "Customer commercial consent decision",
        example = "ACCEPTED")
public enum CustomerDecision {
    /** Awaiting response from the customer. */
    PENDING,
    /** Customer accepted the proposed adjustment and additional cost. */
    ACCEPTED,
    /** Customer declined the proposed adjustment and additional cost. */
    REJECTED
}
