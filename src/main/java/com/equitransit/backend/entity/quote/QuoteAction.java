/*
 * QuoteAction
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.entity.quote;





/** Closed quotation domain codes. */
public enum QuoteAction {
    APPROVE,
    ISSUE,
    ACCEPT,
    REJECT,
    EXPIRE
}
