/*
 * TariffWriteRequest
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.dto.pricing;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.equitransit.backend.entity.pricing.ServiceProduct;
import com.equitransit.backend.entity.pricing.TariffUnit;


/**
 * Creates a retail version; PUT creates a successor instead of rewriting it.
 */
public record TariffWriteRequest(
        @Schema(example = "ROAD_TRANSPORT") @NotNull
        ServiceProduct product,
        @Schema(example = "2.00", pattern = "^\\d+(\\.\\d{1,2})?$",
                type = "string")
        @NotBlank @Size(min = 1, max = 18)
        @Pattern(regexp = "^\\d+(\\.\\d{1,2})?$")
        String unitPrice,
        @Schema(example = "Approved company tariff, October 2026")
        @NotBlank @Size(min = 1, max = 1000)
        String sourceReference,
        @Schema(example = "2027-12-31T00:00:00Z", nullable = true)
        OffsetDateTime validUntil,
        @Schema(example = "1f48f6d1-2280-4c4f-a56c-86dcab0af9cf") @NotNull
        UUID operationId,
        @Schema(example = "000000000001A2B3", nullable = true,
                pattern = "^[0-9a-fA-F]{16}$")
        @Pattern(regexp = "^[0-9a-fA-F]{16}$")
        String recordVersion,
        @Schema(example = "KM", description = "Omit for the legacy create "
                + "default, or to preserve the unit when replacing a version")
        @com.fasterxml.jackson.annotation.JsonInclude(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        TariffUnit unitCode) {

    /** @return selected unit or the backward-compatible product default */
    public String effectiveUnit() {
        return unitCode == null ? product.unit() : unitCode.name();
    }
}
