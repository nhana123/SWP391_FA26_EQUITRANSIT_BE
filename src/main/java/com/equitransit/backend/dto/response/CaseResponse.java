/*
 * CaseResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


import java.util.List;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param orderId parent key
 * @param orderNumber public reference
 * @param orderStatus request state
 * @param recordVersion opaque version
 * @param currentRevisionId current submitted revision
 * @param revision captured current snapshot, never recaptured horse
 * @param decision decision for current revision, or null
 * @param assignments visible active or historical responsibilities
 */
public record CaseResponse(long orderId, String orderNumber, String orderStatus,
        String recordVersion, Long currentRevisionId,
        OrderRevisionResponse revision, CaseDecisionResponse decision,
        List<CaseAssignmentResponse> assignments) {
}
