/*
 * ComplianceAssessmentRequest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.OffsetDateTime;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.entity.compliance.
        QuarantineApplicability;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceAssessmentRequest(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "REQUIRED")
        @NotNull QuarantineApplicability applicability,
        @Schema(example = "14",
                nullable = true)
        @Min(1) @Max(3650) Integer requiredDays,
        @Schema(example = "1")
        @NotNull @Min(0) @Max(10000) Integer visitCount,
        @Schema(example = "2")
        @NotNull @Min(0) @Max(10000) Integer testCount,
        @Schema(example = "1")
        @NotNull @Min(0) @Max(10000) Integer doseCount,
        @Schema(example = "1")
        @NotNull @Min(0) @Max(10000) Integer certificateCount,
        @Schema(example = "Specialist assessment with authority references")
        @NotBlank @Size(min = 1, max = 1000) String sourceReference,
        @Schema(example = "2027-02-20T12:00:00Z")
        @NotNull OffsetDateTime validUntil) {
}
