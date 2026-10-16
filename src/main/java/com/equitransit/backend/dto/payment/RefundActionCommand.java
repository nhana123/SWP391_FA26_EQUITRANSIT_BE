/*
 * RefundActionCommand
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import com.equitransit.backend.entity.payment.RefundAction;


/** Validated manual payment command. */
public record RefundActionCommand(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "APPROVE") @NotNull RefundAction action,
        @Schema(example = "REFUND-2026-001", nullable = true)
        @OptionalText
        @Size(min = 1, max = 150) String bankRefundReference,
        @Schema(example = "d86b4f63-5acc-4f20-8da3-e24323268814",
                nullable = true)
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String proofFileKey,
        @Schema(example = "2026-10-17T08:00:00Z", nullable = true)
        OffsetDateTime refundedAt) {
}
