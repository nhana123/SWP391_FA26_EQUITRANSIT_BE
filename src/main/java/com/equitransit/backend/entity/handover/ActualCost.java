/*
 * ActualCost
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Entity mapping for dbo.ActualCost (internal supplier expenses).
 */
package com.equitransit.backend.entity.handover;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Entity mapping an internal actual supplier expenditure on a transport plan.
 */
@Entity
@Table(name = "ActualCost", schema = "dbo")
public class ActualCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CostID")
    private Long costId;

    @Column(name = "PlanID", nullable = false)
    private long planId;

    @Column(name = "LegID")
    private Long legId;

    @Column(name = "CostCode", nullable = false, length = 50)
    private String costCode;

    @Column(name = "Amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "Currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "IncurredAt", nullable = false)
    private OffsetDateTime incurredAt;

    @Column(name = "Description", length = 1000)
    private String description;

    @Column(name = "RecordedBy")
    private Long recordedBy;

    @Column(name = "EvidenceFileKey", length = 1024)
    private String evidenceFileKey;

    /** Default constructor for JPA. */
    public ActualCost() { }

    /** @return primary key */
    public Long getCostId() {
        return costId;
    }

    /** @param costId primary key */
    public void setCostId(Long costId) {
        this.costId = costId;
    }

    /** @return operational plan key */
    public long getPlanId() {
        return planId;
    }

    /** @param planId operational plan key */
    public void setPlanId(long planId) {
        this.planId = planId;
    }

    /** @return optional leg key */
    public Long getLegId() {
        return legId;
    }

    /** @param legId optional leg key */
    public void setLegId(Long legId) {
        this.legId = legId;
    }

    /** @return cost classification code */
    public String getCostCode() {
        return costCode;
    }

    /** @param costCode classification code */
    public void setCostCode(String costCode) {
        this.costCode = costCode;
    }

    /** @return non-negative amount */
    public BigDecimal getAmount() {
        return amount;
    }

    /** @param amount expenditure amount */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /** @return 3-letter currency code */
    public String getCurrency() {
        return currency;
    }

    /** @param currency currency code */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /** @return timestamp when cost was incurred */
    public OffsetDateTime getIncurredAt() {
        return incurredAt;
    }

    /** @param incurredAt cost incurred timestamp */
    public void setIncurredAt(OffsetDateTime incurredAt) {
        this.incurredAt = incurredAt;
    }

    /** @return description or invoice reference */
    public String getDescription() {
        return description;
    }

    /** @param description description */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @return staff profile key of recorder */
    public Long getRecordedBy() {
        return recordedBy;
    }

    /** @param recordedBy staff profile key */
    public void setRecordedBy(Long recordedBy) {
        this.recordedBy = recordedBy;
    }

    /** @return invoice/receipt file storage key */
    public String getEvidenceFileKey() {
        return evidenceFileKey;
    }

    /** @param evidenceFileKey evidence file key */
    public void setEvidenceFileKey(String evidenceFileKey) {
        this.evidenceFileKey = evidenceFileKey;
    }
}
