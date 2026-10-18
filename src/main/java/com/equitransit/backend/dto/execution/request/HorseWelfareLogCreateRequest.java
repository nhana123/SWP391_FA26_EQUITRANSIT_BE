/*
 * HorseWelfareLogCreateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO for recording live horse welfare assessments.
 */
package com.equitransit.backend.dto.execution.request;

import com.equitransit.backend.dto.execution.HealthStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Request payload for registering a horse welfare check. */
public record HorseWelfareLogCreateRequest(
        @Schema(example = "2026-10-18T08:30:00Z")
        OffsetDateTime checkTime,
        @Schema(example = "HEALTHY")
        @NotNull HealthStatus healthStatus,
        @Schema(example = "37.80")
        @DecimalMin("30.00") @DecimalMax("45.00")
        BigDecimal temperatureCelsius,
        @Schema(example = "15.50")
        @DecimalMin("0.00") BigDecimal waterIntakeLiters,
        @Schema(example = "2.50")
        @DecimalMin("0.00") BigDecimal feedAmountKg,
        @Schema(example = "Horse calm, alert, responsive.")
        @Size(max = 2000) String behaviorNotes,
        @Schema(example = "Provided electrolyte water.")
        @Size(max = 2000) String careNotes,
        @Schema(example = "welfare-photo-check1.jpg")
        @Size(max = 1024) String photoFileKey,
        @Schema(example = "vet-instructions-f12.pdf")
        @Size(max = 1024) String instructionFileKey) { }
