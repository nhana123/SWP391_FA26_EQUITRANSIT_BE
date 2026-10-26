package com.equitransit.backend.dto.route;

import com.equitransit.backend.entity.RouteTransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Stored route catalog metadata; CreatedBy is the staff profile identity. */
public record RouteTemplateResponse(
        @Schema(example = "1") long routeTemplateId,
        @Schema(example = "ROUTE-VN-CN-ROAD") String routeCode,
        @Schema(example = "Việt Nam - Nam Ninh") String routeName,
        @Schema(example = "VN") String originCountryCode,
        @Schema(example = "TP. Hồ Chí Minh", nullable = true) String originCity,
        @Schema(example = "SGN", nullable = true) String originAirportCode,
        @Schema(example = "CN") String destinationCountryCode,
        @Schema(example = "Nam Ninh", nullable = true) String destinationCity,
        @Schema(example = "NNG", nullable = true) String destinationAirportCode,
        @Schema(example = "ROAD") RouteTransportMode transportMode,
        @Schema(example = "1950.00") BigDecimal estimatedRoadKm,
        @Schema(example = "0.00") BigDecimal estimatedAirKm,
        @Schema(example = "35.5") BigDecimal estimatedHours,
        @Schema(example = "TP.HCM → Hữu Nghị Quan → Nam Ninh")
        String routeSummary,
        @Schema(example = "1.00") BigDecimal baseRoadRate,
        @Schema(example = "0.00") BigDecimal baseAirRate,
        @Schema(example = "true") boolean isActive,
        @Schema(example = "4", nullable = true) Long createdBy,
        @Schema(example = "2026-10-25T00:00:00Z") OffsetDateTime createdAt,
        @Schema(example = "2026-10-25T00:00:00Z") OffsetDateTime updatedAt) { }
