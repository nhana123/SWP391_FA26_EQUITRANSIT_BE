/*
 * QuoteSnapshot
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.entity.quote;


import com.equitransit.backend.dto.quote.QuoteLine;
import com.equitransit.backend.dto.quote.QuotePrepareRequest;
import java.util.List;


/** Versioned internal preparation snapshot, never returned as customer JSON. */
public record QuoteSnapshot(int schemaVersion, QuotePrepareRequest request,
        List<QuoteLine> lines, List<QuotePriceSource> sources,
        List<String> blockers, QuoteCalculationContext calculationContext) {
}
