/*
 * PlanModels
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Build and confirm a multimodal operational plan.
 */
package com.equitransit.backend.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Typed OpenAPI 3.1 contracts for F10 multimodal operational planning. */
public final class PlanModels {

    private PlanModels() { }

    /** Plan version lifecycle state. */
    public enum PlanState {
        DRAFT, PROPOSED, PLANNED, CONFIRMED, ACTIVE, IN_TRANSIT,
        COMPLETED, SUPERSEDED, CANCELLED, REJECTED
    }

    /** Gate readiness status on a transport plan. */
    public enum GateState { PENDING, PASS, BLOCKED }

    /** Supported multimodal leg transport modes. */
    public enum PlanLegMode { ROAD, AIR }

    /** Operational leg status. */
    public enum LegState {
        PLANNED, CONFIRMED, IN_TRANSIT, COMPLETED, CANCELLED, REJECTED
    }

    /** Staff role on a trip leg; DRIVER is only valid on ROAD legs. */
    public enum TripRole { DRIVER, GROOM, ATTENDANT, VETERINARIAN, ESCORT }

    /** Assignment status for staff or physical stall equipment. */
    public enum AssignmentState {
        HELD, CONFIRMED, ACTIVE, COMPLETED, CANCELLED, INACTIVE
    }

    /** Reservation or policy confirmation status; HELD is never CONFIRMED. */
    public enum ReservationState { HELD, CONFIRMED, CANCELLED, EXPIRED }

    /** Carrier acceptance state for an air stall assignment. */
    public enum CarrierAcceptanceState { PENDING, ACCEPTED, REJECTED }

    /** Stall sharing code on an air leg. */
    public enum StallSharing { PRIVATE, SHARED, WIDE }

    /** Versioned write command with parent order rowversion and retry UUID. */
    public record PlanCommand(
            @Schema(example = "d2046c13-4c31-49b9-8695-edbab59eec2b")
            @NotNull UUID operationId,
            @Schema(example = "000000000001A2B3")
            @NotBlank @Size(min = 16, max = 16)
            @Pattern(regexp = "^[0-9a-fA-F]{16}$") String recordVersion) { }

    /** Single multimodal leg input in a draft operational plan. */
    public record PlanLegInput(
            @Schema(example = "1") @Positive int sequenceNo,
            @Schema(example = "ROAD") @NotNull PlanLegMode transportMode,
            @Schema(example = "Hanoi Stable")
            @NotBlank @Size(min = 1, max = 300) String startPoint,
            @Schema(example = "Noi Bai Airport")
            @NotBlank @Size(min = 1, max = 300) String endPoint,
            @Schema(example = "45.500") @NotNull
            @DecimalMin("0.001") @Digits(integer = 9, fraction = 3)
            BigDecimal distanceKm,
            @Schema(example = "Google Maps verified route")
            @Size(min = 1, max = 1000) String distanceSource,
            @Schema(example = "2027-02-01T06:00:00Z")
            @NotNull OffsetDateTime plannedDeparture,
            @Schema(example = "2027-02-01T08:00:00Z")
            @NotNull OffsetDateTime plannedArrival,
            @Schema(example = "Asia/Ho_Chi_Minh")
            @Size(min = 1, max = 100) String startTimezone,
            @Schema(example = "Asia/Ho_Chi_Minh")
            @Size(min = 1, max = 100) String endTimezone,
            @Schema(example = "21") @Positive Long supplierId,
            @Schema(example = "VN0051")
            @Size(min = 1, max = 50) String plannedFlightNumber,
            @Schema(example = "60", minimum = "0", maximum = "525600")
            @Min(0) @Max(525600) Integer connectionBufferMinutes,
            @Schema(example = "0", minimum = "0", maximum = "525600")
            @Min(0) @Max(525600) Integer restDurationMinutes,
            @Schema(example = "Provide water before boarding.")
            @Size(min = 1, max = 2000) String careInstructions,
            @Schema(example = "1500.00", nullable = true)
            @DecimalMin("0.01") @Digits(integer = 14, fraction = 2)
            BigDecimal estimatedAirfare) {
        /** Legacy callers may omit the optional airfare estimate. */
        public PlanLegInput(int sequenceNo, PlanLegMode transportMode,
                String startPoint, String endPoint, BigDecimal distanceKm,
                String distanceSource, OffsetDateTime plannedDeparture,
                OffsetDateTime plannedArrival, String startTimezone,
                String endTimezone, Long supplierId,
                String plannedFlightNumber, Integer connectionBufferMinutes,
                Integer restDurationMinutes, String careInstructions) {
            this(sequenceNo, transportMode, startPoint, endPoint, distanceKm,
                    distanceSource, plannedDeparture, plannedArrival,
                    startTimezone, endTimezone, supplierId,
                    plannedFlightNumber, connectionBufferMinutes,
                    restDurationMinutes, careInstructions, null);
        }
    }

    /** Command to create a new immutable version of a transport plan. */
    public record PlanDraftRequest(
            @Schema(example = PlanExamples.COMMAND)
            @NotNull @Valid PlanCommand command,
            @Schema(example = "201") @Positive long revisionId,
            @Schema(example = "true") boolean setAsCurrentDraft,
            @Schema(example = "[" + PlanExamples.LEG_ITEM + "]")
            @NotEmpty @Size(min = 1, max = 20)
            List<@NotNull @Valid PlanLegInput> legs) { }

    /** Command to assign a fleet vehicle and driver to a ROAD leg. */
    public record PlanRoadAssignmentRequest(
            @Schema(example = PlanExamples.COMMAND)
            @NotNull @Valid PlanCommand command,
            @Schema(example = "11") @Positive long vehicleId,
            @Schema(example = "000000000001A2B3")
            @NotBlank @Size(min = 16, max = 16)
            @Pattern(regexp = "^[0-9a-fA-F]{16}$") String vehicleRecordVersion,
            @Schema(example = "41") @Positive long driverStaffId,
            @Schema(example = "60", minimum = "0", maximum = "525600")
            @NotNull @Min(0) @Max(525600) Integer driverBufferMinutes,
            @Schema(example = "Driver briefed on route.")
            @Size(min = 1, max = 1000) String conflictNotes) { }

    /** Command to assign physical stall equipment to an AIR leg. */
    public record PlanStallAssignmentRequest(
            @Schema(example = PlanExamples.COMMAND)
            @NotNull @Valid PlanCommand command,
            @Schema(example = "12") @Positive long stallId,
            @Schema(example = "000000000001A2B3")
            @NotBlank @Size(min = 16, max = 16)
            @Pattern(regexp = "^[0-9a-fA-F]{16}$") String stallRecordVersion,
            @Schema(example = "ACCEPTED")
            @NotNull CarrierAcceptanceState carrierAcceptanceStatus,
            @Schema(example = "CONFIRMED")
            @NotNull AssignmentState status,
            @Schema(example = "Main deck forward bay 2")
            @Size(min = 1, max = 500) String positionDescription,
            @Schema(example = "carrier-approval-2027-01.pdf")
            @Size(min = 1, max = 1024) String carrierApprovalFileKey,
            @Schema(example = "cargo-ops@carrier.example.com")
            @Size(min = 1, max = 500) String carrierContact) { }

    /** Command to record a flight reservation on an AIR leg. */
    public record PlanFlightReservationRequest(
            @Schema(example = PlanExamples.COMMAND)
            @NotNull @Valid PlanCommand command,
            @Schema(example = "501") @Positive long offerId,
            @Schema(example = "Vietnam Airlines Cargo")
            @NotBlank @Size(min = 1, max = 50) String airlineName,
            @Schema(example = "VN0051")
            @NotBlank @Size(min = 1, max = 50) String flightNumber,
            @Schema(example = "AWB-180-99887766")
            @Size(min = 1, max = 150) String reservationReference,
            @Schema(example = "CONFIRMED")
            @NotNull ReservationState status,
            @Schema(example = "flight-booking-2027.pdf")
            @Size(min = 1, max = 1024) String evidenceFileKey,
            @Schema(example = "2027-02-10T00:00:00Z")
            OffsetDateTime expiresAt,
            @Schema(example = "cargo-desk@airline.example.com")
            @Size(min = 1, max = 500) String supplierContact,
            @Schema(example = "3.0") @NotNull
            @DecimalMin("0.1") @Digits(integer = 4, fraction = 1)
            BigDecimal usableCapacitySlots) { }

    /** Command to record a quarantine facility reservation for the order. */
    public record PlanQuarantineReservationRequest(
            @Schema(example = PlanExamples.COMMAND)
            @NotNull @Valid PlanCommand command,
            @Schema(example = "201") @Positive long revisionId,
            @Schema(example = "National Equine Quarantine Station")
            @NotBlank @Size(min = 1, max = 300) String facilityName,
            @Schema(example = "QRES-2027-7788")
            @Size(min = 1, max = 150) String reservationReference,
            @Schema(example = "2027-02-02T00:00:00Z")
            @NotNull OffsetDateTime startsAt,
            @Schema(example = "2027-02-16T00:00:00Z")
            @NotNull OffsetDateTime endsAt,
            @Schema(example = "CONFIRMED")
            @NotNull ReservationState status,
            @Schema(example = "21") @Positive Long supplierId,
            @Schema(example = "quarantine-booking-2027.pdf")
            @Size(min = 1, max = 1024) String evidenceFileKey,
            @Schema(example = "2027-02-20T00:00:00Z")
            OffsetDateTime expiresAt,
            @Schema(example = "booking@quarantine.example.com")
            @Size(min = 1, max = 500) String facilityContact) { }

    /** Command to record a verified insurance policy for the order. */
    public record PlanInsurancePolicyRequest(
            @Schema(example = PlanExamples.COMMAND)
            @NotNull @Valid PlanCommand command,
            @Schema(example = "201") @Positive long revisionId,
            @Schema(example = "Equine Global Underwriters")
            @NotBlank @Size(min = 1, max = 300) String insurerName,
            @Schema(example = "POL-EQ-2027-9001")
            @NotBlank @Size(min = 1, max = 150) String policyNumber,
            @Schema(example = "insurance-policy-2027.pdf")
            @Size(min = 1, max = 1024) String evidenceFileKey,
            @Schema(example = "2027-02-01T00:00:00Z")
            @NotNull OffsetDateTime coverageFrom,
            @Schema(example = "2027-02-20T00:00:00Z")
            @NotNull OffsetDateTime coverageTo,
            @Schema(example = "CONFIRMED")
            @NotNull ReservationState status,
            @Schema(example = "Full mortality and transit cover.")
            @Size(min = 1, max = 2000) String policyTerms) { }

    /** Command to confirm a plan with an accepted quote as CurrentPlanID. */
    public record PlanConfirmRequest(
            @Schema(example = PlanExamples.COMMAND)
            @NotNull @Valid PlanCommand command,
            @Schema(example = "301") @Positive long acceptedQuoteId) { }

    /** Staff view of a trip staff assignment on a leg. */
    public record PlanTripAssignmentView(
            @Schema(example = "701") @Positive long assignmentId,
            @Schema(example = "401") @Positive long legId,
            @Schema(example = "41") @Positive long staffId,
            @Schema(example = "DRIVER") @NotNull TripRole roleInTrip,
            @Schema(example = "2027-02-01T06:00:00Z")
            OffsetDateTime assignedFrom,
            @Schema(example = "2027-02-01T08:00:00Z") OffsetDateTime assignedTo,
            @Schema(example = "CONFIRMED") @NotNull AssignmentState status,
            @Schema(example = "60", minimum = "0", maximum = "525600")
            @Min(0) @Max(525600) Integer bufferMinutes,
            @Schema(example = "Driver briefed on route.")
            @Size(min = 1, max = 1000) String conflictNotes) { }

    /** Staff view of a physical stall assignment on an AIR leg. */
    public record PlanStallAssignmentView(
            @Schema(example = "801") @Positive long stallAssignmentId,
            @Schema(example = "402") @Positive long legId,
            @Schema(example = "12") @Positive long stallId,
            @Schema(example = "1.5") @NotNull
            @DecimalMin("0.1") @Digits(integer = 4, fraction = 1)
            BigDecimal slotQuantity,
            @Schema(example = "SHARED") @NotNull StallSharing sharingCode,
            @Schema(example = "SUITABLE")
            @NotBlank @Size(min = 1, max = 50) String fitStatus,
            @Schema(example = "ACCEPTED")
            @NotNull CarrierAcceptanceState carrierAcceptanceStatus,
            @Schema(example = "2027-02-01T10:00:00Z")
            OffsetDateTime assignedFrom,
            @Schema(example = "2027-02-01T20:00:00Z") OffsetDateTime assignedTo,
            @Schema(example = "CONFIRMED") @NotNull AssignmentState status,
            @Schema(example = "Main deck forward bay 2")
            @Size(min = 1, max = 500) String positionDescription,
            @Schema(example = "carrier-approval-2027-01.pdf")
            @Size(min = 1, max = 1024) String carrierApprovalFileKey,
            @Schema(example = "2027-01-25T10:00:00Z")
            OffsetDateTime carrierApprovedAt,
            @Schema(example = "cargo-ops@carrier.example.com")
            @Size(min = 1, max = 500) String carrierContact) { }

    /** Staff view of a flight reservation on an AIR leg. */
    public record PlanFlightReservationView(
            @Schema(example = "901") @Positive long reservationId,
            @Schema(example = "402") @Positive long legId,
            @Schema(example = "501") @Positive long offerId,
            @Schema(example = "Vietnam Airlines Cargo")
            @NotBlank @Size(min = 1, max = 50) String airlineName,
            @Schema(example = "VN0051")
            @NotBlank @Size(min = 1, max = 50) String flightNumber,
            @Schema(example = "AWB-180-99887766")
            @Size(min = 1, max = 150) String reservationReference,
            @Schema(example = "CONFIRMED") @NotNull ReservationState status,
            @Schema(example = "2027-01-25T10:00:00Z")
            OffsetDateTime confirmedAt,
            @Schema(example = "flight-booking-2027.pdf")
            @Size(min = 1, max = 1024) String evidenceFileKey,
            @Schema(example = "2027-02-10T00:00:00Z") OffsetDateTime expiresAt,
            @Schema(example = "32") @Positive Long confirmedBy,
            @Schema(example = "cargo-desk@airline.example.com")
            @Size(min = 1, max = 500) String supplierContact,
            @Schema(example = "3.0") @NotNull
            @DecimalMin("0.1") @Digits(integer = 4, fraction = 1)
            BigDecimal usableCapacitySlots) { }

    /** Staff view of a quarantine reservation for the revision. */
    public record PlanQuarantineReservationView(
            @Schema(example = "951") @Positive long reservationId,
            @Schema(example = "601") @Positive long quarantineChoiceId,
            @Schema(example = "National Equine Quarantine Station")
            @NotBlank @Size(min = 1, max = 300) String facilityName,
            @Schema(example = "QRES-2027-7788")
            @Size(min = 1, max = 150) String reservationReference,
            @Schema(example = "2027-02-02T00:00:00Z")
            @NotNull OffsetDateTime startsAt,
            @Schema(example = "2027-02-16T00:00:00Z")
            @NotNull OffsetDateTime endsAt,
            @Schema(example = "CONFIRMED") @NotNull ReservationState status,
            @Schema(example = "21") @Positive Long supplierId,
            @Schema(example = "quarantine-booking-2027.pdf")
            @Size(min = 1, max = 1024) String evidenceFileKey,
            @Schema(example = "32") @Positive Long confirmedBy,
            @Schema(example = "2027-01-25T10:00:00Z")
            OffsetDateTime confirmedAt,
            @Schema(example = "2027-02-20T00:00:00Z") OffsetDateTime expiresAt,
            @Schema(example = "booking@quarantine.example.com")
            @Size(min = 1, max = 500) String facilityContact) { }

    /** Staff view of a verified insurance policy for the revision. */
    public record PlanInsurancePolicyView(
            @Schema(example = "961") @Positive long policyId,
            @Schema(example = "602") @Positive long insuranceChoiceId,
            @Schema(example = "Equine Global Underwriters")
            @NotBlank @Size(min = 1, max = 300) String insurerName,
            @Schema(example = "POL-EQ-2027-9001")
            @NotBlank @Size(min = 1, max = 150) String policyNumber,
            @Schema(example = "insurance-policy-2027.pdf")
            @Size(min = 1, max = 1024) String evidenceFileKey,
            @Schema(example = "2027-02-01T00:00:00Z")
            @NotNull OffsetDateTime coverageFrom,
            @Schema(example = "2027-02-20T00:00:00Z")
            @NotNull OffsetDateTime coverageTo,
            @Schema(example = "CONFIRMED") @NotNull ReservationState status,
            @Schema(example = "32") @Positive Long verifiedBy,
            @Schema(example = "2027-01-25T10:00:00Z") OffsetDateTime verifiedAt,
            @Schema(example = "Full mortality and transit cover.")
            @Size(min = 1, max = 2000) String policyTerms) { }

    /** Staff view of a multimodal trip leg and its assignments. */
    public record PlanLegView(
            @Schema(example = "401") @Positive long legId,
            @Schema(example = "311") @Positive long planId,
            @Schema(example = "11") @Positive Long vehicleId,
            @Schema(example = "1") @Positive int sequenceNo,
            @Schema(example = "ROAD") @NotNull PlanLegMode transportMode,
            @Schema(example = "Hanoi Stable")
            @NotBlank @Size(min = 1, max = 300) String startPoint,
            @Schema(example = "Noi Bai Airport")
            @NotBlank @Size(min = 1, max = 300) String endPoint,
            @Schema(example = "45.500") @NotNull
            @DecimalMin("0.001") @Digits(integer = 9, fraction = 3)
            BigDecimal distanceKm,
            @Schema(example = "2027-02-01T06:00:00Z")
            @NotNull OffsetDateTime plannedDeparture,
            @Schema(example = "2027-02-01T08:00:00Z")
            @NotNull OffsetDateTime plannedArrival,
            @Schema(example = "PLANNED") @NotNull LegState legStatus,
            @Schema(example = "Asia/Ho_Chi_Minh")
            @Size(min = 1, max = 100) String startTimezone,
            @Schema(example = "Asia/Ho_Chi_Minh")
            @Size(min = 1, max = 100) String endTimezone,
            @Schema(example = "21") @Positive Long supplierId,
            @Schema(example = "VN0051")
            @Size(min = 1, max = 50) String plannedFlightNumber,
            @Schema(example = "60", minimum = "0", maximum = "525600")
            @Min(0) @Max(525600) Integer connectionBufferMinutes,
            @Schema(example = "0", minimum = "0", maximum = "525600")
            @Min(0) @Max(525600) Integer restDurationMinutes,
            @Schema(example = "Provide water before boarding.")
            @Size(min = 1, max = 2000) String careInstructions,
            @Schema(example = "Google Maps verified route")
            @Size(min = 1, max = 1000) String distanceSource,
            @Schema(example = "32") @Positive Long distanceConfirmedBy,
            @Schema(example = "2027-01-25T09:00:00Z")
            OffsetDateTime distanceConfirmedAt,
            @Schema(example = "[]")
            @NotNull List<@Valid PlanTripAssignmentView> staffAssignments,
            @Schema(example = "[]")
            @NotNull List<@Valid PlanStallAssignmentView> stallAssignments,
            @Schema(example = "[]")
            @NotNull List<@Valid PlanFlightReservationView>
                    flightReservations) { }

    /** Summary header for a transport plan version. */
    public record PlanSummaryView(
            @Schema(example = "311") @Positive long planId,
            @Schema(example = "101") @Positive long orderId,
            @Schema(example = "201") @Positive long revisionId,
            @Schema(example = "1") @Positive int versionNo,
            @Schema(example = "DRAFT") @NotNull PlanState planStatus,
            @Schema(example = "true") boolean currentPlan,
            @Schema(example = "301") @Positive Long acceptedQuoteId,
            @Schema(example = "32") @Positive long createdBy,
            @Schema(example = "2027-02-01T06:00:00Z")
            @NotNull OffsetDateTime plannedStartTime,
            @Schema(example = "2027-02-01T20:00:00Z")
            @NotNull OffsetDateTime plannedEndTime,
            @Schema(example = "PENDING")
            @NotNull GateState financialGateStatus,
            @Schema(example = "PENDING")
            @NotNull GateState operationalGateStatus,
            @Schema(example = "PENDING")
            @NotNull GateState docGateStatus) { }

    /** Bounded page of transport plan versions for an order. */
    public record PlanPageResponse(
            @Schema(example = "[]") @NotNull List<@Valid PlanSummaryView> items,
            @Schema(example = "0", minimum = "0") @Min(0) int page,
            @Schema(example = "20", minimum = "1", maximum = "100")
            @Min(1) @Max(100) int size,
            @Schema(example = "1", minimum = "0")
            @Min(0) long totalElements) { }

    /** Candidate order eligible to share a physical 3-slot air stall. */
    public record SharedStallCandidateView(
            @Schema(example = "102") @Positive long candidateOrderId,
            @Schema(example = "202") @Positive long candidateRevisionId,
            @Schema(example = "VN")
            @NotBlank @Size(min = 1, max = 10) String originCountryCode,
            @Schema(example = "GB")
            @NotBlank @Size(min = 1, max = 10) String destinationCountryCode,
            @Schema(example = "SHARED") @NotNull StallSharing sharingCode,
            @Schema(example = "1.5") @NotNull
            @DecimalMin("0.1") @Digits(integer = 4, fraction = 1)
            BigDecimal candidateSlotQuantity,
            @Schema(example = "3.0") @NotNull
            @DecimalMin("0.1") @Digits(integer = 4, fraction = 1)
            BigDecimal combinedSlotQuantity,
            @Schema(example = "312") @Positive Long candidatePlanId,
            @Schema(example = "405") @Positive Long candidateAirLegId,
            @Schema(example = "VN0051")
            @Size(min = 1, max = 50) String plannedFlightNumber,
            @Schema(example = "12") @Positive Long assignedStallId,
            @Schema(example = "2027-02-01T09:00:00Z")
            @NotNull OffsetDateTime windowStart,
            @Schema(example = "2027-02-01T17:00:00Z")
            @NotNull OffsetDateTime windowEnd) { }

    /** Full operational plan view with legs, quarantine and insurance. */
    public record PlanDetailResponse(
            @Schema(example = "101") @Positive long orderId,
            @Schema(example = "000000000001A2B3")
            @NotBlank @Size(min = 16, max = 16)
            @Pattern(regexp = "^[0-9a-fA-F]{16}$") String recordVersion,
            @Schema(example = "311") @Positive long planId,
            @Schema(example = "201") @Positive long revisionId,
            @Schema(example = "1") @Positive int versionNo,
            @Schema(example = "CONFIRMED") @NotNull PlanState planStatus,
            @Schema(example = "true") boolean currentPlan,
            @Schema(example = "301") @Positive Long acceptedQuoteId,
            @Schema(example = "32") @Positive long createdBy,
            @Schema(example = "2027-02-01T06:00:00Z")
            @NotNull OffsetDateTime plannedStartTime,
            @Schema(example = "2027-02-01T20:00:00Z")
            @NotNull OffsetDateTime plannedEndTime,
            @Schema(example = "PENDING")
            @NotNull GateState financialGateStatus,
            @Schema(example = "PASS")
            @NotNull GateState operationalGateStatus,
            @Schema(example = "PENDING")
            @NotNull GateState docGateStatus,
            @Schema(example = "2027-01-25T10:00:00Z")
            OffsetDateTime gatesCheckedAt,
            @Schema(example = "[]") @NotNull List<@Valid PlanLegView> legs,
            @Schema(example = "[]")
            @NotNull List<@Valid PlanQuarantineReservationView>
                    quarantineReservations,
            @Schema(example = "[]")
            @NotNull List<@Valid PlanInsurancePolicyView> insurancePolicies,
            @Schema(example = "[\"QUOTE_NOT_LINKED\"]")
            @NotNull List<@NotBlank @Size(min = 1, max = 80) String>
                    confirmationBlockers) { }
}
