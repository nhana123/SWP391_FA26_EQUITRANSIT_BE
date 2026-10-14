/*
 * OrderPageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.response;


import java.util.List;


/**
 * Explicit owner-filtered pagination response.
 * @param items the items
 * @param page the page
 * @param size the size
 * @param totalElements the totalElements
 * @param totalPages the totalPages
 */
public record OrderPageResponse(
        List<OrderResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages) {
}
