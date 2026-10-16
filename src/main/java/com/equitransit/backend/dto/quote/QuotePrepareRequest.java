/*
 * QuotePrepareRequest
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;


/** Typed quotation API contract. */
@Schema(example = QuoteApiExamples.PREPARE)
public record QuotePrepareRequest(
        @Schema(example = QuoteApiExamples.COMMAND)
        @NotNull @Valid
        QuoteCommand command,
        @Schema(example = "201", minimum = "1")
        @Positive
        long revisionId,
        @Schema(example = "2027-01-20T08:00:00Z")
        @NotNull
        OffsetDateTime validUntil,
        @Schema(example = "TERMS-2027-01")
        @NotBlank
        @Size(min = 1, max = 50)
        String termsVersion,
        @Schema(example = "Payment follows the agreed schedule.")
        @NotBlank
        @Size(min = 1, max = 10000)
        String terms,
        @Schema(example = "Cancellation fees follow the accepted terms.")
        @NotBlank
        @Size(min = 1, max = 10000)
        String cancellationTerms,
        @Schema(example = QuoteApiExamples.SCHEDULES)
        @NotNull @Size(min = 1, max = 12)
        List<@NotNull @Valid QuoteSchedule> paymentSchedule,
        @Schema(example = "[]")
        @NotNull @Size(max = 20)
        List<@NotNull @Valid QuoteEvidence> assessments) {
}
