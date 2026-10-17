/*
 * ComplianceOverview
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import java.util.List;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceOverview(
        @Schema(example = "101")
        @Positive long revisionId,
        @Schema(example = "000000000001A2B3",
                pattern = "^[0-9a-fA-F]{16}$",
                minLength = 16, maxLength = 16)
        String recordVersion,
        @Schema(example = "{}")
        ComplianceReadiness readiness,
        @Schema(example = "[]")
        List<ComplianceRequirementView> requirements,
        @Schema(example = "0")
        int page,
        @Schema(example = "20")
        int size,
        @Schema(example = "1")
        long totalElements) {
}
