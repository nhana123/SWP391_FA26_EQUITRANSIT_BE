/*
 * ServiceConfirmationResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.dto.pricing.response;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.common.ApiExamples;
import com.equitransit.backend.dto.order.response.OrderResponse;
import com.equitransit.backend.dto.order.response.OrderRevisionResponse;


/**
 * Explicit customer service contract, no supplier costs or secrets.
 * @param order completion state; GET current after historic retry
 * @param revision new immutable revision, null for draft-only selection
 */
public record ServiceConfirmationResponse(
        @Schema(
                example = ApiExamples.ORDER)
        OrderResponse order,
        @Schema(
                example = ApiExamples.REVISION)
        OrderRevisionResponse revision) {
}
