package com.equitransit.backend.entity;


import java.time.OffsetDateTime;


/** Explicit row mapping for the existing DocumentReview table. */
public class DocumentReview {
    private long reviewId;
    private long submissionId;
    private long reviewedBy;
    private String decision;
    private String feedback;
    private OffsetDateTime reviewedAt;

    /** @return the persisted reviewId */
    public long getReviewId() {
        return reviewId;
    }

    /** @param value the persisted reviewId */
    public void setReviewId(long value) {
        this.reviewId = value;
    }

    /** @return the persisted submissionId */
    public long getSubmissionId() {
        return submissionId;
    }

    /** @param value the persisted submissionId */
    public void setSubmissionId(long value) {
        this.submissionId = value;
    }

    /** @return the persisted reviewedBy */
    public long getReviewedBy() {
        return reviewedBy;
    }

    /** @param value the persisted reviewedBy */
    public void setReviewedBy(long value) {
        this.reviewedBy = value;
    }

    /** @return the persisted decision */
    public String getDecision() {
        return decision;
    }

    /** @param value the persisted decision */
    public void setDecision(String value) {
        this.decision = value;
    }

    /** @return the persisted feedback */
    public String getFeedback() {
        return feedback;
    }

    /** @param value the persisted feedback */
    public void setFeedback(String value) {
        this.feedback = value;
    }

    /** @return the persisted reviewedAt */
    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    /** @param value the persisted reviewedAt */
    public void setReviewedAt(OffsetDateTime value) {
        this.reviewedAt = value;
    }
}
