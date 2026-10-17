/*
 * ComplianceAssessmentView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import com.equitransit.backend.entity.compliance.
        QuarantineApplicability;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceAssessmentView(
        @Schema(example = "REQUIRED",
                nullable = true)
        QuarantineApplicability applicability,
        @Schema(example = "14",
                nullable = true)
        Integer requiredDays,
        @Schema(example = "1",
                nullable = true)
        Integer visitCount,
        @Schema(example = "2",
                nullable = true)
        Integer testCount,
        @Schema(example = "1",
                nullable = true)
        Integer doseCount,
        @Schema(example = "1",
                nullable = true)
        Integer certificateCount,
        @Schema(example = "2027-02-20T12:00:00Z",
                nullable = true)
        OffsetDateTime validUntil) {
}
