package com.equitransit.backend.service;

import com.equitransit.backend.dto.route.RouteMatchRequest;
import com.equitransit.backend.dto.route.RouteMatchResponse;
import com.equitransit.backend.dto.route.RouteTemplateRequest;
import com.equitransit.backend.dto.route.RouteTemplateResponse;
import com.equitransit.backend.entity.AirStallPreference;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.LocationCatalogRepository.Location;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Catalog invariants and indicative cost calculation, independent of SQL. */
@Component
public class RouteTemplatePolicy {
    /** Free domestic transfer threshold between Hub and customer address. */
    public static final BigDecimal FREE_DOMESTIC_KM =
            new BigDecimal("100.00");
    /** Domestic transfer rate per km when distance exceeds 100 km. */
    public static final BigDecimal DEFAULT_DOMESTIC_RATE =
            new BigDecimal("2.00");
    private static final Set<String> COUNTRIES =
            Set.of(Locale.getISOCountries());

    /** @param input endpoint selection @return normalized ISO/location codes */
    public RouteMatchRequest normalize(RouteMatchRequest input) {
        String origin = country(input.originCountryCode());
        String destination = country(input.destinationCountryCode());
        require(!origin.equals(destination), "ROUTE_MUST_BE_INTERNATIONAL");
        return new RouteMatchRequest(origin, text(input.originCity()),
                airport(input.originAirportCode()), destination,
                text(input.destinationCity()),
                airport(input.destinationAirportCode()), input.transportMode(),
                input.airStallPreference(), text(input.pickupAddress()),
                text(input.deliveryAddress()), input.originHubId(),
                input.destinationHubId());
    }

    /** @param input editable catalog data @return valid normalized template */
    public RouteTemplateRequest normalize(RouteTemplateRequest input) {
        var route = normalize(new RouteMatchRequest(input.originCountryCode(),
                input.originCity(), input.originAirportCode(),
                input.destinationCountryCode(), input.destinationCity(),
                input.destinationAirportCode(), input.transportMode(), null));
        var road = value(input.estimatedRoadKm(), BigDecimal.ZERO);
        var air = value(input.estimatedAirKm(), BigDecimal.ZERO);
        var roadRate = value(input.baseRoadRate(), BigDecimal.ONE);
        var airRate = value(input.baseAirRate(), BigDecimal.ZERO);
        require(input.transportMode() != null, "ROUTE_MODE_REQUIRED");
        boolean valid = switch (input.transportMode()) {
            case ROAD -> road.signum() > 0 && air.signum() == 0
                    && airRate.signum() == 0;
            case AIR -> air.signum() > 0 && road.signum() == 0;
            case MULTIMODAL -> road.signum() > 0 && air.signum() > 0;
        };
        require(valid, "ROUTE_DISTANCE_MODE_MISMATCH");
        return new RouteTemplateRequest(input.routeCode().strip(),
                input.routeName().strip(), route.originCountryCode(),
                route.originCity(), route.originAirportCode(),
                route.destinationCountryCode(), route.destinationCity(),
                route.destinationAirportCode(), input.transportMode(), road,
                air, input.estimatedHours(), input.routeSummary().strip(),
                roadRate, airRate, !Boolean.FALSE.equals(input.isActive()));
    }

    /** @param route selected active template @param input normalized request
     * @return separate road/air totals rounded to USD cents */
    public RouteMatchResponse estimate(RouteTemplateResponse route,
            RouteMatchRequest input) {
        return estimateWithHubs(route, input, null, null,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP));
    }

    /**
     * Computes 3-leg Hub-and-Spoke pricing and distance summary.
     * @param route matched main-leg template
     * @param input normalized request
     * @param originHub primary company hub in origin country
     * @param destinationHub primary company hub in destination country
     * @param firstMileKm road distance from pickup to originHub
     * @param firstMileHours road hours from pickup to originHub
     * @param lastMileKm road distance from destinationHub to delivery
     * @param lastMileHours road hours from destinationHub to delivery
     * @return full 3-leg RouteMatchResponse
     */
    public RouteMatchResponse estimateWithHubs(RouteTemplateResponse route,
            RouteMatchRequest input, Location originHub,
            Location destinationHub, BigDecimal firstMileKm,
            BigDecimal firstMileHours, BigDecimal lastMileKm,
            BigDecimal lastMileHours) {
        var slots = StallFitEvaluator.slots(input.airStallPreference() == null
                ? AirStallPreference.SHARED_ONE : input.airStallPreference());
        BigDecimal fmKm = value(firstMileKm, BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal lmKm = value(lastMileKm, BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal mainRoadKm = route.estimatedRoadKm()
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRoadKm = fmKm.add(mainRoadKm).add(lmKm)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal fmCost = calculateDomesticCost(fmKm);
        BigDecimal mainRoadCost = mainRoadKm.multiply(route.baseRoadRate())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal lmCost = calculateDomesticCost(lmKm);

        BigDecimal totalRoadCost = fmCost.add(mainRoadCost).add(lmCost)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal airCost = route.baseAirRate().multiply(slots)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalHours = route.estimatedHours()
                .add(value(firstMileHours, BigDecimal.ZERO))
                .add(value(lastMileHours, BigDecimal.ZERO))
                .setScale(1, RoundingMode.HALF_UP);

        String summary = buildThreeLegSummary(route, input, originHub,
                destinationHub, fmKm, lmKm);

        return new RouteMatchResponse(true, route.routeTemplateId(),
                route.transportMode(), summary, totalRoadKm,
                route.estimatedAirKm(), totalHours, totalRoadCost, airCost,
                totalRoadCost.add(airCost), false,
                "Cước dự tính USD cho một ngựa; báo giá chính thức cần duyệt.",
                originHub == null ? null : originHub.locationName(),
                originHub == null ? null : originHub.address(),
                destinationHub == null ? null : destinationHub.locationName(),
                destinationHub == null ? null : destinationHub.address(),
                fmKm, mainRoadKm, lmKm, fmCost, lmCost);
    }

    /** @param input normalized request @return unknown price and custom flag */
    public RouteMatchResponse custom(RouteMatchRequest input) {
        return customWithHubs(input, null, null, null, null);
    }

    /**
     * Calculates domestic transfer cost between EquiTransit Hub and customer:
     * free (0.00 USD) if distance <= 100 km, else 2.00 USD/km.
     * @param distanceKm road leg distance in kilometers
     * @return USD cost rounded to 2 decimal places
     */
    public BigDecimal calculateDomesticCost(BigDecimal distanceKm) {
        BigDecimal km = value(distanceKm, BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        if (km.compareTo(FREE_DOMESTIC_KM) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return km.multiply(DEFAULT_DOMESTIC_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Builds custom coordinator routing response while preserving hub info.
     * @param input normalized request
     * @param originHub origin country hub
     * @param destinationHub destination country hub
     * @param firstMileKm computed first mile road km if known
     * @param lastMileKm computed last mile road km if known
     * @return custom RouteMatchResponse
     */
    public RouteMatchResponse customWithHubs(RouteMatchRequest input,
            Location originHub, Location destinationHub,
            BigDecimal firstMileKm, BigDecimal lastMileKm) {
        BigDecimal fmCost = firstMileKm != null && originHub != null
                ? calculateDomesticCost(firstMileKm) : null;
        BigDecimal lmCost = lastMileKm != null && destinationHub != null
                ? calculateDomesticCost(lastMileKm) : null;
        return new RouteMatchResponse(false, null, input.transportMode(), null,
                null, null, null, null, null, null, true,
                "Tuyến vận chuyển quốc tế này chưa có lịch trình cố định. "
                + "Yêu cầu sẽ được chuyển đến Chuyên viên Điều phối "
                + "(Coordinator) để khảo sát và lập lộ trình chặng riêng.",
                originHub == null ? null : originHub.locationName(),
                originHub == null ? null : originHub.address(),
                destinationHub == null ? null : destinationHub.locationName(),
                destinationHub == null ? null : destinationHub.address(),
                firstMileKm, null, lastMileKm, fmCost, lmCost);
    }

    /**
     * Builds response when origin or destination country lacks a company hub.
     * @param input normalized request
     * @param originHub origin country hub if found
     * @param destinationHub destination country hub if found
     * @return unsupported country RouteMatchResponse
     */
    public RouteMatchResponse unsupportedWithoutHub(RouteMatchRequest input,
            Location originHub, Location destinationHub) {
        String missing;
        if (originHub == null && destinationHub == null) {
            missing = "Quốc gia xuất phát (" + input.originCountryCode()
                    + ") và Quốc gia đích (" + input.destinationCountryCode()
                    + ")";
        } else if (originHub == null) {
            missing = "Quốc gia xuất phát (" + input.originCountryCode() + ")";
        } else {
            missing = "Quốc gia đích (" + input.destinationCountryCode() + ")";
        }
        String msg = missing + " hiện chưa có Trụ sở EquiTransit để tiếp nhận "
                + "và chuẩn bị ngựa từ sân bay/cửa khẩu, nên nằm trong diện "
                + "không hỗ trợ vận chuyển.";
        return new RouteMatchResponse(false, null, input.transportMode(), null,
                null, null, null, null, null, null, false, msg,
                originHub == null ? null : originHub.locationName(),
                originHub == null ? null : originHub.address(),
                destinationHub == null ? null : destinationHub.locationName(),
                destinationHub == null ? null : destinationHub.address(),
                null, null, null, null, null);
    }

    private String buildThreeLegSummary(RouteTemplateResponse route,
            RouteMatchRequest input, Location originHub,
            Location destinationHub, BigDecimal fmKm, BigDecimal lmKm) {
        if (originHub == null && destinationHub == null
                && fmKm.signum() == 0 && lmKm.signum() == 0) {
            return route.routeSummary();
        }
        StringBuilder sb = new StringBuilder();
        String pickupLabel = input.pickupAddress() != null
                ? input.pickupAddress() : input.originCity();
        if (pickupLabel != null && fmKm.signum() > 0) {
            sb.append(pickupLabel).append(" (").append(fmKm).append(" km) → ");
        }
        if (originHub != null) {
            sb.append(originHub.locationName()).append(" → ");
        }
        sb.append(route.routeSummary());
        if (destinationHub != null) {
            sb.append(" → ").append(destinationHub.locationName());
        }
        String dropoffLabel = input.deliveryAddress() != null
                ? input.deliveryAddress() : input.destinationCity();
        if (dropoffLabel != null && lmKm.signum() > 0) {
            sb.append(" → ").append(dropoffLabel).append(" (")
                    .append(lmKm).append(" km)");
        }
        return sb.toString();
    }

    private BigDecimal value(BigDecimal input, BigDecimal fallback) {
        return input == null ? fallback : input;
    }

    private String country(String input) {
        String code = input == null ? ""
                : input.strip().toUpperCase(Locale.ROOT);
        require(COUNTRIES.contains(code), "ROUTE_COUNTRY_INVALID");
        return code;
    }

    private String airport(String input) {
        String code = text(input);
        if (code != null) {
            code = code.toUpperCase(Locale.ROOT);
            require(code.matches("[A-Z]{3}"), "ROUTE_AIRPORT_INVALID");
        }
        return code;
    }

    private String text(String input) {
        return input == null || input.isBlank() ? null
                : input.strip().replaceAll("\\s+", " ");
    }

    private void require(boolean valid, String code) {
        if (!valid) {
            throw new OrderException(HttpStatus.UNPROCESSABLE_ENTITY,
                    code, "Invalid route configuration or endpoint selection.");
        }
    }
}

