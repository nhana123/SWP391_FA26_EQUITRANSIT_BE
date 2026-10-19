/*
 * HandoverOutcome
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Categorize the outcome of a handover event.
 */
package com.equitransit.backend.dto.handover;

/**
 * Result of a multimodal leg or final destination handover.
 */
public enum HandoverOutcome {

    /** Handover completed without incident, all checks passed. */
    SUCCESSFUL,

    /** Handover completed with recorded operational or welfare notes. */
    EXCEPTION,

    /** Consignee or receiving partner refused or disputed receipt. */
    DISPUTED
}
