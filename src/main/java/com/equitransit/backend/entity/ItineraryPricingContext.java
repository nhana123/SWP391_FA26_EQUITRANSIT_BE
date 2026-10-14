/*
 * ItineraryPricingContext
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.entity;


import java.math.BigDecimal;


/**
 * Internal sourced service contract.
 * @param planId exact current plan key
 * @param roadKm verified road km, or null
 * @param airKm verified air km, or null
 * @param status SOURCED or PENDING
 */
public record ItineraryPricingContext(
        Long planId, BigDecimal roadKm, BigDecimal airKm,
        String status) {
}
