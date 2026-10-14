/*
 * CaseAssignmentResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(
                example = "31",
                minimum = "1")
        long assignmentId,
        @Schema(
                example = "31",
                minimum = "1")
        long staffId,
        @Schema(
                example = "SPECIALIST",
                allowableValues = {"SPECIALIST", "COORDINATOR"})
        String role,
        @Schema(
                example = "2026-10-14T14:05:00Z",
                format = "date-time")
        OffsetDateTime assignedAt,
        @Schema(
                example = "2026-10-17T09:00:00Z",
                nullable = true,
                format = "date-time")
        OffsetDateTime endedAt,
        @Schema(
                example = "31",
                nullable = true)
        Long assignedBy,
        @Schema(
                example = "2026-10-16T18:00:00Z",
                nullable = true,
                format = "date-time")
        OffsetDateTime dueAt,
        @Schema(
                example = "Review route conditions.",
                nullable = true)
        String assignmentNote) {
}
