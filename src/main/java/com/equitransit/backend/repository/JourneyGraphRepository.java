/*
 * JourneyGraphRepository
 * Author: EquiTransit development team
 * Date: 2026-10-21
 * Purpose: Parameterized JDBC graph persistence and safe tracking projection.
 */
package com.equitransit.backend.repository;

import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphLayout;
import com.equitransit.backend.dto.plan.JourneyGraphModels.
        GraphDriverCandidate;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphNodeDetails;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphNodeInput;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphNodePosition;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphWireInput;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphWireView;
import com.equitransit.backend.dto.plan.JourneyGraphModels.NodeType;
import com.equitransit.backend.dto.plan.JourneyGraphModels.TransitCustomsMode;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.exception.OrderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Bounded graph adapter; operational data is never overwritten by layout. */
@Repository
public class JourneyGraphRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** @param source transactional datasource @param mapper typed JSON codec */
    public JourneyGraphRepository(DataSource source, ObjectMapper mapper) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        this.jdbc = new NamedParameterJdbcTemplate(template);
        this.mapper = mapper;
    }

    /** @return whether the additive sandbox migration has been deployed */
    public boolean ready() {
        return jdbc.getJdbcTemplate().queryForObject("SELECT CASE WHEN "
                + "OBJECT_ID('dbo.PlanCheckpointNode','U') IS NOT NULL AND "
                + "COL_LENGTH('dbo.PlanCheckpointNode','MetadataJson') IS NOT "
                + "NULL AND COL_LENGTH('dbo.TripLeg','FromNodeKey') IS NOT "
                + "NULL AND COL_LENGTH('dbo.TripLeg','ToNodeKey') IS NOT NULL "
                + "AND COL_LENGTH('dbo.TransportPlan','GraphLayoutJson') IS "
                + "NOT NULL AND COL_LENGTH('dbo.StaffProfile','StaffStatus') "
                + "IS NOT NULL AND (SELECT COUNT(*) FROM sys.foreign_keys "
                + "WHERE name IN ('FK_PlanCheckpointNode_Plan',"
                + "'FK_PlanCheckpointNode_Quarantine',"
                + "'FK_TripLeg_FromCheckpoint','FK_TripLeg_ToCheckpoint') "
                + "AND is_disabled=0 AND is_not_trusted=0)=4 AND (SELECT "
                + "COUNT(*) FROM sys.check_constraints WHERE name IN "
                + "('CK_TripLeg_GraphEndpoints',"
                + "'CK_PlanCheckpointNode_Metadata',"
                + "'CK_TransportPlan_GraphLayout') AND is_disabled=0 AND "
                + "is_not_trusted=0)=3 THEN 1 ELSE 0 END", Integer.class) == 1;
    }

    /** @param order authorized parent @return owner and current plan */
    public Optional<GraphScope> scope(long order) {
        return jdbc.query("SELECT CustomerID,CurrentPlanID FROM "
                + "dbo.TransportOrder WHERE OrderID=:order",
                Map.of("order", order), (r, i) -> new GraphScope(
                        r.getLong("CustomerID"), number(r, "CurrentPlanID")))
                .stream().findFirst();
    }

    /** @param plan published plan @param staff driver @return read access */
    public boolean assignedDriver(long plan, long staff) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TripAssignment "
                + "a JOIN dbo.TripLeg l ON l.LegID=a.LegID WHERE l.PlanID=:p "
                + "AND l.TransportMode='ROAD' AND a.StaffID=:s AND "
                + "a.RoleInTrip='DRIVER' AND a.Status IN "
                + "('CONFIRMED','ACTIVE','COMPLETED')",
                Map.of("p", plan, "s", staff), Integer.class) > 0;
    }

    /** @param plan bounded plan ID @return nodes in itinerary order */
    public List<GraphNodeInput> nodes(long plan) {
        return jdbc.query("SELECT TOP(22) NodeKey,NodeType,SequenceOrder,"
                + "LocationName,CountryCode,PlannedArrivalAt,"
                + "PlannedDepartureAt,DwellMinutes,TransitCustomsMode,"
                + "TransitPermitRef,WelfareCareNotes,QuarantineReservationID,"
                + "PosX,PosY,MetadataJson FROM dbo.PlanCheckpointNode WHERE "
                + "PlanID=:p ORDER BY SequenceOrder", Map.of("p", plan),
                (r, i) -> new GraphNodeInput(r.getString("NodeKey"),
                        NodeType.valueOf(r.getString("NodeType")),
                        r.getInt("SequenceOrder"), r.getString("LocationName"),
                        r.getString("CountryCode"), at(r, "PlannedArrivalAt"),
                        at(r, "PlannedDepartureAt"), r.getInt("DwellMinutes"),
                        r.getString("TransitCustomsMode") == null ? null
                                : TransitCustomsMode.valueOf(r.getString(
                                        "TransitCustomsMode")),
                        r.getString("TransitPermitRef"),
                        r.getString("WelfareCareNotes"),
                        number(r, "QuarantineReservationID"),
                        r.getBigDecimal("PosX"), r.getBigDecimal("PosY"),
                        decode(r.getString("MetadataJson"),
                                GraphNodeDetails.class)));
    }

    /** @param plan bounded plan @param staff current staff or null
     * @return safe assignment/execution facts in one bounded query */
    public List<GraphWireView> wires(long plan, Long staff) {
        return jdbc.query("SELECT TOP(21) l.LegID,l.SequenceNo,l.FromNodeKey,"
                + "l.ToNodeKey,l.TransportMode,l.LegStatus,l.StartPoint,"
                + "l.EndPoint,l.DistanceKm,l.DistanceSource,l.PlannedDeparture,"
                + "l.PlannedArrival,"
                + "l.ActualDeparture,l.ActualArrival,l.VehicleID,"
                + "COALESCE(f.FlightNumber,l.PlannedFlightNumber) FlightNo,"
                + "d.StaffID DriverID,s.StallID,CASE WHEN EXISTS(SELECT 1 "
                + "FROM dbo.TripAssignment own WHERE own.LegID=l.LegID AND "
                + "own.StaffID=:staff AND own.RoleInTrip='DRIVER' AND "
                + "own.Status IN ('CONFIRMED','ACTIVE','COMPLETED')) AND "
                + "l.TransportMode='ROAD' THEN 1 ELSE 0 END OwnLeg "
                + "FROM dbo.TripLeg l OUTER APPLY(SELECT TOP(1) a.StaffID "
                + "FROM dbo.TripAssignment a WHERE a.LegID=l.LegID AND "
                + "a.RoleInTrip='DRIVER' AND a.Status IN "
                + "('HELD','CONFIRMED','ACTIVE','COMPLETED') ORDER BY "
                + "a.AssignmentID DESC) d OUTER APPLY(SELECT TOP(1) a.StallID "
                + "FROM dbo.StallAssignment a WHERE a.LegID=l.LegID AND "
                + "a.Status IN ('HELD','CONFIRMED','ACTIVE') ORDER BY "
                + "a.StallAssignmentID DESC) s OUTER APPLY(SELECT TOP(1) "
                + "f.FlightNumber FROM dbo.FlightReservation f WHERE "
                + "f.LegID=l.LegID AND f.Status IN ('HELD','CONFIRMED') "
                + "ORDER BY f.ReservationID DESC) f WHERE l.PlanID=:p "
                + "ORDER BY l.SequenceNo", new MapSqlParameterSource("p", plan)
                        .addValue("staff", staff),
                (r, i) -> new GraphWireView(r.getLong("LegID"),
                        r.getInt("SequenceNo"), r.getString("FromNodeKey"),
                        r.getString("ToNodeKey"), PlanLegMode.valueOf(
                                r.getString("TransportMode")),
                        LegState.valueOf(r.getString("LegStatus")),
                        r.getString("StartPoint"), r.getString("EndPoint"),
                        r.getBigDecimal("DistanceKm"),
                        r.getString("DistanceSource"),
                        at(r, "PlannedDeparture"), at(r, "PlannedArrival"),
                        at(r, "ActualDeparture"), at(r, "ActualArrival"),
                        number(r, "VehicleID"), number(r, "DriverID"),
                        r.getString("FlightNo"), number(r, "StallID"),
                        r.getBoolean("OwnLeg")));
    }

    /** @param plan plan @return safe typed transform or initial viewport */
    public GraphLayout layout(long plan) {
        String encoded = jdbc.queryForObject("SELECT GraphLayoutJson FROM "
                + "dbo.TransportPlan WHERE PlanID=:p", Map.of("p", plan),
                String.class);

        return encoded == null ? new GraphLayout(BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO)
                : decode(encoded, GraphLayout.class);
    }

    /** @param plan newly created draft @param nodes bounded checkpoints
     * @param wires sequence references @param layout typed transform */
    public void insert(long plan, List<GraphNodeInput> nodes,
            List<GraphWireInput> wires, GraphLayout layout) {
        var args = nodes.stream().map(n -> new MapSqlParameterSource("p", plan)
                .addValue("key", n.nodeKey()).addValue("type", n.nodeType()
                        .name()).addValue("seq", n.sequenceOrder())
                .addValue("name", n.locationName().trim())
                .addValue("country", n.countryCode())
                .addValue("arr", utc(n.plannedArrivalAt()))
                .addValue("dep", utc(n.plannedDepartureAt()))
                .addValue("dwell", n.dwellMinutes())
                .addValue("customs", n.transitCustomsMode() == null ? null
                        : n.transitCustomsMode().name())
                .addValue("permit", n.transitPermitRef())
                .addValue("care", n.welfareCareNotes())
                .addValue("quarantine", n.quarantineReservationId())
                .addValue("x", n.posX()).addValue("y", n.posY())
                .addValue("meta", n.details() == null ? null
                        : encode(n.details())))
                .toArray(MapSqlParameterSource[]::new);

        jdbc.batchUpdate("INSERT dbo.PlanCheckpointNode (PlanID,NodeKey,"
                + "NodeType,SequenceOrder,LocationName,CountryCode,"
                + "PlannedArrivalAt,PlannedDepartureAt,DwellMinutes,"
                + "TransitCustomsMode,TransitPermitRef,WelfareCareNotes,"
                + "QuarantineReservationID,PosX,PosY,MetadataJson) VALUES "
                + "(:p,:key,:type,:seq,:name,:country,:arr,:dep,:dwell,"
                + ":customs,:permit,:care,:quarantine,:x,:y,:meta)", args);
        jdbc.batchUpdate("UPDATE dbo.TripLeg SET FromNodeKey=:from,"
                + "ToNodeKey=:to WHERE PlanID=:p AND SequenceNo=:seq",
                wires.stream().map(w -> new MapSqlParameterSource("p", plan)
                        .addValue("seq", w.sequenceNo())
                        .addValue("from", w.fromNodeKey())
                        .addValue("to", w.toNodeKey()))
                        .toArray(MapSqlParameterSource[]::new));
        saveLayout(plan, layout);
    }

    /** @param plan parent @param positions existing keys @param layout view */
    public void positions(long plan, List<GraphNodePosition> positions,
            GraphLayout layout) {
        jdbc.batchUpdate("UPDATE dbo.PlanCheckpointNode SET PosX=:x,PosY=:y "
                + "WHERE PlanID=:p AND NodeKey=:key", positions.stream()
                .map(n -> new MapSqlParameterSource("p", plan)
                        .addValue("key", n.nodeKey()).addValue("x", n.posX())
                        .addValue("y", n.posY()))
                .toArray(MapSqlParameterSource[]::new));
        saveLayout(plan, layout);
    }

    /** @param revision exact revision @param ids bounded reservation IDs
     * @return number of matching reservations in this revision */
    public int quarantineCount(long revision, List<Long> ids) {
        return ids.isEmpty() ? 0 : jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.QuarantineReservation r JOIN dbo.QuarantineChoice c "
                + "ON c.QuarantineChoiceID=r.QuarantineChoiceID JOIN "
                + "dbo.OrderServiceChoice s ON s.ChoiceID=c.ChoiceID WHERE "
                + "s.RevisionID=:r AND s.ServiceCode='QUARANTINE' AND "
                + "r.ReservationID IN (:ids)",
                Map.of("r", revision, "ids", ids), Integer.class);
    }

    /** @param ids bounded supplier IDs @return all suppliers active */
    public boolean activeSuppliers(List<Long> ids) {
        return ids.isEmpty() || jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.Supplier WITH(HOLDLOCK) WHERE SupplierID IN (:ids) "
                + "AND IsActive=1",
                Map.of("ids", ids), Integer.class) == ids.size();
    }

    /** @param staff identity @return active staff profile, not JWT alone */
    public boolean activeProfile(long staff) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.StaffProfile "
                + "WHERE StaffID=:s AND StaffStatus='ACTIVE'",
                Map.of("s", staff), Integer.class) == 1;
    }

    /** @param plan current draft @param leg ROAD leg @param search prefix
     * @param buffer requested separation in minutes @return at most 10 */
    public List<GraphDriverCandidate> driverCandidates(long plan, long leg,
            String search, int buffer) {
        String prefix = search.replace("[", "[[]").replace("%", "[%]")
                .replace("_", "[_]") + "%";

        return jdbc.query("SELECT TOP(10) s.StaffID,s.FullName FROM "
                + "dbo.StaffProfile s JOIN dbo.[User] u ON u.UserID=s.UserID "
                + "JOIN dbo.[Role] r ON r.RoleID=u.RoleID JOIN dbo.TripLeg t "
                + "ON t.LegID=:leg AND t.PlanID=:p AND t.TransportMode='ROAD' "
                + "WHERE u.Status='ACTIVE' AND s.StaffStatus='ACTIVE' AND "
                + "r.RoleCode='DRIVER' AND s.FullName LIKE :prefix AND "
                + "NOT EXISTS(SELECT 1 FROM dbo.TripAssignment a JOIN "
                + "dbo.TripLeg l ON l.LegID=a.LegID JOIN dbo.TransportPlan p "
                + "ON p.PlanID=l.PlanID WHERE a.StaffID=s.StaffID AND "
                + "a.LegID<>t.LegID AND a.Status NOT IN "
                + "('CANCELLED','CANCELED','INACTIVE') AND p.PlanStatus NOT IN "
                + "('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND "
                + "(a.AssignedFrom IS NULL OR a.AssignedTo IS NULL OR "
                + "a.BufferMinutes IS NULL OR (t.PlannedDeparture<DATEADD("
                + "minute,CASE WHEN :buf>=a.BufferMinutes THEN :buf ELSE "
                + "a.BufferMinutes END,a.AssignedTo) AND a.AssignedFrom<"
                + "DATEADD(minute,CASE WHEN :buf>=a.BufferMinutes THEN :buf "
                + "ELSE a.BufferMinutes END,t.PlannedArrival)))) ORDER BY "
                + "s.FullName,s.StaffID", Map.of("p", plan, "leg", leg,
                        "prefix", prefix, "buf", buffer), (r, i) ->
                        new GraphDriverCandidate(r.getLong("StaffID"),
                                r.getString("FullName")));
    }

    private void saveLayout(long plan, GraphLayout layout) {
        jdbc.update("UPDATE dbo.TransportPlan SET GraphLayoutJson=:layout "
                + "WHERE PlanID=:p",
                Map.of("p", plan, "layout", encode(layout)));
    }

    private String encode(Object value) {
        try {
            String encoded = mapper.writeValueAsString(value);

            if (encoded.length() > 16384) {
                throw unsupported();
            }
            return encoded;
        } catch (JsonProcessingException error) {
            throw unsupported();
        }
    }

    private <T> T decode(String value, Class<T> type) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException error) {
            throw unsupported();
        }
    }

    private static OffsetDateTime at(ResultSet row, String column)
            throws SQLException {
        var value = row.getObject(column, LocalDateTime.class);

        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(
                ZoneOffset.UTC).withNano(0).toLocalDateTime();
    }

    private static Long number(ResultSet row, String column)
            throws SQLException {
        return row.getObject(column) == null ? null : row.getLong(column);
    }

    private static OrderException unsupported() {
        return new OrderException(HttpStatus.CONFLICT,
                "GRAPH_DATA_UNSUPPORTED", "Stored graph is not supported.");
    }

    /** Ownership projection without sensitive domain attributes.
     * @param customerId owner @param currentPlanId published plan */
    public record GraphScope(long customerId, Long currentPlanId) { }
}
