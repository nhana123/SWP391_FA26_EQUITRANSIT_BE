/*
 * SeverityLevel
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Severity classification for operational and welfare incidents.
 */
package com.equitransit.backend.dto.incident;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Severity level defining urgency and operational escalation priority.
 */
@Schema(description = "Incident severity level", example = "CRITICAL")
public enum SeverityLevel {
    /** Minor disruption; schedule shifted by minutes; welfare stable. */
    LOW,
    /** Moderate disruption; requires detour or minor veterinary check. */
    MEDIUM,
    /** Major disruption; route changed, substantial delay, or injury. */
    HIGH,
    /** Critical emergency; horse life/welfare at immediate risk. */
    CRITICAL
}
