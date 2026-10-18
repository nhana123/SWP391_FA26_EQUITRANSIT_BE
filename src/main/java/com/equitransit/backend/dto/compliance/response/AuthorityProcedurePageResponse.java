/*
 * AuthorityProcedurePageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Paginated container for authority procedure responses.
 */
package com.equitransit.backend.dto.compliance.response;

import java.util.List;

/**
 * Standard pagination response for authority procedures checklist.
 * @param items the page items
 * @param page the zero-based page index
 * @param size the maximum page size
 * @param totalElements the total count across all pages
 * @param totalPages the total calculated pages
 */
public record AuthorityProcedurePageResponse(
        List<AuthorityProcedureResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
