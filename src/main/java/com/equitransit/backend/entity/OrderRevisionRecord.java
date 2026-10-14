/*
 * OrderRevisionRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.entity;


import java.time.OffsetDateTime;


/**
 * Immutable persisted revision with the complete submission snapshot.
 * @param revisionId the revisionId
 * @param orderId the orderId
 * @param revisionNo the revisionNo
 * @param snapshot the snapshot
 * @param changeReason the changeReason
 * @param submittedAt the submittedAt
 */
public record OrderRevisionRecord(
        long revisionId,
        long orderId,
        int revisionNo,
        String snapshot,
        String changeReason,
        OffsetDateTime submittedAt) {
}
