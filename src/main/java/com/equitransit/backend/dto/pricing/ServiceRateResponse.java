/*
 * ServiceRateResponse
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.dto.pricing;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.entity.pricing.ServiceProduct;
import com.equitransit.backend.entity.pricing.PricingMode;


/** Public retail unit rate. Missing quantities are not fabricated. */
public record ServiceRateResponse(
        @Schema(example = "ROAD_TRANSPORT") ServiceProduct product,
        @Schema(example = "TRANSPORTATION") String uiSection,
        @Schema(example = "KM") String unit,
        @Schema(example = "2.00", nullable = true,
                pattern = "^\\d+(\\.\\d{1,2})?$") String unitRate,
        @Schema(example = "USD", allowableValues = {"USD"}) String currency,
        @Schema(example = "RATE") PricingMode pricingMode,
        @Schema(example = "31", nullable = true) Long tariffId,
        @Schema(example = "1", nullable = true) Integer tariffVersion) {

    /** @return units the Manager may publish for this product */
    @Schema(example = "[\"KM\"]")
    public java.util.List<com.equitransit.backend.entity.pricing.TariffUnit>
            getAllowedUnits() {
        return product.allowedUnits();
    }
}
