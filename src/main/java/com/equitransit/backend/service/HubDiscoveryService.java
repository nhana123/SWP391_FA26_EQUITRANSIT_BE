package com.equitransit.backend.service;

import com.equitransit.backend.dto.route.HubDistanceOption;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.LocationCatalogRepository;
import com.equitransit.backend.repository.LocationCatalogRepository.Location;
import com.equitransit.backend.service.GeoDistanceService.Coordinates;
import com.equitransit.backend.service.GeoDistanceService.RoadLeg;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Resolves an endpoint once and ranks same-country hubs deterministically. */
public class HubDiscoveryService {
    private final LocationCatalogRepository locations;
    private final GeoDistanceService geo;
    private final RouteTemplatePolicy policy;

    /** @param locations catalog @param geo routing @param policy cost rules */
    public HubDiscoveryService(LocationCatalogRepository locations,
            GeoDistanceService geo, RouteTemplatePolicy policy) {
        this.locations = locations;
        this.geo = geo;
        this.policy = policy;
    }

    /** Selected hub with ranked alternatives.
     * @param hub applied hub @param leg nullable estimated road leg
     * @param options ranked active hubs */
    public record Selection(Location hub, RoadLeg leg,
            List<HubDistanceOption> options) {
        /** @return applied ID, or null for an unsupported country */
        public Integer id() {
            return hub == null ? null : hub.locationId();
        }
    }

    private record Candidate(Location hub, RoadLeg leg) { }

    /** @param country ISO country @param address customer address
     * @param city fallback city @param selectedId explicit choice
     * @param destination true for hub-to-customer driving direction
     * @return applied hub, measured leg and ranked alternatives */
    public Selection resolve(String country, String address, String city,
            Integer selectedId, boolean destination) {
        List<Location> hubs = locations.findAllActiveHubsByCountry(country);
        if (selectedId != null && hubs.stream().noneMatch(
                hub -> hub.locationId() == selectedId)) {
            throw new OrderException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_HUB_SELECTION",
                    "Selected hub must be active and in the endpoint country.");
        }
        if (hubs.isEmpty()) {
            return new Selection(null, null, List.of());
        }
        Coordinates catalog = city == null ? null
                : locations.findByNameOrCode(country, city)
                        .map(this::coordinates).orElse(null);
        var point = address == null && city == null
                ? java.util.Optional.<Coordinates>empty()
                : geo.resolveCoordinates(address, city, country, catalog);
        List<Candidate> ranked = new ArrayList<>();
        for (Location hub : hubs) {
            Coordinates coords = coordinates(hub);
            RoadLeg leg = point.isEmpty() || coords == null ? null
                    : destination ? geo.calculateRoadLeg(coords, point.get())
                            : geo.calculateRoadLeg(point.get(), coords);
            ranked.add(new Candidate(hub, leg));
        }
        ranked.sort(Comparator.comparing(
                (Candidate c) -> c.leg() == null ? null : c.leg().distanceKm(),
                Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(c -> !c.hub().isPrimaryHub())
                .thenComparingInt(c -> c.hub().locationId()));
        Integer nearest = ranked.isEmpty() || ranked.get(0).leg() == null
                ? null : ranked.get(0).hub().locationId();
        Candidate selected = ranked.stream().filter(c -> selectedId != null
                && c.hub().locationId() == selectedId).findFirst()
                .orElse(ranked.isEmpty() ? null : ranked.get(0));
        List<HubDistanceOption> options = ranked.stream().map(c -> {
            Location h = c.hub();
            BigDecimal km = c.leg() == null ? null : c.leg().distanceKm();
            return new HubDistanceOption(h.locationId(), h.locationName(),
                    h.locationCode(), h.address(), h.latitude(), h.longitude(),
                    h.isPrimaryHub(), km, km == null ? null
                            : policy.calculateDomesticCost(km),
                    nearest != null && h.locationId() == nearest);
        }).toList();
        return new Selection(selected == null ? null : selected.hub(),
                selected == null ? null : selected.leg(), options);
    }

    private Coordinates coordinates(Location location) {
        return location.latitude() == null || location.longitude() == null
                ? null : new Coordinates(location.latitude().doubleValue(),
                        location.longitude().doubleValue());
    }
}
