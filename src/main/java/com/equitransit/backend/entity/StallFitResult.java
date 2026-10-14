/*
 * StallFitResult
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.entity;


import java.math.BigDecimal;
import java.util.List;


/**
 * Internal sourced service contract.
 * @param option requested option
 * @param slots configuration slots
 * @param status ELIGIBLE, UNSUITABLE or PENDING
 * @param policyId source policy or null
 * @param policyVersion source version or null
 * @param reasons safe blockers
 */
public record StallFitResult(
        String option, BigDecimal slots, String status,
        Long policyId, Integer policyVersion, List<String> reasons) {
}
