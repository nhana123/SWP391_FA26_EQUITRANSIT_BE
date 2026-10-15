/*
 * OrderChangeRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Map unapplied proposal metadata without exposing raw evidence.
 */
package com.equitransit.backend.entity;


import java.time.OffsetDateTime;


/**
 * Persisted proposal receipt, not approval or applied order changes.
 * @param changeRequestId the proposal key
 * @param orderId the parent order
 * @param baseRevisionId the current request revision at proposal time
 * @param status the proposal state
 * @param createdAt the UTC creation time
 */
public record OrderChangeRecord(long changeRequestId, long orderId,
        Long baseRevisionId, String status, OffsetDateTime createdAt) {
}
