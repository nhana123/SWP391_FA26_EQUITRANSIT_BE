/*
 * CaseSummaryResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.assessment.response;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.entity.order.OrderStatus;
import com.equitransit.backend.dto.common.ApiPatterns;
import java.time.OffsetDateTime;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param orderId parent key
 * @param orderNumber public reference
 * @param orderStatus workflow state
 * @param recordVersion opaque concurrency version
 * @param currentRevisionId exact current revision
 * @param originCountryCode origin ISO code
 * @param destinationCountryCode destination ISO code
 * @param deliveryDeadline UTC deadline
 */
public record CaseSummaryResponse(
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
                example = "VN",
                pattern = ApiPatterns.COUNTRY)
        String originCountryCode,
        @Schema(
                example = "SG",
                pattern = ApiPatterns.COUNTRY)
        String destinationCountryCode,
        @Schema(
                example = "2026-11-03T18:00:00Z",
                format = "date-time")
        OffsetDateTime deliveryDeadline) {
}
