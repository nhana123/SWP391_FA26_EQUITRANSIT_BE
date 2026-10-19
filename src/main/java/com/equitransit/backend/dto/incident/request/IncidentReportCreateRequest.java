/*
 * IncidentReportCreateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO to report an unexpected incident or welfare disruption.
 */
package com.equitransit.backend.dto.incident.request;

import com.equitransit.backend.dto.incident.IncidentType;
import com.equitransit.backend.dto.incident.SeverityLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Payload for drivers and coordinators to report an operational incident.
 */
public record IncidentReportCreateRequest(
        @Schema(example = "HORSE_WELFARE_EMERGENCY")
        @NotNull IncidentType incidentType,
        @Schema(example = "CRITICAL")
        @NotNull SeverityLevel severityLevel,
        @Schema(example = "2026-10-18T14:30:00Z")
        OffsetDateTime incidentTime,
        @Schema(example = "Horse showing signs of acute abdominal colic.")
        @NotBlank @Size(max = 2000) String description,
        @Schema(example = "Administered sedative and called local vet clinic.")
        @Size(max = 2000) String immediateActionNotes,
        @Schema(example = "Dr. Dupont, Equine Clinic Lyon: +33 4 72 00 00 00")
        @Size(max = 500) String emergencyContact,
        @Schema(example = "[\"incident-vet-report-01.pdf\"]")
        @Size(max = 4000) String evidenceFilesJson) { }
