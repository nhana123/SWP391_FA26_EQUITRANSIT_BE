/*
 * DepartureRepository
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized SQL Server access for F11 departure gates & receipt.
 */
package com.equitransit.backend.repository;

import com.equitransit.backend.dto.departure.DepartureModels.HorseReceiptRequest;
import com.equitransit.backend.dto.departure.DepartureModels.HorseReceiptView;
import com.equitransit.backend.dto.departure.DepartureModels.IdentityResult;
import com.equitransit.backend.dto.departure.DepartureModels.ReceiptState;
import com.equitransit.backend.dto.plan.PlanModels.GateState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
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

/** Parameterized JDBC repository for F11 gate verification & horse receipt. */
@Repository
public class DepartureRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Locked or read-only transport plan gate row.
     *
     * @param planId plan PK
     * @param orderId order FK
     * @param revisionId revision FK
     * @param versionNo plan version number
     * @param planStatus plan lifecycle status
     * @param acceptedQuoteId linked accepted quote ID
     * @param plannedStartTime plan start UTC
     * @param plannedEndTime plan end UTC
     * @param financialGateStatus stored financial gate status
     * @param operationalGateStatus stored operational gate status
     * @param docGateStatus stored document gate status
     * @param gatesCheckedAt timestamp when gates were last checked
     * @param departureAuthorizedBy manager StaffID who authorized departure
     * @param departureAuthorizedAt timestamp when departure was authorized
     */
    public record PlanGateRow(
            long planId, long orderId, long revisionId, int versionNo,
            PlanState planStatus, Long acceptedQuoteId,
            OffsetDateTime plannedStartTime, OffsetDateTime plannedEndTime,
            GateState financialGateStatus, GateState operationalGateStatus,
            GateState docGateStatus, OffsetDateTime gatesCheckedAt,
            Long departureAuthorizedBy, OffsetDateTime departureAuthorizedAt) {
    }

    /**
     * First operational leg summary row.
     *
     * @param legId leg PK
     * @param planId plan FK
     * @param sequenceNo leg sequence number
     * @param transportMode ROAD or AIR
     * @param legStatus current leg state
     * @param plannedDeparture planned departure UTC
     * @param plannedArrival planned arrival UTC
     * @param actualDeparture actual departure UTC
     */
    public record FirstLegRow(
            long legId, long planId, int sequenceNo, PlanLegMode transportMode,
            LegState legStatus, OffsetDateTime plannedDeparture,
            OffsetDateTime plannedArrival, OffsetDateTime actualDeparture) { }

    /**
     * Locked or read-only parent order scope row.
     *
     * @param orderId order PK
     * @param customerId owning customer ID
     * @param currentRevisionId current revision ID
     * @param currentPlanId current plan ID
     */
    public record OrderScopeRow(
            long orderId, long customerId, Long currentRevisionId,
            Long currentPlanId) { }

    /**
     * Locked horse identity row from order detail and master horse record.
     *
     * @param horseId horse PK
     * @param snapshotMicrochip microchip stored on TransportOrderDetail
     * @param horseMicrochip microchip stored on Horse
     * @param passportNumber passport number stored on Horse
     */
    public record HorseIdentityRow(
            long horseId, String snapshotMicrochip, String horseMicrochip,
            String passportNumber) { }

    /** @param source shared transactional data source */
    public DepartureRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return true when F11 schema, constraints and trigger are deployed */
    public boolean ready() {
        String sql = "SELECT CASE WHEN OBJECT_ID('dbo.PlanOperation','U') "
                + "IS NOT NULL AND (SELECT COUNT(*) FROM sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.TransportPlan') AND name IN "
                + "('FinancialGateStatus','OperationalGateStatus',"
                + "'DocGateStatus','GatesCheckedAt','DepartureAuthorizedBy',"
                + "'DepartureAuthorizedAt','PlanStatus','AcceptedQuoteID',"
                + "'RevisionID','OrderID'))=10 AND (SELECT COUNT(*) FROM "
                + "sys.columns WHERE object_id=OBJECT_ID('dbo.HorseReceipt') "
                + "AND name IN ('ReceiptID','PlanID','LegID','ReceivedBy',"
                + "'ObservedMicrochip','ObservedPassportNumber',"
                + "'IdentityCheckResult','ConditionNotes','ReceiptStatus',"
                + "'ReceivedAt','EvidenceFileKey','LocationDescription'))=12 "
                + "AND (SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN "
                + "('FK_Figma_TransportPlan_RevisionID',"
                + "'FK_Figma_TransportPlan_AcceptedQuoteID',"
                + "'FK_Figma_HorseReceipt_LegID') AND is_disabled=0 "
                + "AND is_not_trusted=0)=3 AND (SELECT COUNT(*) FROM "
                + "sys.check_constraints WHERE name IN "
                + "('CK_TripLeg_DistanceSource',"
                + "'CK_Figma_HorseReceipt_Identity') AND is_disabled=0 "
                + "AND is_not_trusted=0)=2 AND EXISTS (SELECT 1 FROM "
                + "sys.indexes WHERE object_id=OBJECT_ID('dbo.HorseReceipt') "
                + "AND name='IX_HorseReceipt_PlanLeg' AND is_disabled=0) "
                + "AND EXISTS (SELECT 1 FROM sys.triggers WHERE "
                + "name='TR_Departure_FirstLegGuard' AND is_disabled=0) "
                + "THEN 1 ELSE 0 END";

        return Integer.valueOf(1).equals(jdbc.getJdbcTemplate()
                .queryForObject(sql, Integer.class));
    }

    /**
     * @param order parent order ID
     * @return order scope row with CustomerID and CurrentPlanID
     */
    public Optional<OrderScopeRow> orderScope(long order) {
        var rows = jdbc.query("SELECT OrderID,CustomerID,CurrentRevisionID,"
                + "CurrentPlanID FROM dbo.TransportOrder WHERE OrderID=:order",
                Map.of("order", order),
                (r, i) -> new OrderScopeRow(r.getLong("OrderID"),
                        r.getLong("CustomerID"),
                        number(r, "CurrentRevisionID"),
                        number(r, "CurrentPlanID")));

        return rows.stream().findFirst();
    }

    /**
     * @param order parent order ID
     * @param plan plan ID
     * @param lock whether to acquire UPDLOCK, HOLDLOCK on TransportPlan
     * @return matching plan gate row if found
     */
    public Optional<PlanGateRow> plan(long order, long plan, boolean lock) {
        String hint = lock ? "WITH (UPDLOCK,HOLDLOCK) " : "";
        var rows = jdbc.query("SELECT * FROM dbo.TransportPlan " + hint
                + "WHERE OrderID=:order AND PlanID=:plan",
                Map.of("order", order, "plan", plan),
                (r, i) -> mapPlan(r));

        return rows.stream().findFirst();
    }

    /**
     * @param plan plan ID
     * @return sequence 1 leg of the plan if present
     */
    public Optional<FirstLegRow> firstLeg(long plan) {
        var rows = jdbc.query("SELECT * FROM dbo.TripLeg "
                + "WHERE PlanID=:plan AND SequenceNo=1",
                Map.of("plan", plan),
                (r, i) -> new FirstLegRow(r.getLong("LegID"),
                        r.getLong("PlanID"), r.getInt("SequenceNo"),
                        PlanLegMode.valueOf(r.getString("TransportMode")),
                        LegState.valueOf(r.getString("LegStatus")),
                        time(r, "PlannedDeparture"),
                        time(r, "PlannedArrival"),
                        time(r, "ActualDeparture")));

        return rows.stream().findFirst();
    }

    /**
     * @param plan plan ID
     * @param staff driver StaffID
     * @return true if staff is an active DRIVER assigned on any leg of plan
     */
    public boolean assignedDriverOnPlan(long plan, long staff) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TripAssignment a "
                + "JOIN dbo.TripLeg l ON l.LegID=a.LegID "
                + "JOIN dbo.StaffProfile s ON s.StaffID=a.StaffID "
                + "JOIN dbo.[User] u ON u.UserID=s.UserID "
                + "JOIN dbo.[Role] r ON r.RoleID=u.RoleID "
                + "WHERE l.PlanID=:plan AND a.StaffID=:staff "
                + "AND a.RoleInTrip='DRIVER' "
                + "AND a.Status IN ('CONFIRMED','ACTIVE') "
                + "AND u.Status='ACTIVE' AND r.RoleCode='DRIVER'",
                Map.of("plan", plan, "staff", staff), Integer.class) > 0;
    }

    /**
     * @param plan plan ID
     * @param leg leg ID
     * @param staff driver StaffID
     * @return true if staff is an active DRIVER assigned on the road leg
     */
    public boolean assignedDriverOnLeg(long plan, long leg, long staff) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TripAssignment a "
                + "JOIN dbo.TripLeg l ON l.LegID=a.LegID "
                + "JOIN dbo.StaffProfile s ON s.StaffID=a.StaffID "
                + "JOIN dbo.[User] u ON u.UserID=s.UserID "
                + "JOIN dbo.[Role] r ON r.RoleID=u.RoleID "
                + "WHERE l.PlanID=:plan AND l.LegID=:leg "
                + "AND l.TransportMode='ROAD' AND a.StaffID=:staff "
                + "AND a.RoleInTrip='DRIVER' "
                + "AND a.Status IN ('CONFIRMED','ACTIVE') "
                + "AND u.Status='ACTIVE' AND r.RoleCode='DRIVER'",
                Map.of("plan", plan, "leg", leg, "staff", staff),
                Integer.class) > 0;
    }

    /**
     * @param leg leg ID
     * @return true if every active DRIVER assignment on leg has active user
     */
    public boolean legDriversActive(long leg) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TripAssignment a "
                + "LEFT JOIN dbo.StaffProfile s ON s.StaffID=a.StaffID "
                + "LEFT JOIN dbo.[User] u ON u.UserID=s.UserID "
                + "LEFT JOIN dbo.[Role] r ON r.RoleID=u.RoleID "
                + "WHERE a.LegID=:leg AND a.RoleInTrip='DRIVER' "
                + "AND a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') "
                + "AND (s.StaffID IS NULL OR u.Status<>'ACTIVE' "
                + "OR r.RoleCode<>'DRIVER')",
                Map.of("leg", leg), Integer.class) == 0;
    }

    /**
     * @param revision revision ID
     * @return true if PRE_EXPORT clearance or authority procedure has a hold
     */
    public boolean complianceHoldActive(long revision) {
        int clearanceHold = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT "
                + "TOP (1) Status FROM dbo.ComplianceClearance "
                + "WHERE RevisionID=:rev AND PhaseCode='PRE_EXPORT' "
                + "ORDER BY ClearanceID DESC) c "
                + "WHERE c.Status IN ('HOLD','BLOCKED','REJECTED')",
                Map.of("rev", revision), Integer.class);
        int procedureHold = jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.AuthorityProcedure p WHERE p.RevisionID=:rev "
                + "AND p.PhaseCode='PRE_EXPORT' AND NOT EXISTS (SELECT 1 FROM "
                + "dbo.AuthorityProcedure n WHERE n.RevisionID=p.RevisionID "
                + "AND n.PhaseCode=p.PhaseCode AND n.RuleID=p.RuleID "
                + "AND n.ProcedureID>p.ProcedureID) AND (p.ApprovalStatus IN "
                + "('HOLD','REJECTED') OR (p.HoldReason IS NOT NULL "
                + "AND LTRIM(RTRIM(p.HoldReason))<>''))",
                Map.of("rev", revision), Integer.class);

        return clearanceHold > 0 || procedureHold > 0;
    }

    /**
     * @param order order ID
     * @param lock whether to acquire UPDLOCK, HOLDLOCK on Horse
     * @return horse identity row if present
     */
    public Optional<HorseIdentityRow> horseIdentity(long order, boolean lock) {
        String hint = lock ? "WITH (UPDLOCK,HOLDLOCK) " : "";
        var rows = jdbc.query("SELECT d.HorseID,d.SnapshotMicrochip,"
                + "h.MicrochipID,h.PassportNumber "
                + "FROM dbo.TransportOrderDetail d JOIN dbo.Horse h " + hint
                + "ON h.HorseID=d.HorseID WHERE d.OrderID=:order",
                Map.of("order", order),
                (r, i) -> new HorseIdentityRow(r.getLong("HorseID"),
                        r.getString("SnapshotMicrochip"),
                        r.getString("MicrochipID"),
                        r.getString("PassportNumber")));

        return rows.stream().findFirst();
    }

    /**
     * @param order order ID
     * @param plan plan ID
     * @param financial financial gate status
     * @param legal legal gate status
     * @param operational operational gate status
     * @param checkedAt evaluation UTC timestamp
     * @param clearAuth whether to clear stale manager authorization
     */
    public void updateGates(long order, long plan, GateState financial,
            GateState legal, GateState operational, OffsetDateTime checkedAt,
            boolean clearAuth) {
        String authSql = clearAuth
                ? ",DepartureAuthorizedBy=NULL,DepartureAuthorizedAt=NULL "
                : " ";

        jdbc.update("UPDATE dbo.TransportPlan SET FinancialGateStatus=:fin,"
                + "DocGateStatus=:doc,OperationalGateStatus=:ops,"
                + "GatesCheckedAt=:at" + authSql
                + "WHERE PlanID=:plan AND OrderID=:order",
                Map.of("fin", financial.name(), "doc", legal.name(),
                        "ops", operational.name(), "at", utc(checkedAt),
                        "plan", plan, "order", order));
    }

    /**
     * @param order order ID
     * @param plan plan ID
     * @param managerStaffId active MANAGER StaffID
     * @param now authorization UTC timestamp
     */
    public void authorizeDeparture(long order, long plan, long managerStaffId,
            OffsetDateTime now) {
        jdbc.update("UPDATE dbo.TransportPlan SET FinancialGateStatus='PASS',"
                + "DocGateStatus='PASS',OperationalGateStatus='PASS',"
                + "GatesCheckedAt=:now,DepartureAuthorizedBy=:staff,"
                + "DepartureAuthorizedAt=:now "
                + "WHERE PlanID=:plan AND OrderID=:order",
                Map.of("now", utc(now), "staff", managerStaffId,
                        "plan", plan, "order", order));
    }

    /**
     * @param plan plan ID
     * @param leg leg ID
     * @param staffId driver StaffID
     * @param req horse receipt command
     * @param identity computed identity result
     * @param status computed receipt status
     * @param now server UTC timestamp
     * @return generated ReceiptID
     */
    public long insertHorseReceipt(long plan, long leg, long staffId,
            HorseReceiptRequest req, IdentityResult identity,
            ReceiptState status, OffsetDateTime now) {
        var args = new MapSqlParameterSource("plan", plan)
                .addValue("leg", leg)
                .addValue("staff", staffId)
                .addValue("chip", req.observedMicrochip().trim())
                .addValue("pass", req.observedPassportNumber().trim())
                .addValue("idRes", identity.name())
                .addValue("notes", req.conditionNotes())
                .addValue("status", status.name())
                .addValue("at", utc(now))
                .addValue("key", req.evidenceFileKey())
                .addValue("loc", req.locationDescription().trim());

        return jdbc.queryForObject("INSERT dbo.HorseReceipt (PlanID,LegID,"
                + "ReceivedBy,ObservedMicrochip,ObservedPassportNumber,"
                + "IdentityCheckResult,ConditionNotes,ReceiptStatus,"
                + "ReceivedAt,EvidenceFileKey,LocationDescription) "
                + "OUTPUT INSERTED.ReceiptID VALUES (:plan,:leg,:staff,:chip,"
                + ":pass,:idRes,:notes,:status,:at,:key,:loc)",
                args, Long.class);
    }

    /**
     * @param plan plan ID
     * @return all horse receipts recorded on the plan, newest first
     */
    public List<HorseReceiptView> receipts(long plan) {
        return jdbc.query("SELECT * FROM dbo.HorseReceipt WHERE PlanID=:plan "
                + "ORDER BY ReceiptID DESC", Map.of("plan", plan),
                (r, i) -> new HorseReceiptView(r.getLong("ReceiptID"),
                        r.getLong("PlanID"), number(r, "LegID"),
                        number(r, "ReceivedBy"),
                        r.getString("ObservedMicrochip"),
                        r.getString("ObservedPassportNumber"),
                        IdentityResult.valueOf(
                                r.getString("IdentityCheckResult")),
                        ReceiptState.valueOf(r.getString("ReceiptStatus")),
                        r.getString("ConditionNotes"),
                        r.getString("EvidenceFileKey"),
                        r.getString("LocationDescription"),
                        time(r, "ReceivedAt")));
    }

    /**
     * Atomically transitions first leg, plan and order to IN_TRANSIT.
     *
     * @param order order ID
     * @param plan plan ID
     * @param leg first leg ID
     * @param now departure UTC timestamp
     */
    public void departFirstLeg(long order, long plan, long leg,
            OffsetDateTime now) {
        jdbc.update("UPDATE dbo.TransportPlan SET FinancialGateStatus='PASS',"
                + "DocGateStatus='PASS',OperationalGateStatus='PASS',"
                + "GatesCheckedAt=:now WHERE PlanID=:plan AND OrderID=:order",
                Map.of("now", utc(now), "plan", plan, "order", order));
        jdbc.update("UPDATE dbo.TripLeg SET ActualDeparture=:now,"
                + "LegStatus='IN_TRANSIT' WHERE LegID=:leg AND PlanID=:plan",
                Map.of("now", utc(now), "leg", leg, "plan", plan));
        jdbc.update("UPDATE dbo.TripAssignment SET Status='ACTIVE' "
                + "WHERE LegID=:leg AND Status='CONFIRMED'",
                Map.of("leg", leg));
        jdbc.update("UPDATE dbo.TransportPlan SET PlanStatus='IN_TRANSIT' "
                + "WHERE PlanID=:plan AND OrderID=:order",
                Map.of("plan", plan, "order", order));
        jdbc.update("UPDATE dbo.TransportOrder SET OrderStatus='IN_TRANSIT',"
                + "UpdatedAt=SYSUTCDATETIME() WHERE OrderID=:order",
                Map.of("order", order));
    }

    private PlanGateRow mapPlan(ResultSet r) throws SQLException {
        return new PlanGateRow(r.getLong("PlanID"), r.getLong("OrderID"),
                r.getLong("RevisionID"), r.getInt("VersionNo"),
                PlanState.valueOf(r.getString("PlanStatus")),
                number(r, "AcceptedQuoteID"),
                time(r, "PlannedStartTime"), time(r, "PlannedEndTime"),
                GateState.valueOf(r.getString("FinancialGateStatus")),
                GateState.valueOf(r.getString("OperationalGateStatus")),
                GateState.valueOf(r.getString("DocGateStatus")),
                time(r, "GatesCheckedAt"),
                number(r, "DepartureAuthorizedBy"),
                time(r, "DepartureAuthorizedAt"));
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
