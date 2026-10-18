/*
 * LegMilestoneLogRepository
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized JDBC repository for transport leg milestones.
 */
package com.equitransit.backend.repository.execution;

import com.equitransit.backend.entity.execution.LegMilestoneLog;
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

/** JDBC repository for discrete transport leg milestone progression. */
@Repository
public class LegMilestoneLogRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates milestone log repository with query timeout.
     * @param source shared transactional datasource
     */
    public LegMilestoneLogRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        this.jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return true when LegMilestoneLog table and columns are available */
    public boolean ready() {
        String sql = "SELECT CASE WHEN OBJECT_ID('dbo.LegMilestoneLog','U') "
                + "IS NOT NULL AND (SELECT COUNT(*) FROM sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name IN "
                + "('MilestoneID','LegID','ReportedBy','MilestoneType',"
                + "'OccurredAt','RecordedAt','Latitude','Longitude','Notes',"
                + "'SourceCode','SourceReference','LocationDescription',"
                + "'EstimatedArrivalAt','DelayReason','EvidenceFileKey'))=15 "
                + "THEN 1 ELSE 0 END";

        return Integer.valueOf(1).equals(jdbc.getJdbcTemplate()
                .queryForObject(sql, Integer.class));
    }

    /**
     * Inserts a new leg milestone and returns its generated identifier.
     * @param m milestone model
     * @return generated MilestoneID
     */
    public long save(LegMilestoneLog m) {
        String sql = "INSERT INTO dbo.LegMilestoneLog (LegID, ReportedBy, "
                + "MilestoneType, OccurredAt, RecordedAt, Latitude, Longitude, "
                + "Notes, SourceCode, SourceReference, LocationDescription, "
                + "EstimatedArrivalAt, DelayReason, EvidenceFileKey) "
                + "OUTPUT INSERTED.MilestoneID VALUES (:legId, :reportedBy, "
                + ":milestoneType, :occurredAt, :recordedAt, :latitude, "
                + ":longitude, :notes, :sourceCode, :sourceReference, "
                + ":locationDescription, :estimatedArrivalAt, :delayReason, "
                + ":evidenceFileKey)";

        var params = new MapSqlParameterSource()
                .addValue("legId", m.getLegId())
                .addValue("reportedBy", m.getReportedBy())
                .addValue("milestoneType", m.getMilestoneType())
                .addValue("occurredAt", utc(m.getOccurredAt()))
                .addValue("recordedAt", utc(m.getRecordedAt() == null
                        ? OffsetDateTime.now(ZoneOffset.UTC)
                        : m.getRecordedAt()))
                .addValue("latitude", m.getLatitude())
                .addValue("longitude", m.getLongitude())
                .addValue("notes", m.getNotes())
                .addValue("sourceCode", m.getSourceCode())
                .addValue("sourceReference", m.getSourceReference())
                .addValue("locationDescription", m.getLocationDescription())
                .addValue("estimatedArrivalAt", utc(m.getEstimatedArrivalAt()))
                .addValue("delayReason", m.getDelayReason())
                .addValue("evidenceFileKey", m.getEvidenceFileKey());

        Long id = jdbc.queryForObject(sql, params, Long.class);

        m.setMilestoneId(id);
        return id == null ? 0L : id;
    }

    /**
     * Retrieves a milestone by its primary key.
     * @param milestoneId primary key
     * @return milestone model if present
     */
    public Optional<LegMilestoneLog> findById(long milestoneId) {
        String sql = "SELECT * FROM dbo.LegMilestoneLog WHERE "
                + "MilestoneID=:milestoneId";
        var list = jdbc.query(sql, Map.of("milestoneId", milestoneId),
                this::mapRow);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Retrieves all milestones for a leg in chronological order.
     * @param legId trip leg PK
     * @return list of milestones ordered by occurredAt asc
     */
    public List<LegMilestoneLog> findByLegId(long legId) {
        String sql = "SELECT * FROM dbo.LegMilestoneLog WHERE LegID=:legId "
                + "ORDER BY OccurredAt ASC, MilestoneID ASC";

        return jdbc.query(sql, Map.of("legId", legId), this::mapRow);
    }

    /**
     * Retrieves the latest milestone logged for a leg.
     * @param legId trip leg PK
     * @return latest milestone if present
     */
    public Optional<LegMilestoneLog> findLatestByLegId(long legId) {
        String sql = "SELECT TOP (1) * FROM dbo.LegMilestoneLog WHERE "
                + "LegID=:legId ORDER BY OccurredAt DESC, MilestoneID DESC";
        var list = jdbc.query(sql, Map.of("legId", legId), this::mapRow);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Counts milestones for a given leg.
     * @param legId trip leg PK
     * @return count of milestones
     */
    public int countByLegId(long legId) {
        String sql = "SELECT COUNT(*) FROM dbo.LegMilestoneLog "
                + "WHERE LegID=:legId";
        Integer count = jdbc.queryForObject(sql, Map.of("legId", legId),
                Integer.class);

        return count == null ? 0 : count;
    }

    private LegMilestoneLog mapRow(ResultSet r, int rowNum)
            throws SQLException {
        var m = new LegMilestoneLog();

        m.setMilestoneId(r.getLong("MilestoneID"));
        m.setLegId(r.getLong("LegID"));
        m.setReportedBy(r.getLong("ReportedBy"));
        m.setMilestoneType(r.getString("MilestoneType"));
        m.setOccurredAt(time(r, "OccurredAt"));
        m.setRecordedAt(time(r, "RecordedAt"));
        m.setLatitude(r.getBigDecimal("Latitude"));
        m.setLongitude(r.getBigDecimal("Longitude"));
        m.setNotes(r.getString("Notes"));
        m.setSourceCode(r.getString("SourceCode"));
        m.setSourceReference(r.getString("SourceReference"));
        m.setLocationDescription(r.getString("LocationDescription"));
        m.setEstimatedArrivalAt(time(r, "EstimatedArrivalAt"));
        m.setDelayReason(r.getString("DelayReason"));
        m.setEvidenceFileKey(r.getString("EvidenceFileKey"));
        return m;
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
