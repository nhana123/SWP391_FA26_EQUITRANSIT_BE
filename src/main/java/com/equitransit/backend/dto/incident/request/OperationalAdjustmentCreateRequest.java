/*
 * OperationalAdjustmentCreateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO to propose an operational adjustment to a plan.
 */
package com.equitransit.backend.dto.incident.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload submitted by coordinators to propose an operational adjustment.
 */
public record OperationalAdjustmentCreateRequest(
        @Schema(example = "501")
        Long incidentId,
        @Schema(example = "31")
        Long changeRequestId,
        @Schema(example = "{\"reason\":\"Detour around road closure\","
                + "\"summary\":\"Reroute via Highway A6\","
                + "\"departureShiftMinutes\":120}")
        @NotBlank String proposedChanges,
        @Schema(example = "250.00")
        @NotNull @DecimalMin("0.00") BigDecimal additionalCostEstimate,
        @Schema(example = "Rerouting vehicle via Highway A6 to avoid closure.")
        @Size(max = 2000) String customerDescription) { }
