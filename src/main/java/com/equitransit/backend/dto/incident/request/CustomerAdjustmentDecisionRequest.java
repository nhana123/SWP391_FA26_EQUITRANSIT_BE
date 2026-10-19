/*
 * CustomerAdjustmentDecisionRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO for customer commercial consent on an adjustment.
 */
package com.equitransit.backend.dto.incident.request;

import com.equitransit.backend.dto.incident.CustomerDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload submitted by customer to accept or reject an adjustment cost.
 */
public record CustomerAdjustmentDecisionRequest(
        @Schema(example = "ACCEPTED")
        @NotNull CustomerDecision decision,
        @Schema(example = "Agreed to additional fee for expedited reroute.")
        @Size(max = 2000) String responseNotes) { }
