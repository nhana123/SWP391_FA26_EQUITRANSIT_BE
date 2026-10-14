/*
 * QuarantineAssessmentResult
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.entity;


/**
 * Internal sourced service contract.
 * @param applicability REQUIRED, NOT_REQUIRED or UNKNOWN
 * @param requiredDays Specialist duration, never client-controlled
 * @param assessmentId source assessment or null
 */
public record QuarantineAssessmentResult(
        String applicability, Integer requiredDays,
        Long assessmentId) {
}
