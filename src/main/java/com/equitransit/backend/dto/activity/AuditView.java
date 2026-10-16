/*
 * AuditView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.dto.activity;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;


/** Safe, bounded actor-facing activity contract. */
public record AuditView(
        @Schema(example = "101")
        long eventId,
        @Schema(example = "31", nullable = true)
        Long userId,
        @Schema(minLength = 1, example = "ORDER_ACTIVITY")
        String entityType,
        @Schema(example = "101")
        long entityId,
        @Schema(minLength = 1, example = "ACCEPTED")
        String actionCode,
        @Schema(example = "2026-10-17T08:00:00Z")
        OffsetDateTime occurredAt,
        @Schema(minLength = 1, example = "{\"redacted\":true}",
                description = "Redacted JSON text; no private payload.")
        String redactedSnapshot) {
}
