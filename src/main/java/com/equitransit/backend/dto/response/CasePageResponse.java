/*
 * CasePageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


import java.util.List;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param items visible queue page
 * @param page zero-based offset
 * @param size bounded page size
 * @param totalElements visible count
 * @param totalPages page count
 */
public record CasePageResponse(
        List<CaseSummaryResponse> items, int page, int size,
        long totalElements, long totalPages) {
}
