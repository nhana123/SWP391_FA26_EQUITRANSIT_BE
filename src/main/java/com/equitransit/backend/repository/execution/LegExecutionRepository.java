/*
 * LegExecutionRepository
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized JDBC queries for TripLeg, assignments & order scopes.
 */
package com.equitransit.backend.repository.execution;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized JDBC repository for execution context across legs & orders. */
@Repository("operationalLegExecutionRepository")
public class LegExecutionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Immutable projection of a TripLeg record.
     *
     * @param legId PK
     * @param planId plan FK
     * @param sequenceNo sequence number in plan
     * @param transportMode ROAD or AIR
     * @param startPoint departure location
     * @param endPoint destination location
     * @param distanceKm distance in km
     * @param plannedDeparture scheduled departure UTC
     * @param plannedArrival scheduled arrival UTC
     * @param actualDeparture actual departure UTC
     * @param actualArrival actual arrival UTC
     * @param legStatus current leg lifecycle status
     * @param plannedFlightNumber flight number if AIR
     * @param careInstructions specific welfare care notes
     */
    public record LegRow(
            long legId, long planId, int sequenceNo, String transportMode,
            String startPoint, String endPoint, BigDecimal distanceKm,
            OffsetDateTime plannedDeparture, OffsetDateTime plannedArrival,
            OffsetDateTime actualDeparture, OffsetDateTime actualArrival,
            String legStatus, String plannedFlightNumber,
            String careInstructions) { }

    /**
     * Parent order scope projection.
     *
     * @param orderId PK
     * @param customerId owning customer FK
     * @param orderStatus order lifecycle status
     * @param currentPlanId active operational plan FK
     * @param currentRevisionId active order revision FK
     */
    public record OrderScopeRow(
            long orderId, long customerId, String orderStatus,
            Long currentPlanId, Long currentRevisionId) { }

    /**
     * Staff profile projection.
     *
     * @param staffId PK
     * @param userId user account FK
     * @param fullName full name of the staff member
     * @param phone contact telephone number
     * @param licenseNumber professional driving/handling license
     */
    public record StaffProfileRow(
            long staffId, long userId, String fullName,
            String phone, String licenseNumber) { }

    /**
     * Trip staff assignment projection.
     *
     * @param assignmentId PK
     * @param legId leg FK
     * @param staffId staff FK
     * @param roleInTrip DRIVER, GROOM, or COORDINATOR
     * @param status assignment status
     * @param assignedFrom window start UTC
     * @param assignedTo window end UTC
     */
    public record TripAssignmentRow(
            long assignmentId, long legId, long staffId, String roleInTrip,
            String status, OffsetDateTime assignedFrom,
            OffsetDateTime assignedTo) { }

    /**
     * Creates leg execution repository with timeout.
     * @param source shared datasource
     */
    public LegExecutionRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        this.jdbc = new NamedParameterJdbcTemplate(template);
    }

    /**
     * Retrieves leg row by PK.
     * @param legId leg PK
     * @return leg row if found
     */
    public Optional<LegRow> findLegById(long legId) {
        String sql = "SELECT * FROM dbo.TripLeg WHERE LegID=:legId";
        var list = jdbc.query(sql, Map.of("legId", legId), this::mapLeg);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Retrieves all legs belonging to an operational plan in sequence.
     * @param planId plan PK
     * @return list of legs ordered by sequenceNo
     */
    public List<LegRow> findLegsByPlanId(long planId) {
        String sql = "SELECT * FROM dbo.TripLeg WHERE PlanID=:planId "
                + "ORDER BY SequenceNo ASC";

        return jdbc.query(sql, Map.of("planId", planId), this::mapLeg);
    }

    /**
     * Checks if staff member is assigned as driver on leg.
     * @param legId leg PK
     * @param staffId staff PK
     * @return assignment row if assigned and active/confirmed
     */
    public Optional<TripAssignmentRow> findDriverAssignment(long legId,
            long staffId) {
        return driverAssignment(legId, staffId, false);
    }

    /** F14 custody remains attributable after F12 ends the assignment.
     * @param legId completed leg @param staffId trusted staff identity
     * @return completed assignment, never cancelled or revoked assignments */
    public Optional<TripAssignmentRow> findCompletedDriverAssignment(long legId,
            long staffId) {
        return driverAssignment(legId, staffId, true);
    }

    private Optional<TripAssignmentRow> driverAssignment(long legId,
            long staffId, boolean completed) {
        String sql = "SELECT a.* FROM dbo.TripAssignment a "
                + "JOIN dbo.StaffProfile sp ON sp.StaffID=a.StaffID "
                + "JOIN dbo.[User] u ON u.UserID=sp.UserID "
                + "WHERE a.LegID=:legId AND a.StaffID=:staffId "
                + "AND a.RoleInTrip='DRIVER' AND "
                + (completed ? "a.Status='COMPLETED'" : "a.Status IN "
                    + "('CONFIRMED','ASSIGNED','ACTIVE')")
                + " AND u.Status='ACTIVE'";

        var list = jdbc.query(sql, Map.of("legId", legId, "staffId", staffId),
                this::mapAssignment);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Retrieves parent order scope for a leg.
     * @param legId leg PK
     * @return order scope if found
     */
    public Optional<OrderScopeRow> findOrderByLegId(long legId) {
        String sql = "SELECT o.OrderID, o.CustomerID, o.OrderStatus, "
                + "o.CurrentPlanID, o.CurrentRevisionID FROM dbo.TripLeg l "
                + "JOIN dbo.TransportPlan p ON p.PlanID=l.PlanID "
                + "JOIN dbo.TransportOrder o ON o.OrderID=p.OrderID "
                + "WHERE l.LegID=:legId";
        var list = jdbc.query(sql, Map.of("legId", legId), this::mapOrder);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Retrieves parent order scope by order ID.
     * @param orderId order PK
     * @return order scope if found
     */
    public Optional<OrderScopeRow> findOrderByOrderId(long orderId) {
        String sql = "SELECT OrderID, CustomerID, OrderStatus, "
                + "CurrentPlanID, CurrentRevisionID FROM dbo.TransportOrder "
                + "WHERE OrderID=:orderId";
        var list = jdbc.query(sql, Map.of("orderId", orderId), this::mapOrder);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Retrieves staff profile by staff ID.
     * @param staffId staff PK
     * @return staff profile if found
     */
    public Optional<StaffProfileRow> findStaffProfile(long staffId) {
        String sql = "SELECT * FROM dbo.StaffProfile WHERE StaffID=:staffId";
        var list = jdbc.query(sql, Map.of("staffId", staffId), this::mapStaff);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Helper to lookup full name of staff member.
     * @param staffId staff PK
     * @return full name or fallback string
     */
    public String findStaffName(long staffId) {
        return findStaffProfile(staffId)
                .map(StaffProfileRow::fullName)
                .orElse("Staff #" + staffId);
    }

    /**
     * Updates leg status and actual departure time.
     * @param legId leg PK
     * @param status new status
     * @param departure actual departure UTC
     */
    public void updateLegDeparture(long legId, String status,
            OffsetDateTime departure) {
        String sql = "UPDATE dbo.TripLeg SET LegStatus=:status, "
                + "ActualDeparture=:dep WHERE LegID=:legId";

        jdbc.update(sql, Map.of("status", status, "dep", utc(departure),
                "legId", legId));
    }

    /**
     * Updates leg status and actual arrival time.
     * @param legId leg PK
     * @param status new status
     * @param arrival actual arrival UTC
     */
    public void updateLegArrival(long legId, String status,
            OffsetDateTime arrival) {
        String sql = "UPDATE dbo.TripLeg SET LegStatus=:status, "
                + "ActualArrival=:arr WHERE LegID=:legId";

        jdbc.update(sql, Map.of("status", status, "arr", utc(arrival),
                "legId", legId));
    }

    /**
     * Updates leg status.
     * @param legId leg PK
     * @param status new status
     */
    public void updateLegStatus(long legId, String status) {
        String sql = "UPDATE dbo.TripLeg SET LegStatus=:status "
                + "WHERE LegID=:legId";

        jdbc.update(sql, Map.of("status", status, "legId", legId));
    }

    /**
     * Updates plan status.
     * @param planId plan PK
     * @param status new status
     */
    public void updatePlanStatus(long planId, String status) {
        String sql = "UPDATE dbo.TransportPlan SET PlanStatus=:status "
                + "WHERE PlanID=:planId";

        jdbc.update(sql, Map.of("status", status, "planId", planId));
    }

    /**
     * Updates order status.
     * @param orderId order PK
     * @param status new status
     */
    public void updateOrderStatus(long orderId, String status) {
        String sql = "UPDATE dbo.TransportOrder SET OrderStatus=:status, "
                + "UpdatedAt=SYSUTCDATETIME() WHERE OrderID=:orderId";

        jdbc.update(sql, Map.of("status", status, "orderId", orderId));
    }

    private LegRow mapLeg(ResultSet r, int rowNum) throws SQLException {
        return new LegRow(
                r.getLong("LegID"),
                r.getLong("PlanID"),
                r.getInt("SequenceNo"),
                r.getString("TransportMode"),
                r.getString("StartPoint"),
                r.getString("EndPoint"),
                r.getBigDecimal("DistanceKm"),
                time(r, "PlannedDeparture"),
                time(r, "PlannedArrival"),
                time(r, "ActualDeparture"),
                time(r, "ActualArrival"),
                r.getString("LegStatus"),
                r.getString("PlannedFlightNumber"),
                r.getString("CareInstructions"));
    }

    private OrderScopeRow mapOrder(ResultSet r, int rowNum)
            throws SQLException {
        return new OrderScopeRow(
                r.getLong("OrderID"),
                r.getLong("CustomerID"),
                r.getString("OrderStatus"),
                number(r, "CurrentPlanID"),
                number(r, "CurrentRevisionID"));
    }

    private StaffProfileRow mapStaff(ResultSet r, int rowNum)
            throws SQLException {
        return new StaffProfileRow(
                r.getLong("StaffID"),
                r.getLong("UserID"),
                r.getString("FullName"),
                r.getString("Phone"),
                r.getString("LicenseNumber"));
    }

    private TripAssignmentRow mapAssignment(ResultSet r, int rowNum)
            throws SQLException {
        return new TripAssignmentRow(
                r.getLong("AssignmentID"),
                r.getLong("LegID"),
                r.getLong("StaffID"),
                r.getString("RoleInTrip"),
                r.getString("Status"),
                time(r, "AssignedFrom"),
                time(r, "AssignedTo"));
    }

    private Long number(ResultSet r, String field) throws SQLException {
        return r.getObject(field) == null ? null : r.getLong(field);
    }

    private OffsetDateTime time(ResultSet r, String field) throws SQLException {
        var value = r.getObject(field, LocalDateTime.class);

        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private LocalDateTime utc(OffsetDateTime time) {
        return time == null ? null : time.withOffsetSameInstant(ZoneOffset.UTC)
                .withNano(0).toLocalDateTime();
    }
}
