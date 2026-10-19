/*
 * PlanEvolutionReceipt
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Response DTO receipt for plan evolution upon adjustment approval.
 */
package com.equitransit.backend.dto.incident.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Receipt returned after manager approval creates a new operational plan.
 */
public record PlanEvolutionReceipt(
        @Schema(example = "601")
        long adjustmentId,
        @Schema(example = "101")
        long orderId,
        @Schema(example = "311")
        long previousPlanId,
        @Schema(example = "312")
        long newPlanId,
        @Schema(example = "2")
        int newVersionNo,
        @Schema(example = "3")
        int evolvedLegCount,
        @Schema(example = "APPLIED")
        String approvalStatus,
        @Schema(example = "2026-10-18T15:10:00Z")
        OffsetDateTime decidedAt,
        @Schema(example = "Operational adjustment applied. Plan evolved.")
        String message) { }
