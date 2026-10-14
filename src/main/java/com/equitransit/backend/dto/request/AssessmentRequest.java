/*
 * AssessmentRequest
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
public record AssessmentRequest(
        @Positive long revisionId,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{16}") String recordVersion,
        @NotNull UUID operationId, @NotNull DecisionCode decision,
        @Size(max = 1000) String reason,
        @Positive Long specialistId, @Positive Long coordinatorId,
        OffsetDateTime dueAt, @Size(max = 1000) String assignmentNote) {
}
