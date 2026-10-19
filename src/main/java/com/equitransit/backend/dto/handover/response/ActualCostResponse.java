/*
 * ActualCostResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Staff-facing representation of an internal actual supplier cost.
 */
package com.equitransit.backend.dto.handover.response;

import com.equitransit.backend.dto.handover.HandoverExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Staff-only view of an internal supplier cost incurred on a transport plan.
 *
 * @param costId primary key of the cost record
 * @param planId operational plan identifier
 * @param legId optional leg identifier if allocated to a leg
 * @param costCode category code of the cost
 * @param amount non-negative expenditure amount
 * @param currency ISO 4217 three-letter currency code
 * @param incurredAt point-in-time when cost was incurred
 * @param description invoice narrative or line-item description
 * @param recordedBy staff profile identifier of recorder
 * @param recordedByName full name of staff member who recorded cost
 * @param evidenceFileKey storage key of invoice or receipt file
 */
@Schema(name = "ActualCostResponse",
        description = "Internal staff view of an actual supplier expenditure")
public record ActualCostResponse(
        @Schema(example = HandoverExamples.COST_ID)
        long costId,

        @Schema(example = HandoverExamples.PLAN_ID)
        long planId,

        @Schema(example = HandoverExamples.LEG_ID)
        Long legId,

        @Schema(example = "AIR_FREIGHT")
        String costCode,

        @Schema(example = HandoverExamples.AMOUNT)
        BigDecimal amount,

        @Schema(example = HandoverExamples.CURRENCY)
        String currency,

        @Schema(example = HandoverExamples.TIME)
        OffsetDateTime incurredAt,

        @Schema(example = "Air France Cargo airway bill #618-91234567")
        String description,

        @Schema(example = "103")
        Long recordedBy,

        @Schema(example = "Claire Martin")
        String recordedByName,

        @Schema(example = HandoverExamples.EVIDENCE_KEY)
        String evidenceFileKey
) { }
