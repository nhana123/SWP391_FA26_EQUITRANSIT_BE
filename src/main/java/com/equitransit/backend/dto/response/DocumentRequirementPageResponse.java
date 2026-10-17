/*
 * DocumentRequirementPageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Represent a paged list of document requirements for FR-08.
 */
package com.equitransit.backend.dto.response;


import java.util.List;


/**
 * Bounded page response for document requirements checklist.
 * @param items the list of requirements
 * @param page zero-based page index
 * @param size page size
 * @param totalElements total matching records
 * @param totalPages total page count
 */
public record DocumentRequirementPageResponse(
        List<DocumentRequirementResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
