/*
 * IncidentReportResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Response DTO presenting incident report details.
 */
package com.equitransit.backend.dto.incident.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Detailed representation of an operational incident report.
 */
public record IncidentReportResponse(
        @Schema(example = "501")
        long incidentId,
        @Schema(example = "401")
        long legId,
        @Schema(example = "12")
        long reportedBy,
        @Schema(example = "Jean Dupont")
        String reporterName,
        @Schema(example = "HORSE_WELFARE_EMERGENCY")
        String incidentType,
        @Schema(example = "CRITICAL")
        String severityLevel,
        @Schema(example = "2026-10-18T14:30:00Z")
        OffsetDateTime incidentTime,
        @Schema(example = "Horse showing signs of acute colic.")
        String description,
        @Schema(example = "REPORTED")
        String status,
        @Schema(example = "Administered sedation and requested emergency vet.")
        String immediateActionNotes,
        @Schema(example = "Dr. Dupont, Lyon Equine Clinic: +33 4 72 00 00 00")
        String emergencyContact,
        @Schema(example = "3")
        Long acknowledgedBy,
        @Schema(example = "Claire Martin")
        String acknowledgedByName,
        @Schema(example = "2026-10-18T14:35:00Z")
        OffsetDateTime acknowledgedAt,
        @Schema(example = "3")
        Long resolvedBy,
        @Schema(example = "Claire Martin")
        String resolvedByName,
        @Schema(example = "2026-10-18T15:45:00Z")
        OffsetDateTime resolvedAt,
        @Schema(example = "Veterinarian confirmed horse stabilized.")
        String resolutionNotes,
        @Schema(example = "[\"incident-vet-report-01.pdf\"]")
        String evidenceFilesJson) { }
