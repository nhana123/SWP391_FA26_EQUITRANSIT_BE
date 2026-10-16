/*
 * CommercialReadiness
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Share quotation commitment without asserting payment or departure.
 */
package com.equitransit.backend.entity.quote;


import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;


/**
 * Exact accepted obligation and net reconciled allocations at an instant.
 * @param quoteId accepted quote
 * @param revisionId exact revision
 * @param total accepted retail amount, not money received
 * @param paidAmount reconciled allocation less completed refunds/reversals
 * @param asOf gate evaluation instant
 * @param obligations captured schedule amounts and their net receipts
 * @param departureBlockers F11 financial blockers only
 * @param settlementBlockers F14 financial blockers only
 */
public record CommercialReadiness(long quoteId, long revisionId,
        BigDecimal total, BigDecimal paidAmount, OffsetDateTime asOf,
        List<FinancialObligation> obligations,
        List<String> departureBlockers, List<String> settlementBlockers) {

    /** @return whether financial departure requirements alone are met */
    public boolean departureReady() {
        return departureBlockers.isEmpty();
    }

    /** @return whether financial closure requirements alone are met */
    public boolean settlementReady() {
        return settlementBlockers.isEmpty();
    }
}
