/*
 * TripHandover
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: JDBC model for dbo.TripHandover table (F14 custody transfer).
 */
package com.equitransit.backend.entity.handover;

import java.time.OffsetDateTime;

/**
 * Domain entity mapping the physical custody transfer log on a trip leg.
 */
public class TripHandover {

    private Long handoverId;
    private long legId;
    private long recordedBy;
    private String handoverType;
    private OffsetDateTime handoverTime;
    private String receivedByName;
    private String receiverAuthority;
    private String receiverSignatureFileKey;
    private String horseConditionStatus;
    private String outcome;
    private String exceptionNotes;
    private String photoFileKey;
    private OffsetDateTime customerConfirmedAt;
    private String locationDescription;
    private String customerDecision;
    private String customerFeedback;
    private OffsetDateTime customerRespondedAt;
    private Long respondedByCustomerId;
    private String evidenceFilesJson;

    /** Default constructor for JDBC row mapping. */
    public TripHandover() { }

    /** @return primary key */
    public Long getHandoverId() {
        return handoverId;
    }

    /** @param handoverId primary key */
    public void setHandoverId(Long handoverId) {
        this.handoverId = handoverId;
    }

    /** @return transport leg identifier */
    public long getLegId() {
        return legId;
    }

    /** @param legId transport leg identifier */
    public void setLegId(long legId) {
        this.legId = legId;
    }

    /** @return staff profile key of the recording actor */
    public long getRecordedBy() {
        return recordedBy;
    }

    /** @param recordedBy staff profile key */
    public void setRecordedBy(long recordedBy) {
        this.recordedBy = recordedBy;
    }

    /** @return handover type classification */
    public String getHandoverType() {
        return handoverType;
    }

    /** @param handoverType handover type classification */
    public void setHandoverType(String handoverType) {
        this.handoverType = handoverType;
    }

    /** @return UTC handover timestamp */
    public OffsetDateTime getHandoverTime() {
        return handoverTime;
    }

    /** @param handoverTime UTC handover timestamp */
    public void setHandoverTime(OffsetDateTime handoverTime) {
        this.handoverTime = handoverTime;
    }

    /** @return name of person receiving animal */
    public String getReceivedByName() {
        return receivedByName;
    }

    /** @param receivedByName recipient name */
    public void setReceivedByName(String receivedByName) {
        this.receivedByName = receivedByName;
    }

    /** @return recipient authority */
    public String getReceiverAuthority() {
        return receiverAuthority;
    }

    /** @param receiverAuthority recipient authority */
    public void setReceiverAuthority(String receiverAuthority) {
        this.receiverAuthority = receiverAuthority;
    }

    /** @return signature file storage key */
    public String getReceiverSignatureFileKey() {
        return receiverSignatureFileKey;
    }

    /** @param receiverSignatureFileKey signature key */
    public void setReceiverSignatureFileKey(String receiverSignatureFileKey) {
        this.receiverSignatureFileKey = receiverSignatureFileKey;
    }

    /** @return physical condition assessment */
    public String getHorseConditionStatus() {
        return horseConditionStatus;
    }

    /** @param horseConditionStatus condition assessment */
    public void setHorseConditionStatus(String horseConditionStatus) {
        this.horseConditionStatus = horseConditionStatus;
    }

    /** @return handover outcome */
    public String getOutcome() {
        return outcome;
    }

    /** @param outcome handover outcome */
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    /** @return exception notes */
    public String getExceptionNotes() {
        return exceptionNotes;
    }

    /** @param exceptionNotes exception notes */
    public void setExceptionNotes(String exceptionNotes) {
        this.exceptionNotes = exceptionNotes;
    }

    /** @return photo file storage key */
    public String getPhotoFileKey() {
        return photoFileKey;
    }

    /** @param photoFileKey photo storage key */
    public void setPhotoFileKey(String photoFileKey) {
        this.photoFileKey = photoFileKey;
    }

    /** @return customer confirmation timestamp */
    public OffsetDateTime getCustomerConfirmedAt() {
        return customerConfirmedAt;
    }

    /** @param customerConfirmedAt confirmation timestamp */
    public void setCustomerConfirmedAt(OffsetDateTime customerConfirmedAt) {
        this.customerConfirmedAt = customerConfirmedAt;
    }

    /** @return location description */
    public String getLocationDescription() {
        return locationDescription;
    }

    /** @param locationDescription location description */
    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    /** @return customer decision (ACCEPTED, EXCEPTION, NOT_DELIVERED) */
    public String getCustomerDecision() {
        return customerDecision;
    }

    /** @param customerDecision customer decision */
    public void setCustomerDecision(String customerDecision) {
        this.customerDecision = customerDecision;
    }

    /** @return customer feedback */
    public String getCustomerFeedback() {
        return customerFeedback;
    }

    /** @param customerFeedback customer feedback */
    public void setCustomerFeedback(String customerFeedback) {
        this.customerFeedback = customerFeedback;
    }

    /** @return customer response timestamp */
    public OffsetDateTime getCustomerRespondedAt() {
        return customerRespondedAt;
    }

    /** @param customerRespondedAt response timestamp */
    public void setCustomerRespondedAt(OffsetDateTime customerRespondedAt) {
        this.customerRespondedAt = customerRespondedAt;
    }

    /** @return customer ID who responded */
    public Long getRespondedByCustomerId() {
        return respondedByCustomerId;
    }

    /** @param respondedByCustomerId customer ID */
    public void setRespondedByCustomerId(Long respondedByCustomerId) {
        this.respondedByCustomerId = respondedByCustomerId;
    }

    /** @return evidence files JSON */
    public String getEvidenceFilesJson() {
        return evidenceFilesJson;
    }

    /** @param evidenceFilesJson evidence files JSON */
    public void setEvidenceFilesJson(String evidenceFilesJson) {
        this.evidenceFilesJson = evidenceFilesJson;
    }
}
