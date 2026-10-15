/*
 * QuarantineAssessmentResult
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.entity;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiExamples;


/**
 * Internal sourced service contract.
 * @param applicability REQUIRED, NOT_REQUIRED or UNKNOWN
 * @param requiredDays Specialist duration, never client-controlled
 * @param assessmentId source assessment or null
 */
@Schema(example = ApiExamples.QUARANTINE)
public record QuarantineAssessmentResult(
        @Schema(
                example = "REQUIRED",
                allowableValues = {"REQUIRED", "NOT_REQUIRED", "UNKNOWN"})
        String applicability,
        @Schema(
                example = "14",
                nullable = true,
                minimum = "1")
        Integer requiredDays,
        @Schema(
                example = "31",
                nullable = true,
                minimum = "1")
        Long assessmentId) {
}
