package com.equitransit.backend;

import com.equitransit.backend.dto.route.RouteMatchRequest;
import com.equitransit.backend.entity.RouteTransportMode;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.LocationCatalogRepository;
import com.equitransit.backend.repository.LocationCatalogRepository.Location;
import com.equitransit.backend.service.GeoDistanceService;
import com.equitransit.backend.service.GeoDistanceService.Coordinates;
import com.equitransit.backend.service.GeoDistanceService.RoadLeg;
import com.equitransit.backend.service.HubDiscoveryService;
import com.equitransit.backend.service.RouteTemplatePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Deterministic discovery without live map services or database writes. */
public class HubDiscoveryTest {
    private final LocationCatalogRepository locations =
            mock(LocationCatalogRepository.class);
    private final GeoDistanceService geo = mock(GeoDistanceService.class);
    private final RouteTemplatePolicy policy = new RouteTemplatePolicy();
    private final Coordinates point = new Coordinates(10, 106);
    private final HubDiscoveryService discovery =
            new HubDiscoveryService(locations, geo, policy);

    /** Known endpoint and deliberately unordered primary/far/unknown hubs. */
    @BeforeEach
    public void arrange() {
        when(locations.findAllActiveHubsByCountry("VN"))
                .thenReturn(List.of(hub(1, true, "11"),
                        hub(3, false, null), hub(2, false, "12")));
        when(geo.resolveCoordinates(eq("pickup"), isNull(), eq("VN"),
                isNull())).thenReturn(Optional.of(point));
        when(geo.calculateRoadLeg(eq(point), any())).thenAnswer(call -> {
            Coordinates to = call.getArgument(1);
            return new RoadLeg(new BigDecimal(to.latitude() == 11
                    ? "150.00" : "100.00"), BigDecimal.ONE);
        });
    }

    /** Rank every hub once; known nearest beats the primary flag. */
    @Test
    public void nearestRanksAllHubsAndPricesBoundary() {
        var result = discovery.resolve("VN", "pickup", null, null, false);
        assertThat(result.id()).isEqualTo(2);
        assertThat(result.options()).extracting(o -> o.locationId())
                .containsExactly(2, 1, 3);
        assertThat(result.options().get(0).isRecommendedNearest()).isTrue();
        assertThat(result.options().get(0).domesticCost()).isZero();
        assertThat(result.options().get(1).domesticCost())
                .isEqualByComparingTo("300.00");
        assertThat(result.options().get(2).distanceKm()).isNull();
        assertThat(result.options().get(2).domesticCost()).isNull();
        verify(geo, times(1)).resolveCoordinates("pickup", null, "VN", null);
        verify(geo, times(2)).calculateRoadLeg(eq(point), any());
        assertThat(policy.calculateDomesticCost(new BigDecimal("100.01")))
                .isEqualByComparingTo("200.02");
    }

    /** Explicit choice changes costs, retaining the nearest recommendation. */
    @Test
    public void manualSelectionOverridesNearest() {
        var result = discovery.resolve("VN", "pickup", null, 1, false);
        assertThat(result.id()).isEqualTo(1);
        assertThat(result.leg().distanceKm()).isEqualByComparingTo("150.00");
        assertThat(result.options().get(0).locationId()).isEqualTo(2);
        assertThat(result.options().get(0).isRecommendedNearest()).isTrue();
    }

    /** Unresolved endpoint falls back to primary without false free prices. */
    @Test
    public void unresolvedAddressFallsBackToPrimary() {
        when(geo.resolveCoordinates("pickup", null, "VN", null))
                .thenReturn(Optional.empty());
        var result = discovery.resolve("VN", "pickup", null, null, false);
        assertThat(result.id()).isEqualTo(1);
        assertThat(result.leg()).isNull();
        assertThat(result.options()).allSatisfy(option -> {
            assertThat(option.distanceKm()).isNull();
            assertThat(option.domesticCost()).isNull();
            assertThat(option.isRecommendedNearest()).isFalse();
        });
    }

    /** Foreign, inactive, nonexistent and CITY IDs cannot bypass the list. */
    @Test
    public void invalidChoiceIsRejected() {
        assertThatThrownBy(() -> discovery.resolve(
                "VN", "pickup", null, 999, false))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("active");
        when(locations.findAllActiveHubsByCountry("IS")).thenReturn(List.of());
        assertThat(discovery.resolve("IS", null, null, null, false).id())
                .isNull();
    }

    /** Last-mile routing preserves driving direction, not assumed symmetry. */
    @Test
    public void destinationMeasuresFromHubToCustomer() {
        when(geo.calculateRoadLeg(any(), eq(point)))
                .thenReturn(new RoadLeg(new BigDecimal("120.50"),
                        BigDecimal.ONE));
        var result = discovery.resolve("VN", "pickup", null, 2, true);
        assertThat(result.id()).isEqualTo(2);
        assertThat(result.leg().distanceKm()).isEqualByComparingTo("120.50");
        verify(geo, times(2)).calculateRoadLeg(any(), eq(point));
        assertThat(result.options().get(0).locationId()).isEqualTo(1);
    }

    /** @throws Exception if IDs disappear during JSON/normalization */
    @Test
    public void selectedIdsSurviveNormalizationAndSerialization()
            throws Exception {
        var request = new RouteMatchRequest("vn", null, null, "cn", null,
                null, RouteTransportMode.ROAD, null, "pickup", "delivery",
                11, 22);
        var normalized = policy.normalize(request);
        assertThat(normalized.originHubId()).isEqualTo(11);
        assertThat(normalized.destinationHubId()).isEqualTo(22);
        var mapper = new ObjectMapper();
        assertThat(mapper.readValue(mapper.writeValueAsString(normalized),
                RouteMatchRequest.class)).isEqualTo(normalized);
    }

    private Location hub(int id, boolean primary, String latitude) {
        return new Location(id, "VN", "HUB", "Hub " + id, "H" + id,
                "Address " + id,
                latitude == null ? null : new BigDecimal(latitude),
                latitude == null ? null : new BigDecimal("106"),
                primary, true);
    }
}
