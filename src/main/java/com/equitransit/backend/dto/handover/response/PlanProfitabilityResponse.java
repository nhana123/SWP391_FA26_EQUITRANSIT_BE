/*
 * PlanProfitabilityResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Manager executive profitability analysis comparing quote vs costs.
 */
package com.equitransit.backend.dto.handover.response;

import com.equitransit.backend.dto.handover.HandoverExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Manager-only financial profitability report for an operational plan.
 *
 * @param planId operational plan identifier
 * @param orderId parent transport order identifier
 * @param currency ISO 4217 currency code
 * @param totalQuotedAmount agreed commercial price quoted to customer (F06)
 * @param totalActualCost total internal supplier expenditures recorded (F14)
 * @param grossProfit gross margin amount (quoted - actual)
 * @param profitMarginPercent gross margin expressed as percentage of quote
 * @param costCount number of distinct supplier cost line-items recorded
 */
@Schema(name = "PlanProfitabilityResponse",
        description = "Executive profitability analysis of plan costs")
public record PlanProfitabilityResponse(
        @Schema(example = HandoverExamples.PLAN_ID)
        long planId,

        @Schema(example = HandoverExamples.ORDER_ID)
        long orderId,

        @Schema(example = HandoverExamples.CURRENCY)
        String currency,

        @Schema(example = HandoverExamples.QUOTED_TOTAL)
        BigDecimal totalQuotedAmount,

        @Schema(example = HandoverExamples.AMOUNT)
        BigDecimal totalActualCost,

        @Schema(example = HandoverExamples.GROSS_PROFIT)
        BigDecimal grossProfit,

        @Schema(example = HandoverExamples.MARGIN_PCT)
        BigDecimal profitMarginPercent,

        @Schema(example = "3")
        int costCount
) { }
