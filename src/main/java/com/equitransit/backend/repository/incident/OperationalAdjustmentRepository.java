/*
 * OperationalAdjustmentRepository
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized JDBC repository for OperationalAdjustment.
 */
package com.equitransit.backend.repository.incident;

import com.equitransit.backend.entity.incident.OperationalAdjustment;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC repository for controlled operational adjustments and consent. */
@Repository
public class OperationalAdjustmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Projection of basic plan data needed for plan evolution.
     * @param planId plan primary key
     * @param orderId parent order primary key
     * @param revisionId parent revision primary key
     * @param versionNo version number
     * @param planStatus lifecycle status
     * @param acceptedQuoteId accepted quote identifier
     * @param plannedStartTime start time
     * @param plannedEndTime end time
     */
    public record PlanRow(
            long planId, long orderId, long revisionId, int versionNo,
            String planStatus, Long acceptedQuoteId,
            OffsetDateTime plannedStartTime, OffsetDateTime plannedEndTime) { }

    /**
     * Creates operational adjustment repository with query timeout.
     * @param source shared transactional datasource
     */
    public OperationalAdjustmentRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        this.jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return true when OperationalAdjustment table and columns are ready */
    public boolean ready() {
        String sql = "SELECT CASE WHEN OBJECT_ID('dbo.OperationalAdjustment',"
                + "'U') IS NOT NULL AND (SELECT COUNT(*) FROM sys.columns"
                + " WHERE object_id=OBJECT_ID('dbo.OperationalAdjustment')"
                + " AND name IN ('AdjustmentID','PlanID','IncidentID',"
                + "'ProposedBy','ApprovedBy','ProposedChanges',"
                + "'AdditionalCostEstimate','ApprovalStatus','ProposedAt',"
                + "'DecidedAt','ChangeRequestID','CustomerDecision',"
                + "'RespondedByCustomerID','CustomerRespondedAt',"
                + "'CustomerResponseNotes'))=15 THEN 1 ELSE 0 END";

        return Integer.valueOf(1).equals(jdbc.getJdbcTemplate()
                .queryForObject(sql, Integer.class));
    }

    /**
     * Inserts an operational adjustment and returns generated identifier.
     * @param a adjustment entity
     * @return generated AdjustmentID
     */
    public long save(OperationalAdjustment a) {
        String sql = "INSERT INTO dbo.OperationalAdjustment (PlanID, "
                + "IncidentID, ProposedBy, ApprovedBy, ProposedChanges, "
                + "AdditionalCostEstimate, ApprovalStatus, ProposedAt, "
                + "DecidedAt, ChangeRequestID, CustomerDecision, "
                + "RespondedByCustomerID, CustomerRespondedAt, "
                + "CustomerResponseNotes) "
                + "OUTPUT INSERTED.AdjustmentID VALUES (:planId, :incidentId, "
                + ":proposedBy, :approvedBy, :proposedChanges, :cost, "
                + ":status, :proposedAt, :decidedAt, :changeRequestId, "
                + ":customerDecision, :customerId, :customerRespondedAt, "
                + ":customerResponseNotes)";

        var params = new MapSqlParameterSource()
                .addValue("planId", a.getPlanId())
                .addValue("incidentId", a.getIncidentId())
                .addValue("proposedBy", a.getProposedBy())
                .addValue("approvedBy", a.getApprovedBy())
                .addValue("proposedChanges", a.getProposedChanges())
                .addValue("cost", a.getAdditionalCostEstimate())
                .addValue("status", a.getApprovalStatus())
                .addValue("proposedAt", utc(a.getProposedAt() == null
                        ? OffsetDateTime.now(ZoneOffset.UTC)
                        : a.getProposedAt()))
                .addValue("decidedAt", utc(a.getDecidedAt()))
                .addValue("changeRequestId", a.getChangeRequestId())
                .addValue("customerDecision", a.getCustomerDecision())
                .addValue("customerId", a.getRespondedByCustomerId())
                .addValue("customerRespondedAt",
                        utc(a.getCustomerRespondedAt()))
                .addValue("customerResponseNotes",
                        a.getCustomerResponseNotes());

        Long id = jdbc.queryForObject(sql, params, Long.class);

        a.setAdjustmentId(id);
        return id == null ? 0L : id;
    }

    /**
     * Finds adjustment by primary key.
     * @param adjustmentId primary key
     * @return adjustment if present
     */
    public Optional<OperationalAdjustment> findById(long adjustmentId) {
        String sql = "SELECT * FROM dbo.OperationalAdjustment WHERE "
                + "AdjustmentID=:adjustmentId";
        var list = jdbc.query(sql, Map.of("adjustmentId", adjustmentId),
                this::mapRow);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Lists all adjustments for a plan.
     * @param planId plan foreign key
     * @return list of adjustments ordered by ProposedAt desc
     */
    public List<OperationalAdjustment> findByPlanId(long planId) {
        String sql = "SELECT * FROM dbo.OperationalAdjustment WHERE "
                + "PlanID=:planId ORDER BY ProposedAt DESC, AdjustmentID DESC";

        return jdbc.query(sql, Map.of("planId", planId), this::mapRow);
    }

    /**
     * Lists all adjustments for an order.
     * @param orderId order foreign key
     * @return list of adjustments
     */
    public List<OperationalAdjustment> findByOrderId(long orderId) {
        String sql = "SELECT a.* FROM dbo.OperationalAdjustment a "
                + "JOIN dbo.TransportPlan p ON p.PlanID=a.PlanID "
                + "WHERE p.OrderID=:orderId "
                + "ORDER BY a.ProposedAt DESC, a.AdjustmentID DESC";

        return jdbc.query(sql, Map.of("orderId", orderId), this::mapRow);
    }

    /**
     * Records customer consent decision.
     * @param adjustmentId adjustment primary key
     * @param decision ACCEPTED or REJECTED
     * @param customerId responding customer
     * @param now timestamp
     * @param notes optional customer notes
     * @param newStatus updated approval status
     */
    public void recordCustomerDecision(long adjustmentId, String decision,
            long customerId, OffsetDateTime now, String notes,
            String newStatus) {
        String sql = "UPDATE dbo.OperationalAdjustment SET "
                + "CustomerDecision=:decision, RespondedByCustomerID=:cid, "
                + "CustomerRespondedAt=:now, CustomerResponseNotes=:notes, "
                + "ApprovalStatus=:status "
                + "WHERE AdjustmentID=:id AND CustomerDecision='PENDING'";

        jdbc.update(sql, Map.of("id", adjustmentId, "decision", decision,
                "cid", customerId, "now", utc(now), "notes", notes,
                "status", newStatus));
    }

    /**
     * Records manager approval or rejection.
     * @param adjustmentId adjustment primary key
     * @param status APPLIED, MANAGER_APPROVED, or REJECTED
     * @param managerStaffId approving staff member
     * @param now timestamp
     */
    public void recordManagerDecision(long adjustmentId, String status,
            long managerStaffId, OffsetDateTime now) {
        String sql = "UPDATE dbo.OperationalAdjustment SET "
                + "ApprovalStatus=:status, ApprovedBy=:staffId, "
                + "DecidedAt=:now WHERE AdjustmentID=:id";

        jdbc.update(sql, Map.of("id", adjustmentId, "status", status,
                "staffId", managerStaffId, "now", utc(now)));
    }

    /**
     * Updates an order change request when an adjustment completes.
     * @param changeRequestId change request PK
     * @param status APPLIED or REJECTED
     * @param staffId responding staff member
     * @param now timestamp
     * @param reason explanation
     */
    public void updateOrderChangeRequest(long changeRequestId, String status,
            long staffId, OffsetDateTime now, String reason) {
        String sql = "UPDATE dbo.OrderChangeRequest SET Status=:status, "
                + "RespondedBy=:staffId, RespondedAt=:now, "
                + "ResponseReason=:reason WHERE ChangeRequestID=:id";

        jdbc.update(sql, Map.of("id", changeRequestId, "status", status,
                "staffId", staffId, "now", utc(now), "reason", reason));
    }

    /**
     * Looks up transport plan row by plan ID.
     * @param planId plan primary key
     * @return plan row if found
     */
    public Optional<PlanRow> findPlan(long planId) {
        String sql = "SELECT PlanID, OrderID, RevisionID, VersionNo, "
                + "PlanStatus, AcceptedQuoteID, PlannedStartTime, "
                + "PlannedEndTime FROM dbo.TransportPlan WHERE PlanID=:planId";
        var list = jdbc.query(sql, Map.of("planId", planId), (rs, num) ->
                new PlanRow(
                        rs.getLong("PlanID"),
                        rs.getLong("OrderID"),
                        rs.getLong("RevisionID"),
                        rs.getInt("VersionNo"),
                        rs.getString("PlanStatus"),
                        rs.getObject("AcceptedQuoteID") != null
                                ? rs.getLong("AcceptedQuoteID") : null,
                        toUtc(rs.getObject("PlannedStartTime",
                                LocalDateTime.class)),
                        toUtc(rs.getObject("PlannedEndTime",
                                LocalDateTime.class))
                ));

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Inserts an evolved active plan version.
     * @param orderId order PK
     * @param revisionId revision PK
     * @param staffId approving manager
     * @param versionNo incremented version number
     * @param quoteId accepted quote
     * @param start planned start
     * @param end planned end
     * @param now server timestamp
     * @return generated plan identifier
     */
    public long insertEvolvedPlan(long orderId, long revisionId,
            long staffId, int versionNo, Long quoteId,
            OffsetDateTime start, OffsetDateTime end, OffsetDateTime now) {
        String sql = "INSERT INTO dbo.TransportPlan (OrderID, RevisionID, "
                + "CreatedBy, VersionNo, PlannedStartTime, PlannedEndTime, "
                + "AcceptedQuoteID, FinancialGateStatus, "
                + "OperationalGateStatus, DocGateStatus, GatesCheckedAt, "
                + "PlanStatus) "
                + "OUTPUT INSERTED.PlanID VALUES (:orderId, :revId, :staffId, "
                + ":versionNo, :startTime, :endTime, :quoteId, 'PASS', 'PASS', "
                + "'PASS', :now, 'ACTIVE')";

        var params = new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("revId", revisionId)
                .addValue("staffId", staffId)
                .addValue("versionNo", versionNo)
                .addValue("startTime", utc(start))
                .addValue("endTime", utc(end))
                .addValue("quoteId", quoteId)
                .addValue("now", utc(now));

        Long id = jdbc.queryForObject(sql, params, Long.class);

        return id == null ? 0L : id;
    }

    /**
     * Clones existing trip legs from old plan to new evolved plan.
     * @param oldPlanId source plan
     * @param newPlanId target plan
     * @return number of legs cloned
     */
    public int cloneLegsToNewPlan(long oldPlanId, long newPlanId) {
        String sql = "INSERT INTO dbo.TripLeg (PlanID, VehicleID, SequenceNo, "
                + "TransportMode, StartPoint, EndPoint, DistanceKm, "
                + "PlannedDeparture, PlannedArrival, ActualDeparture, "
                + "ActualArrival, LegStatus, StartTimezone, EndTimezone, "
                + "SupplierID, PlannedFlightNumber, ConnectionBufferMinutes, "
                + "RestDurationMinutes, CareInstructions) "
                + "SELECT :newPlanId, VehicleID, SequenceNo, TransportMode, "
                + "StartPoint, EndPoint, DistanceKm, PlannedDeparture, "
                + "PlannedArrival, ActualDeparture, ActualArrival, "
                + "LegStatus, StartTimezone, EndTimezone, SupplierID, "
                + "PlannedFlightNumber, ConnectionBufferMinutes, "
                + "RestDurationMinutes, CareInstructions "
                + "FROM dbo.TripLeg WHERE PlanID=:oldPlanId "
                + "ORDER BY SequenceNo ASC";

        return jdbc.update(sql, Map.of("newPlanId", newPlanId,
                "oldPlanId", oldPlanId));
    }

    /**
     * Sets plan status to SUPERSEDED.
     * @param planId plan primary key
     */
    public void supersedePlan(long planId) {
        String sql = "UPDATE dbo.TransportPlan SET PlanStatus='SUPERSEDED' "
                + "WHERE PlanID=:planId";

        jdbc.update(sql, Map.of("planId", planId));
    }

    /**
     * Updates active plan pointer on the transport order.
     * @param orderId order primary key
     * @param newPlanId newly evolved plan identifier
     */
    public void updateOrderCurrentPlan(long orderId, long newPlanId) {
        String sql = "UPDATE dbo.TransportOrder SET CurrentPlanID=:planId, "
                + "UpdatedAt=SYSUTCDATETIME() WHERE OrderID=:orderId";

        jdbc.update(sql, Map.of("planId", newPlanId, "orderId", orderId));
    }

    private OperationalAdjustment mapRow(ResultSet rs, int rowNum)
            throws SQLException {
        var a = new OperationalAdjustment();

        a.setAdjustmentId(rs.getLong("AdjustmentID"));
        a.setPlanId(rs.getLong("PlanID"));
        a.setIncidentId(rs.getObject("IncidentID") != null
                ? rs.getLong("IncidentID") : null);
        a.setProposedBy(rs.getLong("ProposedBy"));
        a.setApprovedBy(rs.getObject("ApprovedBy") != null
                ? rs.getLong("ApprovedBy") : null);
        a.setProposedChanges(rs.getString("ProposedChanges"));
        a.setAdditionalCostEstimate(rs.getBigDecimal("AdditionalCostEstimate"));
        a.setApprovalStatus(rs.getString("ApprovalStatus"));
        a.setProposedAt(toUtc(rs.getObject("ProposedAt", LocalDateTime.class)));
        a.setDecidedAt(toUtc(rs.getObject("DecidedAt", LocalDateTime.class)));
        a.setChangeRequestId(rs.getObject("ChangeRequestID") != null
                ? rs.getLong("ChangeRequestID") : null);
        a.setCustomerDecision(rs.getString("CustomerDecision"));
        a.setRespondedByCustomerId(
                rs.getObject("RespondedByCustomerID") != null
                        ? rs.getLong("RespondedByCustomerID") : null);
        a.setCustomerRespondedAt(toUtc(rs.getObject("CustomerRespondedAt",
                LocalDateTime.class)));
        a.setCustomerResponseNotes(rs.getString("CustomerResponseNotes"));
        return a;
    }

    private LocalDateTime utc(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private OffsetDateTime toUtc(LocalDateTime time) {
        return time == null ? null : time.atOffset(ZoneOffset.UTC);
    }
}
