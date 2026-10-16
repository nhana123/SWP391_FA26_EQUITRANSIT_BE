/*
 * QuoteResponse
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;


/** Typed quotation API contract. */
@Schema(example = QuoteApiExamples.RESPONSE)
public record QuoteResponse(
        @Schema(example = QuoteApiExamples.SUMMARY)
        QuoteSummary quote,
        @Schema(example = "000000000001A2B3",
                pattern = "^[0-9a-fA-F]{16}$")
        String recordVersion,
        @Schema(example = "USD", allowableValues = {"USD"})
        String currency,
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
        @Schema(example = "2027-01-10T08:00:00Z", nullable = true)
        OffsetDateTime issuedAt,
        @Schema(example = "2027-01-11T08:00:00Z", nullable = true)
        OffsetDateTime acceptedAt,
        @Schema(example = "[]")
        List<QuoteLine> items,
        @Schema(example = "[]")
        List<QuoteSchedule> paymentSchedule,
        @Schema(example = "[]")
        List<String> blockers) {
}
