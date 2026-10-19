/*
 * FinalDeliveryHandoverCreateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request payload for final destination delivery to consignee.
 */
package com.equitransit.backend.dto.handover.request;

import com.equitransit.backend.dto.handover.HandoverExamples;
import com.equitransit.backend.dto.handover.HandoverOutcome;
import com.equitransit.backend.dto.handover.HorseConditionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Payload to log final delivery of horse to the designated receiver.
 *
 * @param handoverTime UTC point-in-time when animal was delivered
 * @param receivedByName full name of the authorized consignee or stable manager
 * @param receiverAuthority relationship or delegation authority
 * @param receiverSignatureFileKey electronic signature storage key (required)
 * @param horseConditionStatus physical welfare assessment upon delivery
 * @param outcome delivery outcome (SUCCESSFUL, EXCEPTION, DISPUTED)
 * @param exceptionNotes details if condition was fair/injured or disputed
 * @param photoFileKey delivery photo storage key (required)
 * @param locationDescription final stable / box address description
 * @param evidenceFilesJson optional JSON payload of evidence files
 */
@Schema(name = "FinalDeliveryHandoverCreateRequest",
        description = "Logs final destination delivery to authorized consignee")
public record FinalDeliveryHandoverCreateRequest(
        @NotNull
        @Schema(description = "UTC handover timestamp",
                example = HandoverExamples.TIME)
        OffsetDateTime handoverTime,

        @NotBlank
        @Size(max = 300)
        @Schema(description = "Name of authorized consignee or stable manager",
                example = HandoverExamples.RECEIVER_NAME)
        String receivedByName,

        @Size(max = 50)
        @Schema(description = "Authority title or proxy delegation role",
                example = HandoverExamples.RECEIVER_AUTH)
        String receiverAuthority,

        @NotBlank
        @Size(max = 1024)
        @Schema(description = "Storage key of electronic recipient signature",
                example = HandoverExamples.SIG_KEY)
        String receiverSignatureFileKey,

        @NotNull
        @Schema(description = "Observed equine condition upon final delivery",
                example = "EXCELLENT")
        HorseConditionStatus horseConditionStatus,

        @NotNull
        @Schema(description = "Final delivery outcome", example = "SUCCESSFUL")
        HandoverOutcome outcome,

        @Size(max = 2000)
        @Schema(description = "Operational notes or exception details",
                example = HandoverExamples.NOTES)
        String exceptionNotes,

        @NotBlank
        @Size(max = 1024)
        @Schema(description = "Storage key for stall / stable delivery photo",
                example = HandoverExamples.PHOTO_KEY)
        String photoFileKey,

        @Size(max = 500)
        @Schema(description = "Destination physical address or stall ID",
                example = HandoverExamples.LOCATION)
        String locationDescription,

        @Schema(description = "JSON evidence string if available",
                example = HandoverExamples.EVIDENCE_JSON)
        String evidenceFilesJson
) { }
