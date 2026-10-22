/*
 * QuoteScheduleView
 * Author: EquiTransit development team
 * Date: 2026-10-22
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose persisted installment identities for payment requests.
 */
package com.equitransit.backend.dto.quote;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;


/** Read projection; identities are never accepted as quotation input. */
public record QuoteScheduleView(
        @Schema(example = "401", minimum = "1", nullable = true)
        Long scheduleId,
        @Schema(example = "1", minimum = "1") int sequenceNo,
        @Schema(example = "DEPOSIT") String paymentStage,
        @Schema(example = "1500.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$") String amountDue,
        @Schema(example = "2027-01-15T08:00:00Z") OffsetDateTime dueAt) {
}
