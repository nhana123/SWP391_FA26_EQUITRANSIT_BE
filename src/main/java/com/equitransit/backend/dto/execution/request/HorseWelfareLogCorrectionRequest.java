/*
 * HorseWelfareLogCorrectionRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO for superseding a prior incorrect horse welfare record.
 */
package com.equitransit.backend.dto.execution.request;

import com.equitransit.backend.dto.execution.HealthStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Request payload for superseding an incorrect welfare record. */
public record HorseWelfareLogCorrectionRequest(
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
        @Schema(example = "Horse calm, alert.")
        @Size(max = 2000) String behaviorNotes,
        @Schema(example = "Corrected faulty initial thermometer reading.")
        @NotBlank @Size(max = 2000) String careNotes,
        @Schema(example = "welfare-photo-check1-corrected.jpg")
        @Size(max = 1024) String photoFileKey,
        @Schema(example = "vet-instructions-f12.pdf")
        @Size(max = 1024) String instructionFileKey) { }
