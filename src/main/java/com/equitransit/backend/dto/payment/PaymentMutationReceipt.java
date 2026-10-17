/*
 * PaymentMutationReceipt
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import io.swagger.v3.oas.annotations.media.Schema;


/** Public-safe payment read model. */
public record PaymentMutationReceipt(
        @Schema(example = "101", minimum = "1") long entityId,
        @Schema(example = "000000000001A2B3",
                pattern = "^[0-9a-fA-F]{16}$") String recordVersion) {
}
