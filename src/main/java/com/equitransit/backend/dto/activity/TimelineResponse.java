/*
 * TimelineResponse
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.dto.activity;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;


/** Safe, bounded actor-facing activity contract. */
public record TimelineResponse(
        @Schema(example = "101")
        long orderId,
        @Schema(example = "[]")
        List<TimelinePoint> items,
        @Schema(example = "0")
        int page,
        @Schema(example = "20")
        int size) {
}
