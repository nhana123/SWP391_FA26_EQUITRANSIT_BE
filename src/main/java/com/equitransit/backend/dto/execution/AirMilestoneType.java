/*
 * AirMilestoneType
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Discrete sourced milestone types for air transport legs.
 */
package com.equitransit.backend.dto.execution;

/** Permitted discrete sourced milestone types on air transport legs. */
public enum AirMilestoneType {
    CARGO_TENDERED,
    CUSTOMS_CLEARED_OUT,
    AIRCRAFT_LOADED,
    WHEELS_UP,
    WHEELS_DOWN,
    UNLOADED,
    CUSTOMS_CLEARED_IN,
    HANDOVER_READY
}
