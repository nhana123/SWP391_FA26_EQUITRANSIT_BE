package com.equitransit.backend.entity;


import java.time.OffsetDateTime;


/** Explicit row mapping for the existing AuthorityProcedure table. */
public class AuthorityProcedure {
    private long procedureId;
    private long revisionId;
    private String procedureCode;
    private String authorityName;
    private String applicationReference;
    private String approvalStatus;
    private String evidenceFileKey;
    private OffsetDateTime decidedAt;
    private String phaseCode;
    private String authorityContact;
    private String holdReason;
    private OffsetDateTime expiresAt;
    private Long recordedBy;

    /** @return the persisted procedureId */
    public long getProcedureId() {
        return procedureId;
    }

    /** @param value the persisted procedureId */
    public void setProcedureId(long value) {
        this.procedureId = value;
    }

    /** @return the persisted revisionId */
    public long getRevisionId() {
        return revisionId;
    }

    /** @param value the persisted revisionId */
    public void setRevisionId(long value) {
        this.revisionId = value;
    }

    /** @return the persisted procedureCode */
    public String getProcedureCode() {
        return procedureCode;
    }

    /** @param value the persisted procedureCode */
    public void setProcedureCode(String value) {
        this.procedureCode = value;
    }

    /** @return the persisted authorityName */
    public String getAuthorityName() {
        return authorityName;
    }

    /** @param value the persisted authorityName */
    public void setAuthorityName(String value) {
        this.authorityName = value;
    }

    /** @return the persisted applicationReference */
    public String getApplicationReference() {
        return applicationReference;
    }

    /** @param value the persisted applicationReference */
    public void setApplicationReference(String value) {
        this.applicationReference = value;
    }

    /** @return the persisted approvalStatus */
    public String getApprovalStatus() {
        return approvalStatus;
    }

    /** @param value the persisted approvalStatus */
    public void setApprovalStatus(String value) {
        this.approvalStatus = value;
    }

    /** @return the persisted evidenceFileKey */
    public String getEvidenceFileKey() {
        return evidenceFileKey;
    }

    /** @param value the persisted evidenceFileKey */
    public void setEvidenceFileKey(String value) {
        this.evidenceFileKey = value;
    }

    /** @return the persisted decidedAt */
    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    /** @param value the persisted decidedAt */
    public void setDecidedAt(OffsetDateTime value) {
        this.decidedAt = value;
    }

    /** @return the persisted phaseCode */
    public String getPhaseCode() {
        return phaseCode;
    }

    /** @param value the persisted phaseCode */
    public void setPhaseCode(String value) {
        this.phaseCode = value;
    }

    /** @return the persisted authorityContact */
    public String getAuthorityContact() {
        return authorityContact;
    }

    /** @param value the persisted authorityContact */
    public void setAuthorityContact(String value) {
        this.authorityContact = value;
    }

    /** @return the persisted holdReason */
    public String getHoldReason() {
        return holdReason;
    }

    /** @param value the persisted holdReason */
    public void setHoldReason(String value) {
        this.holdReason = value;
    }

    /** @return the persisted expiresAt */
    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    /** @param value the persisted expiresAt */
    public void setExpiresAt(OffsetDateTime value) {
        this.expiresAt = value;
    }

    /** @return the persisted recordedBy */
    public Long getRecordedBy() {
        return recordedBy;
    }

    /** @param value the persisted recordedBy */
    public void setRecordedBy(Long value) {
        this.recordedBy = value;
    }
}
