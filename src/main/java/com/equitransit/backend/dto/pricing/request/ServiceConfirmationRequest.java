/*
 * ServiceConfirmationRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.dto.pricing.request;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.common.ApiPatterns;
import com.equitransit.backend.dto.common.ApiExamples;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.dto.order.request.OrderServicePreferences;


/**
 * Full preference replacement; cannot supply prices or compulsory quantities.
 * @param recordVersion observed order rowversion
 * @param operationId retry UUID reused with exactly the same input
 * @param horseRecordVersion measurement version shown in this booking
 * @param services explicit bounded preferences, never a new horse selection
 * @param changeReason required for submitted replacements, optional for drafts
 */
@Schema(example = ApiExamples.SERVICE_CONFIRM)
public record ServiceConfirmationRequest(
        @Schema(
                example = "000000000001A2B3",
                pattern = ApiPatterns.VERSION)
        @NotBlank @Pattern(regexp = ApiPatterns.VERSION) String recordVersion,
        @Schema(
                example = "fa2c8d7b-6c51-4976-a28d-613c4199cfd6",
                format = "uuid")
        @NotNull UUID operationId,
        @Schema(
                example = "000000000001A2B3",
                pattern = ApiPatterns.VERSION)
        @NotBlank @Pattern(regexp = ApiPatterns.VERSION)
        String horseRecordVersion,
        @Schema(
                example = ApiExamples.SERVICES,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid OrderServicePreferences services,
        @Schema(
                example = "Update delivery schedule.",
                nullable = true)
        @Size(max = 500) String changeReason) {
}
