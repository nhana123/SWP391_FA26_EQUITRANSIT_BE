/*
 * DashboardOrder
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.dto.activity;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.entity.order.OrderStatus;


/** Safe, bounded actor-facing activity contract. */
public record DashboardOrder(
        @Schema(example = "101")
        long orderId,
        @Schema(minLength = 1, example = "ET-101")
        String orderNumber,
        @Schema(example = "SUBMITTED")
        OrderStatus status) {
}
