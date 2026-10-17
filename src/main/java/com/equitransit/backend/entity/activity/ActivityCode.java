/*
 * ActivityCode
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.entity.activity;


/** Trusted producer facts, not commands that change operational state. */
public enum ActivityCode {
    SUBMITTED, ACCEPTED, PAYMENT_ALLOCATED, PAID, CLEARED, DEPARTED,
    DELIVERED, CLOSED, UPDATED
}
