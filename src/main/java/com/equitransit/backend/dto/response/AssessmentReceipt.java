/*
 * AssessmentReceipt
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
 * @param orderStatus state at action completion
 * @param recordVersion version at action completion; GET latest after retry
 * @param decision recorded current decision
 * @param assignments responsibilities at action completion
 */
public record AssessmentReceipt(
        long orderId, String orderStatus, String recordVersion,
        CaseDecisionResponse decision,
        List<CaseAssignmentResponse> assignments) {
}
