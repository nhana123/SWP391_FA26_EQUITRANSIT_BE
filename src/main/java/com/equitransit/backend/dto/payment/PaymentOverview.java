/*
 * PaymentOverview
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;


/** A bounded payment snapshot; report amounts never count as receipts. */
public record PaymentOverview(
        @Schema(example = "000000000001A2B3",
                pattern = "^[0-9a-fA-F]{16}$") String recordVersion,
        @Schema(example = "[]") List<PaymentObligation> obligations,
        @Schema(example = "[]") List<PaymentReportView> reports,
        @Schema(example = "[]") List<PaymentAllocationView> allocations,
        @Schema(example = "[]") List<RefundView> refunds,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size) {
}
