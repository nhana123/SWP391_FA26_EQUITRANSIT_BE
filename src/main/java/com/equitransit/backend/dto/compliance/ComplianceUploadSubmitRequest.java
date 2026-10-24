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
import java.time.LocalDate;
import jakarta.validation.constraints.Positive;

/** Flat command; document type is derived from the authorized requirement. */
public record ComplianceUploadSubmitRequest(
        @Schema(example = "certificate.pdf")
        @Size(max = 255) String fileName,
        @Schema(example = "application/pdf")
        @Size(max = 100) String contentType,
        @Schema(example = ComplianceExamples.PNG)
        @Size(max = 6990508) String contentBase64,
        @Schema(example = "2027-03-01T00:00:00Z")
        OffsetDateTime expiresAt,
        @Schema(example = "CERT-2027-001", nullable = true)
        @OptionalText @Size(max = 50) String referenceNumber,
        @Schema(example = "000000000001A2B3")
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{16}$") String recordVersion,
        @Schema(example = "d86b4f63-5acc-4f20-8da3-e24323268814")
        @NotNull UUID operationId,
        @Schema(description = "Previously uploaded private local file",
                example = "84032724-eac7-45df-8d04-e54f092279d0")
        UUID fileKey,
        @Schema(description = "Reuse an existing version of this horse",
                example = "123")
        @Positive Long existingDocumentVersionId,
        @Schema(example = "CERT-001") @Size(max = 50) String documentNumber,
        @Schema(example = "2026-10-24") LocalDate issuedDate,
        @Schema(example = "2027-04-01") LocalDate expiryDate) {

    /** Legacy base64 constructor retained for current integrations.
     * @param fileName name @param contentType MIME @param contentBase64 data
     * @param expiresAt expiry @param referenceNumber reference
     * @param recordVersion order version @param operationId retry key */
    public ComplianceUploadSubmitRequest(String fileName, String contentType,
            String contentBase64, OffsetDateTime expiresAt,
            String referenceNumber, String recordVersion, UUID operationId) {
        this(fileName, contentType, contentBase64, expiresAt, referenceNumber,
                recordVersion, operationId, null, null, null, null, null);
    }
}
