/*
 * AirMilestoneCreateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO for air transport milestones logged by coordinator.
 */
package com.equitransit.backend.dto.execution.request;

import com.equitransit.backend.dto.execution.AirMilestoneType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Request payload submitted by coordinator for an air leg milestone. */
public record AirMilestoneCreateRequest(
        @Schema(example = "WHEELS_UP")
        @NotNull AirMilestoneType milestoneType,
        @Schema(example = "2026-10-18T04:15:00Z")
        OffsetDateTime occurredAt,
        @Schema(example = "CARRIER_API")
        @NotBlank @Size(max = 30) String sourceCode,
        @Schema(example = "AWB-618-91234567")
        @NotBlank @Size(max = 500) String sourceReference,
        @Schema(example = "Noi Bai International Airport (HAN)")
        @Size(max = 500) String locationDescription,
        @Schema(example = "Flight VN123 departed on schedule.")
        @Size(max = 2000) String notes,
        @Schema(example = "21.221200")
        @DecimalMin("-90.000000") @DecimalMax("90.000000")
        BigDecimal latitude,
        @Schema(example = "105.807200")
        @DecimalMin("-180.000000") @DecimalMax("180.000000")
        BigDecimal longitude,
        @Schema(example = "f12-evidence-flight-dispatch.pdf")
        @Size(max = 1024) String evidenceFileKey) { }
