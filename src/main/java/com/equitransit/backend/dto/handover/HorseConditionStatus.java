/*
 * HorseConditionStatus
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Classify physical welfare condition at the moment of handover.
 */
package com.equitransit.backend.dto.handover;

/**
 * Observed veterinary & physical condition of horse upon handover.
 */
public enum HorseConditionStatus {

    /** Optimal physical state, calm, alert, hydrated, normal vitals. */
    EXCELLENT,

    /** Healthy condition, minor transit fatigue, normal vitals. */
    GOOD,

    /** Visible fatigue or minor stiffness, stable vitals. */
    FAIR,

    /** Sustained injury or trauma requiring veterinary attention. */
    INJURED,

    /** Severe psychological distress, colic risk, or critical illness. */
    DISTRESSED
}
