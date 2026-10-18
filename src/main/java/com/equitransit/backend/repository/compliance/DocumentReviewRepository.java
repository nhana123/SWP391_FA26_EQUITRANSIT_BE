package com.equitransit.backend.repository.compliance;


import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.entity.compliance.DocumentReview;


/** SQL adapter for the existing DocumentReview table. */
@Repository
public class DocumentReviewRepository extends ComplianceJdbcSupport {

    /** @param jdbc the transaction-aware SQL template */
    public DocumentReviewRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /** @param id the row key
     * @return the matching row
     */
    public Optional<DocumentReview> findById(long id) {
        return find("DocumentReview", "ReviewID", id,
                ComplianceRows.documentReview());
    }

    /** @param submission the exact submitted version's identity
     * @return whether it already has an immutable review
     */
    public boolean alreadyReviewed(long submission) {
        return count("SELECT COUNT(*) FROM dbo.DocumentReview"
                + " WHERE SubmissionID=:id", args("id", submission)) > 0;
    }

    /** @param value the new row
     * @return its identity
     */
    public long insert(DocumentReview value) {
        var params = new MapSqlParameterSource();

        params.addValue("SubmissionID", value.getSubmissionId());
        params.addValue("ReviewedBy", value.getReviewedBy());
        params.addValue("Decision", value.getDecision());
        params.addValue("Feedback", value.getFeedback());
        params.addValue("ReviewedAt", now());
        return insert("DocumentReview", "ReviewID", params);
    }

}
