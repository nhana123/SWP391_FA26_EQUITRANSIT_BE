/*
 * OrderRevisionResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.response;


import java.time.OffsetDateTime;


/**
 * Immutable revision, also returned identically for safe retries.
 * @param revisionId the revisionId
 * @param orderId the orderId
 * @param revisionNo the revisionNo
 * @param changeReason the changeReason
 * @param submittedAt the submittedAt
 * @param snapshot the snapshot
 */
public record OrderRevisionResponse(
        long revisionId,
        long orderId,
        int revisionNo,
        String changeReason,
        OffsetDateTime submittedAt,
        OrderSnapshot snapshot) {
}
