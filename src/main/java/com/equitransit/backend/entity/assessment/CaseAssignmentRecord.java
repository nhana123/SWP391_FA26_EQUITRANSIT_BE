/*
 * CaseAssignmentRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.entity.assessment;


import java.time.OffsetDateTime;


/**
 * Internal typed SQL projection, separate from HTTP response contracts.
 * @param assignmentId assignment history key
 * @param staffId responsible staff key
 * @param role preparation role
 * @param assignedAt UTC start
 * @param endedAt UTC revocation, null for active
 * @param assignedBy recorded assigning Manager, null for legacy data
 * @param dueAt optional deadline
 * @param assignmentNote staff-only instructions
 */
public record CaseAssignmentRecord(
        long assignmentId, long staffId, String role,
        OffsetDateTime assignedAt, OffsetDateTime endedAt, Long assignedBy,
        OffsetDateTime dueAt, String assignmentNote) {
}
