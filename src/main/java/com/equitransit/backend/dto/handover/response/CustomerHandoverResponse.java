/*
 * CustomerHandoverResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Redacted customer-facing delivery handover view.
 */
package com.equitransit.backend.dto.handover.response;

import com.equitransit.backend.dto.handover.HandoverExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Customer-visible delivery receipt with sensitive supplier details redacted.
 *
 * @param handoverId primary key of the handover event
 * @param orderId transport order identifier
 * @param handoverType handover type classification
 * @param handoverTime UTC point-in-time when animal was delivered
 * @param receivedByName authorized consignee who signed for receipt
 * @param horseConditionStatus physical welfare assessment upon delivery
 * @param outcome delivery outcome
 * @param photoFileKey storage key of stall / stable delivery photograph
 * @param receiverSignatureFileKey electronic recipient signature file key
 * @param locationDescription physical delivery destination address or box
 * @param customerDecision customer response: ACCEPTED, EXCEPTION,
 *     NOT_DELIVERED
 * @param customerFeedback review comments or dispute explanation
 * @param customerRespondedAt timestamp when customer submitted their response
 */
@Schema(name = "CustomerHandoverResponse",
        description = "Redacted customer view of delivery confirmation")
public record CustomerHandoverResponse(
        @Schema(example = HandoverExamples.HANDOVER_ID)
        long handoverId,

        @Schema(example = HandoverExamples.ORDER_ID)
        long orderId,

        @Schema(example = "FINAL_RECEIVER")
        String handoverType,

        @Schema(example = HandoverExamples.TIME)
        OffsetDateTime handoverTime,

        @Schema(example = HandoverExamples.RECEIVER_NAME)
        String receivedByName,

        @Schema(example = "EXCELLENT")
        String horseConditionStatus,

        @Schema(example = "SUCCESSFUL")
        String outcome,

        @Schema(example = HandoverExamples.PHOTO_KEY)
        String photoFileKey,

        @Schema(example = HandoverExamples.SIG_KEY)
        String receiverSignatureFileKey,

        @Schema(example = HandoverExamples.LOCATION)
        String locationDescription,

        @Schema(example = "ACCEPTED")
        String customerDecision,

        @Schema(example = HandoverExamples.FEEDBACK)
        String customerFeedback,

        @Schema(example = HandoverExamples.TIME)
        OffsetDateTime customerRespondedAt
) { }
