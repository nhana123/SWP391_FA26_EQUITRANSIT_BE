/*
 * QuoteSummary
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import com.equitransit.backend.entity.quote.QuoteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;


/** Typed quotation API contract. */
@Schema(example = QuoteApiExamples.SUMMARY)
public record QuoteSummary(
        @Schema(example = "301", minimum = "1")
        long quoteId,
        @Schema(example = "201", minimum = "1")
        long revisionId,
        @Schema(example = "Q-101-1")
        @NotBlank
        @Size(min = 1, max = 30)
        String quoteNumber,
        @Schema(example = "1", minimum = "1")
        int version,
        @Schema(example = "ISSUED")
        QuoteStatus status,
        @Schema(example = "1500.00", nullable = true,
                pattern = "^\\d+(\\.\\d{1,2})?$", type = "string")
        String total,
        @Schema(example = "2027-01-20T08:00:00Z")
        OffsetDateTime validUntil) {
}
