/*
 * TimelinePoint
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.dto.activity;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import com.equitransit.backend.entity.activity.ActivityCode;


/** Safe, bounded actor-facing activity contract. */
public record TimelinePoint(
        @Schema(example = "SUBMITTED")
        ActivityCode milestone,
        @Schema(example = "2026-10-17T08:00:00Z")
        OffsetDateTime occurredAt) {
}
