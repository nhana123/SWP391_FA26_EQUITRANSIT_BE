package com.equitransit.backend.dto.route;

import java.math.BigDecimal;

/** Distance-ranked active hub; unknown distance/cost remains null.
 * @param locationId ID @param locationName name @param locationCode code
 * @param address address @param latitude latitude @param longitude longitude
 * @param isPrimaryHub primary @param distanceKm estimated road km
 * @param domesticCost USD @param isRecommendedNearest nearest known hub */
public record HubDistanceOption(int locationId, String locationName,
        String locationCode, String address, BigDecimal latitude,
        BigDecimal longitude, boolean isPrimaryHub, BigDecimal distanceKm,
        BigDecimal domesticCost, boolean isRecommendedNearest) { }
