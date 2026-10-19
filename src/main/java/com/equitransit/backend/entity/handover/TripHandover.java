/*
 * TripHandover
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Entity mapping for dbo.TripHandover table (F14 custody transfer).
 */
package com.equitransit.backend.entity.handover;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Entity mapping the physical custody transfer log on a multimodal trip leg.
 */
@Entity
@Table(name = "TripHandover", schema = "dbo")
public class TripHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HandoverID")
    private Long handoverId;

    @Column(name = "LegID", nullable = false)
    private long legId;

    @Column(name = "RecordedBy", nullable = false)
    private long recordedBy;

    @Column(name = "HandoverType", nullable = false, length = 50)
    private String handoverType;

    @Column(name = "HandoverTime", nullable = false)
    private OffsetDateTime handoverTime;

    @Column(name = "ReceivedByName", nullable = false, length = 300)
    private String receivedByName;

    @Column(name = "ReceiverAuthority", length = 50)
    private String receiverAuthority;

    @Column(name = "ReceiverSignatureFileKey", length = 1024)
    private String receiverSignatureFileKey;

    @Column(name = "HorseConditionStatus", nullable = false, length = 50)
    private String horseConditionStatus;

    @Column(name = "Outcome", nullable = false, length = 50)
    private String outcome;

    @Column(name = "ExceptionNotes", length = 2000)
    private String exceptionNotes;

    @Column(name = "PhotoFileKey", length = 1024)
    private String photoFileKey;

    @Column(name = "CustomerConfirmedAt")
    private OffsetDateTime customerConfirmedAt;

    @Column(name = "LocationDescription", length = 500)
    private String locationDescription;

    @Column(name = "CustomerDecision", length = 20)
    private String customerDecision;

    @Column(name = "CustomerFeedback", length = 2000)
    private String customerFeedback;

    @Column(name = "CustomerRespondedAt")
    private OffsetDateTime customerRespondedAt;

    @Column(name = "RespondedByCustomerID")
    private Long respondedByCustomerId;

    @Column(name = "EvidenceFilesJSON")
    private String evidenceFilesJson;

    /** Default constructor for JPA. */
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
