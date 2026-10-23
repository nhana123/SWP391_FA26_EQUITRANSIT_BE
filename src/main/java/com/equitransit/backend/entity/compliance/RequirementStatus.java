/*
 * RequirementStatus
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.entity.compliance;





/** Checklist lifecycle codes. */
public enum RequirementStatus {
    PENDING, SUBMITTED, APPROVED, REJECTED, EXPIRED, REVOKED, SUPERSEDED
}
