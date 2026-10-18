/*
 * AssessmentReceipt
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.assessment.response;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.entity.order.OrderStatus;
import com.equitransit.backend.dto.common.ApiPatterns;
import com.equitransit.backend.dto.common.ApiExamples;
import java.util.List;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param orderId parent key
 * @param orderStatus state at action completion
 * @param recordVersion version at action completion; GET latest after retry
 * @param decision recorded current decision
 * @param assignments responsibilities at action completion
 */
@Schema(example = ApiExamples.RECEIPT)
public record AssessmentReceipt(
        @Schema(
                example = "101",
                minimum = "1")
        long orderId,
        @Schema(example = "SUBMITTED")
        OrderStatus orderStatus,
        @Schema(
                example = "000000000001A2B3",
                pattern = ApiPatterns.VERSION)
        String recordVersion,
        @Schema(
                example = ApiExamples.DECISION)
        CaseDecisionResponse decision,
        @Schema(
                example = ApiExamples.ASSIGNMENTS)
        List<CaseAssignmentResponse> assignments) {
}
