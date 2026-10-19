/*
 * IncidentStatus
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Lifecycle status for incident tracking.
 */
package com.equitransit.backend.dto.incident;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lifecycle status of an incident report.
 */
@Schema(description = "Lifecycle status of an incident", example = "REPORTED")
public enum IncidentStatus {
    /** Initial report logged by driver or coordinator. */
    REPORTED,
    /** Acknowledged by operations coordinator or manager. */
    ACKNOWLEDGED,
    /** Issue resolved, all emergency and operational actions closed. */
    RESOLVED,
    /** Invalid report or false alarm cancelled by staff. */
    CANCELLED
}
