/*
 * VeterinaryAssessmentResult
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.entity.pricing;


/**
 * Internal sourced service contract.
 * @param visits verified visit count or null
 * @param tests verified sample count or null
 * @param doses verified dose count or null
 * @param certificates verified certificate count or null
 */
public record VeterinaryAssessmentResult(
        Integer visits, Integer tests, Integer doses,
        Integer certificates) {
}
