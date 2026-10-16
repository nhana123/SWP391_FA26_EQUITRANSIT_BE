/*
 * FinancialReport
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.dto.activity;


import io.swagger.v3.oas.annotations.media.Schema;


/** Safe, bounded actor-facing activity contract. */
public record FinancialReport(
        @Schema(minLength = 1, example = "USD", allowableValues = {"USD"})
        String currency,
        @Schema(minLength = 1, example = "5000.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String quotedValue,
        @Schema(minLength = 1, example = "1000.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String reportedTransfers,
        @Schema(minLength = 1, example = "800.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String netBankReceipts,
        @Schema(minLength = 1, example = "700.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String netAllocated,
        @Schema(minLength = 1, example = "400.00", type = "string",
                pattern = "^\\d+(\\.\\d{1,2})?$")
        String actualCost,
        @Schema(example = "1")
        long operationallyCompletedOrders,
        @Schema(example = "0")
        long closedOrders) {
}
