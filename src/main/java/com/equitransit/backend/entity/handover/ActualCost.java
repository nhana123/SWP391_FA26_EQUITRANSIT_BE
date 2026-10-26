/*
 * ActualCost
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: JDBC model for dbo.ActualCost (internal supplier expenses).
 */
package com.equitransit.backend.entity.handover;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Domain entity mapping an internal actual supplier expenditure on a plan.
 */
public class ActualCost {

    private Long costId;
    private long planId;
    private Long legId;
    private String costCode;
    private BigDecimal amount;
    private String currency;
    private OffsetDateTime incurredAt;
    private String description;
    private Long recordedBy;
    private String evidenceFileKey;

    /** Default constructor for JDBC row mapping. */
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
