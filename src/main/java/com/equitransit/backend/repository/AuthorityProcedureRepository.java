/*
 * AuthorityProcedureRepository
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: SQL adapter for AuthorityProcedure table.
 */
package com.equitransit.backend.repository;


import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.entity.AuthorityProcedure;


/** SQL adapter for the existing AuthorityProcedure table. */
@Repository
public class AuthorityProcedureRepository extends ComplianceJdbcSupport {

    /** @param jdbc the transaction-aware SQL template */
    public AuthorityProcedureRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /** @param id the row key
     * @return the matching row
     */
    public Optional<AuthorityProcedure> findById(long id) {
        return find("AuthorityProcedure", "ProcedureID", id,
                ComplianceRows.authorityProcedure());
    }

    /** @param revision the authorized revision
     * @return its procedure history
     */
    public List<AuthorityProcedure> findByRevisionId(long revision) {
        return list("AuthorityProcedure", "RevisionID=:id",
                args("id", revision),
                ComplianceRows.authorityProcedure(), "ProcedureID");
    }

    /**
     * Returns a bounded page of procedures ordered by ProcedureID.
     * @param revision the current revision key
     * @param page zero-based page index
     * @param size number of rows per page (max 100)
     * @return one page of authority procedures
     */
    public List<AuthorityProcedure> findByRevisionIdPaged(
            long revision, int page, int size) {
        return jdbc.query(
                "SELECT * FROM dbo.AuthorityProcedure"
                + " WHERE RevisionID=:rev"
                + " ORDER BY ProcedureID"
                + " OFFSET :off ROWS FETCH NEXT :sz ROWS ONLY",
                args("rev", revision)
                        .addValue("off", (long) page * size)
                        .addValue("sz", size),
                ComplianceRows.authorityProcedure());
    }

    /**
     * Counts all procedures for the revision; used for pagination totals.
     * @param revision the current revision key
     * @return total row count
     */
    public long countByRevisionId(long revision) {
        return count("SELECT COUNT(*) FROM dbo.AuthorityProcedure"
                + " WHERE RevisionID=:rev", args("rev", revision));
    }

    /** @param procedure the procedure key
     * @param status the validated external-result state
     */
    public void updateStatus(long procedure, String status) {
        updateStatus(procedure, status, null);
    }

    /**
     * Updates approval status and decision timestamp.
     * @param procedure the procedure key
     * @param status the validated external-result state
     * @param decidedAt the decision timestamp or null
     */
    public void updateStatus(long procedure, String status,
            OffsetDateTime decidedAt) {
        require(jdbc.update("UPDATE dbo.AuthorityProcedure"
                + " SET ApprovalStatus=:status,"
                + " DecidedAt=COALESCE(:decidedAt, DecidedAt)"
                + " WHERE ProcedureID=:id",
                args("id", procedure).addValue("status", status)
                        .addValue("decidedAt", time(decidedAt))) == 1);
    }

    /** @param value the new row
     * @return its identity
     */
    public long insert(AuthorityProcedure value) {
        var params = new MapSqlParameterSource();

        params.addValue("RevisionID", value.getRevisionId());
        params.addValue("ProcedureCode", value.getProcedureCode());
        params.addValue("AuthorityName", value.getAuthorityName());
        params.addValue("ApplicationReference",
                value.getApplicationReference());
        params.addValue("ApprovalStatus", value.getApprovalStatus());
        params.addValue("EvidenceFileKey", value.getEvidenceFileKey());
        params.addValue("DecidedAt", time(value.getDecidedAt()));
        params.addValue("PhaseCode", value.getPhaseCode());
        params.addValue("AuthorityContact", value.getAuthorityContact());
        params.addValue("HoldReason", value.getHoldReason());
        params.addValue("ExpiresAt", time(value.getExpiresAt()));
        params.addValue("RecordedBy", value.getRecordedBy());
        return insert("AuthorityProcedure", "ProcedureID", params);
    }
}
