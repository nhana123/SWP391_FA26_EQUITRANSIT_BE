/*
 * QuotePage
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;


/** Typed quotation API contract. */
public record QuotePage(
        @Schema(example = "[]")
        List<QuoteSummary> items,
        @Schema(example = "0", minimum = "0")
        int page,
        @Schema(example = "20", minimum = "1", maximum = "100")
        int size,
        @Schema(example = "1", minimum = "0")
        long totalElements) {
}
