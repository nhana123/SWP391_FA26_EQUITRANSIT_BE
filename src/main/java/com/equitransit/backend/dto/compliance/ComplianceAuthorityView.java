/*
 * ComplianceAuthorityView
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
import java.util.UUID;
import com.equitransit.backend.entity.compliance.CompliancePhase;
import com.equitransit.backend.entity.compliance.AuthorityDecision;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceAuthorityView(
        @Schema(example = "101")
        @Positive long procedureId,
        @Schema(example = "101")
        @Positive long ruleId,
        @Schema(example = "PRE_EXPORT")
        @NotNull CompliancePhase phase,
        @Schema(example = "APPROVED")
        AuthorityDecision decision,
        @Schema(example = "Competent veterinary authority")
        @NotBlank @Size(min = 1, max = 300) String authorityName,
        @Schema(example = "Authority decision reference")
        @NotBlank @Size(min = 1, max = 1000) String sourceReference,
        @Schema(example = "84032724-eac7-45df-8d04-e54f092279d0",
                nullable = true)
        UUID evidenceFileKey,
        @Schema(example = "2027-03-01T12:00:00Z",
                nullable = true)
        OffsetDateTime expiresAt) {
}
