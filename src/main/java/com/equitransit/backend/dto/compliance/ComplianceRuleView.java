/*
 * ComplianceRuleView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import com.equitransit.backend.entity.compliance.CompliancePhase;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceRuleView(
        @Schema(example = "101")
        @Positive long ruleId,
        @Schema(example = "VN-GB-HEALTH")
        @NotBlank @Size(min = 1, max = 50) String ruleCode,
        @Schema(example = "1")
        int versionNo,
        @Schema(example = "HEALTH_CERTIFICATE")
        @NotBlank @Size(min = 1, max = 50) String documentType,
        @Schema(example = "PRE_EXPORT")
        @NotNull CompliancePhase phase,
        @Schema(example = "https://authority.example/rules/health")
        @NotBlank @Size(min = 1, max = 2048) String sourceUrl,
        @Schema(example = "2026-10-01T00:00:00Z")
        @NotNull OffsetDateTime effectiveFrom,
        @Schema(example = "2027-03-01T12:00:00Z",
                nullable = true)
        OffsetDateTime effectiveTo,
        @Schema(example = "true")
        boolean authorityRequired,
        @Schema(example = "SG",
                nullable = true)
        String transitCountryCode,
        @Schema(example = "Upload the full authority-issued certificate.",
                nullable = true)
        String instructions) {
}
