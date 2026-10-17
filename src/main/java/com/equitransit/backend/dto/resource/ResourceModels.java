/*
 * ResourceModels
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Maintain sourced resources and transactional availability.
 */
package com.equitransit.backend.dto.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
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
import java.util.UUID;

/** Explicit resource contracts; no untyped JSON enters public schemas. */
public final class ResourceModels {

    private ResourceModels() { }

    /** Physical catalogue kind, separate from an operating assignment. */
    public enum ResourceKind { VEHICLE, STALL, SUPPLIER, POLICY }

    /** Catalogue lifecycle; no physical deletion endpoint exists. */
    public enum AssetState { ACTIVE, INACTIVE, MAINTENANCE }

    /** Only sourced approved policy versions can establish fit. */
    public enum PolicyState { DRAFT, APPROVED, INACTIVE }

    /** ROAD vehicles and AIR stall equipment are distinct. */
    public enum ResourceMode { ROAD, AIR }

    /** Unknown evidence stays pending, not available by default. */
    public enum AvailabilityState { AVAILABLE, UNAVAILABLE, PENDING }

    /** Typed causes; never exposes another customer's assignment details. */
    public enum ResourceBlocker {
        INACTIVE, SUPPLIER_INACTIVE, MAINTENANCE, DOCUMENT_EXPIRED,
        DOCUMENT_PENDING, BUFFER_PENDING, OVERLAP, SCHEDULE_PENDING,
        CAPACITY, MEASUREMENTS_PENDING, PHYSICAL_FIT, WEIGHT_LIMIT_PENDING,
        FIT_POLICY_PENDING, FIT_POLICY_AMBIGUOUS, FIT_POLICY_UNSUITABLE,
        SLOT_SELECTION_PENDING
    }

    /** UUID retry and SQL rowversion; version is null only on creation. */
    public record ResourceCommand(
            @Schema(example = "c2046c13-4c31-49b9-8695-edbab59eec2b")
            @NotNull UUID operationId,
            @Schema(example = "000000000001A2B3")
            @Pattern(regexp = "^[0-9a-fA-F]{16}$") String recordVersion) { }

    /** A null maintenance end represents ongoing maintenance. */
    public record ResourceWindow(
            @Schema(example = "2027-01-01T00:00:00Z")
            OffsetDateTime maintenanceFrom,
            @Schema(example = "2027-01-02T00:00:00Z")
            OffsetDateTime maintenanceTo,
            @Schema(example = "2028-01-01T00:00:00Z")
            OffsetDateTime documentExpiresAt,
            @Schema(example = "60", minimum = "0", maximum = "525600")
            @NotNull @Min(0) @Max(525600) Integer bufferMinutes,
            @Schema(example = "Maintenance reviewed by fleet team.")
            @Size(min = 1, max = 2000) String notes) { }

    /** Road fleet metadata, never an aircraft or a price multiplier. */
    public record ResourceVehicleData(
            @Schema(example = "VEH-101")
            @NotBlank @Size(min = 1, max = 50) String vehicleCode,
            @Schema(example = "51C-12345")
            @NotBlank @Size(min = 1, max = 50) String plateNumber,
            @Schema(example = "2") @Min(1) int horseCapacity,
            @Schema(example = "Temperature-controlled ventilation")
            @NotBlank @Size(min = 1, max = 50) String climateSystemType,
            @Schema(example = "ACTIVE") @NotNull AssetState status,
            @Schema(example = "21") @Positive Long ownerSupplierId,
            @Schema(example = "{\"bufferMinutes\":60}")
            @NotNull @Valid ResourceWindow window) { }

    /** Dimensions refer to a real piece of equipment. */
    public record ResourceStallData(
            @Schema(example = "STALL-101")
            @NotBlank @Size(min = 1, max = 50) String stallCode,
            @Schema(example = "3.0") @NotNull
            @DecimalMin("0.1") @Digits(integer = 4, fraction = 1)
            BigDecimal capacitySlots,
            @Schema(example = "240.00") @NotNull
            @DecimalMin("0.01") @Digits(integer = 6, fraction = 2)
            BigDecimal internalHeightCm,
            @Schema(example = "240.00") @NotNull
            @DecimalMin("0.01") @Digits(integer = 6, fraction = 2)
            BigDecimal internalWidthCm,
            @Schema(example = "1800.00")
            @DecimalMin("0.01") @Digits(integer = 6, fraction = 2)
            BigDecimal weightLimitKg,
            @Schema(example = "ACTIVE") @NotNull AssetState status,
            @Schema(example = "21") @Positive Long ownerSupplierId,
            @Schema(example = "{\"bufferMinutes\":60}")
            @NotNull @Valid ResourceWindow window) { }

    /** Supplier tags are bounded descriptions, not invented service enums. */
    public record ResourceSupplierData(
            @Schema(example = "Approved transport partner")
            @NotBlank @Size(min = 1, max = 200) String name,
            @Schema(example = "Operations desk")
            @Size(min = 1, max = 150) String contactName,
            @Schema(example = "operations@example.com")
            @Email @Size(min = 1, max = 255) String email,
            @Schema(example = "+84901234567")
            @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$") String phone,
            @Schema(example = "[\"Road transport\",\"Air stall equipment\"]")
            @NotNull @Size(max = 30)
            List<@NotBlank @Size(min = 1, max = 50) String> services,
            @Schema(example = "true") boolean active) { }

    /** Review identities/timestamps are server-owned, never command fields. */
    public record ResourcePolicyData(
            @Schema(example = "CARRIER-WIDE-2027")
            @NotBlank @Size(min = 1, max = 50) String policyCode,
            @Schema(example = "11") @Positive Long stallId,
            @Schema(example = "21") @Positive Long carrierSupplierId,
            @Schema(example = "1.5", description = "Allowed: 1, 1.5, 3")
            @NotNull @Digits(integer = 1, fraction = 1)
            BigDecimal slotQuantity,
            @Schema(example = "650.00")
            @DecimalMin("0.01") @Digits(integer = 6, fraction = 2)
            BigDecimal maxWeightKg,
            @Schema(example = "190.00")
            @DecimalMin("0.01") @Digits(integer = 6, fraction = 2)
            BigDecimal maxHeightCm,
            @Schema(example = "90.00")
            @DecimalMin("0.01") @Digits(integer = 6, fraction = 2)
            BigDecimal maxWidthCm,
            @Schema(example = "2027-01-01T00:00:00Z")
            @NotNull OffsetDateTime effectiveFrom,
            @Schema(example = "2028-01-01T00:00:00Z")
            OffsetDateTime effectiveTo,
            @Schema(example = "https://carrier.example.com/fit-policy")
            @Size(min = 1, max = 2048) String sourceUrl,
            @Schema(example = "0849d3a2-8d0c-4b75-8855-aeb8c16c71b9")
            @Size(min = 1, max = 1024) String sourceFileKey,
            @Schema(example = "DRAFT") @NotNull PolicyState status) { }

    /** Authorized, current-revision availability probe, never a reservation. */
    public record ResourceAvailabilityRequest(
            @Schema(example = "101") @Positive long orderId,
            @Schema(example = "201") @Positive long revisionId,
            @Schema(example = "AIR") @NotNull ResourceMode mode,
            @Schema(example = "2027-02-01T08:00:00Z")
            @NotNull OffsetDateTime start,
            @Schema(example = "2027-02-01T18:00:00Z")
            @NotNull OffsetDateTime end,
            @Schema(example = "21") @Positive Long carrierSupplierId,
            @Schema(example = "301",
                    description = "Optional existing leg of this revision")
            @Positive Long legId) { }

    /** Results must be rechecked under a retained lock by F10 writers. */
    public record ResourceAvailability(
            @Schema(example = "STALL") ResourceKind kind,
            @Schema(example = "11") long resourceId,
            @Schema(example = "000000000001A2B3") String recordVersion,
            @Schema(example = "AVAILABLE") AvailabilityState status,
            @Schema(example = "2027-02-01T07:00:00Z") OffsetDateTime asOf,
            @Schema(example = "14") Long policyId,
            @Schema(example = "false") boolean carrierConfirmed,
            @Schema(example = "[\"FIT_POLICY_PENDING\"]")
            List<ResourceBlocker> blockers) { }

    /** Case-scoped choice without supplier contacts or maintenance notes. */
    public record ResourceCandidate(
            @Schema(example = "STALL-101") String resourceCode,
            @Schema(example = "{\"kind\":\"STALL\",\"resourceId\":11}")
            ResourceAvailability availability) { }

    /** Each catalogue candidate is assessed; pending stays visible. */
    public record ResourceAvailabilityPage(
            @Schema(example = "[]") List<ResourceCandidate> items,
            @Schema(example = "0") int page,
            @Schema(example = "20") int size,
            @Schema(example = "1") long totalElements,
            @Schema(example = "1") long totalPages) { }

    /** Driver-visible technical data from the currently assigned leg. */
    public record ResourceAssignedVehicle(
            @Schema(example = "301") long legId,
            @Schema(example = "11") long vehicleId,
            @Schema(example = "VEH-101") String vehicleCode,
            @Schema(example = "51C-12345") String plateNumber,
            @Schema(example = "2") int horseCapacity,
            @Schema(example = "Temperature-controlled ventilation")
            String climateSystemType,
            @Schema(example = "ACTIVE") AssetState status) { }
    /** Full replacement command; creates use a null recordVersion. */
    public record ResourceVehicleWrite(
            @Schema(example = ResourceExamples.COMMAND)
            @NotNull @Valid ResourceCommand command,
            @Schema(example = ResourceExamples.VEHICLE)
            @NotNull @Valid ResourceVehicleData data) { }

    /** Internal staff view with observed SQL rowversion. */
    public record ResourceVehicleView(
            @Schema(example = "11") long id,
            @Schema(example = "000000000001A2B3") String recordVersion,
            @Schema(example = ResourceExamples.VEHICLE)
            ResourceVehicleData data) { }

    /** Bounded catalogue page; no per-row lookups. */
    public record ResourceVehiclePage(
            @Schema(example = "[]")
            List<ResourceVehicleView> items,
            @Schema(example = "0") int page,
            @Schema(example = "20") int size,
            @Schema(example = "1") long totalElements) { }

    /** Full replacement command; creates use a null recordVersion. */
    public record ResourceStallWrite(
            @Schema(example = ResourceExamples.COMMAND)
            @NotNull @Valid ResourceCommand command,
            @Schema(example = ResourceExamples.STALL)
            @NotNull @Valid ResourceStallData data) { }

    /** Internal staff view with observed SQL rowversion. */
    public record ResourceStallView(
            @Schema(example = "11") long id,
            @Schema(example = "000000000001A2B3") String recordVersion,
            @Schema(example = ResourceExamples.STALL)
            ResourceStallData data) { }

    /** Bounded catalogue page; no per-row lookups. */
    public record ResourceStallPage(
            @Schema(example = "[]")
            List<ResourceStallView> items,
            @Schema(example = "0") int page,
            @Schema(example = "20") int size,
            @Schema(example = "1") long totalElements) { }

    /** Full replacement command; creates use a null recordVersion. */
    public record ResourceSupplierWrite(
            @Schema(example = ResourceExamples.COMMAND)
            @NotNull @Valid ResourceCommand command,
            @Schema(example = ResourceExamples.SUPPLIER)
            @NotNull @Valid ResourceSupplierData data) { }

    /** Internal staff view with observed SQL rowversion. */
    public record ResourceSupplierView(
            @Schema(example = "11") long id,
            @Schema(example = "000000000001A2B3") String recordVersion,
            @Schema(example = ResourceExamples.SUPPLIER)
            ResourceSupplierData data) { }

    /** Bounded catalogue page; no per-row lookups. */
    public record ResourceSupplierPage(
            @Schema(example = "[]")
            List<ResourceSupplierView> items,
            @Schema(example = "0") int page,
            @Schema(example = "20") int size,
            @Schema(example = "1") long totalElements) { }

    /** Full replacement command; creates use a null recordVersion. */
    public record ResourcePolicyWrite(
            @Schema(example = ResourceExamples.COMMAND)
            @NotNull @Valid ResourceCommand command,
            @Schema(example = ResourceExamples.POLICY)
            @NotNull @Valid ResourcePolicyData data) { }

    /** Internal staff view with observed SQL rowversion. */
    public record ResourcePolicyView(
            @Schema(example = "11") long id,
            @Schema(example = "000000000001A2B3") String recordVersion,
            @Schema(example = "1") int version,
            @Schema(example = "31") Long reviewedBy,
            @Schema(example = "2027-01-01T00:00:00Z")
            OffsetDateTime reviewedAt,
            @Schema(example = ResourceExamples.POLICY)
            ResourcePolicyData data) { }

    /** Bounded catalogue page; no per-row lookups. */
    public record ResourcePolicyPage(
            @Schema(example = "[]")
            List<ResourcePolicyView> items,
            @Schema(example = "0") int page,
            @Schema(example = "20") int size,
            @Schema(example = "1") long totalElements) { }
}
