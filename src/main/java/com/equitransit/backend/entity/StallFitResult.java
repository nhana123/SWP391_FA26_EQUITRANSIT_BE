/*
 * StallFitResult
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.entity;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiExamples;
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
@Schema(example = ApiExamples.FIT)
public record StallFitResult(
        @Schema(
                example = "WIDE",
                allowableValues = {"SHARED_ONE", "WIDE", "PRIVATE"})
        String option,
        @Schema(
                example = "1.5")
        BigDecimal slots,
        @Schema(
                example = "PENDING",
                allowableValues = {"ELIGIBLE", "UNSUITABLE", "PENDING"})
        String status,
        @Schema(
                example = "31",
                nullable = true,
                minimum = "1")
        Long policyId,
        @Schema(
                example = "1",
                nullable = true,
                minimum = "1")
        Integer policyVersion,
        @Schema(
                example = "[\"FIT_POLICY_PENDING\"]")
        List<String> reasons) {
}
