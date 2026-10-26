package com.equitransit.backend.service;

import com.equitransit.backend.dto.response.OrderSnapshot;
import com.equitransit.backend.dto.route.HubDistanceOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Exact money splitting and conservative fixed-price ROAD eligibility. */
@Component
public class BookingFlowPolicy {
    /** @param total fixed total @return 30 percent rounded once to cents */
    public BigDecimal deposit(BigDecimal total) {
        return total.multiply(new BigDecimal("0.30"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** @param snapshot frozen server route/intents
     * @return null unless every selected company fee is known */
    public BigDecimal fixedRoadTotal(OrderSnapshot snapshot) {
        var route = snapshot.routeMatch();
        if (snapshot.request().getRoute() == null || route == null
                || !route.matched() || route.transportMode() == null
                || !route.transportMode().name().equals("ROAD")
                || route.totalEstimatedCost() == null
                || route.totalEstimatedCost().signum() <= 0
                || route.estimatedAirKm() != null
                        && route.estimatedAirKm().signum() > 0) {
            return null;
        }
        // Unknown/assessed add-ons must not be silently omitted from invoices.
        for (var intent : snapshot.services()) {
            if ("ROAD_TRANSPORT".equals(intent.serviceCode())) {
                if (!"EQUITRANSIT".equals(intent.arrangedBy())) {
                    return null;
                }
            } else if (!"CUSTOMER".equals(intent.arrangedBy())
                    && !("INSURANCE".equals(intent.serviceCode())
                            && "NONE".equals(intent.optionCode()))) {
                return null;
            }
        }
        if (!measured(route.originHubOptions(), route.originHubId())
                || !measured(route.destinationHubOptions(),
                        route.destinationHubId())) {
            return null;
        }
        BigDecimal total = route.totalEstimatedCost()
                .setScale(2, RoundingMode.HALF_UP);
        return total.precision() <= 18 && deposit(total).signum() > 0
                && total.subtract(deposit(total)).signum() > 0 ? total : null;
    }

    private boolean measured(List<HubDistanceOption> options,
            Integer selected) {
        return options != null && selected != null && options.stream()
                .anyMatch(o -> Objects.equals(o.locationId(), selected)
                        && o.distanceKm() != null);
    }
}

