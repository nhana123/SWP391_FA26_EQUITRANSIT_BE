package com.equitransit.backend.repository;

import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.location.LocationRequest;
import com.equitransit.backend.service.SubdivisionCatalog;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Location/hub catalog with transactional primary-hub replacement. */
@Repository
public class LocationCatalogRepository {
    private static final BigDecimal DEFAULT_DOMESTIC_RATE =
            new BigDecimal("2.00");
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate writes;
    private final SubdivisionCatalog subdivisions;

    /** @param jdbc SQL access @param subdivisions packaged region data */
    public LocationCatalogRepository(JdbcTemplate jdbc,
            SubdivisionCatalog subdivisions) {
        this.jdbc = jdbc;
        writes = new NamedParameterJdbcTemplate(jdbc);
        this.subdivisions = subdivisions;
    }

    /** Capabilities indicate catalog availability, not route legality.
     * @param countryCode ISO code @param countryName display label
     * @param supportsRoad city @param supportsAir airport */
    @Schema(name = "RouteCountry")
    public record Country(String countryCode, String countryName,
            boolean supportsRoad, boolean supportsAir) { }

    /** A route selector option.
     * @param locationId identity @param countryCode country
     * @param locationType kind @param locationName display label
     * @param locationCode optional code @param address full address
     * @param latitude latitude @param longitude longitude
     * @param isPrimaryHub country primary
     * @param domesticRatePerKm domestic first/last-mile USD/km rate
     * @param isActive active state */
    @Schema(name = "RouteLocation")
    public record Location(int locationId, String countryCode,
            String locationType, String locationName, String locationCode,
            String address, BigDecimal latitude, BigDecimal longitude,
            boolean isPrimaryHub, BigDecimal domesticRatePerKm,
            boolean isActive) {

        /** Legacy catalog constructor.
         * @param locationId ID @param countryCode country
         * @param locationType kind
         * @param locationName name @param locationCode code */
        public Location(int locationId, String countryCode, String locationType,
                String locationName, String locationCode) {
            this(locationId, countryCode, locationType, locationName,
                    locationCode, null, null, null, false,
                    DEFAULT_DOMESTIC_RATE, true);
        }

        /** Constructor without explicit domesticRatePerKm.
         * @param locationId ID @param countryCode country
         * @param locationType kind @param locationName name
         * @param locationCode code @param address full address
         * @param latitude lat @param longitude lon
         * @param isPrimaryHub primary flag @param isActive active flag */
        public Location(int locationId, String countryCode,
                String locationType, String locationName, String locationCode,
                String address, BigDecimal latitude, BigDecimal longitude,
                boolean isPrimaryHub, boolean isActive) {
            this(locationId, countryCode, locationType, locationName,
                    locationCode, address, latitude, longitude, isPrimaryHub,
                    DEFAULT_DOMESTIC_RATE, isActive);
        }
    }

    /** @return active countries with an active EquiTransit Hub */
    public List<Country> countries() {
        requireReady();
        String sql = "SELECT c.CountryCode, MAX(c.CountryName) CountryName, "
                + "MAX(CASE WHEN c.LocationType IN ('CITY','HUB') "
                + "THEN 1 ELSE 0 END) Road, "
                + "MAX(CASE WHEN c.LocationType='AIRPORT' THEN 1 ELSE 0 END) "
                + "Air FROM dbo.LocationCatalog c WHERE c.IsActive=1 "
                + "AND EXISTS (SELECT 1 FROM dbo.LocationCatalog h "
                + "WHERE h.CountryCode=c.CountryCode AND h.LocationType='HUB' "
                + "AND h.IsActive=1) GROUP BY c.CountryCode "
                + "ORDER BY CountryName";
        return jdbc.query(sql, (r, i) -> new Country(
                r.getString("CountryCode"), r.getString("CountryName"),
                r.getBoolean("Road"), r.getBoolean("Air")));
    }

    /** @param country ISO country @param type kind @return active options */
    public List<Location> locations(String country, String type) {
        requireReady();
        return jdbc.query("SELECT " + columns()
                + " FROM dbo.LocationCatalog WHERE "
                + "IsActive=1 AND CountryCode=? AND LocationType=? "
                + "ORDER BY LocationName,LocationID",
                this::view, country, type);
    }

    /** @return whether migration027 coordinate/primary columns exist */
    public boolean hubsReady() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='dbo' "
                + "AND TABLE_NAME='LocationCatalog' AND COLUMN_NAME IN "
                + "('Address','Latitude','Longitude','IsPrimaryHub')",
                Integer.class) == 4;
    }

    /** @return whether DomesticRatePerKm column exists */
    public boolean domesticRateReady() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='dbo' "
                + "AND TABLE_NAME='LocationCatalog' "
                + "AND COLUMN_NAME='DomesticRatePerKm'",
                Integer.class) == 1;
    }

    /** @return whether at least one active company hub exists in the catalog */
    public boolean hasActiveHubs() {
        if (!hubsReady()) {
            return false;
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.LocationCatalog WHERE LocationType='HUB' AND IsActive=1",
                Integer.class);
        return count != null && count > 0;
    }

    /** @param countryCode ISO country @return active designated hub */
    public Optional<Location> findPrimaryHubByCountry(String countryCode) {
        if (!hubsReady()) {
            return Optional.empty();
        }
        return jdbc.query("SELECT TOP(1) " + columns()
                + " FROM dbo.LocationCatalog WHERE CountryCode=? "
                + "AND LocationType='HUB' AND IsActive=1 AND IsPrimaryHub=1 "
                + "ORDER BY IsPrimaryHub DESC, LocationID",
                this::view, countryCode).stream().findFirst();
    }

    /** @param countryCode ISO country @param name city or airport name
     * @return matching active catalog location if found */
    public Optional<Location> findByNameOrCode(String countryCode,
            String name) {
        if (countryCode == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        requireReady();
        Optional<Location> stored = jdbc.query("SELECT TOP(1) " + columns()
                + " FROM dbo.LocationCatalog WHERE IsActive=1 "
                + "AND CountryCode=? AND (LocationName COLLATE "
                + "Latin1_General_100_CI_AI = ? OR LocationCode = ?) "
                + "ORDER BY IsPrimaryHub DESC, LocationID",
                this::view, countryCode, name.strip(), name.strip())
                .stream().findFirst();
        return stored.map(subdivisions::enrich)
                .or(() -> subdivisions.find(countryCode, name));
    }

    /** @param country ISO code @return all active hubs, primary first */
    public List<Location> hubs(String country) {
        requireHubsReady();
        return jdbc.query("SELECT " + columns() + " FROM dbo.LocationCatalog "
                + "WHERE CountryCode=? AND LocationType='HUB' AND IsActive=1 "
                + "ORDER BY IsPrimaryHub DESC,LocationName,LocationID",
                this::view, country);
    }

    /** @param countryCode ISO country @return active hubs, primary first */
    public List<Location> findAllActiveHubsByCountry(String countryCode) {
        requireHubsReady();
        return jdbc.query("SELECT " + columns() + " FROM dbo.LocationCatalog "
                + "WHERE CountryCode=? AND LocationType='HUB' AND IsActive=1 "
                + "ORDER BY IsPrimaryHub DESC,LocationID", this::view,
                countryCode);
    }

    /** @param locationId ID @return active hub only */
    public Optional<Location> findActiveHubById(int locationId) {
        requireHubsReady();
        return jdbc.query("SELECT " + columns() + " FROM dbo.LocationCatalog "
                + "WHERE LocationID=? AND LocationType='HUB' AND IsActive=1",
                this::view, locationId).stream().findFirst();
    }

    /** @param id ID @return row, including inactive catalog entries */
    public Optional<Location> find(int id) {
        requireHubsReady();
        return jdbc.query("SELECT " + columns() + " FROM dbo.LocationCatalog "
                + "WHERE LocationID=?", this::view, id).stream().findFirst();
    }

    /** @param country optional ISO @param type optional kind
     * @param inactive include hidden @param page page @param size cap
     * @return stable management page */
    public List<Location> catalog(String country, String type, boolean inactive,
            int page, int size) {
        requireHubsReady();
        return writes.query("SELECT " + columns() + " FROM dbo.LocationCatalog "
                + "WHERE (:country IS NULL OR CountryCode=:country) AND "
                + "(:type IS NULL OR LocationType=:type) "
                + (inactive ? "" : "AND IsActive=1 ")
                + "ORDER BY CountryCode,LocationType,LocationName,LocationID "
                + "OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                new MapSqlParameterSource()
                        .addValue("country", country, Types.VARCHAR)
                        .addValue("type", type, Types.VARCHAR)
                        .addValue("offset", (long) page * size)
                        .addValue("size", size), this::view);
    }

    /** Acquire the same catalog lock used by migration027. */
    public void lock() {
        requireHubsReady();
        jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.LocationCatalog",
                Long.class);
        jdbc.update("DECLARE @r int; EXEC @r=sys.sp_getapplock "
                + "@Resource='EquiTransit:location-catalog',"
                + "@LockMode='Exclusive',@LockOwner='Transaction',"
                + "@LockTimeout=5000; IF @r<0 THROW 51027,"
                + "'Location catalog is busy.',1;");
    }

    /** @param country ISO @return canonical existing label if known */
    public Optional<String> countryName(String country) {
        return jdbc.query("SELECT TOP(1) CountryName FROM dbo.LocationCatalog "
                + "WHERE CountryCode=? ORDER BY IsActive DESC,LocationID",
                (r, i) -> r.getString(1), country).stream().findFirst();
    }

    /** @param request new primary @param except row being updated, or zero */
    public void demotePrimary(LocationRequest request, int except) {
        jdbc.update("UPDATE dbo.LocationCatalog SET IsPrimaryHub=0 WHERE "
                + "CountryCode=? AND IsPrimaryHub=1 AND LocationID<>?",
                request.countryCode(), except);
    }

    /** @param request normalized location @return generated ID */
    public int insertLocation(LocationRequest request) {
        if (domesticRateReady()) {
            return writes.queryForObject(
                    "INSERT dbo.LocationCatalog(CountryCode,"
                    + "CountryName,LocationType,LocationName,LocationCode,"
                    + "Address,Latitude,Longitude,IsPrimaryHub,"
                    + "DomesticRatePerKm,IsActive) OUTPUT "
                    + "INSERTED.LocationID VALUES(:country,:countryName,:type,"
                    + ":name,:code,:address,:lat,:lon,:primary,:rate,:active)",
                    args(request), Integer.class);
        }
        return writes.queryForObject("INSERT dbo.LocationCatalog(CountryCode,"
                + "CountryName,LocationType,LocationName,LocationCode,Address,"
                + "Latitude,Longitude,IsPrimaryHub,IsActive) OUTPUT "
                + "INSERTED.LocationID VALUES(:country,:countryName,:type,"
                + ":name,:code,:address,:lat,:lon,:primary,:active)",
                args(request), Integer.class);
    }

    /** @param id target @param request full edit @return affected rows */
    public int updateLocation(int id, LocationRequest request) {
        if (domesticRateReady()) {
            return writes.update("UPDATE dbo.LocationCatalog SET "
                    + "CountryCode=:country,CountryName=:countryName,"
                    + "LocationType=:type,LocationName=:name,"
                    + "LocationCode=:code,Address=:address,Latitude=:lat,"
                    + "Longitude=:lon,IsPrimaryHub=:primary,"
                    + "DomesticRatePerKm=:rate,IsActive=:active "
                    + "WHERE LocationID=:id",
                    args(request).addValue("id", id));
        }
        return writes.update("UPDATE dbo.LocationCatalog SET "
                + "CountryCode=:country,CountryName=:countryName,"
                + "LocationType=:type,LocationName=:name,LocationCode=:code,"
                + "Address=:address,Latitude=:lat,Longitude=:lon,"
                + "IsPrimaryHub=:primary,IsActive=:active WHERE LocationID=:id",
                args(request).addValue("id", id));
    }

    /** @param id hides the row and releases its primary-hub designation */
    public void deleteLocation(int id) {
        jdbc.update("UPDATE dbo.LocationCatalog SET IsActive=0,"
                + "IsPrimaryHub=0 WHERE LocationID=?", id);
    }

    private MapSqlParameterSource args(LocationRequest r) {
        BigDecimal rate = r.domesticRatePerKm() == null
                ? DEFAULT_DOMESTIC_RATE : r.domesticRatePerKm();
        return new MapSqlParameterSource("country", r.countryCode())
                .addValue("countryName", r.countryName())
                .addValue("type", r.locationType().name())
                .addValue("name", r.locationName())
                .addValue("code", r.locationCode(), Types.VARCHAR)
                .addValue("address", r.address(), Types.NVARCHAR)
                .addValue("lat", r.latitude(), Types.DECIMAL)
                .addValue("lon", r.longitude(), Types.DECIMAL)
                .addValue("primary", r.isPrimaryHub())
                .addValue("rate", rate, Types.DECIMAL)
                .addValue("active", r.isActive());
    }

    private Location view(ResultSet row, int index) throws SQLException {
        BigDecimal rate = row.getBigDecimal("DomesticRatePerKm");
        return new Location(row.getInt("LocationID"),
                row.getString("CountryCode"), row.getString("LocationType"),
                row.getString("LocationName"), row.getString("LocationCode"),
                row.getString("Address"), row.getBigDecimal("Latitude"),
                row.getBigDecimal("Longitude"), row.getBoolean("IsPrimaryHub"),
                rate == null ? DEFAULT_DOMESTIC_RATE : rate,
                row.getBoolean("IsActive"));
    }

    private String columns() {
        String base = "LocationID,CountryCode,LocationType,LocationName,"
                + "LocationCode,IsActive,";
        if (!hubsReady()) {
            return base + "NULL AS Address,NULL AS Latitude,NULL AS Longitude,"
                    + "CAST(0 AS bit) AS IsPrimaryHub,"
                    + "CAST(2.00 AS decimal(10,2)) AS DomesticRatePerKm";
        }
        String rateCol = domesticRateReady() ? "DomesticRatePerKm"
                : "CAST(2.00 AS decimal(10,2)) AS DomesticRatePerKm";
        return base + "Address,Latitude,Longitude,IsPrimaryHub," + rateCol;
    }

    private void requireHubsReady() {
        if (!hubsReady()) {
            throw new com.equitransit.backend.exception.OrderException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "HUB_SCHEMA_PENDING", "Deploy migration027.");
        }
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
