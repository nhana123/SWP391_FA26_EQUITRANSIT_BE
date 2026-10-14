/*
 * CaseAssignmentRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.request;


import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


/**
 * Replacement responsibility command; absence of metadata clears that field.
 * @param recordVersion the observed parent version
 * @param operationId stable UUID for retries
 * @param staffId active staff identity of the role selected in the path
 * @param reason mandatory explanation for the audit history
 * @param dueAt optional deadline, not a travel availability promise
 * @param assignmentNote staff-only instructions
 */
public record CaseAssignmentRequest(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{16}") String recordVersion,
        @NotNull UUID operationId, @Positive long staffId,
        @NotBlank @Size(max = 1000) String reason,
        OffsetDateTime dueAt, @Size(max = 1000) String assignmentNote) {
}
