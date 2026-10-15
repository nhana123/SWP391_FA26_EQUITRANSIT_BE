/*
 * CaseResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.entity.OrderStatus;
import com.equitransit.backend.dto.ApiPatterns;
import com.equitransit.backend.dto.ApiExamples;
import java.util.List;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param orderId parent key
 * @param orderNumber public reference
 * @param orderStatus request state
 * @param recordVersion opaque version
 * @param currentRevisionId current submitted revision
 * @param revision captured current snapshot, never recaptured horse
 * @param decision decision for current revision, or null
 * @param assignments visible active or historical responsibilities
 */
@Schema(example = ApiExamples.CASE)
public record CaseResponse(
        @Schema(
                example = "101",
                minimum = "1")
        long orderId,
        @Schema(
                example = "ET-2026-000101")
        String orderNumber,
        @Schema(example = "SUBMITTED")
        OrderStatus orderStatus,
        @Schema(
                example = "000000000001A2B3",
                pattern = ApiPatterns.VERSION)
        String recordVersion,
        @Schema(
                example = "201",
                nullable = true,
                minimum = "1")
        Long currentRevisionId,
        @Schema(
                example = ApiExamples.REVISION)
        OrderRevisionResponse revision,
        @Schema(
                example = ApiExamples.DECISION)
        CaseDecisionResponse decision,
        @Schema(
                example = ApiExamples.ASSIGNMENTS)
        List<CaseAssignmentResponse> assignments) {
}
