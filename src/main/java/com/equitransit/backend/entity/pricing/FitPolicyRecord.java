/*
 * FitPolicyRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.entity.pricing;


import java.math.BigDecimal;


/**
 * Internal sourced service contract.
 * @param policyId approved source policy key
 * @param policyCode public policy reference
 * @param version approved version
 * @param slots supported configuration
 * @param maxWeight kg limit
 * @param maxHeight cm limit
 * @param maxWidth cm limit
 */
public record FitPolicyRecord(
        long policyId, String policyCode, int version,
        BigDecimal slots, BigDecimal maxWeight, BigDecimal maxHeight,
        BigDecimal maxWidth) {
}
