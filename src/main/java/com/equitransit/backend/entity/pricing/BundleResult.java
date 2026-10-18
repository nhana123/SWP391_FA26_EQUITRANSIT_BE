/*
 * BundleResult
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.entity.pricing;


import java.util.Set;


/**
 * Internal sourced service contract.
 * @param included explicitly bundled service codes, no supplier price
 * @param ambiguous whether source metadata needs staff review
 */
public record BundleResult(
        Set<String> included, boolean ambiguous) {
}
