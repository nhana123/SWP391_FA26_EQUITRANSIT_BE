/*
 * RefundAction
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.entity.payment;


/** Persisted or commanded payment state. */
public enum RefundAction {
    APPROVE, REJECT, COMPLETE;
}
