/*
 * TransportOrderRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.entity.order;


import java.time.OffsetDateTime;


/**
 * Persisted owner-scoped order metadata; JSON is decoded at the service.
 * @param orderId the orderId
 * @param customerId the customerId
 * @param orderNumber the orderNumber
 * @param orderStatus the orderStatus
 * @param currency the currency
 * @param recordVersion the recordVersion
 * @param currentRevisionId the currentRevisionId
 * @param currentPlanId the currentPlanId
 * @param createdAt the createdAt
 * @param updatedAt the updatedAt
 * @param draftPayload the draftPayload
 */
public record TransportOrderRecord(
        long orderId,
        long customerId,
        String orderNumber,
        String orderStatus,
        String currency,
        String recordVersion,
        Long currentRevisionId,
        Long currentPlanId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String draftPayload) {
}
