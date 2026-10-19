/*
 * OperationalCompletionReceipt
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Receipt returned upon final delivery operational completion.
 */
package com.equitransit.backend.dto.handover.response;

import com.equitransit.backend.dto.handover.HandoverExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Receipt proving operational completion of the transport plan and order.
 *
 * @param handoverId primary key of the final delivery handover event
 * @param orderId transport order identifier
 * @param planId operational plan identifier
 * @param legId final destination transport leg identifier
 * @param orderStatus transitioned order status (DELIVERED)
 * @param planStatus transitioned plan status (COMPLETED)
 * @param completedAt UTC timestamp of operational completion
 * @param receivedByName recipient who accepted delivery
 * @param horseConditionStatus physical welfare condition upon arrival
 * @param quarantineReleaseVerified whether quarantine release clearance passed
 */
@Schema(name = "OperationalCompletionReceipt",
        description = "Receipt confirming order delivery & completion")
public record OperationalCompletionReceipt(
        @Schema(example = HandoverExamples.HANDOVER_ID)
        long handoverId,

        @Schema(example = HandoverExamples.ORDER_ID)
        long orderId,

        @Schema(example = HandoverExamples.PLAN_ID)
        long planId,

        @Schema(example = HandoverExamples.LEG_ID)
        long legId,

        @Schema(example = "DELIVERED")
        String orderStatus,

        @Schema(example = "COMPLETED")
        String planStatus,

        @Schema(example = HandoverExamples.TIME)
        OffsetDateTime completedAt,

        @Schema(example = HandoverExamples.RECEIVER_NAME)
        String receivedByName,

        @Schema(example = "EXCELLENT")
        String horseConditionStatus,

        @Schema(example = "true")
        boolean quarantineReleaseVerified
) { }
