package com.equitransit.backend;

import com.equitransit.backend.repository.LocationCatalogRepository;
import com.equitransit.backend.service.GeoDistanceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
        .AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

/** Real catalog merge and province-only discovery, with rollback fixtures. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_HUB_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_HUB_TEST", matches = "true")
@Transactional
public class SubdivisionSqlTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LocationCatalogRepository locations;
    @Autowired private GeoDistanceService geo;

    /** @throws Exception if public lookup misses required regions or fields */
    @Test
    public void publicCoverageCoordinatesAndSqlIds() throws Exception {
        for (var entry : Map.of("VN", 63, "LA", 18, "CN", 34,
                "TH", 77, "US", 51).entrySet()) {
            var response = mvc.perform(MockMvcRequestBuilders.get(
                    "/api/lookups/locations").param("country", entry.getKey())
                    .param("type", "CITY")).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            JsonNode rows = json.readTree(response.getContentAsString());
            assertThat(rows.size())
                    .isGreaterThanOrEqualTo(entry.getValue());
            for (JsonNode row : rows) {
                assertThat(row.path("latitude").isNumber()).isTrue();
                assertThat(row.path("longitude").isNumber()).isTrue();
                assertThat(row.path("countryCode").asText())
                        .isEqualTo(entry.getKey());
            }
        }
        assertThat(locations.findByNameOrCode("VN", "VN-DN")
                .orElseThrow().latitude()).isNotNull();
        assertThat(locations.findByNameOrCode("CN", "Côn Minh")
                .orElseThrow().latitude()).isEqualByComparingTo("25.03889");
    }

    /** @throws Exception if city fallback fails to rank hubs */
    @Test
    public void provinceOnlyAndUnknownStreetRecommendSameNearestHub()
            throws Exception {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class))
                .isIn("EquiTransit_Demo", "EquiTransit_Test");
        geo.setExternalNetworkEnabled(false);
        var city = locations.findByNameOrCode("VN", "Đà Nẵng").orElseThrow();
        int local = jdbc.queryForObject("INSERT dbo.LocationCatalog("
                + "CountryCode,CountryName,LocationType,LocationName,Latitude,"
                + "Longitude,IsActive,IsPrimaryHub) OUTPUT INSERTED.LocationID "
                + "VALUES('VN',N'Việt Nam','HUB',?,?,?,1,0)", Integer.class,
                "Subdivision test " + UUID.randomUUID(), city.latitude(),
                city.longitude());
        for (String street : new String[]{null, "unknown-street-987654321"}) {
            var body = json.createObjectNode().put("originCountryCode", "VN")
                    .put("destinationCountryCode", "CN")
                    .put("transportMode", "ROAD").put("originCity", "Đà Nẵng");
            if (street != null) {
                body.put("pickupAddress", street);
            }
            var response = mvc.perform(MockMvcRequestBuilders.post(
                    "/api/routes/match").contentType("application/json")
                    .content(body.toString())).andReturn().getResponse();
            assertThat(response.getStatus()).as(response.getContentAsString())
                    .isEqualTo(200);
            var result = json.readTree(response.getContentAsString());
            assertThat(result.path("originHubId").asInt()).isEqualTo(local);
            var nearest = result.path("originHubOptions").get(0);
            assertThat(nearest.path("isRecommendedNearest").asBoolean())
                    .isTrue();
            assertThat(nearest.path("distanceKm").decimalValue()).isZero();
            assertThat(nearest.path("domesticCost").decimalValue()).isZero();
        }
    }
}
