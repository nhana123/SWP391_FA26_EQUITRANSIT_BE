package com.equitransit.backend.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Customer projection of the current revision's issued deposit request.
 * @param totalAmount contract total @param depositAmount deposit due
 * @param note customer message @param requestedAt issue time
 * @param isPaid true only after net reconciled allocations cover the deposit
 * @param currency USD @param quoteId quote to review/accept
 * @param paymentRequestId actual obligation, null until accepted
 * @param paymentAvailable approval and customer acceptance have passed */
public record DepositRequested(
        @Schema(example = "1000.00") BigDecimal totalAmount,
        @Schema(example = "300.00") BigDecimal depositAmount,
        @Schema(example = "Please pay the deposit.", nullable = true)
        String note,
        @Schema(example = "2026-10-27T10:00:00Z") OffsetDateTime requestedAt,
        @Schema(example = "false") boolean isPaid,
        @Schema(example = "USD") String currency,
        @Schema(example = "101", minimum = "1") long quoteId,
        @Schema(example = "201", nullable = true, minimum = "1")
        Long paymentRequestId,
        @Schema(example = "false") boolean paymentAvailable) {
}
