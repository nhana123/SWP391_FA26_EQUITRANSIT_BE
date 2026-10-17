/*
 * ComplianceDocumentView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceDocumentView(
        @Schema(example = "101")
        @Positive long documentVersionId,
        @Schema(example = "101")
        @Positive long documentId,
        @Schema(example = "1")
        int versionNo,
        @Schema(example = "HEALTH_CERTIFICATE")
        @NotBlank @Size(min = 1, max = 50) String documentType,
        @Schema(example = "84032724-eac7-45df-8d04-e54f092279d0",
                nullable = true)
        UUID fileKey,
        @Schema(example = "2026-10-01", nullable = true)
        LocalDate issuedDate,
        @Schema(example = "2027-03-01", nullable = true)
        LocalDate expiryDate,
        @Schema(example = "2027-02-20T12:00:00Z",
                nullable = true)
        OffsetDateTime revokedAt) {
}
