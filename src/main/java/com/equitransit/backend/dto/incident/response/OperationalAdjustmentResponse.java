/*
 * OperationalAdjustmentResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Response DTO presenting operational adjustment details for staff.
 */
package com.equitransit.backend.dto.incident.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Detailed representation of an operational adjustment for internal staff.
 */
public record OperationalAdjustmentResponse(
        @Schema(example = "601")
        long adjustmentId,
        @Schema(example = "311")
        long planId,
        @Schema(example = "101")
        long orderId,
        @Schema(example = "501")
        Long incidentId,
        @Schema(example = "31")
        Long changeRequestId,
        @Schema(example = "3")
        long proposedBy,
        @Schema(example = "Claire Martin")
        String proposerName,
        @Schema(example = "2")
        Long approvedBy,
        @Schema(example = "David Lefebvre")
        String approverName,
        @Schema(example = "{\"reason\":\"Detour around road closure\","
                + "\"summary\":\"Reroute via Highway A6\","
                + "\"departureShiftMinutes\":120}")
        String proposedChanges,
        @Schema(example = "250.00")
        BigDecimal additionalCostEstimate,
        @Schema(example = "CUSTOMER_PENDING")
        String approvalStatus,
        @Schema(example = "2026-10-18T14:40:00Z")
        OffsetDateTime proposedAt,
        @Schema(example = "2026-10-18T15:10:00Z")
        OffsetDateTime decidedAt,
        @Schema(example = "PENDING")
        String customerDecision,
        @Schema(example = "51")
        Long respondedByCustomerId,
        @Schema(example = "2026-10-18T15:05:00Z")
        OffsetDateTime customerRespondedAt,
        @Schema(example = "Agreed to additional charge for expedited reroute.")
        String customerResponseNotes) { }
