/*
 * ComplianceAttachmentView
 * Author: EquiTransit development team
 * Date: 2026-10-23
 * Purpose: Disclose the latest submission's private attachment metadata.
 */
package com.equitransit.backend.dto.compliance;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Retrieve content through the existing authorized /compliance/files API. */
public record ComplianceAttachmentView(
        @Schema(example = "84032724-eac7-45df-8d04-e54f092279d0",
                nullable = true) UUID fileKey,
        @Schema(example = "certificate.pdf") String fileName,
        @Schema(example = "application/pdf") String contentType,
        @Schema(example = "1024") long fileSizeBytes,
        @Schema(example = "CERT-2027-001", nullable = true)
        String referenceNumber,
        @Schema(example = "2027-03-01T00:00:00Z", nullable = true)
        OffsetDateTime expiresAt) { }
