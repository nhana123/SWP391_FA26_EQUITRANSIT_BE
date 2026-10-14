/*
 * CaseDecisionResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


import java.time.OffsetDateTime;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param decisionId immutable decision key
 * @param revisionId reviewed revision
 * @param managerId Manager StaffID, not account ID
 * @param decision assessment code
 * @param reason recorded assessment reason
 * @param decidedAt UTC decision time
 */
public record CaseDecisionResponse(
        long decisionId, long revisionId, long managerId, String decision,
        String reason, OffsetDateTime decidedAt) {
}
