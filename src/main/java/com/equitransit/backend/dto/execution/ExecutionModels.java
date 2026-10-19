package com.equitransit.backend.dto.execution;


import com.equitransit.backend.dto.quote.QuoteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.entity.order.OrderStatus;


/** Typed F12 facts; planned times are never writable execution fields. */
public final class ExecutionModels {
    private ExecutionModels() { }

    /** Transport-specific evidence source; AIR never claims device GPS. */
    public enum SourceCode { DRIVER_REPORT, CARRIER, AIRPORT, AUTHORITY }

    /** Persisted lifecycle and position facts. */
    public enum RecordedMilestoneType {
        POSITION, DELAY, UPDATE, LEG_STARTED, LEG_COMPLETED
    }

    /** Facts do not implicitly change the leg lifecycle. */
    public enum MilestoneType { POSITION, DELAY, UPDATE }

    /** Observations, not an automated veterinary diagnosis. */
    public enum HealthStatus { NORMAL, CONCERN, URGENT }

    /** @param command retry/version envelope @param occurredAt actual time
     * @param sourceCode evidence source @param sourceReference external ref */
    public record TransitionRequest(
            @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                    + "\"operationId\":"
                    + "\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
            @NotNull @Valid QuoteCommand command,
            @Schema(example = "2027-02-01T08:00:00Z")
            @NotNull OffsetDateTime occurredAt,
            @Schema(example = "CARRIER") @NotNull SourceCode sourceCode,
            @Schema(example = "Carrier movement message SQ-2027-101")
            @NotBlank @Size(min = 1, max = 500) String sourceReference) { }

    /** @param orderId parent @param recordVersion updated parent version
     * @param operationId stable retry ID @param legId target @param entityId
     * appended fact @param incidentId optional F13 welfare alert */
    public record ExecutionReceipt(
            @Schema(example = "101") long orderId,
            @Schema(example = "000000000001A2B3",
                    pattern = "^[0-9a-fA-F]{16}$")
            @Size(min = 16, max = 16) String recordVersion,
            @Schema(example = "d86b4f63-5acc-4f20-8da3-e24323268814")
            java.util.UUID operationId,
            @Schema(example = "201") long legId,
            @Schema(example = "301") long entityId,
            @Schema(example = "401", nullable = true) Long incidentId) { }
    /** Timestamped location/ETA fact; source is required for every mode. */
    public record MilestoneRequest(
            @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                    + "\"operationId\":"
                    + "\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
            @NotNull @Valid
            QuoteCommand command,
            @Schema(example = "2027-02-01T08:00:00Z")
            @NotNull
            OffsetDateTime occurredAt,
            @Schema(example = "POSITION")
            @NotNull
            MilestoneType milestoneType,
            @Schema(example = "CARRIER")
            @NotNull
            SourceCode sourceCode,
            @Schema(example = "Carrier message SQ-101")
            @NotBlank @Size(min = 1, max = 500)
            String sourceReference,
            @Schema(example = "10.823100")
            @Size(min = 1, max = 12)
            @Pattern(regexp = "^-?\\d{1,3}(\\.\\d{1,6})?$")
            String latitude,
            @Schema(example = "106.629700")
            @Size(min = 1, max = 12)
            @Pattern(regexp = "^-?\\d{1,3}(\\.\\d{1,6})?$")
            String longitude,
            @Schema(example = "Verified movement update.")
            @Size(min = 1, max = 500)
            String locationDescription,
            @Schema(example = "2027-02-01T10:00:00Z")
            OffsetDateTime estimatedArrivalAt,
            @Schema(example = "Verified movement update.")
            @Size(min = 1, max = 1000)
            String delayReason,
            @Schema(example = "Verified movement update.")
            @Size(min = 1, max = 2000)
            String publicNote,
            @Schema(example = "Verified movement update.")
            @Size(min = 1, max = 2000)
            String internalNotes,
            @Schema(example = "true")
            @NotNull
            Boolean customerVisible) { }
    /** Append-only care observation; URGENT opens an F13 incident. */
    public record WelfareRequest(
            @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                    + "\"operationId\":"
                    + "\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
            @NotNull @Valid
            QuoteCommand command,
            @Schema(example = "2027-02-01T08:00:00Z")
            @NotNull
            OffsetDateTime occurredAt,
            @Schema(example = "NORMAL")
            @NotNull
            HealthStatus healthStatus,
            @Schema(example = "CARRIER")
            @NotNull
            SourceCode sourceCode,
            @Schema(example = "Carrier message SQ-101")
            @NotBlank @Size(min = 1, max = 500)
            String sourceReference,
            @Schema(example = "2.50")
            @Size(min = 1, max = 9)
            @Pattern(regexp = "^\\d{1,6}(\\.\\d{1,2})?$")
            String temperatureCelsius,
            @Schema(example = "2.50")
            @Size(min = 1, max = 9)
            @Pattern(regexp = "^\\d{1,6}(\\.\\d{1,2})?$")
            String waterIntakeLiters,
            @Schema(example = "2.50")
            @Size(min = 1, max = 9)
            @Pattern(regexp = "^\\d{1,6}(\\.\\d{1,2})?$")
            String feedIntakeKg,
            @Schema(example = "Verified movement update.")
            @Size(min = 1, max = 2000)
            String behaviorNotes,
            @Schema(example = "Verified movement update.")
            @Size(min = 1, max = 2000)
            String careNotes,
            @Schema(example = "Verified movement update.")
            @Size(min = 1, max = 2000)
            String publicNote,
            @Schema(example = "false")
            @NotNull
            Boolean customerVisible,
            @Schema(example = "301")
            @Positive
            Long supersedesLogId) { }
    /** Public leg state, distinct planned and actual instants. */
    public record LegView(
            @Schema(example = "201")
            long legId,
            @Schema(example = "1")
            int sequenceNo,
            @Schema(example = "ROAD")
            PlanLegMode transportMode,
            @Schema(example = "IN_TRANSIT")
            LegState legStatus,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime plannedDeparture,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime plannedArrival,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime actualDeparture,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime actualArrival) { }
    /** Visibility-filtered public fact. */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record MilestoneView(
            @Schema(example = "301")
            long milestoneId,
            @Schema(example = "201")
            long legId,
            @Schema(example = "POSITION")
            RecordedMilestoneType milestoneType,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime occurredAt,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime recordedAt,
            @Schema(example = "CARRIER")
            SourceCode sourceCode,
            @Schema(example = "Carrier movement message SQ-101")
            @Size(min = 1, max = 500)
            String sourceReference,
            @Schema(example = "10.823100")
            @Size(min = 1, max = 12)
            String latitude,
            @Schema(example = "106.629700")
            @Size(min = 1, max = 12)
            String longitude,
            @Schema(example = "Airport holding area")
            @Size(min = 1, max = 500)
            String locationDescription,
            @Schema(example = "2027-02-01T10:00:00Z")
            OffsetDateTime estimatedArrivalAt,
            @Schema(example = "Awaiting carrier clearance")
            @Size(min = 1, max = 1000)
            String delayReason,
            @Schema(example = "Arrived at the airport.")
            @Size(min = 1, max = 2000)
            String publicNote,
            @Schema(example = "Coordinator follow-up required.")
            @Size(min = 1, max = 2000)
            String internalNotes,
            @Schema(example = "true")
            boolean customerVisible) { }
    /** Care facts retain correction lineage. */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record WelfareView(
            @Schema(example = "301")
            long welfareLogId,
            @Schema(example = "201")
            long legId,
            @Schema(example = "NORMAL")
            HealthStatus healthStatus,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime occurredAt,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime recordedAt,
            @Schema(example = "2.50")
            @Size(min = 1, max = 9)
            String temperatureCelsius,
            @Schema(example = "2.50")
            @Size(min = 1, max = 9)
            String waterIntakeLiters,
            @Schema(example = "2.50")
            @Size(min = 1, max = 9)
            String feedIntakeKg,
            @Schema(example = "DRIVER_REPORT")
            SourceCode sourceCode,
            @Schema(example = "Driver observation log 101")
            @Size(min = 1, max = 500)
            String sourceReference,
            @Schema(example = "Observation for the assigned care team.")
            @Size(min = 1, max = 2000)
            String behaviorNotes,
            @Schema(example = "Instructions for the assigned care team.")
            @Size(min = 1, max = 2000)
            String careNotes,
            @Schema(example = "Routine care recorded.")
            @Size(min = 1, max = 2000)
            String publicNote,
            @Schema(example = "301")
            Long supersedesLogId,
            @Schema(example = "false")
            boolean customerVisible) { }
    /** Bounded histories use the same page/size independently;
     * totals are scoped. */
    public record ExecutionOverview(
            @Schema(example = "101")
            long orderId,
            @Schema(example = "101")
            long planId,
            @Schema(example = "000000000001A2B3",
                    pattern = "^[0-9a-fA-F]{16}$")
            @Size(min = 16, max = 16)
            String recordVersion,
            @Schema(example = "IN_TRANSIT")
            OrderStatus orderStatus,
            @Schema(example = "0")
            int page,
            @Schema(example = "20")
            int size,
            @Schema(example = "2")
            long legCount,
            @Schema(example = "1")
            long milestoneCount,
            @Schema(example = "1")
            long welfareCount,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime lastUpdateAt,
            @Schema(example = "[]")
            List<LegView> legs,
            @Schema(example = "[]")
            List<MilestoneView> milestones,
            @Schema(example = "[]")
            List<WelfareView> welfareLogs) { }
}
