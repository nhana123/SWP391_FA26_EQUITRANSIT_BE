/*
 * CustomerAssessmentResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param currentRevisionId current submitted revision
 * @param decision current assessment, null while pending
 * @param reason clarification/rejection reason; no internal approval notes
 */
public record CustomerAssessmentResponse(
        Long currentRevisionId, String decision, String reason) {
}
