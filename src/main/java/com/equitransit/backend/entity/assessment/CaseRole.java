/*
 * CaseRole
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.entity.assessment;


/** Case preparation roles; driver/manager are not case assignees. */
public enum CaseRole {
    SPECIALIST, COORDINATOR
}
