/*
 * TariffPage
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.dto.pricing;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;


/** Bounded manager history; expired versions remain visible. */
public record TariffPage(
        @Schema(example = "[]") List<TariffResponse> items,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "1") long totalElements,
        @Schema(example = "1") long totalPages) {
}
