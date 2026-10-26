package com.equitransit.backend.dto.route;

import com.equitransit.backend.entity.order.AirStallPreference;
import com.equitransit.backend.entity.RouteTransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Customer endpoint selection; no client-supplied distances or prices. */
public record RouteMatchRequest(
        @NotNull @Pattern(regexp = "[A-Za-z]{2}")
        @Schema(example = "VN") String originCountryCode,
        @Size(max = 100) @Schema(example = "Hà Nội", nullable = true)
        String originCity,
        @Pattern(regexp = "[A-Za-z]{3}")
        @Schema(example = "HAN", nullable = true) String originAirportCode,
        @NotNull @Pattern(regexp = "[A-Za-z]{2}")
        @Schema(example = "FR") String destinationCountryCode,
        @Size(max = 100) @Schema(example = "Paris", nullable = true)
        String destinationCity,
        @Pattern(regexp = "[A-Za-z]{3}")
        @Schema(example = "CDG", nullable = true) String destinationAirportCode,
        @Schema(example = "AIR", nullable = true)
        RouteTransportMode transportMode,
        @Schema(example = "SHARED_ONE", nullable = true)
        AirStallPreference airStallPreference,
        @Size(max = 500)
        @Schema(example = "Quận 1, TP. Hồ Chí Minh", nullable = true)
        String pickupAddress,
        @Size(max = 500)
        @Schema(example = "Qingxiu District, Nanning", nullable = true)
        String deliveryAddress,
        @Positive @Schema(nullable = true) Integer originHubId,
        @Positive @Schema(nullable = true) Integer destinationHubId) {

    /** Address-aware legacy constructor.
     * @param originCountryCode country @param originCity city
     * @param originAirportCode IATA @param destinationCountryCode country
     * @param destinationCity city @param destinationAirportCode IATA
     * @param transportMode mode @param airStallPreference stall
     * @param pickupAddress pickup @param deliveryAddress delivery */
    public RouteMatchRequest(String originCountryCode, String originCity,
            String originAirportCode, String destinationCountryCode,
            String destinationCity, String destinationAirportCode,
            RouteTransportMode transportMode,
            AirStallPreference airStallPreference, String pickupAddress,
            String deliveryAddress) {
        this(originCountryCode, originCity, originAirportCode,
                destinationCountryCode, destinationCity, destinationAirportCode,
                transportMode, airStallPreference, pickupAddress,
                deliveryAddress, null, null);
    }

    /** Legacy 8-arg constructor without pickup/delivery addresses.
     * @param originCountryCode origin ISO @param originCity origin city
     * @param originAirportCode origin IATA @param destinationCountryCode dest
     * @param destinationCity dest city @param destinationAirportCode dest IATA
     * @param transportMode mode @param airStallPreference stall */
    public RouteMatchRequest(String originCountryCode, String originCity,
            String originAirportCode, String destinationCountryCode,
            String destinationCity, String destinationAirportCode,
            RouteTransportMode transportMode,
            AirStallPreference airStallPreference) {
        this(originCountryCode, originCity, originAirportCode,
                destinationCountryCode, destinationCity,
                destinationAirportCode, transportMode, airStallPreference,
                null, null);
    }
}
