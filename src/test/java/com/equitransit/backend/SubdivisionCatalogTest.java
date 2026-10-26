package com.equitransit.backend;

import com.equitransit.backend.repository.LocationCatalogRepository.Location;
import com.equitransit.backend.service.GeoDistanceService;
import com.equitransit.backend.service.SubdivisionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Dataset coverage, aliases, SQL precedence and offline geocoding. */
public class SubdivisionCatalogTest {
    private final SubdivisionCatalog catalog = new SubdivisionCatalog();

    /** Core regions have unique stable IDs/codes and valid country points. */
    @Test
    public void completeCoreCountriesAndCoordinateBounds() {
        var counts = Map.of("VN", 63, "LA", 18, "CN", 34, "TH", 77, "US", 51);
        var ids = new HashSet<Integer>();
        counts.forEach((country, count) -> {
            var rows = catalog.list(country);
            assertThat(rows).hasSize(count);
            assertThat(rows).extracting(Location::locationCode)
                    .doesNotHaveDuplicates();
            for (Location row : rows) {
                assertThat(ids.add(row.locationId())).isTrue();
                assertThat(row.locationId()).isNegative();
                assertThat(row.latitude()).isBetween(new BigDecimal("-90"),
                        new BigDecimal("90"));
                assertThat(row.longitude()).isBetween(new BigDecimal("-180"),
                        new BigDecimal("180"));
                assertThat(row.locationType()).isEqualTo("CITY");
                assertThat(row.isActive()).isTrue();
                if (country.equals("TH")) {
                    assertThat(row.longitude()).isBetween(new BigDecimal("97"),
                            new BigDecimal("106"));
                }
                if (country.equals("CN")) {
                    assertThat(row.longitude()).isBetween(new BigDecimal("73"),
                            new BigDecimal("135"));
                }
            }
        });
        assertThat(catalog.list("FR").size()).isGreaterThan(5);
        assertThat(catalog.list("ZZ")).isEmpty();
    }

    /** Vietnamese prefixes, accents and aliases are scoped by country. */
    @Test
    public void aliasesCodesAndDistinctVientianeRegions() {
        assertThat(catalog.find("VN", "TP. Hồ Chí Minh"))
                .isEqualTo(catalog.find("VN", "Ho Chi Minh City"));
        assertThat(catalog.find("VN", " Tỉnh Đà Nẵng "))
                .isEqualTo(catalog.find("VN", "VN-DN"));
        assertThat(catalog.find("VN", "da nang")).isPresent();
        assertThat(catalog.find("CN", "Quảng Tây")).isPresent();
        assertThat(catalog.find("US", "California")).isPresent();
        assertThat(catalog.find("VN", "California")).isEmpty();
        assertThat(catalog.find("LA", "Vientiane").orElseThrow().locationCode())
                .isEqualTo("LA-VT");
        assertThat(catalog.find("LA", "Vientiane Province")
                .orElseThrow().locationCode()).isEqualTo("LA-VI");
        assertThat(catalog.find("CN", "Côn Minh").orElseThrow().latitude())
                .isEqualByComparingTo("25.0388900");
    }

    /** SQL rows retain IDs/coordinates; normalized aliases do not duplicate. */
    @Test
    public void mergePreservesSqlIdentityAndFillsMissingCoordinates() {
        var hcm = new Location(9, "VN", "CITY", "TP. Hồ Chí Minh", null);
        var hanoi = new Location(10, "VN", "CITY", "Hà Nội", "HAN", "address",
                new BigDecimal("21.0285"), new BigDecimal("105.8542"),
                false, true);
        var rows = catalog.merge("VN", List.of(hcm, hanoi));
        assertThat(rows).hasSize(63);
        assertThat(rows.stream().filter(r -> r.locationId() == 9)
                .findFirst().orElseThrow().latitude()).isNotNull();
        assertThat(rows.stream().filter(r -> r.locationId() == 10)
                .findFirst().orElseThrow()).isEqualTo(hanoi);
        var extra = new Location(11, "VN", "CITY", "Customer configured town",
                "CCT", null, BigDecimal.TEN, BigDecimal.valueOf(106),
                false, true);
        assertThat(catalog.merge("VN", List.of(hcm, hanoi, extra))).hasSize(64);
    }

    /** No live network is needed for an unknown street + known province. */
    @Test
    public void offlineCityFallbackWorksForUnresolvableStreet() {
        var geo = new GeoDistanceService(new ObjectMapper(), false);
        geo.configureSubdivisions(catalog);
        var city = geo.resolveCoordinates(null, "Đà Nẵng", "VN", null)
                .orElseThrow();
        var unknown = geo.resolveCoordinates("unknown-street-987654321",
                "Đà Nẵng", "VN", null).orElseThrow();
        assertThat(unknown).isEqualTo(city);
        assertThat(city.latitude()).isEqualTo(catalog.find("VN", "Đà Nẵng")
                .orElseThrow().latitude().doubleValue());
        assertThat(geo.resolveCoordinates(null, "Texas", "US", null))
                .isPresent();
    }
}
