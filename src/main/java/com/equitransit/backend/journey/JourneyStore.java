package com.equitransit.backend.journey;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * SQL adapter. Callers hold a transaction-scoped v2 lifecycle lock on writes.
 */
@Repository
public class JourneyStore {
    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc transaction-aware shared connection */
    public JourneyStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Serializes sandbox v2 mutations, including cross-booking resources. */
    public void lock() {
        // SQL Server implicit transactions start on table access, not SELECT 1.
        jdbc.getJdbcTemplate().queryForList("SELECT TOP(1) MigrationID"
                + " FROM dbo.SchemaMigrationHistory WITH (HOLDLOCK)");
        jdbc.getJdbcTemplate().execute("DECLARE @r int; EXEC @r="
                + "sys.sp_getapplock @Resource='EquiTransit:resources',"
                + "@LockMode='Exclusive',@LockOwner='Transaction',"
                + "@LockTimeout=10000; IF @r<0 THROW 51021,"
                + "'Resource lock unavailable.',1;");
        jdbc.getJdbcTemplate().execute("DECLARE @r int; EXEC @r="
                + "sys.sp_getapplock @Resource='JourneyV2Lifecycle',"
                + "@LockMode='Exclusive',@LockOwner='Transaction',"
                + "@LockTimeout=10000; IF @r<0 THROW 51021,"
                + "'Lifecycle lock unavailable.',1;");
    }

    /** @param sql internal constant SQL @param args bound values
     * @return detached row maps */
    public List<Map<String, Object>> rows(String sql, Map<String, ?> args) {
        return jdbc.queryForList(sql, args);
    }

    /** @param sql internal constant SQL @param args bound values
     * @return affected rows */
    public int write(String sql, Map<String, ?> args) {
        return jdbc.update(sql, args);
    }

    /** @param sql internal constant INSERT @param args bound values
     * @return generated identity */
    public long insert(String sql, Map<String, ?> args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource(args), keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Identity not returned.");
        }
        return key.longValue();
    }
}
