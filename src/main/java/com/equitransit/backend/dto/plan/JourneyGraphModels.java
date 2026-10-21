/*
 * JourneyGraphModels
 * Author: EquiTransit development team
 * Date: 2026-10-21
 * Purpose: Bounded visual journey contracts; no arbitrary customer JSON.
 */
package com.equitransit.backend.dto.plan;

import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanCommand;
import com.equitransit.backend.dto.plan.PlanModels.PlanDraftRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Additive canvas contracts; operational assignments retain existing APIs. */
public final class JourneyGraphModels {

    private JourneyGraphModels() { }

    /** Checkpoints are places/interventions, never transport legs. */
    public enum NodeType {
        ORIGIN, REST_STOP, AIRPORT_HANDOVER, TRANSIT_HUB, QUARANTINE,
        DESTINATION
    }

    /** Transit treatment recorded by the Coordinator, not legal approval. */
    public enum TransitCustomsMode {
        STAY_ON_AIRCRAFT, AIRSIDE_TRANSFER, TERMINAL_HOLDING
    }

    /** Quarantine planning stage; evidence is a separate reservation. */
    public enum QuarantineType { PRE_EXPORT, POST_ARRIVAL }

    /** Facility isolation preference, not evidence of availability. */
    public enum BiosecurityLevel { STANDARD, HIGH_ISOLATION }

    /** Explicit optional node metadata; hidden from public projections. */
    public record GraphNodeDetails(
            @Schema(example = "Stable manager")
            @Size(min = 1, max = 200) String contactPerson,
            @Schema(example = "true") Boolean requireMicrochipCheck,
            @Schema(example = "Confirm fitness before loading.")
            @Size(min = 1, max = 1000) String preDepartureVetNote,
            @Schema(example = "Offer water and hay at rest.")
            @Size(min = 1, max = 1000) String feedingAndWateringPlan,
            @Schema(example = "true") Boolean mandatoryVetCheck,
            @Schema(example = "21") @Positive Long facilitySupplierId,
            @Schema(example = "PRE_EXPORT") QuarantineType quarantineType,
            @Schema(example = "1") @Min(1) @Max(100) Integer reservedStallCount,
            @Schema(example = "STANDARD") BiosecurityLevel biosecurityLevel,
            @Schema(example = "22") @Positive Long groundHandlerSupplierId,
            @Schema(example = "Receiving stable manager")
            @Size(min = 1, max = 200) String receiverName,
            @Schema(example = "+84901234567")
            @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$") String receiverPhone,
            @Schema(example = "23") @Positive Long insurerSupplierId,
            @Schema(example = "POL-EQ-2027-01")
            @Size(min = 1, max = 150) String policyNumber,
            @Schema(example = "50000.00")
            @Pattern(regexp = "^\\d{1,15}(\\.\\d{1,2})?$")
            String coverageAmount) { }

    /** Bounded checkpoint input; all timestamps use explicit offsets. */
    public record GraphNodeInput(
            @Schema(example = "pickup-1") @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String nodeKey,
            @Schema(example = "ORIGIN") @NotNull NodeType nodeType,
            @Schema(example = "1") @Min(1) @Max(21) int sequenceOrder,
            @Schema(example = "Hanoi Stable")
            @NotBlank @Size(min = 1, max = 200) String locationName,
            @Schema(example = "VN") @NotBlank
            @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
            @Schema(example = "2027-02-01T08:00:00+07:00")
            OffsetDateTime plannedArrivalAt,
            @Schema(example = "2027-02-01T09:00:00+07:00")
            OffsetDateTime plannedDepartureAt,
            @Schema(example = "60") @Min(0) @Max(525600) int dwellMinutes,
            @Schema(example = "AIRSIDE_TRANSFER")
            TransitCustomsMode transitCustomsMode,
            @Schema(example = "TRANSIT-PERMIT-001")
            @Size(min = 1, max = 120) String transitPermitRef,
            @Schema(example = "Offer water during the rest stop.")
            @Size(min = 1, max = 2000) String welfareCareNotes,
            @Schema(example = "951") @Positive Long quarantineReservationId,
            @Schema(example = "120.00") @NotNull
            @DecimalMin("-99999999.99") @DecimalMax("99999999.99")
            @Digits(integer = 8, fraction = 2) BigDecimal posX,
            @Schema(example = "180.00") @NotNull
            @DecimalMin("-99999999.99") @DecimalMax("99999999.99")
            @Digits(integer = 8, fraction = 2) BigDecimal posY,
            @Schema(description = "Internal planning metadata")
            @Valid GraphNodeDetails details) { }

    /** One ordered wire maps to exactly one persisted TripLeg. */
    public record GraphWireInput(
            @Schema(example = "1") @Min(1) @Max(20) int sequenceNo,
            @Schema(example = "pickup-1") @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String fromNodeKey,
            @Schema(example = "airport-1") @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String toNodeKey) { }

    /** Presentation-only transform; no arbitrary JSON or executable data. */
    public record GraphLayout(
            @Schema(example = "1.00") @NotNull
            @DecimalMin("0.5") @DecimalMax("1.5")
            @Digits(integer = 1, fraction = 2) BigDecimal zoom,
            @Schema(example = "120.00") @NotNull
            @DecimalMin("-99999999.99") @DecimalMax("99999999.99")
            @Digits(integer = 8, fraction = 2) BigDecimal panX,
            @Schema(example = "180.00") @NotNull
            @DecimalMin("-99999999.99") @DecimalMax("99999999.99")
            @Digits(integer = 8, fraction = 2) BigDecimal panY) { }

    /** Atomic compilation into the established F10 draft command. */
    public record GraphDraftRequest(
            @Schema(description = "Existing F10 draft and write command")
            @NotNull @Valid PlanDraftRequest draft,
            @Schema(example = "[]") @NotNull @Size(min = 2, max = 21)
            List<@NotNull @Valid GraphNodeInput> checkpointNodes,
            @Schema(example = "[]") @NotNull @Size(min = 1, max = 20)
            List<@NotNull @Valid GraphWireInput> wires,
            @Schema(description = "Canvas presentation transform")
            @NotNull @Valid GraphLayout layout) { }

    /** Coordinate-only update cannot alter schedules or resource facts. */
    public record GraphNodePosition(
            @Schema(example = "pickup-1") @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String nodeKey,
            @Schema(example = "120.00") @NotNull
            @DecimalMin("-99999999.99") @DecimalMax("99999999.99")
            @Digits(integer = 8, fraction = 2) BigDecimal posX,
            @Schema(example = "180.00") @NotNull
            @DecimalMin("-99999999.99") @DecimalMax("99999999.99")
            @Digits(integer = 8, fraction = 2) BigDecimal posY) { }

    /** New semantics require a new draft, never reschedule an assigned leg. */
    public record GraphLayoutRequest(
            @Schema(example = PlanExamples.COMMAND)
            @NotNull @Valid PlanCommand command,
            @Schema(example = "[]") @NotNull @Size(min = 2, max = 21)
            List<@NotNull @Valid GraphNodePosition> positions,
            @Schema(description = "Canvas presentation transform")
            @NotNull @Valid GraphLayout layout) { }

    /** Safe wire projection; internal offer/cost/evidence fields are absent. */
    public record GraphWireView(
            @Schema(example = "401") long legId,
            @Schema(example = "1") int sequenceNo,
            @Schema(example = "pickup-1") String fromNodeKey,
            @Schema(example = "airport-1") String toNodeKey,
            @Schema(example = "ROAD") PlanLegMode transportMode,
            @Schema(example = "PLANNED") LegState legStatus,
            @Schema(example = "Hanoi Stable") String startPoint,
            @Schema(example = "Noi Bai Airport") String endPoint,
            @Schema(example = "45.500") BigDecimal distanceKm,
            @Schema(example = "MANUAL_VERIFIED") String distanceSource,
            @Schema(example = "2027-02-01T09:00:00+07:00")
            OffsetDateTime plannedDeparture,
            @Schema(example = "2027-02-01T10:00:00+07:00")
            OffsetDateTime plannedArrival,
            @Schema(example = "2027-02-01T09:10:00+07:00")
            OffsetDateTime actualDeparture,
            @Schema(example = "2027-02-01T10:10:00+07:00")
            OffsetDateTime actualArrival,
            @Schema(example = "11") Long vehicleId,
            @Schema(example = "41") Long driverStaffId,
            @Schema(example = "VN0051") String flightNumber,
            @Schema(example = "12") Long stallEquipmentId,
            @Schema(example = "true") boolean assignedToCurrentDriver) { }

    /** Read model shared by editors and scoped public tracking views. */
    public record JourneyGraphView(
            @Schema(example = "101") long orderId,
            @Schema(example = "311") long planId,
            @Schema(example = "201") long revisionId,
            @Schema(example = "DRAFT") PlanState planStatus,
            @Schema(example = "000000000001A2B3") String recordVersion,
            @Schema(example = "true") boolean editable,
            @Schema(example = "[]") List<GraphNodeInput> checkpointNodes,
            @Schema(example = "[]") List<GraphWireView> wires,
            @Schema(description = "Safe typed canvas transform")
            GraphLayout layout) { }

    /** Minimal staff picker; no email/contact/account information. */
    public record GraphDriverCandidate(
            @Schema(example = "41") long staffId,
            @Schema(example = "Nguyen Van An") String fullName) { }
}
