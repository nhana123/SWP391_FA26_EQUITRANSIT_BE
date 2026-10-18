/*
 * HealthStatus
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Horse vital assessment statuses adhering to FEI welfare standards.
 */
package com.equitransit.backend.dto.execution;

/** Horse vital assessment status complying with FEI standards. */
public enum HealthStatus {
    HEALTHY,
    STRESSED,
    MINOR_INJURY,
    CRITICAL
}
