/*
 * QuoteCalculationContext
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Capture original quantity and fit inputs for reproducible prices.
 */
package com.equitransit.backend.entity.quote;


import java.math.BigDecimal;
import java.util.List;
import com.equitransit.backend.entity.pricing.VeterinaryAssessmentResult;


/** Internal evidence snapshot, not a customer or readiness response. */
public record QuoteCalculationContext(Long planId, BigDecimal roadKm,
        BigDecimal airKm, Integer requiredDays, Long quarantineAssessmentId,
        VeterinaryAssessmentResult veterinary, Long fitPolicyId,
        Integer fitPolicyVersion, List<String> includedCharges) {
}
