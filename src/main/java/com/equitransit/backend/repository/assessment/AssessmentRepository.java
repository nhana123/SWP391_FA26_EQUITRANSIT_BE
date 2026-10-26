/*
 * AssessmentRepository
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Persist assessment history and current case responsibility.
 */
package com.equitransit.backend.repository.assessment;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.equitransit.backend.entity.assessment.CaseAssignmentRecord;
import com.equitransit.backend.entity.assessment.CaseDecisionRecord;
import com.equitransit.backend.entity.assessment.CaseStaffRecord;
import com.equitransit.backend.entity.assessment.CaseSummaryRecord;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.compliance.ReviewState;


/** SQL adapter; all mutations require the service's parent-order lock. */
@Repository
@SuppressWarnings("null")
public class AssessmentRepository {

    private static final String SUMMARY = "o.OrderID,o.OrderNumber,"
            + "o.OrderStatus,o.RecordVersion,o.CurrentRevisionID,"
            + "o.OriginCountryCode,o.DestinationCountryCode,o.DeliveryDeadline";

    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc the shared transaction-aware SQL template */
    public AssessmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reads or locks a manager-visible parent.
     * @param id the parent order key
     * @param lock whether a mutation or revocable access check follows
     * @return the persisted parent metadata
     */
    public Optional<CaseOrder> order(long id, boolean lock) {
        return jdbc.query("SELECT OrderID,OrderNumber,OrderStatus,"
                + "RecordVersion,"
                + "CurrentRevisionID FROM dbo.TransportOrder"
                + (lock ? " WITH (UPDLOCK,HOLDLOCK)" : "")
                + " WHERE OrderID=:id", args(id), (row, index) ->
                new CaseOrder(row.getLong("OrderID"),
                        row.getString("OrderNumber"),
                        row.getString("OrderStatus"), version(row),
                        nullableLong(row, "CurrentRevisionID")))
                .stream().findFirst();
    }

    /**
     * Rechecks live staff status/role under a lock retained to commit.
     * @param staff the trusted or selected StaffID
     * @param role the service-required role
     * @return whether the profile is currently active and correctly typed
     */
    public boolean activeStaff(long staff, String role) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.StaffProfile s WITH (HOLDLOCK)"
                + " JOIN dbo.[User] u WITH (UPDLOCK,HOLDLOCK)"
                + " ON u.UserID=s.UserID JOIN dbo.[Role] r WITH (HOLDLOCK)"
                + " ON r.RoleID=u.RoleID WHERE s.StaffID=:staff"
                + " AND s.StaffStatus='ACTIVE'"
                + " AND u.Status='ACTIVE' AND r.RoleCode=:role",
                new MapSqlParameterSource("staff", staff)
                        .addValue("role", role), Integer.class);

        return count != null && count == 1;
    }

    /**
     * Checks responsibility from persisted current assignments, never JWT.
     * @param order the locked parent
     * @param staff the live staff key
     * @param role the expected preparation role
     * @return whether one active matching responsibility exists
     */
    public boolean assigned(long order, long staff, CaseRole role) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.OrderAssignment WHERE OrderID=:id"
                + " AND StaffID=:staff AND AssignmentRole=:role"
                + " AND EndedAt IS NULL", args(order).addValue("staff", staff)
                        .addValue("role", role.name()), Integer.class);

        return count != null && count == 1;
    }

    /**
     * Lists a bounded manager queue or the live staff's assigned queue.
     * @param state the optional manager queue state
     * @param staff the staff key or null for manager scope
     * @param role the live staff role or null for manager scope
     * @param page zero-based page
     * @param size bounded size
     * @return visible current summaries
     */
    public List<CaseSummaryRecord> queue(ReviewState state, Long staff,
            String role, int page, int size) {
        return jdbc.query("SELECT " + SUMMARY + " FROM dbo.TransportOrder o"
                + filter(staff) + " ORDER BY o.OrderID DESC"
                + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                queueArgs(state, staff, role).addValue("offset",
                        (long) page * size).addValue("size", size),
                (row, index) -> new CaseSummaryRecord(row.getLong("OrderID"),
                        row.getString("OrderNumber"),
                        row.getString("OrderStatus"), version(row),
                        nullableLong(row, "CurrentRevisionID"),
                        row.getString("OriginCountryCode"),
                        row.getString("DestinationCountryCode"),
                        time(row, "DeliveryDeadline")));
    }

    /**
     * Counts exactly the visible queue scope.
     * @param state optional manager filter
     * @param staff live staff key or null
     * @param role live role or null
     * @return visible total
     */
    public long queueCount(ReviewState state, Long staff, String role) {
        Long count = jdbc.queryForObject("SELECT COUNT_BIG(*)"
                + " FROM dbo.TransportOrder o" + filter(staff),
                queueArgs(state, staff, role), Long.class);

        return count == null ? 0L : count;
    }

    /**
     * Lists active eligible staff with workload, not invented availability.
     * @param role the required case role
     * @param page zero-based page
     * @param size bounded size
     * @return current staff directory
     */
    public List<CaseStaffRecord> staff(CaseRole role, int page, int size) {
        return jdbc.query("SELECT s.StaffID,s.FullName,r.RoleCode,"
                + " (SELECT COUNT_BIG(*) FROM dbo.OrderAssignment a"
                + " JOIN dbo.TransportOrder o ON o.OrderID=a.OrderID"
                + " WHERE a.StaffID=s.StaffID AND a.EndedAt IS NULL"
                + " AND o.OrderStatus='APPROVED') AS ActiveCases"
                + " FROM dbo.StaffProfile s JOIN dbo.[User] u"
                + " ON u.UserID=s.UserID JOIN dbo.[Role] r ON r.RoleID=u.RoleID"
                + " WHERE u.Status='ACTIVE' AND r.RoleCode=:role"
                + " ORDER BY s.StaffID OFFSET :offset ROWS"
                + " FETCH NEXT :size ROWS ONLY",
                new MapSqlParameterSource("role", role.name())
                        .addValue("offset", (long) page * size)
                        .addValue("size", size),
                (row, index) -> new CaseStaffRecord(row.getLong("StaffID"),
                        row.getString("FullName"), row.getString("RoleCode"),
                        row.getLong("ActiveCases")));
    }

    /**
     * Counts active eligible staff without exposing account details.
     * @param role the required case role
     * @return eligible total
     */
    public long staffCount(CaseRole role) {
        Long count = jdbc.queryForObject("SELECT COUNT_BIG(*)"
                + " FROM dbo.StaffProfile s JOIN dbo.[User] u"
                + " ON u.UserID=s.UserID JOIN dbo.[Role] r ON r.RoleID=u.RoleID"
                + " WHERE u.Status='ACTIVE' AND r.RoleCode=:role",
                new MapSqlParameterSource("role", role.name()), Long.class);

        return count == null ? 0L : count;
    }

    /**
     * Returns the single immutable assessment for a submitted revision.
     * @param revision exact revision key
     * @return decision or empty before assessment
     */
    public Optional<CaseDecisionRecord> decision(long revision) {
        return jdbc.query("SELECT * FROM dbo.OrderDecision"
                + " WHERE RevisionID=:id", args(revision),
                (row, index) -> decision(row)).stream().findFirst();
    }

    /**
     * Reads bounded history through an already authorized parent.
     * @param order authorized order key
     * @param page zero-based page
     * @param size bounded size
     * @return historical decisions, newest first
     */
    public List<CaseDecisionRecord> decisions(long order, int page,
            int size) {
        return jdbc.query("SELECT d.* FROM dbo.OrderDecision d"
                + " JOIN dbo.OrderRevision r ON r.RevisionID=d.RevisionID"
                + " WHERE r.OrderID=:id ORDER BY d.DecisionID DESC"
                + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                args(order).addValue("offset", (long) page * size)
                        .addValue("size", size), (row, index) -> decision(row));
    }

    /**
     * Reads active responsibility or bounded history.
     * @param order authorized parent
     * @param activeOnly whether ended rows are excluded
     * @param page zero-based page
     * @param size bounded size
     * @return immutable assignment views
     */
    public List<CaseAssignmentRecord> assignments(long order,
            boolean activeOnly, int page, int size) {
        return jdbc.query("SELECT * FROM dbo.OrderAssignment WHERE OrderID=:id"
                + (activeOnly ? " AND EndedAt IS NULL" : "")
                + " ORDER BY AssignmentID DESC OFFSET :offset ROWS"
                + " FETCH NEXT :size ROWS ONLY",
                args(order).addValue("offset", (long) page * size)
                        .addValue("size", size), (row, index) ->
                new CaseAssignmentRecord(row.getLong("AssignmentID"),
                        row.getLong("StaffID"),
                        row.getString("AssignmentRole"),
                        time(row, "AssignedAt"), time(row, "EndedAt"),
                        nullableLong(row, "AssignedBy"), time(row, "DueAt"),
                        row.getString("AssignmentNote")));
    }

    /**
     * Appends one assessment without modifying its history later.
     * @param revision reviewed revision
     * @param manager authenticated Manager StaffID
     * @param decision validated CHECK code
     * @param reason normalized optional reason
     */
    public void insertDecision(long revision, long manager, String decision,
            String reason) {
        jdbc.update("INSERT dbo.OrderDecision"
                + " (RevisionID,ManagerID,Decision,Reason) VALUES"
                + " (:id,:manager,:decision,:reason)", args(revision)
                        .addValue("manager", manager)
                        .addValue("decision", decision)
                        .addValue("reason", reason));
    }

    /**
     * Ends responsibility without deleting audit or assignment history.
     * @param order locked order
     * @param role role to end, null for all case roles
     * @return number of ended rows
     */
    public int end(long order, CaseRole role) {
        return jdbc.update("UPDATE dbo.OrderAssignment"
                + " SET EndedAt=SYSUTCDATETIME() WHERE OrderID=:id"
                + " AND EndedAt IS NULL"
                + (role == null ? "" : " AND AssignmentRole=:role"),
                args(order).addValue("role", role == null ? null
                        : role.name()));
    }

    /**
     * Appends new responsibility after any old role holder was ended.
     * @param order locked order
     * @param role validated preparation role
     * @param staff active staff of that role
     * @param manager authenticated Manager StaffID
     * @param dueAt optional UTC deadline
     * @param note optional staff-only note
     */
    public void assign(long order, CaseRole role, long staff, long manager,
            OffsetDateTime dueAt, String note) {
        jdbc.update("INSERT dbo.OrderAssignment (OrderID,StaffID,"
                + "AssignmentRole,AssignedBy,DueAt,AssignmentNote)"
                + " VALUES (:id,:staff,:role,:manager,:due,:note)", args(order)
                        .addValue("staff", staff).addValue("role", role.name())
                        .addValue("manager", manager)
                        .addValue("due", dueAt == null ? null
                                : dueAt.toLocalDateTime())
                        .addValue("note", note));
    }

    /**
     * Advances the parent rowversion while checking the locked old value.
     * @param order locked metadata
     * @param state service-owned next state
     * @return successful version comparison
     */
    public boolean transition(CaseOrder order, String state) {
        return jdbc.update("UPDATE dbo.TransportOrder SET OrderStatus=:state,"
                + "UpdatedAt=SYSUTCDATETIME() WHERE OrderID=:id"
                + " AND RecordVersion=:version", args(order.orderId())
                        .addValue("state", state).addValue("version",
                                HexFormat.of().parseHex(order.version()))) == 1;
    }

    private String filter(Long staff) {
        return staff == null ? " WHERE o.CurrentRevisionID IS NOT NULL"
                + " AND (:state IS NULL OR o.OrderStatus=:state)"
                : " WHERE o.OrderStatus='APPROVED' AND EXISTS"
                + " (SELECT 1 FROM dbo.OrderAssignment a"
                + " WHERE a.OrderID=o.OrderID AND a.StaffID=:staff"
                + " AND a.AssignmentRole=:role AND a.EndedAt IS NULL)";
    }

    private MapSqlParameterSource queueArgs(ReviewState state, Long staff,
            String role) {
        return new MapSqlParameterSource("state", state == null ? null
                : state.name()).addValue("staff", staff).addValue("role", role);
    }

    private MapSqlParameterSource args(long id) {
        return new MapSqlParameterSource("id", id);
    }

    private CaseDecisionRecord decision(ResultSet row) throws SQLException {
        return new CaseDecisionRecord(row.getLong("DecisionID"),
                row.getLong("RevisionID"), row.getLong("ManagerID"),
                row.getString("Decision"), row.getString("Reason"),
                time(row, "DecidedAt"));
    }

    private String version(ResultSet row) throws SQLException {
        return HexFormat.of().formatHex(row.getBytes("RecordVersion"));
    }

    private Long nullableLong(ResultSet row, String name) throws SQLException {
        long value = row.getLong(name);

        return row.wasNull() ? null : value;
    }

    private OffsetDateTime time(ResultSet row, String name)
            throws SQLException {
        return row.getTimestamp(name) == null ? null
                : row.getTimestamp(name).toLocalDateTime()
                        .atOffset(ZoneOffset.UTC);
    }
}
