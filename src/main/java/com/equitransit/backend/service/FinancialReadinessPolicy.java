/*
 * FinancialReadinessPolicy
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Separate F11 departure and F14 settlement conditions.
 */
package com.equitransit.backend.service;


import com.equitransit.backend.entity.quote.CommercialReadiness;
import com.equitransit.backend.entity.quote.FinancialObligation;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;


/** Owner-approved as-of, single-stage and pending-refund financial rules. */
public final class FinancialReadinessPolicy {

    private FinancialReadinessPolicy() {
    }

    /**
     * @param quote accepted quote
     * @param revision exact revision
     * @param total accepted obligation
     * @param rows every schedule including unrequested stages
     * @param asOf departure evaluation time
     * @param pendingRefund whether any requested/approved refund remains
     * @return financial evidence only, not legal or operational clearance
     */
    public static CommercialReadiness evaluate(long quote, long revision,
            BigDecimal total, List<FinancialObligation> rows,
            OffsetDateTime asOf, boolean pendingRefund) {
        var departure = new ArrayList<String>();
        var settlement = new ArrayList<String>();
        var paid = rows.stream().map(FinancialObligation::paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var due = rows.stream().map(FinancialObligation::amountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (rows.isEmpty() || due.compareTo(total) != 0) {
            departure.add("SCHEDULE_INCOMPLETE");
            settlement.add("SCHEDULE_INCOMPLETE");
        }
        if (paid.signum() <= 0) {
            departure.add("NO_RECONCILED_RECEIPT");
        }
        for (var row : rows) {
            if (row.dueAt() == null) {
                departure.add("SCHEDULE_DUE_DATE_PENDING");
                settlement.add("SCHEDULE_DUE_DATE_PENDING");
            }
            if (row.paidAmount().compareTo(row.amountDue()) < 0) {
                settlement.add("SCHEDULE_UNPAID:" + row.scheduleId());
                if (rows.size() == 1 || row.dueAt() == null
                        || !row.dueAt().isAfter(asOf)) {
                    departure.add("SCHEDULE_UNPAID:" + row.scheduleId());
                }
            }
        }
        if (pendingRefund) {
            departure.add("REFUND_PENDING");
            settlement.add("REFUND_PENDING");
        }
        return new CommercialReadiness(quote, revision, total, paid, asOf,
                List.copyOf(rows), List.copyOf(departure),
                List.copyOf(settlement));
    }
}
