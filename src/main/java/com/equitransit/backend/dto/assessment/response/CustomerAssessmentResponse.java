/*
 * CustomerAssessmentResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.assessment.response;


import io.swagger.v3.oas.annotations.media.Schema;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param currentRevisionId current submitted revision
 * @param decision current assessment, null while pending
 * @param reason clarification/rejection reason; no internal approval notes
 */
public record CustomerAssessmentResponse(
        @Schema(
                example = "201",
                nullable = true,
                minimum = "1")
        Long currentRevisionId,
        @Schema(
                example = "CHANGES_REQUESTED",
                allowableValues = {
                        "APPROVED",
                        "REJECTED",
                        "CHANGES_REQUESTED"})
        String decision,
        @Schema(
                example = "Please confirm the route.",
                nullable = true)
        String reason) {
}
