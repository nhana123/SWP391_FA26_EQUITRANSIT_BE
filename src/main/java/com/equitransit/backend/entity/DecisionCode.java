/*
 * DecisionCode
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.entity;


/** Existing OrderDecision CHECK codes; approval only starts preparation. */
public enum DecisionCode {
    APPROVED, REJECTED, CHANGES_REQUESTED
}
