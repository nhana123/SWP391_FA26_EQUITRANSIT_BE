/*
 * ComplianceUploadSubmitRequest
 * Author: EquiTransit development team
 * Date: 2026-10-23
 * Purpose: Atomically upload and submit a requirement's evidence.
 */
package com.equitransit.backend.dto.compliance;

import com.equitransit.backend.dto.validation.OptionalText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Flat command; document type is derived from the authorized requirement. */
public record ComplianceUploadSubmitRequest(
        @Schema(example = "certificate.pdf")
        @NotBlank @Size(max = 255) String fileName,
        @Schema(example = "application/pdf")
        @NotBlank @Size(max = 100) String contentType,
        @Schema(example = ComplianceExamples.PNG)
        @NotBlank @Size(max = 6990508) String contentBase64,
        @Schema(example = "2027-03-01T00:00:00Z")
        @NotNull OffsetDateTime expiresAt,
        @Schema(example = "CERT-2027-001", nullable = true)
        @OptionalText @Size(max = 50) String referenceNumber,
        @Schema(example = "000000000001A2B3")
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{16}$") String recordVersion,
        @Schema(example = "d86b4f63-5acc-4f20-8da3-e24323268814")
        @NotNull UUID operationId) { }
