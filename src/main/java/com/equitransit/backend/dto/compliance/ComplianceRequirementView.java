/*
 * ComplianceRequirementView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import com.equitransit.backend.entity.compliance.RequirementStatus;
import com.equitransit.backend.entity.compliance.LegalDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import com.equitransit.backend.entity.compliance.CompliancePhase;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceRequirementView(
        @Schema(example = "101")
        @Positive long requirementId,
        @Schema(example = "101")
        @Positive long ruleId,
        @Schema(example = "HEALTH_CERTIFICATE")
        @NotBlank @Size(min = 1, max = 50) String documentType,
        @Schema(example = "PRE_EXPORT")
        @NotNull CompliancePhase phase,
        @Schema(example = "PENDING")
        @NotBlank @Size(min = 1, max = 50) RequirementStatus status,
        @Schema(example = "102",
                nullable = true)
        Long submissionId,
        @Schema(example = "103",
                nullable = true)
        Long documentVersionId,
        @Schema(example = "APPROVED",
                nullable = true)
        @Size(min = 1, max = 50) LegalDecision decision,
        @Schema(example = "Upload the full authority-issued certificate.",
                nullable = true)
        String instructions,
        @Schema(description = "The Specialist's note on the latest review "
                + "of the latest submission, shown to the owning customer.",
                example = "The scan is unreadable; upload a clearer copy.",
                nullable = true)
        @Size(max = 2000) String feedback) {
}
