package com.equitransit.backend.repository.compliance;


import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import com.equitransit.backend.exception.compliance.ComplianceException;


/** Shared parameterized JDBC operations for F08 row adapters. */
abstract class ComplianceJdbcSupport {
    protected final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc the transaction-aware SQL template */
    protected ComplianceJdbcSupport(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    protected <T> List<T> list(String table, String filter,
            MapSqlParameterSource args, RowMapper<T> mapper, String key) {
        return jdbc.query("SELECT * FROM dbo." + table + " WHERE " + filter
                + " ORDER BY " + key, args, mapper);
    }

    protected <T> Optional<T> find(String table, String key, long id,
            RowMapper<T> mapper) {
        return jdbc.query("SELECT * FROM dbo." + table + " WHERE " + key
                + "=:id", args("id", id), mapper).stream().findFirst();
    }

    protected long insert(String table, String key,
            MapSqlParameterSource args) {
        String columns = String.join(",", args.getValues().keySet());
        String placeholders = args.getValues().keySet().stream()
                .map(name -> ":" + name).collect(Collectors.joining(","));
        var holder = new GeneratedKeyHolder();

        jdbc.update("INSERT INTO dbo." + table + " (" + columns + ") VALUES ("
                + placeholders + ")", args, holder, new String[]{key});
        Number identity = holder.getKey();

        if (identity == null) {
            throw new IllegalStateException("F08 identity unavailable.");
        }
        return identity.longValue();
    }

    protected long count(String sql, MapSqlParameterSource args) {
        Long result = jdbc.queryForObject(sql, args, Long.class);

        return result == null ? 0 : result;
    }

    protected static MapSqlParameterSource args(String key, Object value) {
        return new MapSqlParameterSource(key, value);
    }

    protected static Object time(OffsetDateTime value) {
        return value == null ? null
                : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    protected static Object now() {
        return time(OffsetDateTime.now(ZoneOffset.UTC).withNano(0));
    }

    protected static void require(boolean present) {
        if (!present) {
            throw new ComplianceException(HttpStatus.NOT_FOUND,
                    "COMPLIANCE_NOT_FOUND", "Compliance resource not found.");
        }
    }
}
