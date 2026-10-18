/*
 * StaffAdministrationRepository
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized SQL for atomic internal staff administration.
 */
package com.equitransit.backend.repository;

import com.equitransit.backend.dto.staff.StaffModels.StaffProfileInput;
import com.equitransit.backend.dto.staff.StaffModels.StaffRole;
import com.equitransit.backend.dto.staff.StaffModels.StaffStatus;
import com.equitransit.backend.dto.staff.StaffModels.StaffView;
import com.equitransit.backend.exception.OrderException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** No writes occur outside the service's transaction and global admin lock. */
@Repository
public class StaffAdministrationRepository {
    private static final String FROM = """
            FROM dbo.StaffProfile s JOIN dbo.[User] u ON u.UserID=s.UserID
            JOIN dbo.Role r ON r.RoleID=u.RoleID
            """;
    private static final String INTERNAL = """
            r.RoleCode IN ('MANAGER','SPECIALIST','COORDINATOR','DRIVER')
            """;
    private static final String VIEW = """
            SELECT s.StaffID,s.UserID,s.FullName,s.Phone,s.EmployeeCode,
                s.Department,s.Specialization,s.RecordVersion,s.StaffStatus,
                u.Email,u.Status AS AccountStatus,r.RoleCode,
                (SELECT COUNT_BIG(*) FROM dbo.OrderAssignment a
                    WHERE a.StaffID=s.StaffID AND a.EndedAt IS NULL) AS Orders,
                (SELECT COUNT_BIG(*) FROM dbo.TripAssignment t
                    JOIN dbo.TripLeg l ON l.LegID=t.LegID
                    WHERE t.StaffID=s.StaffID
                    AND t.Status NOT IN ('INACTIVE','CANCELLED','COMPLETED')
                    AND l.LegStatus NOT IN ('CANCELLED','COMPLETED')) AS Trips
            """;
    private final NamedParameterJdbcTemplate jdbc;

    /** @param source same data source and transaction manager as auth */
    public StaffAdministrationRepository(DataSource source) {
        jdbc = new NamedParameterJdbcTemplate(source);
        jdbc.getJdbcTemplate().setQueryTimeout(10);
    }

    /** @return whether the additive deployment contract is available */
    public boolean ready() {
        return Boolean.TRUE.equals(jdbc.getJdbcTemplate().queryForObject("""
                SELECT CAST(CASE WHEN
                    COL_LENGTH('dbo.StaffProfile','StaffStatus') IS NOT NULL
                    AND COL_LENGTH('dbo.StaffProfile','EmployeeCode')
                        IS NOT NULL
                    AND COL_LENGTH('dbo.StaffProfile','Department') IS NOT NULL
                    AND COL_LENGTH('dbo.StaffProfile','Specialization')
                        IS NOT NULL
                    AND COL_LENGTH('dbo.StaffProfile','RecordVersion')
                        IS NOT NULL
                    AND COL_LENGTH('dbo.AuditEvent','EventKey') IS NOT NULL
                    AND OBJECT_ID('dbo.StaffAdminOperation','U') IS NOT NULL
                    AND EXISTS (SELECT 1 FROM sys.indexes WHERE
                        object_id=OBJECT_ID('dbo.StaffProfile')
                        AND name='UQ_StaffProfile_EmployeeCode'
                        AND is_unique=1 AND is_disabled=0)
                    AND EXISTS (SELECT 1 FROM sys.check_constraints WHERE
                        name='CK_StaffProfile_StaffStatus'
                        AND is_disabled=0 AND is_not_trusted=0)
                    THEN 1 ELSE 0 END AS bit)
                """, Boolean.class));
    }

    /** Serialize role/status changes, including concurrent last Managers. */
    public void lock() {
        Integer result = jdbc.getJdbcTemplate().queryForObject("""
                DECLARE @result int;
                EXEC @result=sys.sp_getapplock
                    @Resource='EquiTransit:staff-admin',
                    @LockMode='Exclusive',@LockOwner='Transaction',
                    @LockTimeout=5000;
                SELECT @result;
                """, Integer.class);
        if (result == null || result < 0) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "STAFF_ADMIN_BUSY", "Retry the staff command.");
        }
    }

    /** @param user live authenticated UserID @return active Manager StaffID */
    public long manager(long user) {
        var rows = jdbc.queryForList("""
                SELECT s.StaffID FROM dbo.[User] u WITH (UPDLOCK,HOLDLOCK)
                JOIN dbo.StaffProfile s WITH (UPDLOCK,HOLDLOCK)
                    ON s.UserID=u.UserID
                JOIN dbo.Role r ON r.RoleID=u.RoleID
                WHERE u.UserID=:user AND u.Status='ACTIVE'
                    AND s.StaffStatus='ACTIVE' AND r.RoleCode='MANAGER'
                """, Map.of("user", user), Long.class);
        if (rows.isEmpty()) {
            throw new OrderException(HttpStatus.FORBIDDEN,
                    "ACTIVE_MANAGER_REQUIRED", "Active Manager required.");
        }
        return rows.getFirst();
    }

    /** @param staff domain key @return current private view or 404 */
    public StaffView get(long staff) {
        var rows = jdbc.query(VIEW + FROM + " WHERE s.StaffID=:id AND "
                + INTERNAL, Map.of("id", staff), this::view);
        if (rows.isEmpty()) {
            throw new OrderException(HttpStatus.NOT_FOUND,
                    "STAFF_NOT_FOUND", "Staff profile was not found.");
        }
        return rows.getFirst();
    }

    /** @param staff serialize against F04/F10 live account validation */
    public void lockTarget(long staff) {
        jdbc.queryForList("""
                SELECT u.UserID FROM dbo.[User] u WITH (UPDLOCK,HOLDLOCK)
                JOIN dbo.StaffProfile s WITH (UPDLOCK,HOLDLOCK)
                    ON s.UserID=u.UserID WHERE s.StaffID=:id
                """, Map.of("id", staff), Long.class);
    }

    /** @param role optional filter @param status optional filter
     * @param page zero based @param size bounded @return one SQL page */
    public List<StaffView> list(StaffRole role, StaffStatus status,
            int page, int size) {
        var args = filters(role, status).addValue("offset", page * size)
                .addValue("size", size);
        return jdbc.query(VIEW + FROM + filterSql() + """
                 ORDER BY s.StaffID OFFSET :offset ROWS
                FETCH NEXT :size ROWS ONLY
                """, args, this::view);
    }

    /** @param role optional filter @param status optional account filter
     * @return total matching profiles */
    public long count(StaffRole role, StaffStatus status) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) " + FROM + filterSql(),
                filters(role, status), Long.class);
    }

    /** @param p normalized profile @param except existing UserID, or zero
     * @return duplicate email or employee code exists */
    public boolean duplicate(StaffProfileInput p, long except) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.[User] u
                LEFT JOIN dbo.StaffProfile s ON s.UserID=u.UserID
                WHERE u.UserID<>:except AND
                    (LOWER(u.Email)=:email OR s.EmployeeCode=:employee)
                """, profile(p).addValue("except", except), Integer.class) > 0;
    }

    /** @param p normalized profile @param hash adaptive encoded password
     * @return inserted StaffID; account/profile commit or roll back together */
    public long create(StaffProfileInput p, String hash) {
        return jdbc.queryForObject("""
                DECLARE @users TABLE (ID bigint);
                INSERT dbo.[User](RoleID,Email,PasswordHash,Status)
                OUTPUT INSERTED.UserID INTO @users
                SELECT RoleID,:email,:hash,'ACTIVE' FROM dbo.Role
                    WHERE RoleCode=:role;
                DECLARE @staff TABLE (ID bigint);
                INSERT dbo.StaffProfile(UserID,FullName,Phone,EmployeeCode,
                    Department,Specialization,StaffStatus)
                OUTPUT INSERTED.StaffID INTO @staff
                SELECT ID,:name,:phone,:employee,:department,:specialization,
                    'ACTIVE' FROM @users;
                SELECT ID FROM @staff;
                """, profile(p).addValue("hash", hash), Long.class);
    }

    /** @param id target @param p replacement @param version expected CAS */
    public void update(long id, StaffProfileInput p, String version) {
        var args = profile(p).addValue("id", id)
                .addValue("version", version);
        cas(jdbc.update("""
                UPDATE dbo.StaffProfile SET FullName=:name,Phone=:phone,
                    EmployeeCode=:employee,Department=:department,
                    Specialization=:specialization
                WHERE StaffID=:id AND RecordVersion=
                    CONVERT(binary(8),:version,2)
                """, args));
        jdbc.update("""
                UPDATE u SET Email=:email,RoleID=r.RoleID
                FROM dbo.[User] u JOIN dbo.StaffProfile s ON s.UserID=u.UserID
                CROSS JOIN dbo.Role r
                WHERE s.StaffID=:id AND r.RoleCode=:role
                """, args);
    }

    /** @param id target @param status synchronized account/profile state
     * @param version expected target rowversion */
    public void status(long id, StaffStatus status, String version) {
        var args = Map.of("id", id, "status", status.name(),
                "version", version);
        cas(jdbc.update("""
                UPDATE dbo.StaffProfile SET StaffStatus=:status
                WHERE StaffID=:id AND RecordVersion=
                    CONVERT(binary(8),:version,2)
                """, args));
        jdbc.update("""
                UPDATE u SET Status=:status FROM dbo.[User] u
                JOIN dbo.StaffProfile s ON s.UserID=u.UserID
                WHERE s.StaffID=:id
                """, args);
    }

    /** @param except Manager being demoted/disabled @return others active */
    public long otherManagers(long except) {
        return jdbc.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.[User] u WITH (UPDLOCK,HOLDLOCK)
                JOIN dbo.StaffProfile s WITH (UPDLOCK,HOLDLOCK)
                    ON s.UserID=u.UserID
                JOIN dbo.Role r ON r.RoleID=u.RoleID
                WHERE r.RoleCode='MANAGER' AND u.Status='ACTIVE'
                    AND s.StaffStatus='ACTIVE' AND s.StaffID<>:id
                """, Map.of("id", except), Long.class);
    }

    /** @param id staff @param role requested role @return incompatible work */
    public long incompatible(long id, StaffRole role) {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT_BIG(*) FROM dbo.OrderAssignment
                    WITH (UPDLOCK,HOLDLOCK)
                    WHERE StaffID=:id AND EndedAt IS NULL
                    AND AssignmentRole<>:role)
                + (SELECT COUNT_BIG(*) FROM dbo.TripAssignment t
                    WITH (UPDLOCK,HOLDLOCK)
                    JOIN dbo.TripLeg l ON l.LegID=t.LegID
                    WHERE t.StaffID=:id AND t.RoleInTrip<>:role
                    AND t.Status NOT IN ('INACTIVE','CANCELLED','COMPLETED')
                    AND l.LegStatus NOT IN ('COMPLETED','CANCELLED'))
                """, Map.of("id", id, "role", role.name()), Long.class);
    }

    /** @param id staff @return whether reassignment is required first */
    public boolean driving(long id) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.TripAssignment t
                    WITH (UPDLOCK,HOLDLOCK)
                JOIN dbo.TripLeg l WITH (UPDLOCK,HOLDLOCK) ON l.LegID=t.LegID
                WHERE t.StaffID=:id AND t.RoleInTrip='DRIVER'
                    AND l.LegStatus='IN_TRANSIT'
                    AND t.Status NOT IN ('INACTIVE','CANCELLED','COMPLETED')
                """, Map.of("id", id), Integer.class) > 0;
    }

    /** @param id disabled staff; preserve all historical rows */
    public void endAssignments(long id) {
        jdbc.update("""
                UPDATE dbo.OrderAssignment SET EndedAt=SYSUTCDATETIME()
                WHERE StaffID=:id AND EndedAt IS NULL
                """, Map.of("id", id));
        jdbc.update("""
                UPDATE t SET Status='INACTIVE' FROM dbo.TripAssignment t
                JOIN dbo.TripLeg l ON l.LegID=t.LegID
                WHERE t.StaffID=:id
                    AND t.Status NOT IN ('INACTIVE','CANCELLED','COMPLETED')
                    AND l.LegStatus NOT IN ('COMPLETED','CANCELLED')
                """, Map.of("id", id));
    }

    /** @param user actor @param op UUID @return receipt if committed */
    public List<Map<String, Object>> operation(long user, UUID op) {
        return jdbc.queryForList("""
                SELECT RequestHash,ReceiptJSON FROM dbo.StaffAdminOperation
                WHERE UserID=:user AND OperationID=:op
                """, Map.of("user", user, "op", op.toString()));
    }

    /** @param user actor @param op UUID @param hash keyed request digest
     * @param receipt safe immutable response, no initial credentials */
    public void save(long user, UUID op, String hash, String receipt) {
        jdbc.update("""
                INSERT dbo.StaffAdminOperation
                    (UserID,OperationID,RequestHash,ReceiptJSON)
                VALUES(:user,:op,:hash,:receipt)
                """, Map.of("user", user, "op", op.toString(),
                        "hash", hash, "receipt", receipt));
    }

    private String filterSql() {
        return " WHERE " + INTERNAL + " AND (:role IS NULL OR "
                + "r.RoleCode=:role) AND (:status IS NULL OR u.Status=:status)";
    }

    private MapSqlParameterSource filters(StaffRole role, StaffStatus status) {
        return new MapSqlParameterSource("role", role == null ? null
                : role.name()).addValue("status", status == null ? null
                        : status.name());
    }

    private MapSqlParameterSource profile(StaffProfileInput p) {
        return new MapSqlParameterSource("email",
                p.email().strip().toLowerCase(Locale.ROOT))
                .addValue("name", p.fullName().strip())
                .addValue("phone", p.phone()).addValue("role", p.role().name())
                .addValue("employee",
                        p.employeeCode().strip().toUpperCase(Locale.ROOT))
                .addValue("department", p.department())
                .addValue("specialization", p.specialization());
    }

    private StaffView view(ResultSet r, int row) throws SQLException {
        long orders = r.getLong("Orders");
        long trips = r.getLong("Trips");
        return new StaffView(r.getLong("StaffID"), r.getLong("UserID"),
                r.getString("Email"), r.getString("FullName"),
                r.getString("Phone"),
                StaffRole.valueOf(r.getString("RoleCode")),
                StaffStatus.valueOf(r.getString("AccountStatus")),
                StaffStatus.valueOf(r.getString("StaffStatus")),
                r.getString("EmployeeCode"), r.getString("Department"),
                r.getString("Specialization"), HexFormat.of().withUpperCase()
                        .formatHex(r.getBytes("RecordVersion")),
                orders, trips, orders + trips);
    }

    private void cas(int changed) {
        if (changed != 1) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "STAFF_VERSION_CONFLICT", "Reload the staff profile.");
        }
    }
}
