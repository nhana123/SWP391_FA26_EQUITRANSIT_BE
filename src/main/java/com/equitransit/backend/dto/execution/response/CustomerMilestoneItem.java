/*
 * CustomerMilestoneItem
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Safe customer-facing milestone projection with redacted notes.
 */
package com.equitransit.backend.dto.execution.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/** Public milestone item for customer journey tracking (redacted). */
public record CustomerMilestoneItem(
        @Schema(example = "101") @Positive long milestoneId,
        @Schema(example = "REST_STOP") @NotBlank String milestoneType,
        @Schema(example = "2026-10-18T08:30:00Z")
        @NotNull OffsetDateTime occurredAt,
        @Schema(example = "Highway 1A Rest Area Km 45")
        String locationDescription) { }
