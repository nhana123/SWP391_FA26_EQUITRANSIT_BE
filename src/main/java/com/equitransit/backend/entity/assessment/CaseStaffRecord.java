/*
 * CaseStaffRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.entity.assessment;


/**
 * Internal typed SQL projection, separate from HTTP response contracts.
 * @param staffId active staff key
 * @param fullName display name
 * @param role verified database role
 * @param activeCases current case workload, not capacity
 */
public record CaseStaffRecord(long staffId, String fullName, String role,
        long activeCases) {
}
