package com.equitransit.backend.dto.location;

import com.equitransit.backend.entity.LocationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Catalog edit; omitted flags preserve current values on update. */
public record LocationRequest(
        @NotNull @Pattern(regexp = "[A-Za-z]{2}")
        @Schema(example = "VN") String countryCode,
        @Size(max = 100) @Schema(example = "Việt Nam", nullable = true)
        String countryName,
        @NotNull @Schema(example = "HUB") LocationType locationType,
        @NotBlank @Size(max = 150) @Schema(example = "EquiTransit Hub")
        String locationName,
        @Pattern(regexp = "[A-Za-z0-9_-]{1,10}")
        @Schema(example = "VN-HUB-01", nullable = true) String locationCode,
        @Size(max = 500) @Schema(example = "Củ Chi, TP.HCM", nullable = true)
        String address,
        @DecimalMin("-90") @DecimalMax("90")
        @Digits(integer = 3, fraction = 7)
        @Schema(example = "11.0067000", nullable = true) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180")
        @Digits(integer = 3, fraction = 7)
        @Schema(example = "106.5132000", nullable = true) BigDecimal longitude,
        @Schema(example = "true", nullable = true) Boolean isPrimaryHub,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        @Schema(example = "1.50", nullable = true) BigDecimal domesticRatePerKm,
        @Schema(example = "true", nullable = true) Boolean isActive) {

    /** Legacy 10-arg constructor without explicit domesticRatePerKm.
     * @param countryCode ISO country @param countryName label
     * @param locationType kind @param locationName name
     * @param locationCode code @param address full address
     * @param latitude lat @param longitude lon
     * @param isPrimaryHub primary flag @param isActive active flag */
    public LocationRequest(String countryCode, String countryName,
            LocationType locationType, String locationName,
            String locationCode, String address, BigDecimal latitude,
            BigDecimal longitude, Boolean isPrimaryHub, Boolean isActive) {
        this(countryCode, countryName, locationType, locationName,
                locationCode, address, latitude, longitude, isPrimaryHub,
                null, isActive);
    }
}
