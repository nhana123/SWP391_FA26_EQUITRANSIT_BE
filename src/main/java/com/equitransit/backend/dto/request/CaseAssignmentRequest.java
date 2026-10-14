/*
 * CaseAssignmentRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiPatterns;
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
        @Schema(
                example = "000000000001A2B3",
                pattern = ApiPatterns.VERSION)
        @NotBlank @Pattern(regexp = ApiPatterns.VERSION) String recordVersion,
        @Schema(
                example = "fa2c8d7b-6c51-4976-a28d-613c4199cfd6",
                format = "uuid")
        @NotNull UUID operationId,
        @Schema(
                example = "31",
                nullable = true,
                minimum = "1")
        @Positive long staffId,
        @Schema(
                example = "Please confirm the route.",
                nullable = true)
        @NotBlank @Size(max = 1000) String reason,
        @Schema(
                example = "2026-10-16T18:00:00Z",
                nullable = true,
                format = "date-time")
        OffsetDateTime dueAt,
        @Schema(
                example = "Review route conditions.",
                nullable = true)
        @Size(max = 1000) String assignmentNote) {
}
