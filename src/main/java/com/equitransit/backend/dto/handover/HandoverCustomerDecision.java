/*
 * HandoverCustomerDecision
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Enforce customer confirmation and dispute responses on delivery.
 */
package com.equitransit.backend.dto.handover;

/**
 * Customer delivery decision matching CK_Figma_TripHandover_CustomerDecision.
 */
public enum HandoverCustomerDecision {

    /** Customer confirmed delivery in good order and accepted completion. */
    ACCEPTED,

    /** Customer noted exceptions, injuries, or missing equipment. */
    EXCEPTION,

    /** Customer disputed receipt, asserting animal was not delivered. */
    NOT_DELIVERED
}
