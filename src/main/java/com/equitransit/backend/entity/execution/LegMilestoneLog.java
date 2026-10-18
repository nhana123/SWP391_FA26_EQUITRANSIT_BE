/*
 * LegMilestoneLog
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Persistence model for discrete transport leg milestones.
 */
package com.equitransit.backend.entity.execution;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Represents a discrete milestone logged along a transport leg. */
public class LegMilestoneLog {

    private Long milestoneId;
    private Long legId;
    private Long reportedBy;
    private String milestoneType;
    private OffsetDateTime occurredAt;
    private OffsetDateTime recordedAt;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String notes;
    private String sourceCode;
    private String sourceReference;
    private String locationDescription;
    private OffsetDateTime estimatedArrivalAt;
    private String delayReason;
    private String evidenceFileKey;

    /** Creates an uninitialized milestone log model. */
    public LegMilestoneLog() { }

    /** @return milestone PK */
    public Long getMilestoneId() {
        return milestoneId;
    }

    /** @param milestoneId milestone PK */
    public void setMilestoneId(Long milestoneId) {
        this.milestoneId = milestoneId;
    }

    /** @return trip leg FK */
    public Long getLegId() {
        return legId;
    }

    /** @param legId trip leg FK */
    public void setLegId(Long legId) {
        this.legId = legId;
    }

    /** @return reporting staff FK */
    public Long getReportedBy() {
        return reportedBy;
    }

    /** @param reportedBy reporting staff FK */
    public void setReportedBy(Long reportedBy) {
        this.reportedBy = reportedBy;
    }

    /** @return milestone type code */
    public String getMilestoneType() {
        return milestoneType;
    }

    /** @param milestoneType milestone type code */
    public void setMilestoneType(String milestoneType) {
        this.milestoneType = milestoneType;
    }

    /** @return actual occurrence UTC */
    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    /** @param occurredAt actual occurrence UTC */
    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    /** @return server record UTC */
    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    /** @param recordedAt server record UTC */
    public void setRecordedAt(OffsetDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    /** @return GPS latitude */
    public BigDecimal getLatitude() {
        return latitude;
    }

    /** @param latitude GPS latitude */
    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    /** @return GPS longitude */
    public BigDecimal getLongitude() {
        return longitude;
    }

    /** @param longitude GPS longitude */
    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    /** @return technical or internal operational notes */
    public String getNotes() {
        return notes;
    }

    /** @param notes technical or internal operational notes */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /** @return data source code */
    public String getSourceCode() {
        return sourceCode;
    }

    /** @param sourceCode data source code */
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    /** @return external reference or AWB */
    public String getSourceReference() {
        return sourceReference;
    }

    /** @param sourceReference external reference or AWB */
    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    /** @return human readable location description */
    public String getLocationDescription() {
        return locationDescription;
    }

    /** @param locationDescription human readable location description */
    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    /** @return revised estimated arrival UTC */
    public OffsetDateTime getEstimatedArrivalAt() {
        return estimatedArrivalAt;
    }

    /** @param estimatedArrivalAt revised estimated arrival UTC */
    public void setEstimatedArrivalAt(OffsetDateTime estimatedArrivalAt) {
        this.estimatedArrivalAt = estimatedArrivalAt;
    }

    /** @return delay reason explanation */
    public String getDelayReason() {
        return delayReason;
    }

    /** @param delayReason delay reason explanation */
    public void setDelayReason(String delayReason) {
        this.delayReason = delayReason;
    }

    /** @return storage key of supporting evidence photo/document */
    public String getEvidenceFileKey() {
        return evidenceFileKey;
    }

    /** @param evidenceFileKey storage key of supporting evidence */
    public void setEvidenceFileKey(String evidenceFileKey) {
        this.evidenceFileKey = evidenceFileKey;
    }
}
