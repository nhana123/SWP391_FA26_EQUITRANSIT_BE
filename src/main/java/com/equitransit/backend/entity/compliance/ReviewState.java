/*
 * ReviewState
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.entity.compliance;


/** Explicit manager queue states, never customer quote acceptance. */
public enum ReviewState {
    SUBMITTED, APPROVED, CHANGES_REQUESTED, REJECTED
}
