/*
 * RoadMilestoneCreateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO for road transport milestones logged by driver.
 */
package com.equitransit.backend.dto.execution.request;

import com.equitransit.backend.dto.execution.RoadMilestoneType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Request payload submitted by assigned driver on a road leg. */
public record RoadMilestoneCreateRequest(
        @Schema(example = "REST_STOP")
        @NotNull RoadMilestoneType milestoneType,
        @Schema(example = "2026-10-18T08:30:00Z")
        OffsetDateTime occurredAt,
        @Schema(example = "21.028511")
        @DecimalMin("-90.000000") @DecimalMax("90.000000")
        BigDecimal latitude,
        @Schema(example = "105.854444")
        @DecimalMin("-180.000000") @DecimalMax("180.000000")
        BigDecimal longitude,
        @Schema(example = "Scheduled rest stop; horse hydrated.")
        @Size(max = 2000) String notes,
        @Schema(example = "Highway 1A Rest Area Km 45")
        @Size(max = 500) String locationDescription,
        @Schema(example = "Heavy highway traffic")
        @Size(max = 1000) String delayReason,
        @Schema(example = "f12-evidence-road-stop-01.jpg")
        @Size(max = 1024) String evidenceFileKey) { }
