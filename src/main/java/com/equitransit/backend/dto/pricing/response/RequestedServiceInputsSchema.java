/*
 * RequestedServiceInputsSchema
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Describe version-one selection inputs without untyped properties.
 */
package com.equitransit.backend.dto.pricing.response;


import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;


/**
 * Documentation schema; persisted service maps retain their exact JSON shape.
 * Only fields applicable to a service are present in its requestedInputs.
 * @param schemaVersion envelope version
 * @param status request intent, not confirmation
 * @param requestedDays extra groom days
 * @param requestedNights extra boarding nights
 * @param slotQuantity requested configuration slots
 * @param fitStatus measurement result, not carrier acceptance
 * @param policyId sourced fit policy key
 * @param policyVersion sourced policy version
 * @param carrierConfirmation carrier readiness remains separate
 * @param requiredDaysStatus quarantine source is pending for a new revision
 * @param declaredValue horse value, not premium
 * @param valueCurrency declared value currency
 * @param existingPolicyReference reference awaiting verification
 * @param notes bounded requested escort instructions
 */
public record RequestedServiceInputsSchema(
        @Schema(example = "1", minimum = "1") Integer schemaVersion,
        @Schema(example = "REQUESTED", allowableValues = {"REQUESTED"})
        String status,
        @Schema(example = "2", minimum = "1", nullable = true)
        Integer requestedDays,
        @Schema(example = "1", minimum = "1", nullable = true)
        Integer requestedNights,
        @Schema(example = "1.5",
                description = "Allowed values: 1, 1.5, 3",
                nullable = true) BigDecimal slotQuantity,
        @Schema(example = "PENDING", allowableValues = {"SUITABLE", "PENDING"},
                nullable = true) String fitStatus,
        @Schema(example = "51", minimum = "1", nullable = true) Long policyId,
        @Schema(example = "1", minimum = "1", nullable = true)
        Integer policyVersion,
        @Schema(example = "NOT_CONFIRMED",
                allowableValues = {"NOT_CONFIRMED"}, nullable = true)
        String carrierConfirmation,
        @Schema(example = "UNKNOWN", allowableValues = {"UNKNOWN"},
                nullable = true) String requiredDaysStatus,
        @Schema(example = "50000.00", type = "string", nullable = true,
                pattern = "^\\d+(\\.\\d{1,2})?$") String declaredValue,
        @Schema(example = "USD", pattern = "^[A-Z]{3}$", nullable = true)
        String valueCurrency,
        @Schema(example = "POL-2026-0147", nullable = true)
        String existingPolicyReference,
        @Schema(example = "Escort requested for airport handover.",
                nullable = true) String notes) {
}
