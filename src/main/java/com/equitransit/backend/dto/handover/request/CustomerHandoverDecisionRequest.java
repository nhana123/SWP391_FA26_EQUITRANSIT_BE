/*
 * CustomerHandoverDecisionRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Customer delivery acceptance, feedback, or dispute response payload.
 */
package com.equitransit.backend.dto.handover.request;

import com.equitransit.backend.dto.handover.HandoverCustomerDecision;
import com.equitransit.backend.dto.handover.HandoverExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Customer delivery response: ACCEPTED, EXCEPTION, or NOT_DELIVERED.
 *
 * @param decision customer confirmation decision
 * @param feedback detailed customer feedback or reason for dispute/exception
 */
@Schema(name = "CustomerHandoverDecisionRequest",
        description = "Customer delivery confirmation or dispute response")
public record CustomerHandoverDecisionRequest(
        @NotNull
        @Schema(description = "Customer decision: ACCEPTED, EXCEPTION, "
                + "NOT_DELIVERED",
                example = "ACCEPTED")
        HandoverCustomerDecision decision,

        @Size(max = 2000)
        @Schema(description = "Customer review comments or exception notes",
                example = HandoverExamples.FEEDBACK)
        String feedback
) { }
