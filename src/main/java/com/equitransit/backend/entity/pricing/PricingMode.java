/*
 * PricingMode
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.entity.pricing;


/** Retail basis, not legal or operational readiness. */
public enum PricingMode {
    RATE, EVIDENCE, INCLUDED, EVENT
}
