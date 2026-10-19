/*
 * CaseStaffResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.assessment.response;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.entity.assessment.CaseRole;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param staffId active staff key
 * @param fullName display name
 * @param role verified database role
 * @param activeCases current case workload, not capacity
 * @param availabilityStatus NOT_CONFIGURED; no calendar promise
 */
public record CaseStaffResponse(
        @Schema(
                example = "31",
                minimum = "1")
        long staffId,
        @Schema(
                example = "Alex Nguyen")
        String fullName,
        @Schema(example = "SPECIALIST")
        CaseRole role,
        @Schema(
                example = "2",
                minimum = "0")
        long activeCases,
        @Schema(
                example = "NOT_CONFIGURED",
                allowableValues = {"NOT_CONFIGURED"})
        String availabilityStatus) {
}
