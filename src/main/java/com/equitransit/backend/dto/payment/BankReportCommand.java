/*
 * BankReportCommand
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import com.equitransit.backend.dto.quote.QuoteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;


/** Validated manual payment command. */
public record BankReportCommand(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "101", minimum = "1")
        @NotNull @Positive Long paymentRequestId,
        @Schema(example = "1500.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        @NotBlank @Size(min = 1, max = 19)
        @Pattern(regexp = "^\\d+(\\.\\d{1,2})?$") String reportedAmount,
        @Schema(example = "USD", allowableValues = {"USD"})
        @NotBlank @Pattern(regexp = "^USD$") String reportedCurrency,
        @Schema(example = "2026-10-17T08:00:00Z")
        @NotNull
        OffsetDateTime transferredAt,
        @Schema(example = "BANK-2026-001")
        @NotBlank
        @Size(min = 1, max = 150) String bankReference,
        @Schema(example = "d86b4f63-5acc-4f20-8da3-e24323268814")
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String proofFileKey) {
}
