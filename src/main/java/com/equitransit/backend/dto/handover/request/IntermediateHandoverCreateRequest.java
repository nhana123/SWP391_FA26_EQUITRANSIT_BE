/*
 * IntermediateHandoverCreateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request payload for recording intermediate carrier or hub handovers.
 */
package com.equitransit.backend.dto.handover.request;

import com.equitransit.backend.dto.handover.HandoverExamples;
import com.equitransit.backend.dto.handover.HandoverOutcome;
import com.equitransit.backend.dto.handover.HandoverType;
import com.equitransit.backend.dto.handover.HorseConditionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Payload to log an intermediate handover between transport legs or hubs.
 *
 * @param handoverType transfer type: INTERMEDIATE_CARRIER/AIRPORT/QUARANTINE
 * @param handoverTime point-in-time when physical custody was transferred
 * @param receivedByName full name of the receiving agent, driver, or groom
 * @param receiverAuthority authority code or organization of receiver
 * @param receiverSignatureFileKey optional storage key for signature file
 * @param horseConditionStatus physical state observed upon handover
 * @param outcome operational outcome of this transfer
 * @param exceptionNotes details if condition was fair/injured or disputed
 * @param photoFileKey optional photo captured during intermediate transfer
 * @param locationDescription physical location description of the transfer
 * @param evidenceFilesJson optional JSON payload of evidence file keys
 */
@Schema(name = "IntermediateHandoverCreateRequest",
        description = "Logs an intermediate hub or multimodal transfer")
public record IntermediateHandoverCreateRequest(
        @NotNull
        @Schema(description = "Must be an intermediate handover type",
                example = "INTERMEDIATE_AIRPORT")
        HandoverType handoverType,

        @NotNull
        @Schema(description = "UTC handover timestamp",
                example = HandoverExamples.TIME)
        OffsetDateTime handoverTime,

        @NotBlank
        @Size(max = 300)
        @Schema(description = "Receiving handler, ramp agent, or relay driver",
                example = "Markus Vance")
        String receivedByName,

        @Size(max = 50)
        @Schema(description = "Authority title, cargo handler ID, or carrier",
                example = "AIRPORT_RAMP_SUPERVISOR")
        String receiverAuthority,

        @Size(max = 1024)
        @Schema(description = "Storage key of electronic signature",
                example = HandoverExamples.SIG_KEY)
        String receiverSignatureFileKey,

        @NotNull
        @Schema(description = "Observed equine condition upon transfer",
                example = "EXCELLENT")
        HorseConditionStatus horseConditionStatus,

        @NotNull
        @Schema(description = "Transfer outcome", example = "SUCCESSFUL")
        HandoverOutcome outcome,

        @Size(max = 2000)
        @Schema(description = "Operational notes or exception details",
                example = HandoverExamples.NOTES)
        String exceptionNotes,

        @Size(max = 1024)
        @Schema(description = "Storage key for custody transfer photo",
                example = HandoverExamples.PHOTO_KEY)
        String photoFileKey,

        @Size(max = 500)
        @Schema(description = "Location description of the transfer point",
                example = "Charles de Gaulle Cargo Apron Gate 4")
        String locationDescription,

        @Schema(description = "JSON evidence string if available",
                example = HandoverExamples.EVIDENCE_JSON)
        String evidenceFilesJson
) { }
