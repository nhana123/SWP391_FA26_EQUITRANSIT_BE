/*
 * ComplianceAuthorityRequest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import com.equitransit.backend.dto.validation.OptionalText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.entity.compliance.AuthorityDecision;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceAuthorityRequest(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "101")
        @Positive long ruleId,
        @Schema(example = "APPROVED")
        @NotNull AuthorityDecision decision,
        @Schema(example = "Competent veterinary authority")
        @NotBlank @Size(min = 1, max = 300) String authorityName,
        @Schema(example = "AUTH-2027-001")
        @NotBlank @Size(min = 1, max = 150) String applicationReference,
        @Schema(example = "Authority decision reference")
        @NotBlank @Size(min = 1, max = 1000) String sourceReference,
        @Schema(example = "84032724-eac7-45df-8d04-e54f092279d0",
                nullable = true)
        UUID evidenceFileKey,
        @Schema(example = "2026-10-16T12:00:00Z",
                nullable = true)
        OffsetDateTime decidedAt,
        @Schema(example = "2027-03-01T12:00:00Z",
                nullable = true)
        OffsetDateTime expiresAt,
        @Schema(example = "Awaiting authority response.",
                nullable = true)
        @OptionalText @Size(min = 1, max = 2000) String holdReason) {
}
