package com.equitransit.backend.entity;


import java.time.OffsetDateTime;


/** Explicit row mapping for the existing ComplianceClearance table. */
public class ComplianceClearance {
    private long clearanceId;
    private long revisionId;
    private long specialistId;
    private String phaseCode;
    private String status;
    private OffsetDateTime checkedAt;
    private OffsetDateTime expiresAt;
    private String assessmentNotes;

    /** @return the persisted clearanceId */
    public long getClearanceId() {
        return clearanceId;
    }

    /** @param value the persisted clearanceId */
    public void setClearanceId(long value) {
        this.clearanceId = value;
    }

    /** @return the persisted revisionId */
    public long getRevisionId() {
        return revisionId;
    }

    /** @param value the persisted revisionId */
    public void setRevisionId(long value) {
        this.revisionId = value;
    }

    /** @return the persisted specialistId */
    public long getSpecialistId() {
        return specialistId;
    }

    /** @param value the persisted specialistId */
    public void setSpecialistId(long value) {
        this.specialistId = value;
    }

    /** @return the persisted phaseCode */
    public String getPhaseCode() {
        return phaseCode;
    }

    /** @param value the persisted phaseCode */
    public void setPhaseCode(String value) {
        this.phaseCode = value;
    }

    /** @return the persisted status */
    public String getStatus() {
        return status;
    }

    /** @param value the persisted status */
    public void setStatus(String value) {
        this.status = value;
    }

    /** @return the persisted checkedAt */
    public OffsetDateTime getCheckedAt() {
        return checkedAt;
    }

    /** @param value the persisted checkedAt */
    public void setCheckedAt(OffsetDateTime value) {
        this.checkedAt = value;
    }

    /** @return the persisted expiresAt */
    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    /** @param value the persisted expiresAt */
    public void setExpiresAt(OffsetDateTime value) {
        this.expiresAt = value;
    }

    /** @return the persisted assessmentNotes */
    public String getAssessmentNotes() {
        return assessmentNotes;
    }

    /** @param value the persisted assessmentNotes */
    public void setAssessmentNotes(String value) {
        this.assessmentNotes = value;
    }
}
