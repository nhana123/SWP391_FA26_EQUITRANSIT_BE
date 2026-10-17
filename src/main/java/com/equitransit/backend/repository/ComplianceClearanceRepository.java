/*
 * ComplianceClearanceRepository
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: SQL adapter for ComplianceClearance and quarantine status queries.
 */
package com.equitransit.backend.repository;


import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.entity.ComplianceClearance;
import com.equitransit.backend.entity.QuarantineAssessment;


/** SQL adapter for the existing ComplianceClearance table. */
@Repository
public class ComplianceClearanceRepository extends ComplianceJdbcSupport {

    /** @param jdbc the transaction-aware SQL template */
    public ComplianceClearanceRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /** @param id the row key
     * @return the matching row
     */
    public Optional<ComplianceClearance> findById(long id) {
        return find("ComplianceClearance", "ClearanceID", id,
                ComplianceRows.complianceClearance());
    }

    /** @param revision the authorized revision
     * @return its bounded phase-assessment history
     */
    public List<ComplianceClearance> findByRevisionId(long revision) {
        return list("ComplianceClearance", "RevisionID=:id",
                args("id", revision),
                ComplianceRows.complianceClearance(), "ClearanceID");
    }

    /** @param value the new row
     * @return its identity
     */
    public long insert(ComplianceClearance value) {
        var params = new MapSqlParameterSource();

        params.addValue("RevisionID", value.getRevisionId());
        params.addValue("SpecialistID", value.getSpecialistId());
        params.addValue("PhaseCode", value.getPhaseCode());
        params.addValue("Status", value.getStatus());
        params.addValue("CheckedAt", now());
        params.addValue("ExpiresAt", time(value.getExpiresAt()));
        params.addValue("AssessmentNotes", value.getAssessmentNotes());
        return insert("ComplianceClearance", "ClearanceID", params);
    }

    /**
     * Evaluates whether the CURRENT (latest) clearance for the phase is PASS
     * and has not expired. A newer FAIL or PENDING record immediately
     * supersedes and invalidates an older PASS record.
     * @param revision the current revision key
     * @param phase the phase code to check (PRE_EXPORT, TRANSIT, etc.)
     * @return true only when the latest clearance is an active non-expired PASS
     */
    public boolean hasValidPass(long revision, String phase) {
        var latest = jdbc.query(
                "SELECT TOP 1 Status, CheckedAt, ExpiresAt"
                + " FROM dbo.ComplianceClearance"
                + " WHERE RevisionID=:rev AND PhaseCode=:phase"
                + " ORDER BY CheckedAt DESC, ClearanceID DESC",
                args("rev", revision).addValue("phase", phase),
                (row, i) -> {
                    var c = new ComplianceClearance();
                    c.setStatus(row.getString("Status"));
                    c.setCheckedAt(row.getTimestamp("CheckedAt") == null ? null
                            : row.getTimestamp("CheckedAt").toLocalDateTime()
                                    .atOffset(ZoneOffset.UTC));
                    c.setExpiresAt(row.getTimestamp("ExpiresAt") == null ? null
                            : row.getTimestamp("ExpiresAt").toLocalDateTime()
                                    .atOffset(ZoneOffset.UTC));
                    return c;
                }).stream().findFirst();

        if (latest.isEmpty()) {
            return false;
        }
        var current = latest.get();
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);

        return "PASS".equals(current.getStatus())
                && current.getCheckedAt() != null
                && !current.getCheckedAt().isAfter(nowUtc)
                && current.getExpiresAt() != null
                && current.getExpiresAt().isAfter(nowUtc);
    }

    /**
     * Returns the active quarantine assessment for a revision.
     * Reads BE2 history first, then the confirmed BE1 contract.
     * @param revision the current revision key
     * @return the active assessment, or empty
     */
    public Optional<QuarantineAssessment> findLatestQuarantine(long revision) {
        String ownSql = "SELECT TOP 1 Applicability,RequiredDays,ValidUntil"
                + " FROM dbo.QuarantineAssessment WHERE RevisionID=:rev"
                + " AND SupersededAt IS NULL ORDER BY AssessmentID DESC";
        var own = jdbc.query(ownSql, args("rev", revision), (row, i) -> {
            var qa = new QuarantineAssessment();
            var expiry = row.getTimestamp("ValidUntil");
            qa.setApplicability(expiry != null && !expiry.toLocalDateTime()
                    .isAfter(java.time.LocalDateTime.now(ZoneOffset.UTC))
                    ? "PENDING" : row.getString("Applicability"));
            qa.setRequiredDays(row.getObject("RequiredDays", Integer.class));
            return qa;
        });
        if (!own.isEmpty()) {
            return Optional.of(own.getFirst());
        }
        String sql = "SELECT TOP 1 Applicability, RequiredDays"
                + " FROM dbo.RevisionQuarantineAssessment"
                + " WHERE RevisionID=:rev AND SupersededAt IS NULL"
                + " AND (Applicability = 'PENDING' OR ValidUntil > :now)"
                + " ORDER BY AssessedAt DESC, AssessmentID DESC";
        var params = args("rev", revision).addValue("now", now());
        return jdbc.query(sql, params,
                (row, i) -> {
                    var qa = new QuarantineAssessment();
                    qa.setApplicability(row.getString("Applicability"));
                    qa.setRequiredDays(
                            row.getObject("RequiredDays", Integer.class));
                    return qa;
                }).stream().findFirst();
    }
}
