/*
 * CaseStaffPageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.assessment.response;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param items eligible staff page
 * @param page zero-based page
 * @param size bounded size
 * @param totalElements eligible count
 * @param totalPages page count
 */
public record CaseStaffPageResponse(
        @Schema(
                example = "[{\"staffId\":31,\"fullName\":\"Alex Nguy" +
                        "en\",\"role\":\"SPECIALIST\",\"activeCases" +
                        "\":2,\"availabilityStatus\":\"NOT_CONFIG" +
                        "URED\"}]")
        List<CaseStaffResponse> items,
        @Schema(
                example = "0",
                minimum = "0")
        int page,
        @Schema(
                example = "20",
                minimum = "1",
                maximum = "100")
        int size,
        @Schema(
                example = "1",
                minimum = "0")
        long totalElements,
        @Schema(
                example = "1",
                minimum = "0")
        long totalPages) {
}
