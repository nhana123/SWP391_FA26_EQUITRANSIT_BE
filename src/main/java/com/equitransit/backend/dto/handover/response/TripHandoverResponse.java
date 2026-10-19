/*
 * TripHandoverResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Staff-visible response representation for a trip handover event.
 */
package com.equitransit.backend.dto.handover.response;

import com.equitransit.backend.dto.handover.HandoverExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Detailed staff projection of a trip handover event.
 *
 * @param handoverId primary key of the handover record
 * @param legId transport leg identifier
 * @param orderId parent order identifier
 * @param planId operational plan identifier
 * @param recordedBy staff profile identifier of the recorder
 * @param recordedByName full name of the staff member who logged handover
 * @param handoverType INTERMEDIATE_CARRIER/AIRPORT/QUARANTINE/FINAL_RECEIVER
 * @param handoverTime UTC handover timestamp
 * @param receivedByName recipient or handler name
 * @param receiverAuthority authority or organization title
 * @param receiverSignatureFileKey signature image key
 * @param horseConditionStatus physical welfare assessment
 * @param outcome handover outcome
 * @param exceptionNotes exception notes if any
 * @param photoFileKey handover photo key
 * @param locationDescription physical transfer location
 * @param customerConfirmedAt timestamp when customer confirmed receipt
 * @param customerDecision customer decision: ACCEPTED, EXCEPTION, NOT_DELIVERED
 * @param customerFeedback customer review comments
 * @param customerRespondedAt timestamp of customer decision
 * @param respondedByCustomerId customer identifier who responded
 * @param evidenceFilesJson JSON evidence file list
 */
@Schema(name = "TripHandoverResponse",
        description = "Complete staff-facing view of a trip handover record")
public record TripHandoverResponse(
        @Schema(example = HandoverExamples.HANDOVER_ID)
        long handoverId,

        @Schema(example = HandoverExamples.LEG_ID)
        long legId,

        @Schema(example = HandoverExamples.ORDER_ID)
        long orderId,

        @Schema(example = HandoverExamples.PLAN_ID)
        long planId,

        @Schema(example = "102")
        long recordedBy,

        @Schema(example = "Antoine Dupont")
        String recordedByName,

        @Schema(example = "FINAL_RECEIVER")
        String handoverType,

        @Schema(example = HandoverExamples.TIME)
        OffsetDateTime handoverTime,

        @Schema(example = HandoverExamples.RECEIVER_NAME)
        String receivedByName,

        @Schema(example = HandoverExamples.RECEIVER_AUTH)
        String receiverAuthority,

        @Schema(example = HandoverExamples.SIG_KEY)
        String receiverSignatureFileKey,

        @Schema(example = "EXCELLENT")
        String horseConditionStatus,

        @Schema(example = "SUCCESSFUL")
        String outcome,

        @Schema(example = HandoverExamples.NOTES)
        String exceptionNotes,

        @Schema(example = HandoverExamples.PHOTO_KEY)
        String photoFileKey,

        @Schema(example = HandoverExamples.LOCATION)
        String locationDescription,

        @Schema(example = HandoverExamples.TIME)
        OffsetDateTime customerConfirmedAt,

        @Schema(example = "ACCEPTED")
        String customerDecision,

        @Schema(example = HandoverExamples.FEEDBACK)
        String customerFeedback,

        @Schema(example = HandoverExamples.TIME)
        OffsetDateTime customerRespondedAt,

        @Schema(example = "501")
        Long respondedByCustomerId,

        @Schema(example = HandoverExamples.EVIDENCE_JSON)
        String evidenceFilesJson
) { }
