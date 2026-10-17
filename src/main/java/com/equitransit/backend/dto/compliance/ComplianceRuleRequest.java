/*
 * ComplianceRuleRequest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.entity.compliance.CompliancePhase;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceRuleRequest(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "PRE_EXPORT")
        @NotNull CompliancePhase phase,
        @Schema(example = "VN-GB-HEALTH")
        @NotBlank @Size(min = 1, max = 50)
        @Pattern(regexp = "^[A-Z0-9_-]{1,50}$") String ruleCode,
        @Schema(example = "HEALTH_CERTIFICATE")
        @NotBlank @Size(min = 1, max = 50)
        @Pattern(regexp = "^[A-Z0-9_-]{1,50}$") String documentType,
        @Schema(example = "Veterinary health certificate")
        @NotBlank @Size(min = 1, max = 200) String displayName,
        @Schema(example = "SG",
                nullable = true)
        @Pattern(regexp = "^[A-Z]{2}$") String transitCountryCode,
        @Schema(example = "https://authority.example/rules/health")
        @NotBlank @Size(min = 1, max = 2048) String sourceUrl,
        @Schema(example = "2026-10-01T00:00:00Z")
        @NotNull OffsetDateTime effectiveFrom,
        @Schema(example = "2027-03-01T12:00:00Z",
                nullable = true)
        OffsetDateTime effectiveTo,
        @Schema(example = "Upload the full certificate issued by authority.",
                nullable = true)
        @com.equitransit.backend.dto.validation.OptionalText
        @Size(min = 1, max = 2000) String instructions,
        @Schema(example = "true")
        boolean authorityRequired) {
}
