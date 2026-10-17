/*
 * BundleCoverage
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Distinguish full-route inclusion from partial or unknown coverage.
 */
package com.equitransit.backend.entity.quote;

/** Missing legacy coverage must never imply an entirely free route. */
public enum BundleCoverage {
    ENTIRE_ROUTE, PARTIAL_ROUTE, UNKNOWN
}
