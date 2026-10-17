/*
 * ComplianceFileView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceFileView(
        @Schema(example = "84032724-eac7-45df-8d04-e54f092279d0")
        UUID fileKey,
        @Schema(example = "image/png")
        @NotBlank @Size(min = 1, max = 100) String contentType,
        @Schema(example = "health-certificate.png")
        @NotBlank @Size(min = 1, max = 255) String fileName,
        @Schema(example = ComplianceExamples.PNG)
        @NotBlank @Size(min = 1, max = 6990508) String contentBase64) {
}
