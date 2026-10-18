/*
 * QuotePriceSource
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.entity.quote;


import java.math.BigDecimal;
import java.time.OffsetDateTime;


/** Internal source; purchase costs never enter customer contracts. */
public record QuotePriceSource(long id, boolean offer, String code,
        String unit, String group, BigDecimal rate, BigDecimal quantity,
        OffsetDateTime validUntil, String inclusions, boolean includesStall,
        Integer version) {
}
