package com.equitransit.backend.service;

import com.equitransit.backend.dto.order.request.OrderDraftRequest;
import com.equitransit.backend.dto.route.RouteMatchRequest;
import com.equitransit.backend.dto.route.RouteMatchResponse;
import com.equitransit.backend.dto.route.RouteTemplateRequest;
import com.equitransit.backend.dto.route.RouteTemplateResponse;
import com.equitransit.backend.entity.order.AirStallPreference;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.LocationCatalogRepository;
import com.equitransit.backend.repository.RouteTemplateRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.GeoDistanceService.RoadLeg;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Route previews/catalog use cases; estimates do not issue quotations. */
@Service
@Validated
@Transactional(readOnly = true)
public class RouteTemplateService {
    private final RouteTemplateRepository data;
    private final RouteTemplatePolicy policy;
    private final CurrentActorService actors;
    private LocationCatalogRepository locations;
    private GeoDistanceService geo;

    /** @param data SQL @param policy invariants @param actors live caller */
    public RouteTemplateService(RouteTemplateRepository data,
            RouteTemplatePolicy policy, CurrentActorService actors) {
        this.data = data;
        this.policy = policy;
        this.actors = actors;
        this.geo = new GeoDistanceService();
    }

    /** @param locations catalog repository @param geo distance resolver */
    @Autowired(required = false)
    public void configureHubDistance(LocationCatalogRepository locations,
            GeoDistanceService geo) {
        this.locations = locations;
        if (geo != null) {
            this.geo = geo;
        }
    }

    /** @param inactive include hidden @param page zero based @param size bound
     * @return catalog page */
    @PreAuthorize("hasAnyRole('COORDINATOR','MANAGER')")
    public List<RouteTemplateResponse> list(boolean inactive, int page,
            int size) {
        staff();
        ready();
        return data.list(inactive, page, size);
    }

    /** @param id template identity @return stored template */
    @PreAuthorize("hasAnyRole('COORDINATOR','MANAGER')")
    public RouteTemplateResponse detail(long id) {
        staff();
        ready();
        return one(id);
    }

    /** @param request normalized by the policy @return created template */
    @Transactional
    @PreAuthorize("hasAnyRole('COORDINATOR','MANAGER')")
    public RouteTemplateResponse create(@Valid @NotNull
            RouteTemplateRequest request) {
        long creator = staff();
        ready();
        try {
            return one(data.insert(policy.normalize(request), creator));
        } catch (DuplicateKeyException error) {
            throw duplicate();
        }
    }

    /** @param id target @param request full replacement @return current row */
    @Transactional
    @PreAuthorize("hasAnyRole('COORDINATOR','MANAGER')")
    public RouteTemplateResponse update(long id, @Valid @NotNull
            RouteTemplateRequest request) {
        staff();
        ready();
        try {
            if (data.update(id, policy.normalize(request)) == 0) {
                throw missing();
            }
        } catch (DuplicateKeyException error) {
            throw duplicate();
        }
        return one(id);
    }

    /** @param id hidden from matching; revision snapshots remain intact */
    @Transactional
    @PreAuthorize("hasAnyRole('COORDINATOR','MANAGER')")
    public void hide(long id) {
        staff();
        ready();
        if (data.hide(id) == 0) {
            throw missing();
        }
    }

    /** @param request endpoints @return match or explicit custom flag */
    public RouteMatchResponse matchRoute(@Valid @NotNull
            RouteMatchRequest request) {
        ready();
        var normalized = policy.normalize(request);
        boolean enabled = locations != null && locations.hubsReady();
        if (!enabled && (normalized.originHubId() != null
                || normalized.destinationHubId() != null)) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "HUB_SCHEMA_PENDING", "Deploy migration027.");
        }
        var empty = new HubDiscoveryService.Selection(null, null, List.of());
        var discovery = new HubDiscoveryService(locations, geo, policy);
        var origin = enabled ? discovery.resolve(normalized.originCountryCode(),
                normalized.pickupAddress(), normalized.originCity(),
                normalized.originHubId(), false) : empty;
        var dest = enabled ? discovery.resolve(
                normalized.destinationCountryCode(),
                normalized.deliveryAddress(), normalized.destinationCity(),
                normalized.destinationHubId(), true) : empty;
        RouteMatchResponse result;
        if (enabled && (origin.hub() == null || dest.hub() == null)) {
            result = policy.unsupportedWithoutHub(normalized,
                    origin.hub(), dest.hub());
        } else {
            var match = data.match(normalized).stream().findFirst();
            if (match.isPresent()) {
                RoadLeg first = zeroIfUnknown(origin.leg());
                RoadLeg last = zeroIfUnknown(dest.leg());
                result = policy.estimateWithHubs(match.get(), normalized,
                        origin.hub(), dest.hub(), first.distanceKm(),
                        first.durationHours(), last.distanceKm(),
                        last.durationHours());
            } else {
                result = policy.customWithHubs(normalized,
                        origin.hub(), dest.hub(), origin.leg() == null ? null
                                : origin.leg().distanceKm(),
                        dest.leg() == null ? null : dest.leg().distanceKm());
            }
        }
        return result.withHubOptions(origin.options(), dest.options(),
                origin.id(), dest.id());
    }

    /** Submission freezes the current server result into OrderSnapshot.
     * @param draft submitted draft @return null only on legacy schema */
    public RouteMatchResponse resolveOrder(OrderDraftRequest draft) {
        if (!data.ready() && draft.getRoute() == null) {
            return null;
        }
        var selected = draft.getRoute();
        AirStallPreference stall = draft.getServices() == null ? null
                : draft.getServices().getAirStall();
        if (selected == null) {
            selected = new RouteMatchRequest(draft.getOriginCountryCode(),
                    null, null, draft.getDestinationCountryCode(), null,
                    null, null, stall, null, null);
        }
        var normalized = policy.normalize(selected);
        if (!normalized.originCountryCode().equals(draft.getOriginCountryCode())
                || !normalized.destinationCountryCode().equals(
                        draft.getDestinationCountryCode())
                || stall != null && normalized.airStallPreference() != null
                        && stall != normalized.airStallPreference()) {
            throw new OrderException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "ROUTE_SELECTION_MISMATCH",
                    "Route endpoints/stall must match the order selections.");
        }
        RouteMatchResponse result = matchRoute(new RouteMatchRequest(
                normalized.originCountryCode(), normalized.originCity(),
                normalized.originAirportCode(),
                normalized.destinationCountryCode(),
                normalized.destinationCity(),
                normalized.destinationAirportCode(), normalized.transportMode(),
                stall == null ? normalized.airStallPreference() : stall,
                normalized.pickupAddress(), normalized.deliveryAddress(),
                normalized.originHubId(), normalized.destinationHubId()));
        if (!result.matched() && !result.requiresCustomCoordinatorPlan()) {
            if (draft.getRoute() == null) {
                // Legacy submissions predate the hub selector contract.
                return null;
            }
            throw new OrderException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "COUNTRY_HUB_NOT_SUPPORTED", result.message());
        }
        return result;
    }

    private RoadLeg zeroIfUnknown(RoadLeg leg) {
        return leg == null ? new RoadLeg(BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(1)) : leg;
    }

    private long staff() {
        var actor = actors.current();
        if (!List.of("COORDINATOR", "MANAGER").contains(actor.roleCode())
                || actor.staffId() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Route catalog administration requires active staff.");
        }
        return actor.staffId();
    }

    private RouteTemplateResponse one(long id) {
        return data.find(id).stream().findFirst().orElseThrow(this::missing);
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND,
                "ROUTE_TEMPLATE_NOT_FOUND", "Route template was not found.");
    }

    private OrderException duplicate() {
        return new OrderException(HttpStatus.CONFLICT,
                "ROUTE_CODE_EXISTS", "RouteCode is already used.");
    }

    private void ready() {
        if (!data.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ROUTE_TEMPLATE_SCHEMA_PENDING", "Deploy migration026.");
        }
    }
}


