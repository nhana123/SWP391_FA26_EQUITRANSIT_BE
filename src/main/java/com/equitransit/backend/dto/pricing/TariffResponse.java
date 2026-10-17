/*
 * TariffResponse
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.dto.pricing;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import com.equitransit.backend.entity.pricing.ServiceProduct;
import com.equitransit.backend.entity.pricing.TariffStatus;


/** Manager-only tariff provenance. No supplier purchase cost is stored here. */
public record TariffResponse(
        @Schema(example = "101") long tariffId,
        @Schema(example = "ROAD_TRANSPORT", nullable = true)
        ServiceProduct product,
        @Schema(example = "KM") String unit,
        @Schema(example = "2.00", nullable = true,
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String unitPrice,
        @Schema(example = "USD", allowableValues = {"USD"}) String currency,
        @Schema(example = "1") int version,
        @Schema(example = "2026-10-16T10:00:00Z") OffsetDateTime effectiveFrom,
        @Schema(example = "2027-12-31T00:00:00Z", nullable = true)
        OffsetDateTime effectiveTo,
        @Schema(example = "ACTIVE") TariffStatus status,
        @Schema(example = "Approved company tariff", nullable = true)
        String sourceReference,
        @Schema(example = "false") boolean sourceReviewRequired,
        @Schema(example = "000000000001A2B3",
                pattern = "^[0-9a-fA-F]{16}$") String recordVersion) {
}
