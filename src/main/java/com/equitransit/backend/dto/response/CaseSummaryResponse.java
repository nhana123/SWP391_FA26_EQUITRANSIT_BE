/*
 * CaseSummaryResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


import java.time.OffsetDateTime;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param orderId parent key
 * @param orderNumber public reference
 * @param orderStatus workflow state
 * @param recordVersion opaque concurrency version
 * @param currentRevisionId exact current revision
 * @param originCountryCode origin ISO code
 * @param destinationCountryCode destination ISO code
 * @param deliveryDeadline UTC deadline
 */
public record CaseSummaryResponse(
        long orderId, String orderNumber, String orderStatus,
        String recordVersion, Long currentRevisionId, String originCountryCode,
        String destinationCountryCode, OffsetDateTime deliveryDeadline) {
}
