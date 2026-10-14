/*
 * OrderResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.response;


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
 * @param draft the draft
 */
public record OrderResponse(
        long orderId,
        String orderNumber,
        String orderStatus,
        String currency,
        String recordVersion,
        Long currentRevisionId,
        Long currentPlanId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OrderDraftRequest draft) {
}
