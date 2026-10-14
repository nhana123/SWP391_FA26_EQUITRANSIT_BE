/*
 * CaseOrder
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.entity;


/**
 * Locked parent metadata shared by assessment and assigned-case access.
 * @param orderId the parent key
 * @param orderNumber the public reference
 * @param status the persisted workflow state
 * @param version the opaque SQL rowversion
 * @param revisionId the exact current submitted revision
 */
public record CaseOrder(long orderId, String orderNumber, String status,
        String version, Long revisionId) {
}
