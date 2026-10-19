/*
 * IncidentType
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Categorization of transport and welfare incidents.
 */
package com.equitransit.backend.dto.incident;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Standard classification for operational, technical and welfare incidents.
 */
@Schema(description = "Category of transport disruption or emergency",
        example = "HORSE_WELFARE_EMERGENCY")
public enum IncidentType {
    /** Acute colic, injury, distress or vital welfare emergency. */
    HORSE_WELFARE_EMERGENCY,
    /** Vehicle mechanical breakdown, tire blowout, trailer failure. */
    VEHICLE_BREAKDOWN,
    /** Heavy traffic congestion, road blockage, accident detour. */
    TRAFFIC_DELAY,
    /** Airline cargo delay, canceled flight, airway change. */
    FLIGHT_DELAY,
    /** Severe weather, storm, extreme temperature disruption. */
    WEATHER_DISRUPTION,
    /** Border crossing inspection hold, customs clearance delay. */
    BORDER_HOLD,
    /** Stabling, quarantine or rest facility unavailable or damaged. */
    FACILITY_ISSUE,
    /** Other unplanned operational disruption. */
    OTHER
}
