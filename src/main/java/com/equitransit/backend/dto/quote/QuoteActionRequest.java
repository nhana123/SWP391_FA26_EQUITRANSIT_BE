/*
 * QuoteActionRequest
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import com.equitransit.backend.entity.quote.QuoteAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;


/** Typed quotation API contract. */
@Schema(example = QuoteApiExamples.ACTION)
public record QuoteActionRequest(
        @Schema(example = QuoteApiExamples.COMMAND)
        @NotNull @Valid
        QuoteCommand command,
        @Schema(example = "APPROVE")
        @NotNull
        QuoteAction action) {
}
