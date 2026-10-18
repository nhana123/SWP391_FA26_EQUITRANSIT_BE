/*
 * LegTimelineItem
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Leg progression summary within customer tracking timeline.
 */
package com.equitransit.backend.dto.execution.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.List;

/** Single leg summary in the customer timeline. */
public record LegTimelineItem(
        @Schema(example = "901") @Positive long legId,
        @Schema(example = "1") int sequenceNo,
        @Schema(example = "ROAD") @NotBlank String transportMode,
        @Schema(example = "Hanoi Stable") String startPoint,
        @Schema(example = "Noi Bai Airport") String endPoint,
        @Schema(example = "IN_PROGRESS") @NotBlank String legStatus,
        @Schema(example = "2026-10-18T07:00:00Z")
        OffsetDateTime actualDeparture,
        @Schema(example = "null") OffsetDateTime actualArrival,
        @NotNull List<CustomerMilestoneItem> milestones,
        @NotNull List<CustomerWelfareItem> welfareChecks) { }
