package com.equitransit.backend.repository;

import com.equitransit.backend.dto.route.RouteMatchRequest;
import com.equitransit.backend.dto.route.RouteTemplateRequest;
import com.equitransit.backend.dto.route.RouteTemplateResponse;
import com.equitransit.backend.entity.RouteTransportMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Bound route CRUD and specificity-ranked endpoint matching. */
@Repository
public class RouteTemplateRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc transaction-aware parameter binding */
    public RouteTemplateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return migration026 is present; harmless on legacy/H2 databases */
    public boolean ready() {
        return jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM "
                + "INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' "
                + "AND TABLE_NAME='RouteTemplate'", Integer.class) == 1;
    }

    /** @param inactive include hidden @param page page @param size cap
     * @return stable catalog page */
    public List<RouteTemplateResponse> list(boolean inactive, int page,
            int size) {
        return jdbc.query("SELECT * FROM dbo.RouteTemplate "
                + (inactive ? "" : "WHERE IsActive=1 ")
                + "ORDER BY RouteCode,RouteTemplateID OFFSET :offset ROWS "
                + "FETCH NEXT :size ROWS ONLY", Map.of("offset", (long) page
                        * size, "size", size), this::view);
    }

    /** @param id identity @return existing template, including hidden rows */
    public List<RouteTemplateResponse> find(long id) {
        return jdbc.query("SELECT * FROM dbo.RouteTemplate WHERE "
                + "RouteTemplateID=:id", Map.of("id", id), this::view);
    }

    /** @param request normalized catalog row @param staff trusted creator
     * @return generated identity */
    public long insert(RouteTemplateRequest request, long staff) {
        return jdbc.queryForObject("INSERT dbo.RouteTemplate(RouteCode,"
                + "RouteName,OriginCountryCode,OriginCity,OriginAirportCode,"
                + "DestinationCountryCode,DestinationCity,"
                + "DestinationAirportCode,TransportMode,EstimatedRoadKm,"
                + "EstimatedAirKm,EstimatedHours,RouteSummary,BaseRoadRate,"
                + "BaseAirRate,IsActive,CreatedBy) OUTPUT "
                + "INSERTED.RouteTemplateID VALUES(:code,:name,:origin,"
                + ":originCity,:originAirport,:destination,:destinationCity,"
                + ":destinationAirport,:mode,:road,:air,:hours,:summary,"
                + ":roadRate,:airRate,:active,:staff)", args(request)
                        .addValue("staff", staff), Long.class);
    }

    /** @param id identity @param request full normalized replacement
     * @return affected rows */
    public int update(long id, RouteTemplateRequest request) {
        return jdbc.update("UPDATE dbo.RouteTemplate SET RouteCode=:code,"
                + "RouteName=:name,OriginCountryCode=:origin,"
                + "OriginCity=:originCity,OriginAirportCode=:originAirport,"
                + "DestinationCountryCode=:destination,"
                + "DestinationCity=:destinationCity,"
                + "DestinationAirportCode=:destinationAirport,"
                + "TransportMode=:mode,EstimatedRoadKm=:road,"
                + "EstimatedAirKm=:air,EstimatedHours=:hours,"
                + "RouteSummary=:summary,BaseRoadRate=:roadRate,"
                + "BaseAirRate=:airRate,IsActive=:active,"
                + "UpdatedAt=SYSDATETIMEOFFSET() WHERE RouteTemplateID=:id",
                args(request).addValue("id", id));
    }

    /** @param id identity @return affected rows; never deletes history */
    public int hide(long id) {
        return jdbc.update("UPDATE dbo.RouteTemplate SET IsActive=0,"
                + "UpdatedAt=SYSDATETIMEOFFSET() WHERE RouteTemplateID=:id",
                Map.of("id", id));
    }

    /** @param request endpoints @return most specific candidate, then ID */
    public List<RouteTemplateResponse> match(RouteMatchRequest request) {
        var args = new MapSqlParameterSource("origin",
                request.originCountryCode()).addValue("destination",
                        request.destinationCountryCode())
                .addValue("mode", request.transportMode() == null ? null
                        : request.transportMode().name(), Types.VARCHAR)
                .addValue("originCity", request.originCity(), Types.NVARCHAR)
                .addValue("destinationCity", request.destinationCity(),
                        Types.NVARCHAR)
                .addValue("originAirport", request.originAirportCode(),
                        Types.VARCHAR)
                .addValue("destinationAirport",
                        request.destinationAirportCode(),
                        Types.VARCHAR);
        return jdbc.query("SELECT TOP(1) * FROM dbo.RouteTemplate WHERE "
                + "IsActive=1 AND OriginCountryCode=:origin AND "
                + "DestinationCountryCode=:destination AND "
                + "(:mode IS NULL OR TransportMode=:mode) "
                + compatible("OriginCity", "originCity")
                + compatible("DestinationCity", "destinationCity")
                + compatible("OriginAirportCode", "originAirport")
                + compatible("DestinationAirportCode", "destinationAirport")
                + " ORDER BY (" + score("OriginCity", "originCity") + "+"
                + score("DestinationCity", "destinationCity") + "+"
                + score("OriginAirportCode", "originAirport") + "+"
                + score("DestinationAirportCode", "destinationAirport")
                + ") DESC,RouteTemplateID ASC", args, this::view);
    }

    private String compatible(String column, String parameter) {
        return " AND (:" + parameter + " IS NULL OR " + column
                + " IS NULL OR " + column + " COLLATE Latin1_General_100_CI_AI="
                + ":" + parameter + ")";
    }

    private String score(String column, String parameter) {
        return "CASE WHEN :" + parameter + " IS NOT NULL AND " + column
                + " IS NOT NULL THEN 1 ELSE 0 END";
    }

    private MapSqlParameterSource args(RouteTemplateRequest r) {
        return new MapSqlParameterSource("code", r.routeCode())
                .addValue("name", r.routeName())
                .addValue("origin", r.originCountryCode())
                .addValue("originCity", r.originCity(), Types.NVARCHAR)
                .addValue("originAirport", r.originAirportCode(), Types.VARCHAR)
                .addValue("destination", r.destinationCountryCode())
                .addValue("destinationCity", r.destinationCity(),
                        Types.NVARCHAR)
                .addValue("destinationAirport", r.destinationAirportCode(),
                        Types.VARCHAR)
                .addValue("mode", r.transportMode().name())
                .addValue("road", r.estimatedRoadKm())
                .addValue("air", r.estimatedAirKm())
                .addValue("hours", r.estimatedHours())
                .addValue("summary", r.routeSummary())
                .addValue("roadRate", r.baseRoadRate())
                .addValue("airRate", r.baseAirRate())
                .addValue("active", r.isActive());
    }

    private RouteTemplateResponse view(ResultSet row, int index)
            throws SQLException {
        return new RouteTemplateResponse(row.getLong("RouteTemplateID"),
                row.getString("RouteCode"), row.getString("RouteName"),
                row.getString("OriginCountryCode"), row.getString("OriginCity"),
                row.getString("OriginAirportCode"),
                row.getString("DestinationCountryCode"),
                row.getString("DestinationCity"),
                row.getString("DestinationAirportCode"), RouteTransportMode
                        .valueOf(row.getString("TransportMode")),
                row.getBigDecimal("EstimatedRoadKm"),
                row.getBigDecimal("EstimatedAirKm"),
                row.getBigDecimal("EstimatedHours"),
                row.getString("RouteSummary"),
                row.getBigDecimal("BaseRoadRate"),
                row.getBigDecimal("BaseAirRate"),
                row.getBoolean("IsActive"), row.getObject("CreatedBy") == null
                        ? null : row.getLong("CreatedBy"),
                row.getObject("CreatedAt", OffsetDateTime.class),
                row.getObject("UpdatedAt", OffsetDateTime.class));
    }
}
