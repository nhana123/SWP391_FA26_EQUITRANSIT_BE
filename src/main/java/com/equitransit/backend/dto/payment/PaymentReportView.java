/*
 * PaymentReportView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import com.equitransit.backend.entity.payment.ReportState;


/** Public-safe payment read model. */
public record PaymentReportView(
        @Schema(example = "101", minimum = "1") long reportId,
        @Schema(example = "101", minimum = "1") long paymentRequestId,
        @Schema(example = "1500.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String reportedAmount,
        @Schema(example = "USD", allowableValues = {"USD"})
        String reportedCurrency,
        @Schema(example = "2026-10-17T08:00:00Z")
        OffsetDateTime transferredAt,
        @Schema(example = "d86b4f63-5acc-4f20-8da3-e24323268814")
        String proofFileKey,
        @Schema(example = "SUBMITTED") ReportState status) {
}
