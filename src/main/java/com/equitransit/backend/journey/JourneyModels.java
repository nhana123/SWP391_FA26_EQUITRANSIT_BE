package com.equitransit.backend.journey;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Additive v2 contracts. All timestamps are explicit UTC local timestamps. */
public final class JourneyModels {
    private static final String COUNTRY = "[A-Z]{2}";

    private JourneyModels() {
    }

    /** Tariffs are server-read; quantities have bounded explicit inputs. */
    public record Estimate(
            @Pattern(regexp = "ROAD|AIR") @NotNull String mode,
            @NotNull @DecimalMin("0.01") @DecimalMax("100000")
            @Digits(integer = 6, fraction = 2) BigDecimal distanceKm,
            @Min(1) @Max(100) int horseCount,
            @NotEmpty @Size(max = 30)
            List<@NotNull @Positive Long> tariffIds) {
    }

    /** Multi-horse booking; price recomputed inside creation transaction. */
    public record BookingRequest(
            @NotNull UUID operationId,
            @Pattern(regexp = "[A-Z]{2}") @NotNull String originCountry,
            @Pattern(regexp = "[A-Z]{2}") @NotNull String destinationCountry,
            @NotBlank @Size(max = 500) String originAddress,
            @NotBlank @Size(max = 500) String destinationAddress,
            @NotNull LocalDateTime pickupFrom,
            @NotNull LocalDateTime pickupTo,
            @NotNull LocalDateTime deliveryBy,
            @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> horseIds,
            @NotNull @Valid Estimate estimate,
            @Size(max = 20)
            List<@NotNull @Pattern(regexp = COUNTRY) String> transitCountries) {
    }

    /** Expected aggregate version and immutable retry identifier. */
    public record Command(@NotNull UUID operationId, @Positive long version) {
    }

    /** Manager assigns live staff and freezes deposit percentage. */
    public record Approval(@NotNull @Valid Command command,
            @Positive long specialistId, @Positive long coordinatorId,
            @Min(1) @Max(100) int depositPercent, boolean routeReviewed) {
    }

    /** Receipt is reconciled by manager, never asserted by customer. */
    public record PaymentRequest(@NotNull @Valid Command command,
            @NotNull @DecimalMin("0.01")
            @Digits(integer = 16, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 100) String bankReference) {
    }

    /**
     * Shared resource trip; physical capacity is read from vehicle for ROAD.
     */
    public record TripRequest(@NotNull UUID operationId,
            @NotNull @Pattern(regexp = "ROAD|AIR") String mode,
            @Positive Long vehicleId, @Positive Long supplierId,
            @Min(1) @Max(100) int capacity, @Positive long driverId,
            @NotNull @Pattern(regexp = "[A-Z]{2}") String originCountry,
            @NotNull @Pattern(regexp = "[A-Z]{2}") String destinationCountry,
            @NotNull LocalDateTime startsAt,
            @NotNull LocalDateTime endsAt,
            @Size(max = 20)
            List<@NotNull @Pattern(regexp = COUNTRY) String> transitCountries) {
    }

    /** Manifest selections may span customers but each horse has one trip. */
    public record ManifestRequest(@NotNull @Valid Command command,
            @NotEmpty @Size(max = 100)
            List<@NotNull @Positive Long> bookingHorseIds) {
    }

    /** One-step document upload and submission. */
    public record DocumentRequest(@NotNull @Valid Command command,
            @NotBlank @Size(max = 255) String fileName,
            @NotNull @Pattern(regexp =
                    "application/pdf|image/png|image/jpeg") String contentType,
            @NotBlank @Size(max = 6990508) String contentBase64,
            @NotNull LocalDateTime expiresAt) {
    }

    /** Reviewed submission key prevents review of a superseded document. */
    public record ReviewRequest(@NotNull @Valid Command command,
            @Positive long submissionId,
            @NotNull @Pattern(regexp = "APPROVED|REJECTED") String decision,
            @Size(max = 2000) String notes) {
    }

    /** Immutable evidence saved using the document upload file store. */
    public record DeliveryRequest(@NotNull @Valid Command command,
            @Positive long bookingHorseId,
            @NotBlank @Size(max = 150) String receiverName,
            @NotNull UUID evidenceKey,
            @NotNull LocalDateTime occurredAt) {
    }

    /** Manager-approved incident expense allocated to one booking. */
    public record ExtraRequest(@NotNull @Valid Command command,
            @Positive long bookingId,
            @NotNull @DecimalMin("0.01")
            @Digits(integer = 16, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 1000) String reason) {
    }

    /** Driver signs an explicit safety/horse-receipt checklist. */
    public record SafetyRequest(@NotNull @Valid Command command,
            boolean vehicleSafe, boolean waterReady, boolean beddingReady,
            boolean horsesReceived) {
    }
}
