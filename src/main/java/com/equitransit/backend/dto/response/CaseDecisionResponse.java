/*
 * CaseDecisionResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param decisionId immutable decision key
 * @param revisionId reviewed revision
 * @param managerId Manager StaffID, not account ID
 * @param decision assessment code
 * @param reason recorded assessment reason
 * @param decidedAt UTC decision time
 */
public record CaseDecisionResponse(
        @Schema(
                example = "31",
                minimum = "1")
        long decisionId,
        @Schema(
                example = "201",
                nullable = true,
                minimum = "1")
        long revisionId,
        @Schema(
                example = "31",
                minimum = "1")
        long managerId,
        @Schema(
                example = "CHANGES_REQUESTED",
                allowableValues = {
                        "APPROVED",
                        "REJECTED",
                        "CHANGES_REQUESTED"})
        String decision,
        @Schema(
                example = "Please confirm the route.",
                nullable = true)
        String reason,
        @Schema(
                example = "2026-10-14T14:00:00Z",
                format = "date-time")
        OffsetDateTime decidedAt) {
}
