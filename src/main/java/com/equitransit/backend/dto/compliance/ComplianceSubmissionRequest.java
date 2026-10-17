/*
 * ComplianceSubmissionRequest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.equitransit.backend.dto.quote.QuoteCommand;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceSubmissionRequest(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "101")
        @Positive long documentVersionId) {
}
