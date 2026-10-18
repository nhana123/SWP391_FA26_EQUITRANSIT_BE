/*
 * RoadMilestoneType
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Discrete milestone event types for road transport legs.
 */
package com.equitransit.backend.dto.execution;

/** Permitted discrete milestone event types on road transport legs. */
public enum RoadMilestoneType {
    DEPARTED,
    REST_STOP,
    CHECKPOINT,
    ARRIVED,
    DELAYED
}
