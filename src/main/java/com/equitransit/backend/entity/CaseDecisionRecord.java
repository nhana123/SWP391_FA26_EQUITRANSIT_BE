/*
 * CaseDecisionRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.entity;


import java.time.OffsetDateTime;


/**
 * Internal typed SQL projection, separate from HTTP response contracts.
 * @param decisionId immutable decision key
 * @param revisionId reviewed revision
 * @param managerId Manager StaffID, not account ID
 * @param decision assessment code
 * @param reason recorded assessment reason
 * @param decidedAt UTC decision time
 */
public record CaseDecisionRecord(
        long decisionId, long revisionId, long managerId, String decision,
        String reason, OffsetDateTime decidedAt) {
}
