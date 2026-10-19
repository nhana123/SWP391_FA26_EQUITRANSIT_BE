/*
 * OperationalAdjustment
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Domain entity representing a controlled operational plan change.
 */
package com.equitransit.backend.entity.incident;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Domain entity representing a proposed operational change to a plan.
 */
public class OperationalAdjustment {

    private Long adjustmentId;
    private Long planId;
    private Long incidentId;
    private Long proposedBy;
    private Long approvedBy;
    private String proposedChanges;
    private BigDecimal additionalCostEstimate;
    private String approvalStatus;
    private OffsetDateTime proposedAt;
    private OffsetDateTime decidedAt;
    private Long changeRequestId;
    private String customerDecision;
    private Long respondedByCustomerId;
    private OffsetDateTime customerRespondedAt;
    private String customerResponseNotes;

    /** Creates an empty operational adjustment. */
    public OperationalAdjustment() { }

    /** @return adjustment primary key */
    public Long getAdjustmentId() {
        return adjustmentId;
    }

    /** @param adjustmentId adjustment primary key */
    public void setAdjustmentId(Long adjustmentId) {
        this.adjustmentId = adjustmentId;
    }

    /** @return target plan foreign key */
    public Long getPlanId() {
        return planId;
    }

    /** @param planId target plan foreign key */
    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    /** @return triggering incident foreign key or null */
    public Long getIncidentId() {
        return incidentId;
    }

    /** @param incidentId triggering incident foreign key or null */
    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    /** @return proposer staff profile foreign key */
    public Long getProposedBy() {
        return proposedBy;
    }

    /** @param proposedBy proposer staff profile foreign key */
    public void setProposedBy(Long proposedBy) {
        this.proposedBy = proposedBy;
    }

    /** @return approving staff profile foreign key or null */
    public Long getApprovedBy() {
        return approvedBy;
    }

    /** @param approvedBy approving staff profile foreign key or null */
    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    /** @return JSON structure containing proposed adjustments */
    public String getProposedChanges() {
        return proposedChanges;
    }

    /** @param proposedChanges JSON structure of proposed adjustments */
    public void setProposedChanges(String proposedChanges) {
        this.proposedChanges = proposedChanges;
    }

    /** @return estimated customer additional charge, non-negative */
    public BigDecimal getAdditionalCostEstimate() {
        return additionalCostEstimate;
    }

    /** @param additionalCostEstimate estimated customer additional charge */
    public void setAdditionalCostEstimate(BigDecimal additionalCostEstimate) {
        this.additionalCostEstimate = additionalCostEstimate;
    }

    /** @return workflow approval status */
    public String getApprovalStatus() {
        return approvalStatus;
    }

    /** @param approvalStatus workflow approval status */
    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    /** @return timestamp when adjustment was proposed */
    public OffsetDateTime getProposedAt() {
        return proposedAt;
    }

    /** @param proposedAt timestamp when adjustment was proposed */
    public void setProposedAt(OffsetDateTime proposedAt) {
        this.proposedAt = proposedAt;
    }

    /** @return timestamp when adjustment was decided */
    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    /** @param decidedAt timestamp when adjustment was decided */
    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    /** @return linked order change request foreign key or null */
    public Long getChangeRequestId() {
        return changeRequestId;
    }

    /** @param changeRequestId linked change request foreign key */
    public void setChangeRequestId(Long changeRequestId) {
        this.changeRequestId = changeRequestId;
    }

    /** @return customer decision status: PENDING, ACCEPTED, REJECTED */
    public String getCustomerDecision() {
        return customerDecision;
    }

    /** @param customerDecision customer decision status */
    public void setCustomerDecision(String customerDecision) {
        this.customerDecision = customerDecision;
    }

    /** @return responding customer foreign key or null */
    public Long getRespondedByCustomerId() {
        return respondedByCustomerId;
    }

    /** @param respondedByCustomerId responding customer foreign key */
    public void setRespondedByCustomerId(Long respondedByCustomerId) {
        this.respondedByCustomerId = respondedByCustomerId;
    }

    /** @return timestamp of customer response */
    public OffsetDateTime getCustomerRespondedAt() {
        return customerRespondedAt;
    }

    /** @param customerRespondedAt timestamp of customer response */
    public void setCustomerRespondedAt(OffsetDateTime customerRespondedAt) {
        this.customerRespondedAt = customerRespondedAt;
    }

    /** @return customer remarks or feedback */
    public String getCustomerResponseNotes() {
        return customerResponseNotes;
    }

    /** @param customerResponseNotes customer remarks or feedback */
    public void setCustomerResponseNotes(String customerResponseNotes) {
        this.customerResponseNotes = customerResponseNotes;
    }
}
