/*
 * QuarantineAssessment
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Represent the F08 quarantine applicability evaluation result.
 */
package com.equitransit.backend.entity;


/**
 * Quarantine applicability assessment result for a revision.
 * RequiredDays is null when Applicability is PENDING or NOT_REQUIRED;
 * it is a positive integer only when Applicability is REQUIRED.
 * The DB constraint CHECK (RequiredDays > 0) enforces this invariant;
 * never store zero.
 */
public class QuarantineAssessment {

    private String applicability;
    private Integer requiredDays;

    /**
     * Returns the applicability code.
     * @return PENDING, NOT_REQUIRED, or REQUIRED
     */
    public String getApplicability() {
        return applicability;
    }

    /**
     * Sets the applicability code.
     * @param value PENDING, NOT_REQUIRED, or REQUIRED
     */
    public void setApplicability(String value) {
        this.applicability = value;
    }

    /**
     * Returns the required quarantine days when Applicability is REQUIRED.
     * Returns null for PENDING and NOT_REQUIRED states.
     * Never returns zero; the DB CHECK constraint rejects it.
     * @return positive integer, or null
     */
    public Integer getRequiredDays() {
        return requiredDays;
    }

    /**
     * Sets the required quarantine days.
     * @param value positive integer, or null
     */
    public void setRequiredDays(Integer value) {
        this.requiredDays = value;
    }
}
