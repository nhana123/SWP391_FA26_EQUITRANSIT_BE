/*
 * ServiceDisclosureResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.dto.response;


import java.util.List;
import java.time.OffsetDateTime;
import com.equitransit.backend.dto.request.OrderServicePreferences;
import com.equitransit.backend.entity.StallFitResult;
import com.equitransit.backend.entity.QuarantineAssessmentResult;


/**
 * Explicit customer service contract, no supplier costs or secrets.
 * @param orderId owned order key
 * @param recordVersion parent concurrency version
 * @param revisionId exact revision, null for draft
 * @param horseRecordVersion captured or current measurement version
 * @param asOf server calculation time
 * @param currency USD
 * @param selected explicit saved preferences, null if none
 * @param sections seven UI sections with no automatically selected options
 * @param airStalls eligible or pending configurations; unsuitable omitted
 * @param quarantine verified applicability, not zero for unknown
 * @param charges Show all breakdown including pending and event-only rows
 * @param knownSubtotal sum of known selected estimates, not payable
 * @param totalEstimate null until all applicable selected charges resolve
 * @param payable always false; payment belongs to issued quote flows
 * @param carrierConfirmation NOT_CONFIRMED; measurement fit is not reservation
 */
public record ServiceDisclosureResponse(
        long orderId, String recordVersion, Long revisionId,
        String horseRecordVersion, OffsetDateTime asOf, String currency,
        OrderServicePreferences selected, List<BookingSectionResponse> sections,
        List<StallFitResult> airStalls,
        QuarantineAssessmentResult quarantine,
                List<ServiceChargeResponse> charges,
        String knownSubtotal, String totalEstimate,
        boolean payable, String carrierConfirmation) {
}
