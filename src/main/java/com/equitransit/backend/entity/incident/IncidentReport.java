/*
 * IncidentReport
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Domain entity representing an operational or welfare incident.
 */
package com.equitransit.backend.entity.incident;

import java.time.OffsetDateTime;

/**
 * Domain entity representing an unexpected incident or welfare disruption.
 */
public class IncidentReport {

    private Long incidentId;
    private Long legId;
    private Long reportedBy;
    private String incidentType;
    private String severityLevel;
    private OffsetDateTime incidentTime;
    private String description;
    private String status;
    private String immediateActionNotes;
    private String emergencyContact;
    private Long acknowledgedBy;
    private OffsetDateTime acknowledgedAt;
    private Long resolvedBy;
    private OffsetDateTime resolvedAt;
    private String resolutionNotes;
    private String evidenceFilesJson;

    /** Creates an empty incident report. */
    public IncidentReport() { }

    /** @return incident primary key */
    public Long getIncidentId() {
        return incidentId;
    }

    /** @param incidentId incident primary key */
    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    /** @return leg foreign key */
    public Long getLegId() {
        return legId;
    }

    /** @param legId leg foreign key */
    public void setLegId(Long legId) {
        this.legId = legId;
    }

    /** @return reporter staff profile foreign key */
    public Long getReportedBy() {
        return reportedBy;
    }

    /** @param reportedBy reporter staff profile foreign key */
    public void setReportedBy(Long reportedBy) {
        this.reportedBy = reportedBy;
    }

    /** @return incident type category code */
    public String getIncidentType() {
        return incidentType;
    }

    /** @param incidentType incident type category code */
    public void setIncidentType(String incidentType) {
        this.incidentType = incidentType;
    }

    /** @return severity classification */
    public String getSeverityLevel() {
        return severityLevel;
    }

    /** @param severityLevel severity classification */
    public void setSeverityLevel(String severityLevel) {
        this.severityLevel = severityLevel;
    }

    /** @return timestamp of incident occurrence */
    public OffsetDateTime getIncidentTime() {
        return incidentTime;
    }

    /** @param incidentTime timestamp of incident occurrence */
    public void setIncidentTime(OffsetDateTime incidentTime) {
        this.incidentTime = incidentTime;
    }

    /** @return factual narrative description */
    public String getDescription() {
        return description;
    }

    /** @param description factual narrative description */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @return lifecycle status of incident */
    public String getStatus() {
        return status;
    }

    /** @param status lifecycle status of incident */
    public void setStatus(String status) {
        this.status = status;
    }

    /** @return immediate emergency response or medical care notes */
    public String getImmediateActionNotes() {
        return immediateActionNotes;
    }

    /** @param immediateActionNotes immediate emergency response notes */
    public void setImmediateActionNotes(String immediateActionNotes) {
        this.immediateActionNotes = immediateActionNotes;
    }

    /** @return on-scene emergency contact details */
    public String getEmergencyContact() {
        return emergencyContact;
    }

    /** @param emergencyContact on-scene emergency contact details */
    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }

    /** @return staff profile who acknowledged */
    public Long getAcknowledgedBy() {
        return acknowledgedBy;
    }

    /** @param acknowledgedBy staff profile who acknowledged */
    public void setAcknowledgedBy(Long acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    /** @return timestamp of acknowledgment */
    public OffsetDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    /** @param acknowledgedAt timestamp of acknowledgment */
    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    /** @return staff profile who resolved */
    public Long getResolvedBy() {
        return resolvedBy;
    }

    /** @param resolvedBy staff profile who resolved */
    public void setResolvedBy(Long resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    /** @return timestamp of resolution */
    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    /** @param resolvedAt timestamp of resolution */
    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    /** @return formal resolution notes */
    public String getResolutionNotes() {
        return resolutionNotes;
    }

    /** @param resolutionNotes formal resolution notes */
    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    /** @return JSON array of evidence file keys */
    public String getEvidenceFilesJson() {
        return evidenceFilesJson;
    }

    /** @param evidenceFilesJson JSON array of evidence file keys */
    public void setEvidenceFilesJson(String evidenceFilesJson) {
        this.evidenceFilesJson = evidenceFilesJson;
    }
}
