/*
 * OrderResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiPatterns;
import com.equitransit.backend.dto.ApiExamples;
import java.time.OffsetDateTime;
import com.equitransit.backend.dto.request.OrderDraftRequest;


/**
 * Current request view; current plan may use an earlier accepted revision.
 * @param orderId the orderId
 * @param orderNumber the orderNumber
 * @param orderStatus the orderStatus
 * @param currency the currency
 * @param recordVersion the recordVersion
 * @param currentRevisionId the currentRevisionId
 * @param currentPlanId the currentPlanId
 * @param createdAt the createdAt
 * @param updatedAt the updatedAt
 * @param depositRequested current Manager request, or null
 * @param draft the draft
 */
@Schema(example = ApiExamples.ORDER)
public record OrderResponse(
        @Schema(
                example = "101",
                minimum = "1")
        long orderId,
        @Schema(
                example = "ET-2026-000101")
        String orderNumber,
        @Schema(
                example = "SUBMITTED",
                allowableValues = {
                        "DRAFT",
                        "SUBMITTED",
                        "APPROVED",
                        "CHANGES_REQUESTED",
                        "REJECTED",
                        "IN_PREPARATION",
                        "CONFIRMED",
                        "IN_TRANSIT",
                        "DELIVERED",
                        "COMPLETED",
                        "CANCELLED",
                        "CANCELED",
                        "CLOSED"})
        String orderStatus,
        @Schema(
                example = "USD",
                pattern = ApiPatterns.CURRENCY)
        String currency,
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
                example = "31",
                nullable = true,
                minimum = "1")
        Long currentPlanId,
        @Schema(
                example = "2026-10-14T10:00:00Z",
                format = "date-time")
        OffsetDateTime createdAt,
        @Schema(
                example = "2026-10-14T12:05:00Z",
                format = "date-time")
        OffsetDateTime updatedAt,
        @Schema(
                example = ApiExamples.DRAFT)
        OrderDraftRequest draft,
        @Schema(nullable = true, example = "{\"totalAmount\":1000.00,"
                + "\"depositAmount\":300.00,\"note\":null,"
                + "\"requestedAt\":\"2026-10-27T11:00:00Z\","
                + "\"isPaid\":false,\"currency\":\"USD\","
                + "\"quoteId\":201,\"paymentRequestId\":null,"
                + "\"paymentAvailable\":false}")
        com.equitransit.backend.dto.payment.DepositRequested
        depositRequested) {
}
