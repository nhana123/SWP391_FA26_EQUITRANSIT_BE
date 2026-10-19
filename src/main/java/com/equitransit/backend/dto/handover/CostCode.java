/*
 * CostCode
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Categorize internal actual supplier expenditures incurred on plans.
 */
package com.equitransit.backend.dto.handover;

/**
 * Standard classification codes for actual operational costs.
 */
public enum CostCode {

    /** Air transport cargo space charter or airway bill charges. */
    AIR_FREIGHT,

    /** Highway, turnpike, tunnel, or bridge toll fees. */
    ROAD_TOLL,

    /** Commercial vehicle diesel or aviation fuel expenditure. */
    FUEL,

    /** Stall rental, forage, bedding, and board at quarantine station. */
    QUARANTINE_LODGING,

    /** Border import duties, broker fees, or consular clearance taxes. */
    CUSTOMS_DUTY,

    /** On-route emergency veterinary diagnostics, surgery or medicine. */
    VET_EMERGENCY,

    /** Ground handling, ramp loading, or horse stall handling charges. */
    HANDLING_FEE,

    /** Miscellaneous operational expenditure not otherwise classified. */
    OTHER
}
