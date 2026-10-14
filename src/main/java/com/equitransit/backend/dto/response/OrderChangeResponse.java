/*
 * OrderChangeResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.response;


import java.time.OffsetDateTime;


/**
 * Unapplied proposal for the controlled F13 workflow.
 * @param changeRequestId the changeRequestId
 * @param orderId the orderId
 * @param baseRevisionId the baseRevisionId
 * @param status the status
 * @param createdAt the createdAt
 */
public record OrderChangeResponse(
        long changeRequestId,
        long orderId,
        Long baseRevisionId,
        String status,
        OffsetDateTime createdAt) {
}
