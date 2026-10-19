/*
 * AdjustmentApprovalStatus
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Workflow approval state for operational adjustments.
 */
package com.equitransit.backend.dto.incident;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Status of the operational adjustment review and approval workflow.
 */
@Schema(description = "Operational adjustment approval workflow status",
        example = "CUSTOMER_PENDING")
public enum AdjustmentApprovalStatus {
    /** Adjustment drafted, waiting for manager review (zero customer cost). */
    PROPOSED,
    /** Adjustment proposed with extra cost, waiting customer consent. */
    CUSTOMER_PENDING,
    /** Approved by manager, ready to evolve plan. */
    MANAGER_APPROVED,
    /** Rejected by customer or manager. */
    REJECTED,
    /** Applied to active plan, new plan version generated and activated. */
    APPLIED
}
