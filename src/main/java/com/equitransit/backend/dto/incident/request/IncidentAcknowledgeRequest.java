/*
 * IncidentAcknowledgeRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO to acknowledge an incident.
 */
package com.equitransit.backend.dto.incident.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Payload for coordinators and managers to formally acknowledge an incident.
 */
public record IncidentAcknowledgeRequest(
        @Schema(example = "Operations team monitoring situation closely.")
        @Size(max = 2000) String notes) { }
