/*
 * DepartureModels
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify departure gates and physically receive the horse.
 */
package com.equitransit.backend.dto.departure;

import com.equitransit.backend.dto.plan.PlanModels.GateState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Typed OpenAPI 3.1 contracts for F11 departure gates and horse receipt. */
public final class DepartureModels {

    private DepartureModels() { }

    /** Physical horse microchip and passport verification result. */
    public enum IdentityResult { MATCHED, MISMATCHED, PENDING }

    /** Physical horse receipt acceptance state on a road leg. */
    public enum ReceiptState { ACCEPTED, REJECTED, PENDING }

    /** Versioned write command with parent order rowversion and retry UUID. */
    public record DepartureCommand(
            @Schema(example = "a1046c13-4c31-49b9-8695-edbab59eec01")
            @NotNull UUID operationId,
            @Schema(example = "000000000001A2B3")
            @NotBlank @Size(min = 16, max = 16)
            @Pattern(regexp = "^[0-9a-fA-F]{16}$") String recordVersion) { }

    /** Command to evaluate and persist real-time 3-gate departure readiness. */
    public record DepartureGateEvaluateRequest(
            @Schema(description = "Idempotency and concurrency command",
                    example = DepartureExamples.COMMAND)
            @NotNull @Valid DepartureCommand command) { }

    /** Manager command to authorize departure when all 3 gates are PASS. */
    public record DepartureAuthorizeRequest(
            @Schema(description = "Idempotency and concurrency command",
                    example = DepartureExamples.COMMAND)
            @NotNull @Valid DepartureCommand command,
            @Schema(example = "801") @Positive long planId) { }

    /** Assigned road driver command to physically inspect/receive the horse. */
    public record HorseReceiptRequest(
            @Schema(description = "Idempotency and concurrency command",
                    example = DepartureExamples.COMMAND)
            @NotNull @Valid DepartureCommand command,
            @Schema(example = "801") @Positive long planId,
            @Schema(example = "901") @Positive long legId,
            @Schema(example = "981020000123456")
            @NotBlank @Size(min = 1, max = 50) String observedMicrochip,
            @Schema(example = "FEI-VN-2026-009")
            @NotBlank @Size(min = 1, max = 50) String observedPassportNumber,
            @Schema(example = "true") @NotNull Boolean fitToTravel,
            @Schema(example = "Calm, hydrated, normal temperature.")
            @NotBlank @Size(min = 1, max = 2000) String conditionNotes,
            @Schema(example = "f3046c13-4c31-49b9-8695-edbab59eec2f")
            @NotBlank @Size(min = 1, max = 1024) String evidenceFileKey,
            @Schema(example = "Hanoi Stable Gate A")
            @NotBlank @Size(min = 1, max = 500) String locationDescription) { }

    /** Assigned road driver command to start first-leg physical departure. */
    public record LegDepartRequest(
            @Schema(description = "Idempotency and concurrency command",
                    example = DepartureExamples.COMMAND)
            @NotNull @Valid DepartureCommand command,
            @Schema(example = "801") @Positive long planId,
            @Schema(example = "901") @Positive long legId,
            @Schema(example = "1001") @Positive long receiptId) { }

    /** Evaluated status and blocker codes for a single departure gate. */
    public record GateDetailView(
            @Schema(example = "PASS") @NotNull GateState status,
            @Schema(example = "[]")
            @NotNull List<@NotBlank @Size(min = 1, max = 80) String>
                    blockers) { }

    /** Physical horse receipt view (redacted when viewed by CUSTOMER). */
    public record HorseReceiptView(
            @Schema(example = "1001") @Positive long receiptId,
            @Schema(example = "801") @Positive long planId,
            @Schema(example = "901") @Positive Long legId,
            @Schema(example = "41") @Positive Long receivedBy,
            @Schema(example = "981020000123456")
            @Size(min = 1, max = 50) String observedMicrochip,
            @Schema(example = "FEI-VN-2026-009")
            @Size(min = 1, max = 50) String observedPassportNumber,
            @Schema(example = "MATCHED")
            @NotNull IdentityResult identityCheckResult,
            @Schema(example = "ACCEPTED")
            @NotNull ReceiptState receiptStatus,
            @Schema(example = "Calm, hydrated, normal temperature.")
            @Size(min = 1, max = 2000) String conditionNotes,
            @Schema(example = "f3046c13-4c31-49b9-8695-edbab59eec2f")
            @Size(min = 1, max = 1024) String evidenceFileKey,
            @Schema(example = "Hanoi Stable Gate A")
            @Size(min = 1, max = 500) String locationDescription,
            @Schema(example = "2027-02-01T05:25:00Z")
            @NotNull OffsetDateTime receivedAt) { }

    /** Server-stored image descriptor scoped to the authenticated pickup. */
    public record DepartureEvidence(
            @Schema(example = "f3046c13-4c31-49b9-8695-edbab59eec2f")
            @NotBlank @Size(min = 36, max = 36) String fileKey,
            @Schema(example = "image/png")
            @NotBlank @Size(max = 64) String contentType,
            @Schema(example = "1024") @Positive long sizeBytes,
            @Schema(example = "00000000000000000000000000000000"
                    + "00000000000000000000000000000000")
            @NotBlank @Size(min = 64, max = 64) String sha256) { }

    /** Full F11 departure gate evaluation, authorization and receipt state. */
    public record DepartureReadinessResponse(
            @Schema(example = "101") @Positive long orderId,
            @Schema(example = "000000000001A2B7")
            @NotBlank @Size(min = 16, max = 16)
            @Pattern(regexp = "^[0-9a-fA-F]{16}$") String recordVersion,
            @Schema(example = "IN_TRANSIT")
            @NotBlank @Size(min = 1, max = 50) String orderStatus,
            @Schema(example = "501") @Positive Long currentRevisionId,
            @Schema(example = "801") @Positive Long planId,
            @Schema(example = "1") @Positive Integer planVersionNo,
            @Schema(example = "IN_TRANSIT") PlanState planStatus,
            @Schema(example = "301") @Positive Long acceptedQuoteId,
            @Schema(example = "901") @Positive Long firstLegId,
            @Schema(example = "IN_TRANSIT") LegState firstLegStatus,
            @Schema(example = "2027-02-01T05:30:00Z")
            OffsetDateTime firstLegActualDeparture,
            @Schema(example = "2027-02-01T05:30:00Z")
            @NotNull OffsetDateTime asOf,
            @Schema(example = "2027-02-01T05:30:00Z")
            OffsetDateTime gatesCheckedAt,
            @Schema(description = "Financial gate readiness",
                    example = DepartureExamples.GATE_PASS)
            @NotNull @Valid GateDetailView financialGate,
            @Schema(description = "Legal and document gate readiness",
                    example = DepartureExamples.GATE_PASS)
            @NotNull @Valid GateDetailView legalGate,
            @Schema(description = "Operational plan and resource readiness",
                    example = DepartureExamples.GATE_PASS)
            @NotNull @Valid GateDetailView operationalGate,
            @Schema(example = "true") boolean readyForAuthorization,
            @Schema(example = "11") @Positive Long departureAuthorizedBy,
            @Schema(example = "2027-02-01T05:15:00Z")
            OffsetDateTime departureAuthorizedAt,
            @Schema(example = "true") boolean readyForDeparture,
            @Schema(example = "[]")
            @NotNull List<@NotBlank @Size(min = 1, max = 80) String>
                    departureBlockers,
            @Schema(description = "Physical horse receipts on current plan",
                    example = "[" + DepartureExamples.RECEIPT_VIEW + "]")
            @NotNull List<@Valid HorseReceiptView> receipts) { }
}
