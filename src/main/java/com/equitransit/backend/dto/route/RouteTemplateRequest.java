package com.equitransit.backend.dto.route;

import com.equitransit.backend.entity.RouteTransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Full catalog replacement; omitted rates/distances use the SQL defaults. */
public record RouteTemplateRequest(
        @NotBlank @Size(max = 50) @Schema(example = "ROUTE-VN-CN-ROAD")
        String routeCode,
        @NotBlank @Size(max = 200) @Schema(example = "Việt Nam - Nam Ninh")
        String routeName,
        @NotNull @Pattern(regexp = "[A-Za-z]{2}")
        @Schema(example = "VN") String originCountryCode,
        @Size(max = 100) @Schema(example = "TP. Hồ Chí Minh", nullable = true)
        String originCity,
        @Pattern(regexp = "[A-Za-z]{3}")
        @Schema(example = "SGN", nullable = true) String originAirportCode,
        @NotNull @Pattern(regexp = "[A-Za-z]{2}")
        @Schema(example = "CN") String destinationCountryCode,
        @Size(max = 100) @Schema(example = "Nam Ninh", nullable = true)
        String destinationCity,
        @Pattern(regexp = "[A-Za-z]{3}")
        @Schema(example = "NNG", nullable = true) String destinationAirportCode,
        @NotNull @Schema(example = "ROAD") RouteTransportMode transportMode,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        @Schema(example = "1950.00") BigDecimal estimatedRoadKm,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        @Schema(example = "0.00") BigDecimal estimatedAirKm,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 5, fraction = 1)
        @Schema(example = "35.5") BigDecimal estimatedHours,
        @NotBlank @Size(max = 1000)
        @Schema(example = "TP.HCM → Hữu Nghị Quan → Nam Ninh")
        String routeSummary,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2)
        @Schema(example = "1.00") BigDecimal baseRoadRate,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2)
        @Schema(example = "0.00") BigDecimal baseAirRate,
        @Schema(example = "true", nullable = true) Boolean isActive) { }
