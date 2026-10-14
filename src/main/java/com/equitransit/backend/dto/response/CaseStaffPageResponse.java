/*
 * CaseStaffPageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


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
        List<CaseStaffResponse> items, int page, int size,
        long totalElements, long totalPages) {
}
