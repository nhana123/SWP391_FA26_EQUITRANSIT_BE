package com.equitransit.backend.dto.route;

import com.equitransit.backend.entity.RouteTransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Indicative USD estimate per horse; custom-route amounts remain unknown. */
public record RouteMatchResponse(
        @Schema(example = "true") boolean matched,
        @Schema(example = "1", nullable = true) Long templateId,
        @Schema(example = "ROAD", nullable = true)
        RouteTransportMode transportMode,
        @Schema(example = "TP.HCM → Nam Ninh", nullable = true)
        String routeSummary,
        @Schema(example = "1950.00", nullable = true)
        BigDecimal estimatedRoadKm,
        @Schema(example = "0.00", nullable = true) BigDecimal estimatedAirKm,
        @Schema(example = "35.5", nullable = true) BigDecimal estimatedHours,
        @Schema(example = "1950.00", nullable = true)
        BigDecimal estimatedRoadCost,
        @Schema(example = "0.00", nullable = true) BigDecimal estimatedAirCost,
        @Schema(example = "1950.00", nullable = true)
        BigDecimal totalEstimatedCost,
        @Schema(example = "false") boolean requiresCustomCoordinatorPlan,
        @Schema(example = "Indicative estimate; final quotation needs review.")
        String message,
        @Schema(example = "Trụ sở EquiTransit Việt Nam (Củ Chi, TP.HCM)",
                nullable = true)
        String originHubName,
        @Schema(example = "Đường Bến Súc, An Phú, Củ Chi, TP. Hồ Chí Minh",
                nullable = true)
        String originHubAddress,
        @Schema(example = "Trạm trung chuyển EquiTransit Bằng Tường",
                nullable = true)
        String destinationHubName,
        @Schema(example = "Khu thương mại cửa khẩu Hữu Nghị Quan, Bằng Tường",
                nullable = true)
        String destinationHubAddress,
        @Schema(example = "39.45", nullable = true)
        BigDecimal firstMileRoadKm,
        @Schema(example = "1950.00", nullable = true)
        BigDecimal mainLegRoadKm,
        @Schema(example = "195.20", nullable = true)
        BigDecimal lastMileRoadKm,
        @Schema(example = "59.18", nullable = true)
        BigDecimal firstMileCost,
        @Schema(example = "351.36", nullable = true)
        BigDecimal lastMileCost,
        List<HubDistanceOption> originHubOptions,
        List<HubDistanceOption> destinationHubOptions,
        Integer originHubId, Integer destinationHubId) {

    /** Hub-aware compatibility constructor.
     * @param matched match @param templateId ID @param transportMode mode
     * @param routeSummary summary @param estimatedRoadKm road km
     * @param estimatedAirKm air km @param estimatedHours hours
     * @param estimatedRoadCost road USD @param estimatedAirCost air USD
     * @param totalEstimatedCost total
     * @param requiresCustomCoordinatorPlan custom
     * @param message note @param originHubName hub @param originHubAddress addr
     * @param destinationHubName hub @param destinationHubAddress addr
     * @param firstMileRoadKm pickup km @param mainLegRoadKm main km
     * @param lastMileRoadKm delivery km @param firstMileCost pickup USD
     * @param lastMileCost delivery USD */
    public RouteMatchResponse(boolean matched, Long templateId,
            RouteTransportMode transportMode, String routeSummary,
            BigDecimal estimatedRoadKm, BigDecimal estimatedAirKm,
            BigDecimal estimatedHours, BigDecimal estimatedRoadCost,
            BigDecimal estimatedAirCost, BigDecimal totalEstimatedCost,
            boolean requiresCustomCoordinatorPlan, String message,
            String originHubName, String originHubAddress,
            String destinationHubName, String destinationHubAddress,
            BigDecimal firstMileRoadKm, BigDecimal mainLegRoadKm,
            BigDecimal lastMileRoadKm, BigDecimal firstMileCost,
            BigDecimal lastMileCost) {
        this(matched, templateId, transportMode, routeSummary, estimatedRoadKm,
                estimatedAirKm, estimatedHours, estimatedRoadCost,
                estimatedAirCost, totalEstimatedCost,
                requiresCustomCoordinatorPlan, message, originHubName,
                originHubAddress, destinationHubName, destinationHubAddress,
                firstMileRoadKm, mainLegRoadKm, lastMileRoadKm,
                firstMileCost, lastMileCost, List.of(), List.of(), null, null);
    }

    /** Legacy 12-arg constructor retained for existing callers and tests.
     * @param matched whether a template matched @param templateId ID
     * @param transportMode mode @param routeSummary summary
     * @param estimatedRoadKm road km @param estimatedAirKm air km
     * @param estimatedHours hours @param estimatedRoadCost road USD
     * @param estimatedAirCost air USD @param totalEstimatedCost total USD
     * @param requiresCustomCoordinatorPlan custom flag @param message note */
    public RouteMatchResponse(boolean matched, Long templateId,
            RouteTransportMode transportMode, String routeSummary,
            BigDecimal estimatedRoadKm, BigDecimal estimatedAirKm,
            BigDecimal estimatedHours, BigDecimal estimatedRoadCost,
            BigDecimal estimatedAirCost, BigDecimal totalEstimatedCost,
            boolean requiresCustomCoordinatorPlan, String message) {
        this(matched, templateId, transportMode, routeSummary,
                estimatedRoadKm, estimatedAirKm, estimatedHours,
                estimatedRoadCost, estimatedAirCost, totalEstimatedCost,
                requiresCustomCoordinatorPlan, message, null, null, null, null,
                estimatedRoadKm != null ? BigDecimal.ZERO.setScale(2) : null,
                estimatedRoadKm,
                estimatedRoadKm != null ? BigDecimal.ZERO.setScale(2) : null,
                estimatedRoadCost != null ? BigDecimal.ZERO.setScale(2) : null,
                estimatedRoadCost != null ? BigDecimal.ZERO.setScale(2) : null);
    }
    /** @param origin origin choices @param destination destination choices
     * @param originId applied hub @param destinationId applied hub
     * @return enriched result retaining all legacy fields */
    public RouteMatchResponse withHubOptions(List<HubDistanceOption> origin,
            List<HubDistanceOption> destination, Integer originId,
            Integer destinationId) {
        return new RouteMatchResponse(matched, templateId, transportMode,
                routeSummary, estimatedRoadKm, estimatedAirKm, estimatedHours,
                estimatedRoadCost, estimatedAirCost, totalEstimatedCost,
                requiresCustomCoordinatorPlan, message, originHubName,
                originHubAddress, destinationHubName, destinationHubAddress,
                firstMileRoadKm, mainLegRoadKm, lastMileRoadKm,
                firstMileCost, lastMileCost, origin, destination,
                originId, destinationId);
    }

}


