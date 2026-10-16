/*
 * TaskView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.dto.activity;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;


/** Active case or leg responsibility; never a resource booking command. */
public record TaskView(
        @Schema(example = "101") long assignmentId,
        @Schema(example = "101") long orderId,
        @Schema(example = "201", nullable = true) Long legId,
        @Schema(example = "DRIVER", allowableValues = {"SPECIALIST",
                "COORDINATOR", "DRIVER"}) String role,
        @Schema(example = "2026-10-18T08:00:00Z", nullable = true)
        OffsetDateTime dueAt) {
}
