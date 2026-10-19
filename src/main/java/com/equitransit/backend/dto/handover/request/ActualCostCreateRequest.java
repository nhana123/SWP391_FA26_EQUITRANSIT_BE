/*
 * ActualCostCreateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Payload to record an actual internal supplier expense on a plan.
 */
package com.equitransit.backend.dto.handover.request;

import com.equitransit.backend.dto.handover.CostCode;
import com.equitransit.backend.dto.handover.HandoverExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Payload to log an actual internal supplier expenditure against a plan/leg.
 *
 * @param legId optional leg key if expenditure is allocated to a specific leg
 * @param costCode category code (AIR_FREIGHT, ROAD_TOLL, FUEL, etc.)
 * @param amount non-negative expenditure amount (CK_ActualCost_Amount)
 * @param currency three-letter uppercase ISO currency code
 * @param incurredAt point-in-time when cost was incurred or billed
 * @param description invoice narrative or line-item description
 * @param evidenceFileKey storage key of the supplier invoice or receipt file
 */
@Schema(name = "ActualCostCreateRequest",
        description = "Logs an internal supplier cost incurred on a plan")
public record ActualCostCreateRequest(
        @Positive
        @Schema(description = "Optional leg identifier within the plan",
                example = HandoverExamples.LEG_ID)
        Long legId,

        @NotNull
        @Schema(description = "Standard operational cost classification code",
                example = "AIR_FREIGHT")
        CostCode costCode,

        @NotNull
        @DecimalMin("0.00")
        @Schema(description = "Incurred amount in plan currency (non-negative)",
                example = HandoverExamples.AMOUNT)
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$",
                message = "Currency must be a 3-letter ISO code")
        @Schema(description = "3-letter ISO currency code",
                example = HandoverExamples.CURRENCY)
        String currency,

        @NotNull
        @Schema(description = "UTC timestamp when cost was incurred",
                example = HandoverExamples.TIME)
        OffsetDateTime incurredAt,

        @Size(max = 1000)
        @Schema(description = "Invoice reference or cost description",
                example = "Air France Cargo airway bill #618-91234567")
        String description,

        @Size(max = 1024)
        @Schema(description = "Storage key of invoice or receipt file",
                example = HandoverExamples.EVIDENCE_KEY)
        String evidenceFileKey
) { }
