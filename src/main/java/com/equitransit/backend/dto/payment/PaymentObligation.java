/*
 * PaymentObligation
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;


/** Public-safe payment read model. */
public record PaymentObligation(
        @Schema(example = "101", minimum = "1") long paymentRequestId,
        @Schema(example = "101", minimum = "1") long scheduleId,
        @Schema(example = "101", minimum = "1") long quoteId,
        @Schema(example = "DEPOSIT")
        String paymentStage,
        @Schema(example = "1500.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String amountDue,
        @Schema(example = "USD", allowableValues = {"USD"})
        String currency,
        @Schema(example = "2026-10-17T08:00:00Z", nullable = true)
        OffsetDateTime dueAt,
        @Schema(example = "1500.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String paidAmount) {
}
