/*
 * QuoteEvidence
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import com.equitransit.backend.entity.quote.QuoteChargeCode;
import com.equitransit.backend.entity.quote.QuoteEvidenceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;


/** Typed quotation API contract. */
@Schema(example = QuoteApiExamples.EVIDENCE)
public record QuoteEvidence(
        @Schema(example = "AIRFARE")
        @NotNull
        QuoteChargeCode code,
        @Schema(example = "PRICED")
        @NotNull
        QuoteEvidenceStatus status,
        @Schema(example = "1", nullable = true,
                pattern = "^\\d+(\\.\\d{1,3})?$", type = "string")
        @Size(min = 1, max = 20)
        @Pattern(regexp = "^\\d+(\\.\\d{1,3})?$")
        String quantity,
        @Schema(example = "41", nullable = true)
        @Positive
        Long tariffId,
        @Schema(example = "51", nullable = true)
        @Positive
        Long offerId,
        @Schema(example = "Verified quotation evidence REF-2027-001")
        @NotBlank
        @Size(min = 1, max = 1000)
        String sourceReference,
        @Schema(example = "2027-02-01T08:00:00Z")
        @NotNull
        OffsetDateTime validUntil,
        @Schema(example = "Verified customer retail quotation.")
        @NotBlank
        @Size(min = 1, max = 1000)
        String reason) {
}
