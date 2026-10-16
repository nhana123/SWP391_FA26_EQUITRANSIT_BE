/*
 * RefundView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import com.equitransit.backend.entity.payment.RefundState;


/** Public-safe payment read model. */
public record RefundView(
        @Schema(example = "101", minimum = "1") long refundId,
        @Schema(example = "101", minimum = "1") long allocationId,
        @Schema(example = "1500.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String amount,
        @Schema(example = "Customer requested refund")
        String reason,
        @Schema(example = "REQUESTED") RefundState status,
        @Schema(example = "2026-10-17T08:00:00Z", nullable = true)
        OffsetDateTime refundedAt) {
}
