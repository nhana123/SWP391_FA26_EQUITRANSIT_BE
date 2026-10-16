/*
 * QuoteCommand
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;


/** Typed quotation API contract. */
@Schema(example = QuoteApiExamples.COMMAND)
public record QuoteCommand(
        @Schema(example = "000000000001A2B3",
                minLength = 16, maxLength = 16)
        @NotNull
        @Pattern(regexp = "^[0-9a-fA-F]{16}$")
        String recordVersion,
        @Schema(example = "d86b4f63-5acc-4f20-8da3-e24323268814")
        @NotNull
        UUID operationId) {
}
