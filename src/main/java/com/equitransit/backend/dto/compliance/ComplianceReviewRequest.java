/*
 * ComplianceReviewRequest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import com.equitransit.backend.dto.validation.OptionalText;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.entity.compliance.LegalDecision;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceReviewRequest(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "APPROVED")
        @NotNull LegalDecision decision,
        @Schema(example = "Evidence matches the requirement.",
                nullable = true)
        @JsonAlias({"notes", "reason"})
        @OptionalText @Size(min = 1, max = 2000) String feedback) {
}
