/*
 * IncidentResolveRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO to close and resolve an incident.
 */
package com.equitransit.backend.dto.incident.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for coordinators and managers to resolve an active incident.
 */
public record IncidentResolveRequest(
        @Schema(example = "Veterinarian confirmed horse stabilized.")
        @NotBlank @Size(max = 2000) String resolutionNotes) { }
