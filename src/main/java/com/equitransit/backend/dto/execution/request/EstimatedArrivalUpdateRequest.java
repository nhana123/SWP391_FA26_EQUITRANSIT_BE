/*
 * EstimatedArrivalUpdateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO to update estimated arrival time and delay reason.
 */
package com.equitransit.backend.dto.execution.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Request payload to update leg arrival estimate and delay reason. */
public record EstimatedArrivalUpdateRequest(
        @Schema(example = "2026-10-18T18:00:00Z")
        @NotNull OffsetDateTime estimatedArrivalAt,
        @Schema(example = "Flight delay due to adverse weather.")
        @NotBlank @Size(max = 1000) String delayReason) { }
