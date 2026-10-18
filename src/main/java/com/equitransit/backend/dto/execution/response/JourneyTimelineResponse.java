/*
 * JourneyTimelineResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Full customer tracking timeline with active status & safety notes.
 */
package com.equitransit.backend.dto.execution.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Customer tracking timeline response. */
public record JourneyTimelineResponse(
        @Schema(example = "101") @Positive long orderId,
        @Schema(example = "CONFIRMED") @NotBlank String orderStatus,
        @Schema(example = "801") @Positive long planId,
        @Schema(example = "901") Long currentLegId,
        @Schema(example = "1") Integer currentLegSequenceNo,
        @Schema(example = "ROAD") String currentTransportMode,
        @Schema(example = "IN_PROGRESS") String currentLegStatus,
        @Schema(example = "REST_STOP") String currentMilestone,
        @Schema(example = "2026-10-18T08:30:00Z")
        OffsetDateTime lastUpdated,
        @Schema(example = "2026-10-18T18:00:00Z")
        OffsetDateTime estimatedArrival,
        @Schema(example = "HEALTHY") String latestWelfareStatus,
        @Schema(example = "37.80") BigDecimal latestTemperatureCelsius,
        @Schema(example = "photo-key.jpg") String latestWelfarePhotoKey,
        @NotNull List<LegTimelineItem> legs) { }
