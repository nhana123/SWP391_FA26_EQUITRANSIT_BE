package com.equitransit.backend.dto.compliance.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Explicit specialist policy for a reviewed source rule.
 * @param scopeCode HORSE or SHIPMENT
 * @param mandatory whether fulfillment is mandatory
 * @param validityBasis validity through departure or delivery
 * @param instructions public instructions
 */
public record ComplianceRulePolicyRequest(
        @NotBlank @Pattern(regexp = "HORSE|SHIPMENT") String scopeCode,
        boolean mandatory,
        @NotBlank @Pattern(regexp = "DEPARTURE|DELIVERY") String validityBasis,
        @Size(max = 2000) String instructions) {
}
