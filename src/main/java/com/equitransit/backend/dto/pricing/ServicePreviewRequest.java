/*
 * ServicePreviewRequest
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.dto.pricing;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import com.equitransit.backend.dto.common.ApiExamples;
import com.equitransit.backend.dto.order.request.OrderServicePreferences;


/**
 * Complete unsaved preferences; no rate, quantity or status from the client.
 */
public record ServicePreviewRequest(
        @Schema(example = "000000000001A2B3",
                pattern = "^[0-9a-fA-F]{16}$")
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{16}$")
        String recordVersion,
        @Schema(example = "000000000001A2B4", nullable = true,
                pattern = "^[0-9a-fA-F]{16}$")
        @Pattern(regexp = "^[0-9a-fA-F]{16}$") String horseRecordVersion,
        @Schema(example = ApiExamples.SERVICES) @NotNull @Valid
        OrderServicePreferences services) {
}
