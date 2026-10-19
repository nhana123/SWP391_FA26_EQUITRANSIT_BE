/*
 * IncidentReportRepository
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized JDBC repository for IncidentReport.
 */
package com.equitransit.backend.repository.incident;

import com.equitransit.backend.entity.incident.IncidentReport;
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

/** JDBC repository for unexpected incidents and welfare emergency reports. */
@Repository
public class IncidentReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates incident report repository with query timeout.
     * @param source shared transactional datasource
     */
    public IncidentReportRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        this.jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return true when IncidentReport table and columns are available */
    public boolean ready() {
        String sql = "SELECT CASE WHEN OBJECT_ID('dbo.IncidentReport','U') "
                + "IS NOT NULL AND (SELECT COUNT(*) FROM sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.IncidentReport') AND name IN "
                + "('IncidentID','LegID','ReportedBy','IncidentType',"
                + "'SeverityLevel','IncidentTime','Description','Status',"
                + "'ImmediateActionNotes','EmergencyContact','AcknowledgedBy',"
                + "'AcknowledgedAt','ResolvedBy','ResolvedAt',"
                + "'ResolutionNotes','EvidenceFilesJSON'))=16 "
                + "THEN 1 ELSE 0 END";

        return Integer.valueOf(1).equals(jdbc.getJdbcTemplate()
                .queryForObject(sql, Integer.class));
    }

    /**
     * Inserts an incident report and returns the generated identifier.
     * @param r incident entity
     * @return generated IncidentID
     */
    public long save(IncidentReport r) {
        String sql = "INSERT INTO dbo.IncidentReport (LegID, ReportedBy, "
                + "IncidentType, SeverityLevel, IncidentTime, Description, "
                + "Status, ImmediateActionNotes, EmergencyContact, "
                + "AcknowledgedBy, AcknowledgedAt, ResolvedBy, ResolvedAt, "
                + "ResolutionNotes, EvidenceFilesJSON) "
                + "OUTPUT INSERTED.IncidentID VALUES (:legId, :reportedBy, "
                + ":incidentType, :severityLevel, :incidentTime, :description, "
                + ":status, :immediateActionNotes, :emergencyContact, "
                + ":acknowledgedBy, :acknowledgedAt, :resolvedBy, :resolvedAt, "
                + ":resolutionNotes, :evidenceFilesJson)";

        var params = new MapSqlParameterSource()
                .addValue("legId", r.getLegId())
                .addValue("reportedBy", r.getReportedBy())
                .addValue("incidentType", r.getIncidentType())
                .addValue("severityLevel", r.getSeverityLevel())
                .addValue("incidentTime", utc(r.getIncidentTime() == null
                        ? OffsetDateTime.now(ZoneOffset.UTC)
                        : r.getIncidentTime()))
                .addValue("description", r.getDescription())
                .addValue("status", r.getStatus())
                .addValue("immediateActionNotes", r.getImmediateActionNotes())
                .addValue("emergencyContact", r.getEmergencyContact())
                .addValue("acknowledgedBy", r.getAcknowledgedBy())
                .addValue("acknowledgedAt", utc(r.getAcknowledgedAt()))
                .addValue("resolvedBy", r.getResolvedBy())
                .addValue("resolvedAt", utc(r.getResolvedAt()))
                .addValue("resolutionNotes", r.getResolutionNotes())
                .addValue("evidenceFilesJson", r.getEvidenceFilesJson());

        Long id = jdbc.queryForObject(sql, params, Long.class);

        r.setIncidentId(id);
        return id == null ? 0L : id;
    }

    /**
     * Finds incident by primary key.
     * @param incidentId primary key
     * @return incident entity if present
     */
    public Optional<IncidentReport> findById(long incidentId) {
        String sql = "SELECT * FROM dbo.IncidentReport WHERE "
                + "IncidentID=:incidentId";
        var list = jdbc.query(sql, Map.of("incidentId", incidentId),
                this::mapRow);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Lists all incidents for a leg.
     * @param legId leg foreign key
     * @return list of incidents ordered by incidentTime desc
     */
    public List<IncidentReport> findByLegId(long legId) {
        String sql = "SELECT * FROM dbo.IncidentReport WHERE LegID=:legId "
                + "ORDER BY IncidentTime DESC, IncidentID DESC";

        return jdbc.query(sql, Map.of("legId", legId), this::mapRow);
    }

    /**
     * Acknowledges an incident.
     * @param incidentId incident primary key
     * @param staffId acknowledging staff
     * @param acknowledgedAt timestamp
     */
    public void acknowledge(long incidentId, long staffId,
            OffsetDateTime acknowledgedAt) {
        String sql = "UPDATE dbo.IncidentReport SET Status='ACKNOWLEDGED', "
                + "AcknowledgedBy=:staffId, AcknowledgedAt=:acknowledgedAt "
                + "WHERE IncidentID=:incidentId AND Status='REPORTED'";

        jdbc.update(sql, Map.of("incidentId", incidentId, "staffId", staffId,
                "acknowledgedAt", utc(acknowledgedAt)));
    }

    /**
     * Resolves an incident with resolution notes.
     * @param incidentId incident primary key
     * @param staffId resolving staff
     * @param resolvedAt timestamp
     * @param resolutionNotes formal notes
     */
    public void resolve(long incidentId, long staffId,
            OffsetDateTime resolvedAt, String resolutionNotes) {
        String sql = "UPDATE dbo.IncidentReport SET Status='RESOLVED', "
                + "ResolvedBy=:staffId, ResolvedAt=:resolvedAt, "
                + "ResolutionNotes=:notes "
                + "WHERE IncidentID=:incidentId AND Status IN "
                + "('REPORTED','ACKNOWLEDGED')";

        jdbc.update(sql, Map.of("incidentId", incidentId, "staffId", staffId,
                "resolvedAt", utc(resolvedAt), "notes", resolutionNotes));
    }

    private IncidentReport mapRow(ResultSet rs, int rowNum)
            throws SQLException {
        var r = new IncidentReport();

        r.setIncidentId(rs.getLong("IncidentID"));
        r.setLegId(rs.getLong("LegID"));
        r.setReportedBy(rs.getLong("ReportedBy"));
        r.setIncidentType(rs.getString("IncidentType"));
        r.setSeverityLevel(rs.getString("SeverityLevel"));
        r.setIncidentTime(toUtc(rs.getObject("IncidentTime",
                LocalDateTime.class)));
        r.setDescription(rs.getString("Description"));
        r.setStatus(rs.getString("Status"));
        r.setImmediateActionNotes(rs.getString("ImmediateActionNotes"));
        r.setEmergencyContact(rs.getString("EmergencyContact"));
        r.setAcknowledgedBy(rs.getObject("AcknowledgedBy") != null
                ? rs.getLong("AcknowledgedBy") : null);
        r.setAcknowledgedAt(toUtc(rs.getObject("AcknowledgedAt",
                LocalDateTime.class)));
        r.setResolvedBy(rs.getObject("ResolvedBy") != null
                ? rs.getLong("ResolvedBy") : null);
        r.setResolvedAt(toUtc(rs.getObject("ResolvedAt", LocalDateTime.class)));
        r.setResolutionNotes(rs.getString("ResolutionNotes"));
        r.setEvidenceFilesJson(rs.getString("EvidenceFilesJSON"));
        return r;
    }

    private LocalDateTime utc(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private OffsetDateTime toUtc(LocalDateTime time) {
        return time == null ? null : time.atOffset(ZoneOffset.UTC);
    }
}
