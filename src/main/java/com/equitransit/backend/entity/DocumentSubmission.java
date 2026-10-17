package com.equitransit.backend.entity;


import java.time.OffsetDateTime;


/** Explicit row mapping for the existing DocumentSubmission table. */
public class DocumentSubmission {
    private long submissionId;
    private long requirementId;
    private long documentVersionId;
    private String status;
    private OffsetDateTime submittedAt;

    /** @return the persisted submissionId */
    public long getSubmissionId() {
        return submissionId;
    }

    /** @param value the persisted submissionId */
    public void setSubmissionId(long value) {
        this.submissionId = value;
    }

    /** @return the persisted requirementId */
    public long getRequirementId() {
        return requirementId;
    }

    /** @param value the persisted requirementId */
    public void setRequirementId(long value) {
        this.requirementId = value;
    }

    /** @return the persisted documentVersionId */
    public long getDocumentVersionId() {
        return documentVersionId;
    }

    /** @param value the persisted documentVersionId */
    public void setDocumentVersionId(long value) {
        this.documentVersionId = value;
    }

    /** @return the persisted status */
    public String getStatus() {
        return status;
    }

    /** @param value the persisted status */
    public void setStatus(String value) {
        this.status = value;
    }

    /** @return the persisted submittedAt */
    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    /** @param value the persisted submittedAt */
    public void setSubmittedAt(OffsetDateTime value) {
        this.submittedAt = value;
    }
}
