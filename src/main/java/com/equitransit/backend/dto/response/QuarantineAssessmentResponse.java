/*
 * QuarantineAssessmentResponse
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Publish quarantine applicability result for BE1 (F05) and F14.
 */
package com.equitransit.backend.dto.response;


import java.time.OffsetDateTime;


/**
 * Public quarantine assessment result without internal audit details.
 * requiredQuarantineDays is null for PENDING and NOT_REQUIRED; never zero.
 *
 * @param revisionId              the assessed revision
 * @param applicability           PENDING, NOT_REQUIRED, or REQUIRED
 * @param requiredQuarantineDays  positive integer or null
 * @param assessedAt              when the specialist made this determination
 */
public record QuarantineAssessmentResponse(
        long revisionId,
        String applicability,
        Integer requiredQuarantineDays,
        OffsetDateTime assessedAt) {
}
