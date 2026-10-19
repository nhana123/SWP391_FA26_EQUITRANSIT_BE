/*
 * InsurancePreference
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.entity.order;


/** Typed request codes; preferences do not imply external confirmation. */
public enum InsurancePreference {
    NONE, EXISTING, MORTALITY, COMBINED
}
