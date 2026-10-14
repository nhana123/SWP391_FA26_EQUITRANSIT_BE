/*
 * CaseStaffResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param staffId active staff key
 * @param fullName display name
 * @param role verified database role
 * @param activeCases current case workload, not capacity
 * @param availabilityStatus NOT_CONFIGURED; no calendar promise
 */
public record CaseStaffResponse(long staffId, String fullName, String role,
        long activeCases, String availabilityStatus) {
}
