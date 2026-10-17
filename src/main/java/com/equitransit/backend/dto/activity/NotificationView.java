/*
 * NotificationView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.dto.activity;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;


/** Safe, bounded actor-facing activity contract. */
public record NotificationView(
        @Schema(example = "101")
        long notificationId,
        @Schema(example = "101", nullable = true)
        Long orderId,
        @Schema(minLength = 1, example = "Order update")
        String title,
        @Schema(minLength = 1,
                example = "Open EquiTransit to view your order update.")
        String message,
        @Schema(example = "2026-10-17T08:00:00Z")
        OffsetDateTime createdAt,
        @Schema(example = "2026-10-17T09:00:00Z", nullable = true)
        OffsetDateTime readAt) {
}
