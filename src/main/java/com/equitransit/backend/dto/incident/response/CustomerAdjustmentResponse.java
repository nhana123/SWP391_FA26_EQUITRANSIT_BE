/*
 * CustomerAdjustmentResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Customer-facing redacted representation of an adjustment.
 */
package com.equitransit.backend.dto.incident.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Redacted view of an operational adjustment for the owning customer.
 */
public record CustomerAdjustmentResponse(
        @Schema(example = "601")
        long adjustmentId,
        @Schema(example = "101")
        long orderId,
        @Schema(example = "250.00")
        BigDecimal additionalCost,
        @Schema(example = "PENDING")
        String customerDecision,
        @Schema(example = "CUSTOMER_PENDING")
        String approvalStatus,
        @Schema(example = "Rerouting vehicle via Highway A6 to avoid closure.")
        String summary,
        @Schema(example = "2026-10-18T14:40:00Z")
        OffsetDateTime proposedAt,
        @Schema(example = "Agreed to additional charge for expedited reroute.")
        String customerResponseNotes,
        @Schema(example = "2026-10-18T15:05:00Z")
        OffsetDateTime respondedAt) { }
