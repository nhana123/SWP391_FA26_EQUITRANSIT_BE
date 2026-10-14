/*
 * CaseAssignmentResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


import java.time.OffsetDateTime;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param assignmentId assignment history key
 * @param staffId responsible staff key
 * @param role preparation role
 * @param assignedAt UTC start
 * @param endedAt UTC revocation, null for active
 * @param assignedBy recorded assigning Manager, null for legacy data
 * @param dueAt optional deadline
 * @param assignmentNote staff-only instructions
 */
public record CaseAssignmentResponse(
        long assignmentId, long staffId, String role,
        OffsetDateTime assignedAt, OffsetDateTime endedAt, Long assignedBy,
        OffsetDateTime dueAt, String assignmentNote) {
}
