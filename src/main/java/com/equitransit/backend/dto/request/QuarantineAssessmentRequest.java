/*
 * QuarantineAssessmentRequest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Capture specialist quarantine applicability decision for FR-08.
 */
package com.equitransit.backend.dto.request;


import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


/**
 * Specialist quarantine evaluation result.
 *
 * @param applicability PENDING, NOT_REQUIRED or REQUIRED
 * @param requiredDays positive integer when REQUIRED; null otherwise
 * @param ruleId the sourced compliance rule authorising this decision
 * @param sourceReference authoritative legal regulation citation
 * @param validUntil expiry timestamp for this assessment result
 * @param assessmentNotes specialist rationale
 */
public record QuarantineAssessmentRequest(
        @NotBlank @Size(max = 20) String applicability,
        @Positive Integer requiredDays,
        @Positive Long ruleId,
        @Size(max = 1000) String sourceReference,
        OffsetDateTime validUntil,
        @Size(max = 2000) String assessmentNotes) {
}
