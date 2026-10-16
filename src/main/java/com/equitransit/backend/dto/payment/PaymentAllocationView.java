/*
 * PaymentAllocationView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import io.swagger.v3.oas.annotations.media.Schema;


/** Public-safe payment read model. */
public record PaymentAllocationView(
        @Schema(example = "101", minimum = "1") long allocationId,
        @Schema(example = "101", minimum = "1") long transactionId,
        @Schema(example = "101", minimum = "1") long paymentRequestId,
        @Schema(example = "1500.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String amount) {
}
