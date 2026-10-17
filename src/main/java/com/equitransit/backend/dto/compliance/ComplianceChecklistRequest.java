/*
 * ComplianceChecklistRequest
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
import java.util.List;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.entity.compliance.CompliancePhase;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceChecklistRequest(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "PRE_EXPORT")
        @NotNull CompliancePhase phase,
        @Schema(example = "[\"SG\"]")
        @NotNull @Size(max = 15)
        List<@Pattern(regexp = "^[A-Z]{2}$") String> transitCountryCodes,
        @Schema(example = "Authority route assessment reference")
        @NotBlank @Size(min = 1, max = 1000) String sourceReference,
        @Schema(example = "2027-02-20T12:00:00Z")
        @NotNull OffsetDateTime validUntil) {
}
