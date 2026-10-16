/*
 * BankReceiptView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import com.equitransit.backend.entity.payment.BankState;


/** Public-safe payment read model. */
public record BankReceiptView(
        @Schema(example = "101", minimum = "1") long transactionId,
        @Schema(example = "1500.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String amountReceived,
        @Schema(example = "USD", allowableValues = {"USD"})
        String currency,
        @Schema(example = "RECONCILED") BankState status,
        @Schema(example = "2026-10-17T08:00:00Z")
        OffsetDateTime postedAt) {
}
