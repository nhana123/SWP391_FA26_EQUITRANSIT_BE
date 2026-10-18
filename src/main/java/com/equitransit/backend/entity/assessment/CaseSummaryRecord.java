/*
 * CaseSummaryRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.entity.assessment;


import java.time.OffsetDateTime;


/**
 * Internal typed SQL projection, separate from HTTP response contracts.
 * @param orderId parent key
 * @param orderNumber public reference
 * @param orderStatus workflow state
 * @param recordVersion opaque concurrency version
 * @param currentRevisionId exact current revision
 * @param originCountryCode origin ISO code
 * @param destinationCountryCode destination ISO code
 * @param deliveryDeadline UTC deadline
 */
public record CaseSummaryRecord(
        long orderId, String orderNumber, String orderStatus,
        String recordVersion, Long currentRevisionId, String originCountryCode,
        String destinationCountryCode, OffsetDateTime deliveryDeadline) {
}
