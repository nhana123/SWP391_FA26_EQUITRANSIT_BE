/*
 * ComplianceReadiness
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import com.equitransit.backend.entity.compliance.CompliancePhase;
import com.equitransit.backend.entity.compliance.ComplianceStatus;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceReadiness(
        @Schema(example = "101")
        long revisionId,
        @Schema(example = "PRE_EXPORT")
        @NotNull CompliancePhase phase,
        @Schema(example = "2027-02-20T12:00:00Z")
        @NotNull OffsetDateTime asOf,
        @Schema(example = "2027-03-01T12:00:00Z",
                nullable = true)
        OffsetDateTime expiresAt,
        @Schema(example = "PENDING")
        ComplianceStatus status,
        @Schema(example = "[\"RULE_ASSESSMENT_PENDING\"]")
        List<String> blockers) {
}
