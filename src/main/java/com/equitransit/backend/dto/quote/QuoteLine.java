/*
 * QuoteLine
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;


/** Typed quotation API contract. */
@Schema(example = QuoteApiExamples.LINE)
public record QuoteLine(
        @Schema(example = "ROAD_TRANSPORT")
        @NotBlank
        @Size(min = 1, max = 50)
        String code,
        @Schema(example = "TRANSPORTATION")
        @NotBlank
        @Size(min = 1, max = 50)
        String costGroup,
        @Schema(example = "Road transport")
        @NotBlank
        @Size(min = 1, max = 2000)
        String description,
        @Schema(example = "KM")
        @NotBlank
        @Size(min = 1, max = 50)
        String unit,
        @Schema(example = "100.125", nullable = true,
                pattern = "^\\d+(\\.\\d{1,3})?$", type = "string")
        String quantity,
        @Schema(example = "2.00", nullable = true,
                pattern = "^\\d+(\\.\\d{1,2})?$", type = "string")
        String unitRate,
        @Schema(example = "200.25", nullable = true,
                pattern = "^\\d+(\\.\\d{1,2})?$", type = "string")
        String amount,
        @Schema(example = "PRICED",
                allowableValues = {"PRICED", "INCLUDED", "PENDING",
                        "NOT_REQUIRED", "EXTERNAL_PROVIDER",
                        "NOT_SELECTED", "EVENT_ONLY"})
        String pricingStatus,
        @Schema(example = "[]")
        List<String> blockers) {
}
