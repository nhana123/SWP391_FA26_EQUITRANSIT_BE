/*
 * CustomerWelfareItem
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Safe customer-facing horse welfare check projection.
 */
package com.equitransit.backend.dto.execution.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Public horse welfare snapshot item for customer tracking. */
public record CustomerWelfareItem(
        @Schema(example = "2026-10-18T08:30:00Z")
        @NotNull OffsetDateTime checkTime,
        @Schema(example = "HEALTHY") @NotBlank String healthStatus,
        @Schema(example = "37.80") BigDecimal temperatureCelsius,
        @Schema(example = "photo-key.jpg") String photoFileKey) { }
