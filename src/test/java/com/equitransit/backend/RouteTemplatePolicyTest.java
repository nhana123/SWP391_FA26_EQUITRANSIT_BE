package com.equitransit.backend;

import com.equitransit.backend.dto.order.request.OrderDraftRequest;
import com.equitransit.backend.dto.route.RouteMatchRequest;
import com.equitransit.backend.dto.route.RouteTemplateResponse;
import com.equitransit.backend.entity.order.AirStallPreference;
import com.equitransit.backend.entity.RouteTransportMode;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.LocationCatalogRepository.Location;
import com.equitransit.backend.service.GeoDistanceService;
import com.equitransit.backend.service.GeoDistanceService.Coordinates;
import com.equitransit.backend.service.RouteTemplatePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Price math and unknown-route semantics need no SQL Server. */
public class RouteTemplatePolicyTest {
    private final RouteTemplatePolicy policy = new RouteTemplatePolicy();

    /** Airfare is per horse/slot, never multiplied by flight distance.
     * @param preference slot code @param expected airfare USD */
    @ParameterizedTest
    @CsvSource({"SHARED_ONE,1200.00", "WIDE,1800.00", "PRIVATE,3600.00"})
    public void airfareUsesExistingSlotFactors(AirStallPreference preference,
            String expected) {
        var result = policy.estimate(template("0.00"), request(preference));
        assertThat(result.estimatedAirCost()).isEqualByComparingTo(expected);
        assertThat(result.estimatedRoadCost()).isZero();
    }

    /** The road component rounds once to cents before summing airfare. */
    @Test
    public void roadRoundingDoesNotUseFloatingPoint() {
        var result = policy.estimate(template("100.25"),
                request(AirStallPreference.WIDE));
        assertThat(result.estimatedRoadCost()).isEqualByComparingTo("234.59");
        assertThat(result.totalEstimatedCost()).isEqualByComparingTo("2034.59");
    }

    /** 3-leg Hub-and-Spoke math: <=100km free, >100km is $2.00/km. */
    @Test
    public void threeLegHubAndSpokeMathCombinesDomesticAndMainLegs() {
        var originHub = new Location(1, "VN", "HUB",
                "Trụ sở EquiTransit Việt Nam (Củ Chi Hub)", "HUB-VN",
                "Củ Chi, TP.HCM", new BigDecimal("11.0067000"),
                new BigDecimal("106.5132000"), true,
                new BigDecimal("2.00"), true);
        var destHub = new Location(2, "CN", "HUB",
                "Trạm trung chuyển EquiTransit Bằng Tường", "HUB-CN",
                "Bằng Tường, Quảng Tây", new BigDecimal("22.0988000"),
                new BigDecimal("106.7581000"), true,
                new BigDecimal("2.00"), true);
        var req = new RouteMatchRequest("VN", "TP. Hồ Chí Minh", null,
                "CN", "Nam Ninh (Nanning)", null, RouteTransportMode.ROAD,
                null, "Quận 1, TP. Hồ Chí Minh", "Qingxiu, Nanning");
        var mainTemplate = new RouteTemplateResponse(1, "ROUTE-VN-CN-ROAD",
                "VN - CN", "VN", null, null, "CN", null, null,
                RouteTransportMode.ROAD, new BigDecimal("1950.00"),
                BigDecimal.ZERO, new BigDecimal("35.5"),
                "Quốc lộ 1A → Hữu Nghị Quan", new BigDecimal("1.00"),
                BigDecimal.ZERO, true, 1L,
                OffsetDateTime.parse("2026-10-25T00:00:00Z"),
                OffsetDateTime.parse("2026-10-25T00:00:00Z"));
        var res = policy.estimateWithHubs(mainTemplate, req, originHub,
                destHub, new BigDecimal("40.00"), new BigDecimal("1.0"),
                new BigDecimal("200.00"), new BigDecimal("3.5"));

        assertThat(res.firstMileRoadKm()).isEqualByComparingTo("40.00");
        assertThat(res.mainLegRoadKm()).isEqualByComparingTo("1950.00");
        assertThat(res.lastMileRoadKm()).isEqualByComparingTo("200.00");
        assertThat(res.estimatedRoadKm()).isEqualByComparingTo("2190.00");
        assertThat(res.firstMileCost()).isEqualByComparingTo("0.00");
        assertThat(res.lastMileCost()).isEqualByComparingTo("400.00");
        assertThat(res.estimatedRoadCost()).isEqualByComparingTo("2350.00");
        assertThat(res.totalEstimatedCost()).isEqualByComparingTo("2350.00");
        assertThat(res.originHubName())
                .isEqualTo("Trụ sở EquiTransit Việt Nam (Củ Chi Hub)");
        assertThat(res.destinationHubName())
                .isEqualTo("Trạm trung chuyển EquiTransit Bằng Tường");
        assertThat(res.routeSummary())
                .contains("Quận 1, TP. Hồ Chí Minh (40.00 km)")
                .contains("Trụ sở EquiTransit Việt Nam (Củ Chi Hub)")
                .contains("Trạm trung chuyển EquiTransit Bằng Tường")
                .contains("Qingxiu, Nanning (200.00 km)");
    }

    /** Domestic transfer <= 100 km is free; > 100 km charges $2.00/km. */
    @Test
    public void domesticTransferFreeUnder100KmAndTwoUsdAbove() {
        assertThat(policy.calculateDomesticCost(new BigDecimal("0.00")))
                .isEqualByComparingTo("0.00");
        assertThat(policy.calculateDomesticCost(new BigDecimal("99.99")))
                .isEqualByComparingTo("0.00");
        assertThat(policy.calculateDomesticCost(new BigDecimal("100.00")))
                .isEqualByComparingTo("0.00");
        assertThat(policy.calculateDomesticCost(new BigDecimal("100.01")))
                .isEqualByComparingTo("200.02");
        assertThat(policy.calculateDomesticCost(new BigDecimal("150.00")))
                .isEqualByComparingTo("300.00");
    }

    /** Missing company hub marks country unsupported (not custom route). */
    @Test
    public void unsupportedWithoutHubRejectsMissingCountryHub() {
        var req = new RouteMatchRequest("VN", null, "HAN", "IN", null, "DEL",
                RouteTransportMode.AIR, null);
        var originHub = new Location(1, "VN", "HUB",
                "Trụ sở EquiTransit Việt Nam", "HUB-VN");
        var res = policy.unsupportedWithoutHub(req, originHub, null);
        assertThat(res.matched()).isFalse();
        assertThat(res.requiresCustomCoordinatorPlan()).isFalse();
        assertThat(res.message()).contains("IN")
                .contains("không hỗ trợ vận chuyển");
    }

    /** Offline Haversine fallback calculates deterministic road leg. */
    @Test
    public void geoDistanceServiceResolvesKnownCoordinatesAndFallbackRoad() {
        var geo = new GeoDistanceService(new ObjectMapper(), false);
        var q1 = geo.resolveCoordinates("Quận 1, TP. Hồ Chí Minh",
                "TP. Hồ Chí Minh", "VN", null);
        assertThat(q1).isPresent();
        var hub = new Coordinates(11.0067, 106.5132);
        var leg = geo.calculateRoadLeg(q1.get(), hub);
        assertThat(leg.distanceKm()).isBetween(
                new BigDecimal("30.00"), new BigDecimal("55.00"));
        assertThat(leg.durationHours()).isGreaterThan(BigDecimal.ZERO);
    }

    /** A missing route has unknown costs, not a misleading free quote. */
    @Test
    public void customRouteHasNoInventedDistanceOrPrice() {
        var result = policy.custom(request(null));
        assertThat(result.matched()).isFalse();
        assertThat(result.requiresCustomCoordinatorPlan()).isTrue();
        assertThat(result.templateId()).isNull();
        assertThat(result.estimatedRoadKm()).isNull();
        assertThat(result.totalEstimatedCost()).isNull();
    }

    /** Unknown country codes must not create a route catalog entry. */
    @Test
    public void validatesIsoCountriesAndCanonicalizesCodes() {
        var normalized = policy.normalize(new RouteMatchRequest("vn", null,
                "han", "fr", null, "cdg", RouteTransportMode.AIR, null));
        assertThat(normalized.originCountryCode()).isEqualTo("VN");
        assertThat(normalized.originAirportCode()).isEqualTo("HAN");
        assertThatThrownBy(() -> policy.normalize(new RouteMatchRequest("ZZ",
                null, null, "FR", null, null, null, null)))
                .isInstanceOfSatisfying(OrderException.class, e ->
                        assertThat(e.getCode())
                                .isEqualTo("ROUTE_COUNTRY_INVALID"));
    }

    /** Omitted route selection retains legacy draft fingerprints. */
    @Test
    public void absentRouteDoesNotChangeLegacyRequestJson() {
        var tree = new ObjectMapper().valueToTree(new OrderDraftRequest());
        assertThat(tree.has("route")).isFalse();
    }

    private RouteMatchRequest request(AirStallPreference preference) {
        return new RouteMatchRequest("VN", null, "HAN", "FR", null, "CDG",
                RouteTransportMode.AIR, preference);
    }

    private RouteTemplateResponse template(String road) {
        var now = OffsetDateTime.parse("2026-10-25T00:00:00Z");
        return new RouteTemplateResponse(1, "TEST", "Test route", "VN", null,
                "HAN", "FR", null, "CDG", RouteTransportMode.MULTIMODAL,
                new BigDecimal(road), new BigDecimal("9200.00"),
                new BigDecimal("14.5"), "Test only", new BigDecimal("2.34"),
                new BigDecimal("1200.00"), true, 1L, now, now);
    }
}
