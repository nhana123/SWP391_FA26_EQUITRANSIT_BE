/*
 * CommercialReadiness
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Share quotation commitment without asserting payment or departure.
 */
package com.equitransit.backend.entity.quote;


import java.math.BigDecimal;


/**
 * Accepted quotation evidence only; F07 still owns money reconciliation.
 * @param quoteId customer-accepted quotation
 * @param revisionId exact contractual revision
 * @param total accepted retail price, not money received
 */
public record CommercialReadiness(long quoteId, long revisionId,
        BigDecimal total) {
}
