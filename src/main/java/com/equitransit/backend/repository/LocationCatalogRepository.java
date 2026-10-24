package com.equitransit.backend.repository;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only route master data adapter. */
@Repository
public class LocationCatalogRepository {
    private final JdbcTemplate jdbc;

    /** @param jdbc parameterized catalog access */
    public LocationCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Capabilities indicate catalog availability, not route legality.
     * @param countryCode ISO code @param countryName display label
     * @param supportsRoad city @param supportsAir airport */
    @Schema(name = "RouteCountry")
    public record Country(String countryCode, String countryName,
            boolean supportsRoad, boolean supportsAir) { }

    /** A route selector option.
     * @param locationId identity @param countryCode country
     * @param locationType CITY/AIRPORT @param locationName display label
     * @param locationCode optional IATA code */
    @Schema(name = "RouteLocation")
    public record Location(int locationId, String countryCode,
            String locationType, String locationName, String locationCode) { }

    /** @return active countries with derived capabilities */
    public List<Country> countries() {
        requireReady();
        return jdbc.query("SELECT CountryCode, MAX(CountryName) CountryName, "
                + "MAX(CASE WHEN LocationType='CITY' THEN 1 ELSE 0 END) Road, "
                + "MAX(CASE WHEN LocationType='AIRPORT' THEN 1 ELSE 0 END) Air"
                + " FROM dbo.LocationCatalog WHERE IsActive=1 GROUP BY "
                + "CountryCode ORDER BY CountryName", (r, i) -> new Country(
                        r.getString("CountryCode"), r.getString("CountryName"),
                        r.getBoolean("Road"), r.getBoolean("Air")));
    }

    /** @param country ISO country @param type kind @return active options */
    public List<Location> locations(String country, String type) {
        requireReady();
        return jdbc.query("SELECT * FROM dbo.LocationCatalog WHERE "
                + "IsActive=1 AND CountryCode=? AND LocationType=? "
                + "ORDER BY LocationName", (r, i) -> new Location(
                        r.getInt("LocationID"), r.getString("CountryCode"),
                        r.getString("LocationType"),
                        r.getString("LocationName"),
                        r.getString("LocationCode")), country, type);
    }

    private void requireReady() {
        if (jdbc.queryForObject("SELECT CASE WHEN OBJECT_ID("
                + "'dbo.LocationCatalog','U') IS NULL THEN 0 ELSE 1 END",
                Integer.class) != 1) {
            throw new com.equitransit.backend.exception.OrderException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "LOCATION_SCHEMA_PENDING", "Deploy migration024.");
        }
    }
}
