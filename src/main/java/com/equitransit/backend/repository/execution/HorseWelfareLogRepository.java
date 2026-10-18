/*
 * HorseWelfareLogRepository
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized JDBC repository for append-only horse welfare audits.
 */
package com.equitransit.backend.repository.execution;

import com.equitransit.backend.entity.execution.HorseWelfareLog;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Append-only audit repository for live horse welfare assessments. */
@Repository
public class HorseWelfareLogRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates welfare log repository with query timeout.
     * @param source shared transactional datasource
     */
    public HorseWelfareLogRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        this.jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return true when HorseWelfareLog table and constraints are ready */
    public boolean ready() {
        String sql = "SELECT CASE WHEN OBJECT_ID('dbo.HorseWelfareLog','U') "
                + "IS NOT NULL AND (SELECT COUNT(*) FROM sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name IN "
                + "('WelfareLogID','LegID','RecordedBy','CheckTime',"
                + "'HealthStatus','TemperatureCelsius','WaterIntakeLiters',"
                + "'BehaviorNotes','PhotoFileKey','FeedAmountKg','CareNotes',"
                + "'InstructionFileKey','SupersedesLogID'))=13 "
                + "AND (SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN "
                + "('FK_Figma_HorseWelfareLog_SupersedesLogID',"
                + "'FK_HorseWelfareLog_LegID','FK_HorseWelfareLog_RecordedBy')"
                + " AND is_disabled=0 AND is_not_trusted=0)=3 "
                + "AND (SELECT COUNT(*) FROM sys.check_constraints WHERE name "
                + "IN ('CK_Figma_HorseWelfareLog_FeedAmountKg',"
                + "'CK_Figma_HorseWelfareLog_Supersedes',"
                + "'CK_HorseWelfareLog_WaterIntakeLiters') AND is_disabled=0 "
                + "AND is_not_trusted=0)=3 THEN 1 ELSE 0 END";

        return Integer.valueOf(1).equals(jdbc.getJdbcTemplate()
                .queryForObject(sql, Integer.class));
    }

    /**
     * Inserts an immutable welfare log record and returns its PK.
     * @param w welfare model
     * @return generated WelfareLogID
     */
    public long save(HorseWelfareLog w) {
        String sql = "INSERT INTO dbo.HorseWelfareLog (LegID, RecordedBy, "
                + "CheckTime, HealthStatus, TemperatureCelsius, "
                + "WaterIntakeLiters, BehaviorNotes, PhotoFileKey, "
                + "FeedAmountKg, CareNotes, InstructionFileKey, "
                + "SupersedesLogID) OUTPUT INSERTED.WelfareLogID VALUES "
                + "(:legId, :recordedBy, :checkTime, :healthStatus, "
                + ":temperatureCelsius, :waterIntakeLiters, :behaviorNotes, "
                + ":photoFileKey, :feedAmountKg, :careNotes, "
                + ":instructionFileKey, :supersedesLogId)";

        var params = new MapSqlParameterSource()
                .addValue("legId", w.getLegId())
                .addValue("recordedBy", w.getRecordedBy())
                .addValue("checkTime", utc(w.getCheckTime()))
                .addValue("healthStatus", w.getHealthStatus())
                .addValue("temperatureCelsius", w.getTemperatureCelsius())
                .addValue("waterIntakeLiters", w.getWaterIntakeLiters())
                .addValue("behaviorNotes", w.getBehaviorNotes())
                .addValue("photoFileKey", w.getPhotoFileKey())
                .addValue("feedAmountKg", w.getFeedAmountKg())
                .addValue("careNotes", w.getCareNotes())
                .addValue("instructionFileKey", w.getInstructionFileKey())
                .addValue("supersedesLogId", w.getSupersedesLogId());

        Long id = jdbc.queryForObject(sql, params, Long.class);

        w.setWelfareLogId(id);
        return id == null ? 0L : id;
    }

    /**
     * Retrieves a welfare log by its primary key.
     * @param welfareLogId primary key
     * @return welfare log model if present
     */
    public Optional<HorseWelfareLog> findById(long welfareLogId) {
        String sql = "SELECT * FROM dbo.HorseWelfareLog WHERE "
                + "WelfareLogID=:welfareLogId";
        var list = jdbc.query(sql, Map.of("welfareLogId", welfareLogId),
                this::mapRow);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Retrieves all welfare logs for a leg (including superseded).
     * @param legId trip leg PK
     * @return list of welfare logs
     */
    public List<HorseWelfareLog> findByLegId(long legId) {
        String sql = "SELECT * FROM dbo.HorseWelfareLog WHERE LegID=:legId "
                + "ORDER BY CheckTime ASC, WelfareLogID ASC";

        return jdbc.query(sql, Map.of("legId", legId), this::mapRow);
    }

    /**
     * Retrieves active (non-superseded) welfare logs for a leg.
     * @param legId trip leg PK
     * @return active welfare logs ordered by checkTime asc
     */
    public List<HorseWelfareLog> findActiveByLegId(long legId) {
        String sql = "SELECT hw.* FROM dbo.HorseWelfareLog hw WHERE "
                + "hw.LegID=:legId AND NOT EXISTS (SELECT 1 FROM "
                + "dbo.HorseWelfareLog c WHERE "
                + "c.SupersedesLogID=hw.WelfareLogID AND c.LegID=hw.LegID) "
                + "ORDER BY hw.CheckTime ASC, hw.WelfareLogID ASC";

        return jdbc.query(sql, Map.of("legId", legId), this::mapRow);
    }

    /**
     * Retrieves the latest active welfare check for a leg.
     * @param legId trip leg PK
     * @return latest active welfare check if present
     */
    public Optional<HorseWelfareLog> findLatestActiveByLegId(long legId) {
        String sql = "SELECT TOP (1) hw.* FROM dbo.HorseWelfareLog hw WHERE "
                + "hw.LegID=:legId AND NOT EXISTS (SELECT 1 FROM "
                + "dbo.HorseWelfareLog c WHERE "
                + "c.SupersedesLogID=hw.WelfareLogID AND c.LegID=hw.LegID) "
                + "ORDER BY hw.CheckTime DESC, hw.WelfareLogID DESC";
        var list = jdbc.query(sql, Map.of("legId", legId), this::mapRow);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Traces the full audit chain for a welfare record on a leg.
     * @param legId trip leg PK
     * @param logId target welfare log PK
     * @return list of welfare logs in the audit chain ordered chronologically
     */
    public List<HorseWelfareLog> findAuditChain(long legId, long logId) {
        var all = findByLegId(legId);
        var targetOpt = all.stream()
                .filter(w -> w.getWelfareLogId() == logId).findFirst();

        if (targetOpt.isEmpty()) {
            return Collections.emptyList();
        }
        var chainIds = new HashSet<Long>();
        chainIds.add(logId);

        // Traverse upwards (ancestors via supersedesLogId)
        Long currentSupersedes = targetOpt.get().getSupersedesLogId();
        while (currentSupersedes != null
                && !chainIds.contains(currentSupersedes)) {
            chainIds.add(currentSupersedes);
            Long nextId = currentSupersedes;
            var parent = all.stream()
                    .filter(w -> w.getWelfareLogId().equals(nextId))
                    .findFirst();
            currentSupersedes = parent.map(HorseWelfareLog::getSupersedesLogId)
                    .orElse(null);
        }

        // Traverse downwards (descendants where supersedesLogId is in chain)
        boolean added;
        do {
            added = false;
            for (var w : all) {
                if (w.getSupersedesLogId() != null
                        && chainIds.contains(w.getSupersedesLogId())
                        && chainIds.add(w.getWelfareLogId())) {
                    added = true;
                }
            }
        } while (added);

        var result = new ArrayList<HorseWelfareLog>();
        for (var w : all) {
            if (chainIds.contains(w.getWelfareLogId())) {
                result.add(w);
            }
        }
        return result;
    }

    private HorseWelfareLog mapRow(ResultSet r, int rowNum)
            throws SQLException {
        var w = new HorseWelfareLog();

        w.setWelfareLogId(r.getLong("WelfareLogID"));
        w.setLegId(r.getLong("LegID"));
        w.setRecordedBy(r.getLong("RecordedBy"));
        w.setCheckTime(time(r, "CheckTime"));
        w.setHealthStatus(r.getString("HealthStatus"));
        w.setTemperatureCelsius(r.getBigDecimal("TemperatureCelsius"));
        w.setWaterIntakeLiters(r.getBigDecimal("WaterIntakeLiters"));
        w.setBehaviorNotes(r.getString("BehaviorNotes"));
        w.setPhotoFileKey(r.getString("PhotoFileKey"));
        w.setFeedAmountKg(r.getBigDecimal("FeedAmountKg"));
        w.setCareNotes(r.getString("CareNotes"));
        w.setInstructionFileKey(r.getString("InstructionFileKey"));
        w.setSupersedesLogId(number(r, "SupersedesLogID"));
        return w;
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
