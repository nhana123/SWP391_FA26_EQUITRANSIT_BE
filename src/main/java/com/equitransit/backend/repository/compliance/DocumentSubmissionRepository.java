package com.equitransit.backend.repository.compliance;


import java.util.Optional;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import com.equitransit.backend.dto.compliance.response.
        DocumentHistoryPageResponse;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.entity.compliance.DocumentSubmission;


/** SQL adapter for the existing DocumentSubmission table. */
@Repository
public class DocumentSubmissionRepository extends ComplianceJdbcSupport {

    /** @param jdbc the transaction-aware SQL template */
    public DocumentSubmissionRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /** @param id the row key
     * @return the matching row
     */
    public Optional<DocumentSubmission> findById(long id) {
        return find("DocumentSubmission", "SubmissionID", id,
                ComplianceRows.documentSubmission());
    }

    /** @param requirement the requirement key
     * @return the latest submission identity, zero if none
     */
    public long latestId(long requirement) {
        return count("SELECT COALESCE(MAX(SubmissionID),0)"
                + " FROM dbo.DocumentSubmission WHERE RequirementID=:id",
                args("id", requirement));
    }

    /** Reads the complete paginated public history.
     * @param requirement authorized requirement
     * @param page zero-based page
     * @param size bounded size
     * @return page with accurate totals
     */
    public DocumentHistoryPageResponse history(long requirement,
            int page, int size) {
        var params = args("id", requirement)
                .addValue("off", (long) page * size).addValue("sz", size);
        long total = count("SELECT COUNT(*) FROM dbo.DocumentSubmission"
                + " WHERE RequirementID=:id", params);
        var items = jdbc.query("SELECT s.SubmissionID,s.DocumentVersionID,"
                + "v.VersionNo,s.SubmittedAt,r.Decision,r.Feedback,r.ReviewedAt"
                + " FROM dbo.DocumentSubmission s JOIN dbo.DocumentVersion v"
                + " ON v.DocumentVersionID=s.DocumentVersionID"
                + " LEFT JOIN dbo.DocumentReview r ON r.ReviewID=("
                + " SELECT MAX(r2.ReviewID) FROM dbo.DocumentReview r2"
                + " WHERE r2.SubmissionID=s.SubmissionID)"
                + " WHERE s.RequirementID=:id ORDER BY s.SubmissionID DESC"
                + " OFFSET :off ROWS FETCH NEXT :sz ROWS ONLY", params,
                (row, i) -> new DocumentHistoryPageResponse.Entry(
                        row.getLong("SubmissionID"),
                        row.getLong("DocumentVersionID"),
                        row.getInt("VersionNo"),
                        utc(row.getTimestamp("SubmittedAt")),
                        row.getString("Decision"), row.getString("Feedback"),
                        utc(row.getTimestamp("ReviewedAt"))));
        return new DocumentHistoryPageResponse(items, page, size, total,
                (int) ((total + size - 1) / size));
    }

    private static java.time.OffsetDateTime utc(Timestamp value) {
        return value == null ? null
                : value.toLocalDateTime().atOffset(ZoneOffset.UTC);
    }

    /** @param value the new row
     * @return its identity
     */
    public long insert(DocumentSubmission value) {
        var params = new MapSqlParameterSource();

        params.addValue("RequirementID", value.getRequirementId());
        params.addValue("DocumentVersionID", value.getDocumentVersionId());
        params.addValue("Status", "SUBMITTED");
        params.addValue("SubmittedAt", now());
        return insert("DocumentSubmission", "SubmissionID", params);
    }

}
