package com.equitransit.backend.repository.compliance;


import java.time.LocalDate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.equitransit.backend.entity.compliance.DocumentRequirement;


/** SQL adapter for the existing DocumentRequirement table. */
@Repository
public class DocumentRequirementRepository extends ComplianceJdbcSupport {

    /** @param jdbc the transaction-aware SQL template */
    public DocumentRequirementRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /** @param id the row key
     * @return the matching row
     */
    public Optional<DocumentRequirement> findById(long id) {
        return find("DocumentRequirement", "RequirementID", id,
                ComplianceRows.documentRequirement());
    }

    /** @param revision the authorized revision
     * @return its bounded checklist
     */
    public List<DocumentRequirement> findByRevisionId(long revision) {
        return list("DocumentRequirement", "RevisionID=:id",
                args("id", revision), ComplianceRows.documentRequirement(),
                "RequirementID");
    }

    /** @param order the order key
     * @param customer the authenticated customer
     * @return the owner's exact current revision
     */
    public Optional<Long> currentOwnedRevisionId(long order, long customer) {
        return jdbc.query("SELECT CurrentRevisionID FROM "
                + "dbo.TransportOrder WITH (UPDLOCK,HOLDLOCK)"
                + " WHERE OrderID=:id AND CustomerID=:owner"
                + " AND CurrentRevisionID IS NOT NULL",
                args("id", order).addValue("owner", customer),
                (row, index) -> row.getLong(1)).stream().findFirst();
    }

    /** @param requirement the requirement key
     * @param status the service-controlled status
     */
    public void updateStatus(long requirement, String status) {
        require(jdbc.update("UPDATE dbo.DocumentRequirement SET Status=:status"
                + " WHERE RequirementID=:id", args("id", requirement)
                        .addValue("status", status)) == 1);
    }

    /** @param value the new row
     * @return its identity
     */
    public long insert(DocumentRequirement value) {
        var params = new MapSqlParameterSource();

        params.addValue("RevisionID", value.getRevisionId());
        params.addValue("RuleID", value.getRuleId());
        params.addValue("DocumentType", value.getDocumentType());
        params.addValue("PhaseCode", value.getPhaseCode());
        params.addValue("IsMandatory", value.isMandatory());
        params.addValue("DueAt", time(value.getDueAt()));
        params.addValue("Status", value.getStatus());
        params.addValue("Instructions", value.getInstructions());
        params.addValue("ScopeCode", value.getScopeCode());
        params.addValue("HorseID", value.getHorseId());
        params.addValue("ValidityConditionsJSON",
                value.getValidityConditionsJson());
        params.addValue("CaseJustification", value.getCaseJustification());
        return insert("DocumentRequirement", "RequirementID", params);
    }

    /**
     * Resolves the departure date for the given revision.
     * Database access errors propagate fail-closed without swallowing.
     * @param revision the revision key
     * @return the departure date if present and set, or empty
     */
    public Optional<LocalDate> findDepartureDate(long revision) {
        List<LocalDate> dates = jdbc.query(
                "SELECT CAST(PreferredDepartureAt AS DATE)"
                + " FROM dbo.RevisionRouteSource WHERE RevisionID = :rev",
                args("rev", revision),
                (rs, rowNum) -> {
                    java.sql.Date d = rs.getDate(1);
                    return d != null ? d.toLocalDate() : null;
                });
        if (!dates.isEmpty() && dates.get(0) != null) {
            return Optional.of(dates.get(0));
        }
        return Optional.empty();
    }

    /**
     * Returns true when any mandatory requirement for the phase is not
     * COMPLIANT, or when its approved document version has expired.
     * Statuses PENDING, PENDING_REVIEW and BLOCKER all block. An approved
     * COMPLIANT requirement also blocks if its latest approved version's
     * expiry date has passed or expires before scheduled departure.
     * @param revision the current revision key
     * @param phase the phase code to evaluate
     * @return true when a mandatory blocker exists
     */
    public boolean hasMandatoryBlocker(long revision, String phase) {
        for (DocumentRequirement req : findByRevisionId(revision)) {
            if (!req.isMandatory() || !phase.equals(req.getPhaseCode())) {
                continue;
            }
            if (!"COMPLIANT".equals(req.getStatus())) {
                return true;
            }
            LocalDate cutoff = validityCutoff(req);
            long approved = count("SELECT COUNT(*)"
                    + " FROM dbo.DocumentSubmission s"
                    + " JOIN dbo.DocumentReview r ON "
                            + "r.SubmissionID=s.SubmissionID"
                    + " AND r.Decision='APPROVED'"
                    + " JOIN dbo.DocumentVersion v"
                    + " ON v.DocumentVersionID=s.DocumentVersionID"
                    + " JOIN dbo.HorseDocument d ON d.DocumentID=v.DocumentID"
                    + " WHERE s.RequirementID=:id AND d.DocumentType=:type"
                    + " AND (:horse IS NULL OR d.HorseID=:horse)"
                    + " AND s.SubmissionID=(SELECT MAX(s2.SubmissionID)"
                    + " FROM dbo.DocumentSubmission s2"
                    + " WHERE s2.RequirementID=:id)"
                    + " AND (v.IssuedDate IS NULL OR v.IssuedDate<=:today)"
                    + " AND (v.ExpiryDate>=:cutoff OR"
                    + " (v.ExpiryDate IS NULL AND :dated=0))",
                    args("id", req.getRequirementId())
                            .addValue("type", req.getDocumentType())
                            .addValue("horse", req.getHorseId(),
                                    java.sql.Types.BIGINT)
                            .addValue("cutoff", cutoff)
                            .addValue("today", LocalDate.now(ZoneOffset.UTC))
                            .addValue("dated",
                                    req.getValidityConditionsJson() == null
                                            ? 0 : 1));
            if (approved == 0) {
                return true;
            }
        }
        return false;
    }

    /** Computes validity through the current flight and source policy.
     * @param requirement exact revision requirement
     * @return latest required validity date
     */
    public LocalDate validityCutoff(DocumentRequirement requirement) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate cutoff = findDepartureDate(requirement.getRevisionId())
                .filter(d -> d.isAfter(today)).orElse(today);
        String conditions = requirement.getValidityConditionsJson();
        if (conditions != null) {
            try {
                String date = new ObjectMapper().readTree(conditions)
                        .path("validThrough").asText();
                if (!date.isBlank()) {
                    LocalDate policyDate = LocalDate.parse(date);
                    if (policyDate.isAfter(cutoff)) {
                        cutoff = policyDate;
                    }
                }
            } catch (java.io.IOException | java.time.DateTimeException ex) {
                throw new IllegalStateException(
                        "Invalid persisted document validity policy", ex);
            }
        }
        return cutoff;
    }

    /**
     * Returns a bounded page of requirements ordered by RequirementID.
     * Front-end must use this when a checklist may exceed 100 rows.
     * @param revision the current revision key
     * @param page zero-based page index
     * @param size number of rows per page (max 100)
     * @return one page of requirements
     */
    public List<DocumentRequirement> findByRevisionIdPaged(
            long revision, int page, int size) {
        return jdbc.query(
                "SELECT * FROM dbo.DocumentRequirement"
                + " WHERE RevisionID=:rev"
                + " ORDER BY RequirementID"
                + " OFFSET :off ROWS FETCH NEXT :sz ROWS ONLY",
                args("rev", revision)
                        .addValue("off", (long) page * size)
                        .addValue("sz", size),
                ComplianceRows.documentRequirement());
    }

    /**
     * Counts all requirements for the revision; used for pagination totals.
     * @param revision the current revision key
     * @return total row count
     */
    public long countByRevisionId(long revision) {
        return count("SELECT COUNT(*) FROM dbo.DocumentRequirement"
                + " WHERE RevisionID=:rev", args("rev", revision));
    }

}

