/*
 * BankReceiptCommand
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.dto.validation.OptionalText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;


/** Validated manual payment command. */
public record BankReceiptCommand(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "1500.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        @NotBlank @Size(min = 1, max = 19)
        @Pattern(regexp = "^\\d+(\\.\\d{1,2})?$") String amountReceived,
        @Schema(example = "USD", allowableValues = {"USD"})
        @NotBlank @Pattern(regexp = "^USD$") String currency,
        @Schema(example = "COMPANY-USD-ACCOUNT")
        @NotBlank
        @Size(min = 1, max = 150) String recipientAccount,
        @Schema(example = "BANK-2026-001")
        @NotBlank
        @Size(min = 1, max = 150) String bankTransactionReference,
        @Schema(example = "2026-10-17T08:00:00Z")
        @NotNull
        OffsetDateTime postedAt,
        @Schema(example = "Bank statement verified")
        @NotBlank
        @Size(min = 1, max = 500) String verificationSource,
        @Schema(example = "d86b4f63-5acc-4f20-8da3-e24323268814")
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String proofFileKey,
        @Schema(example = "Reviewed against statement", nullable = true)
        @OptionalText
        @Size(min = 1, max = 2000) String internalNotes) {
}
