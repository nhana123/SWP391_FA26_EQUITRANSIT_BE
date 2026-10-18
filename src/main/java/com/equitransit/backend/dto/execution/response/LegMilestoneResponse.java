/*
 * LegMilestoneResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Response DTO for a recorded leg milestone event.
 */
package com.equitransit.backend.dto.execution.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Detailed response representation of a leg milestone. */
public record LegMilestoneResponse(
        @Schema(example = "101") @Positive long milestoneId,
        @Schema(example = "901") @Positive long legId,
        @Schema(example = "41") @Positive long reportedBy,
        @Schema(example = "Nguyen Van Driver") String reportedByName,
        @Schema(example = "REST_STOP") @NotBlank String milestoneType,
        @Schema(example = "2026-10-18T08:30:00Z")
        @NotNull OffsetDateTime occurredAt,
        @Schema(example = "2026-10-18T08:31:00Z")
        @NotNull OffsetDateTime recordedAt,
        @Schema(example = "21.028511") BigDecimal latitude,
        @Schema(example = "105.854444") BigDecimal longitude,
        @Schema(example = "Rest stop completed.") String notes,
        @Schema(example = "DRIVER_APP") String sourceCode,
        @Schema(example = "TRIP-REG-2026") String sourceReference,
        @Schema(example = "Highway 1A Rest Area Km 45")
        String locationDescription,
        @Schema(example = "2026-10-18T18:00:00Z")
        OffsetDateTime estimatedArrivalAt,
        @Schema(example = "Minor highway traffic") String delayReason,
        @Schema(example = "evidence-key.jpg")
        String evidenceFileKey) { }
