/*
 * SupplierOfferRequest
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import com.equitransit.backend.entity.quote.QuoteChargeCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;


/** Typed quotation API contract. */
@Schema(example = QuoteApiExamples.OFFER)
public record SupplierOfferRequest(
        @Schema(example = QuoteApiExamples.COMMAND)
        @NotNull @Valid
        QuoteCommand command,
        @Schema(example = "201", minimum = "1")
        @Positive
        long revisionId,
        @Schema(example = "AIRFARE")
        @NotNull
        QuoteChargeCode serviceCode,
        @Schema(example = "Verified Supplier")
        @NotBlank
        @Size(min = 1, max = 200)
        String providerName,
        @Schema(example = "SUPPLIER-2027-001")
        @NotBlank
        @Size(min = 1, max = 100)
        String offerReference,
        @Schema(example = "SHIPMENT")
        @NotBlank
        @Size(min = 1, max = 30)
        String unitCode,
        @Schema(example = "1",
                pattern = "^\\d+(\\.\\d{1,3})?$", type = "string")
        @NotBlank @Size(min = 1, max = 20)
        @Pattern(regexp = "^\\d+(\\.\\d{1,3})?$")
        String quantity,
        @Schema(example = "900.00",
                pattern = "^\\d+(\\.\\d{1,2})?$", type = "string")
        @NotBlank @Size(min = 1, max = 19)
        @Pattern(regexp = "^\\d+(\\.\\d{1,2})?$")
        String purchaseUnitPrice,
        @Schema(example = "1000.00",
                pattern = "^\\d+(\\.\\d{1,2})?$", type = "string")
        @NotBlank @Size(min = 1, max = 19)
        @Pattern(regexp = "^\\d+(\\.\\d{1,2})?$")
        String retailUnitPrice,
        @Schema(example = "2027-02-01T08:00:00Z")
        @NotNull
        OffsetDateTime validUntil,
        @Schema(example = "evidence/verified-offer-001.pdf")
        @NotBlank
        @Size(min = 1, max = 1024)
        String evidenceFileKey,
        @Schema(example = "false")
        boolean includesStall,
        @Schema(example = "[]")
        @NotNull @Size(max = 4)
        List<@NotNull QuoteChargeCode> includedCharges) {
}
