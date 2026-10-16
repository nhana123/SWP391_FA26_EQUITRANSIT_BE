/*
 * QuoteSchedule
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;


/** Typed quotation API contract. */
@Schema(example = QuoteApiExamples.SCHEDULE)
public record QuoteSchedule(
        @Schema(example = "1", minimum = "1")
        @Positive
        int sequenceNo,
        @Schema(example = "DEPOSIT")
        @NotBlank
        @Size(min = 1, max = 50)
        String paymentStage,
        @Schema(example = "1500.00",
                pattern = "^\\d+(\\.\\d{1,2})?$", type = "string")
        @NotBlank
        @Size(min = 1, max = 19)
        @Pattern(regexp = "^\\d+(\\.\\d{1,2})?$")
        String amountDue,
        @Schema(example = "2027-01-15T08:00:00Z")
        @NotNull
        OffsetDateTime dueAt) {
}
