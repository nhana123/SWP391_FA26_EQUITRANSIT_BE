package com.equitransit.backend.entity;


import java.time.OffsetDateTime;


/** Explicit row mapping for the existing DocumentRequirement table. */
public class DocumentRequirement {
    private long requirementId;
    private long revisionId;
    private long ruleId;
    private String documentType;
    private String phaseCode;
    private boolean mandatory;
    private OffsetDateTime dueAt;
    private String status;
    private String instructions;
    private String scopeCode;
    private Long horseId;
    private String validityConditionsJson;
    private String caseJustification;

    /** @return the persisted requirementId */
    public long getRequirementId() {
        return requirementId;
    }

    /** @param value the persisted requirementId */
    public void setRequirementId(long value) {
        this.requirementId = value;
    }

    /** @return the persisted revisionId */
    public long getRevisionId() {
        return revisionId;
    }

    /** @param value the persisted revisionId */
    public void setRevisionId(long value) {
        this.revisionId = value;
    }

    /** @return the persisted ruleId */
    public long getRuleId() {
        return ruleId;
    }

    /** @param value the persisted ruleId */
    public void setRuleId(long value) {
        this.ruleId = value;
    }

    /** @return the persisted documentType */
    public String getDocumentType() {
        return documentType;
    }

    /** @param value the persisted documentType */
    public void setDocumentType(String value) {
        this.documentType = value;
    }

    /** @return the persisted phaseCode */
    public String getPhaseCode() {
        return phaseCode;
    }

    /** @param value the persisted phaseCode */
    public void setPhaseCode(String value) {
        this.phaseCode = value;
    }

    /** @return the persisted mandatory */
    public boolean isMandatory() {
        return mandatory;
    }

    /** @param value the persisted mandatory */
    public void setMandatory(boolean value) {
        this.mandatory = value;
    }

    /** @return the persisted dueAt */
    public OffsetDateTime getDueAt() {
        return dueAt;
    }

    /** @param value the persisted dueAt */
    public void setDueAt(OffsetDateTime value) {
        this.dueAt = value;
    }

    /** @return the persisted status */
    public String getStatus() {
        return status;
    }

    /** @param value the persisted status */
    public void setStatus(String value) {
        this.status = value;
    }

    /** @return the persisted instructions */
    public String getInstructions() {
        return instructions;
    }

    /** @param value the persisted instructions */
    public void setInstructions(String value) {
        this.instructions = value;
    }

    /** @return the persisted scopeCode */
    public String getScopeCode() {
        return scopeCode;
    }

    /** @param value the persisted scopeCode */
    public void setScopeCode(String value) {
        this.scopeCode = value;
    }

    /** @return the persisted horseId */
    public Long getHorseId() {
        return horseId;
    }

    /** @param value the persisted horseId */
    public void setHorseId(Long value) {
        this.horseId = value;
    }

    /** @return the persisted validityConditionsJson */
    public String getValidityConditionsJson() {
        return validityConditionsJson;
    }

    /** @param value the persisted validityConditionsJson */
    public void setValidityConditionsJson(String value) {
        this.validityConditionsJson = value;
    }

    /** @return the persisted caseJustification */
    public String getCaseJustification() {
        return caseJustification;
    }

    /** @param value the persisted caseJustification */
    public void setCaseJustification(String value) {
        this.caseJustification = value;
    }
}
