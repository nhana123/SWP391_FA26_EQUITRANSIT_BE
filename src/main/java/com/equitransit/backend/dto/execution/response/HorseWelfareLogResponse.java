/*
 * HorseWelfareLogResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Response DTO for a recorded horse welfare inspection log.
 */
package com.equitransit.backend.dto.execution.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Detailed response representation of a horse welfare log. */
public record HorseWelfareLogResponse(
        @Schema(example = "501") @Positive long welfareLogId,
        @Schema(example = "901") @Positive long legId,
        @Schema(example = "41") @Positive long recordedBy,
        @Schema(example = "Nguyen Van Driver") String recordedByName,
        @Schema(example = "2026-10-18T08:30:00Z")
        @NotNull OffsetDateTime checkTime,
        @Schema(example = "HEALTHY") @NotBlank String healthStatus,
        @Schema(example = "37.80") BigDecimal temperatureCelsius,
        @Schema(example = "15.50") BigDecimal waterIntakeLiters,
        @Schema(example = "2.50") BigDecimal feedAmountKg,
        @Schema(example = "Calm and drinking well.") String behaviorNotes,
        @Schema(example = "Provided electrolyte water.") String careNotes,
        @Schema(example = "photo-key.jpg") String photoFileKey,
        @Schema(example = "vet-inst.pdf") String instructionFileKey,
        @Schema(example = "null") Long supersedesLogId,
        @Schema(example = "false") boolean fever,
        @Schema(example = "false") boolean emergencyAlert,
        @Schema(example = "null") String alertReason) { }
