/*
 * ServiceDisclosureResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.dto.pricing.response;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.common.ApiPatterns;
import com.equitransit.backend.dto.common.ApiExamples;
import java.util.List;
import java.time.OffsetDateTime;
import com.equitransit.backend.dto.order.request.OrderServicePreferences;
import com.equitransit.backend.entity.pricing.StallFitResult;
import com.equitransit.backend.entity.pricing.QuarantineAssessmentResult;


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
@Schema(example = ApiExamples.DISCLOSURE)
public record ServiceDisclosureResponse(
        @Schema(
                example = "101",
                minimum = "1")
        long orderId,
        @Schema(
                example = "000000000001A2B3",
                pattern = ApiPatterns.VERSION)
        String recordVersion,
        @Schema(
                example = "201",
                nullable = true,
                minimum = "1")
        Long revisionId,
        @Schema(
                example = "000000000001A2B3",
                nullable = true,
                pattern = ApiPatterns.VERSION)
        String horseRecordVersion,
        @Schema(
                example = "2026-10-14T13:00:00Z",
                format = "date-time")
        OffsetDateTime asOf,
        @Schema(
                example = "USD",
                pattern = ApiPatterns.CURRENCY)
        String currency,
        @Schema(
                example = ApiExamples.SERVICES)
        OrderServicePreferences selected,
        @Schema(
                example = ApiExamples.SECTIONS)
        List<BookingSectionResponse> sections,
        @Schema(
                example = ApiExamples.AIR_STALLS)
        List<StallFitResult> airStalls,
        @Schema(
                example = ApiExamples.QUARANTINE)
        QuarantineAssessmentResult quarantine,
        @Schema(
                example = "[{\"code\":\"DOCUMENT_COORDINATION\",\"ui" +
                        "Section\":\"ADDITIONAL_CHARGES\",\"costG" +
                        "roup\":\"DOCUMENTATION_QUARANTINE\",\"la" +
                        "bel\":\"Document coordination\",\"mandat" +
                        "ory\":true,\"selected\":true,\"unit\":\"RO" +
                        "UTE_KM\",\"quantity\":null,\"unitRate\":n" +
                        "ull,\"amount\":null,\"pricingStatus\":\"R" +
                        "ATE_AND_QUANTITY_PENDING\",\"tariffId\"" +
                        ":null,\"tariffVersion\":null,\"formula\"" +
                        ":\"(ROAD km + AIR km) \\u00d7 rate\",\"b" +
                        "lockers\":[\"RATE_PENDING\",\"QUANTITY_P" +
                        "ENDING\"]}]")
        List<ServiceChargeResponse> charges,
        @Schema(
                example = "550.00",
                pattern = ApiPatterns.DECIMAL,
                type = "string")
        String knownSubtotal,
        @Schema(
                example = "550.00",
                nullable = true,
                pattern = ApiPatterns.DECIMAL,
                type = "string")
        String totalEstimate,
        @Schema(
                example = "false")
        boolean payable,
        @Schema(
                example = "NOT_CONFIRMED",
                allowableValues = {"NOT_CONFIRMED"})
        String carrierConfirmation) {
}
