/*
 * SupplierOfferReceipt
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import io.swagger.v3.oas.annotations.media.Schema;


/** Typed quotation API contract. */
public record SupplierOfferReceipt(
        @Schema(example = "51", minimum = "1")
        long offerId,
        @Schema(example = "000000000001A2B3",
                pattern = "^[0-9a-fA-F]{16}$")
        String recordVersion) {
}
