/*
 * OrderServiceIntent
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.entity.order;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.common.ApiPatterns;
import com.equitransit.backend.dto.common.ApiExamples;
import com.equitransit.backend.dto.pricing.response.RequestedServiceInputsSchema;
import java.util.Map;




/**
 * Requested service input, not pricing, fit or external confirmation.
 * @param serviceCode the serviceCode
 * @param optionCode the optionCode
 * @param arrangedBy the arrangedBy
 * @param requestedInputs the requestedInputs
 * @param providerName the providerName
 * @param providerContact the providerContact
 */
@Schema(example = ApiExamples.INTENT)
public record OrderServiceIntent(
        @Schema(
                example = "AIR_STALL")
        String serviceCode,
        @Schema(
                example = "WIDE",
                allowableValues = {
                        "EQUITRANSIT",
                        "CUSTOMER",
                        "SHARED_ONE",
                        "WIDE",
                        "PRIVATE",
                        "ENHANCED",
                        "EXTRA",
                        "STANDARD",
                        "NONE",
                        "EXISTING",
                        "MORTALITY",
                        "COMBINED",
                        "REQUESTED"})
        String optionCode,
        @Schema(
                example = "EQUITRANSIT",
                allowableValues = {"EQUITRANSIT", "CUSTOMER"})
        String arrangedBy,
        @Schema(
                example = ApiExamples.INPUTS,
                implementation = RequestedServiceInputsSchema.class,
                additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
        Map<String, Object> requestedInputs,
        @Schema(
                example = "Customer road provider",
                nullable = true)
        String providerName,
        @Schema(
                example = "+84901234567",
                nullable = true,
                pattern = ApiPatterns.PHONE)
        String providerContact) {
}
