/*
 * QuoteRecord
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.entity.quote;


import java.math.BigDecimal;
import java.time.OffsetDateTime;


/** Persisted quotation header with private snapshot. */
public record QuoteRecord(long id, long revision, String number, int version,
        QuoteStatus status, BigDecimal total, OffsetDateTime validUntil,
        OffsetDateTime issuedAt, OffsetDateTime acceptedAt, String snapshot) {
}
