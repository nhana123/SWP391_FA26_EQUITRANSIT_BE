/*
 * AssessmentRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiPatterns;
import com.equitransit.backend.dto.validation.OptionalText;
import com.equitransit.backend.dto.ApiExamples;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.entity.DecisionCode;


/**
 * Manager command bound to the exact revision and observed parent version.
 * @param revisionId the revision actually reviewed
 * @param recordVersion the last observed order version
 * @param operationId the client-generated retry UUID
 * @param decision the allowed assessment outcome
 * @param reason public clarification/rejection reason, optional for approval
 * @param specialistId active Specialist StaffID, required for approval
 * @param coordinatorId active Coordinator StaffID, required for approval
 * @param dueAt optional case deadline, not a booked travel interval
 * @param assignmentNote staff-only preparation instructions
 */
@Schema(example = ApiExamples.ASSESSMENT)
public record AssessmentRequest(
        @Schema(
                example = "201",
                minimum = "1")
        @Positive long revisionId,
        @Schema(
                example = "000000000001A2B3",
                pattern = ApiPatterns.VERSION)
        @NotBlank @Size(min = 1, max = 16)
        @Pattern(regexp = ApiPatterns.VERSION) String recordVersion,
        @Schema(
                example = "fa2c8d7b-6c51-4976-a28d-613c4199cfd6",
                format = "uuid")
        @NotNull UUID operationId,
        @Schema(
                example = "CHANGES_REQUESTED")
        @NotNull DecisionCode decision,
        @Schema(
                example = "Please confirm the route.",
                nullable = true)
        @OptionalText @Size(min = 1, max = 1000) String reason,
        @Schema(
                example = "31",
                nullable = true,
                minimum = "1")
        @Positive Long specialistId,
        @Schema(
                example = "32",
                nullable = true,
                minimum = "1")
        @Positive Long coordinatorId,
        @Schema(
                example = "2026-10-16T18:00:00Z",
                nullable = true,
                format = "date-time")
        OffsetDateTime dueAt,
        @Schema(
                example = "Review route conditions.",
                nullable = true)
        @OptionalText @Size(min = 1, max = 1000) String assignmentNote) {
}
