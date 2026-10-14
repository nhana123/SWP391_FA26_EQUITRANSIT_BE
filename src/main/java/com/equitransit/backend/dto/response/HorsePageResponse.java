/*
 * HorsePageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement customer-owned horse profiles.
 */
package com.equitransit.backend.dto.response;


import java.util.List;


/**
 * Stable pagination contract, without exposing Spring Page internals.
 * @param items the profiles on this page
 * @param page the zero-based page
 * @param size the requested size
 * @param totalElements the owner's matching record count
 * @param totalPages the number of matching pages
 */
public record HorsePageResponse(List<HorseResponse> items, int page,
        int size, long totalElements, long totalPages) {
}
