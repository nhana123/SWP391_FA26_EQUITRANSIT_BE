/*
 * FinancialReadinessPolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Owner-approved F11/F14 financial gate invariants.
 */
package com.equitransit.backend;


import com.equitransit.backend.entity.quote.FinancialObligation;
import com.equitransit.backend.service.FinancialReadinessPolicy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/** Departure is not settlement, and reports are never input to either. */
public class FinancialReadinessPolicyTest {

    private final OffsetDateTime now = OffsetDateTime.parse(
            "2026-10-17T08:00:00Z");

    /** Future single-stage obligation still requires100percent. */
    @Test
    public void singleStageMustBeFullyPaidEvenBeforeDueDate() {
        var result = FinancialReadinessPolicy.evaluate(1, 2,
                new BigDecimal("100.00"), List.of(stage("100", "50", 5)),
                now, false);

        assertThat(result.departureReady()).isFalse();
        assertThat(result.settlementReady()).isFalse();
    }

    /** Future balances block closure without blocking a paid deposit. */
    @Test
    public void paidDueStageAllowsDepartureButNotSettlement() {
        var result = FinancialReadinessPolicy.evaluate(1, 2,
                new BigDecimal("100.00"), List.of(stage("30", "30", -1),
                        stage("70", "0", 5)), now, false);

        assertThat(result.departureReady()).isTrue();
        assertThat(result.settlementReady()).isFalse();
        assertThat(result.paidAmount()).isEqualByComparingTo("30");
    }

    /** No funds cannot pass even when every stage is in the future. */
    @Test
    public void zeroPaidAlwaysBlocksDeparture() {
        var result = FinancialReadinessPolicy.evaluate(1, 2,
                new BigDecimal("100.00"), List.of(stage("30", "0", 1),
                        stage("70", "0", 5)), now, false);

        assertThat(result.departureBlockers())
                .contains("NO_RECONCILED_RECEIPT");
    }

    /** Unresolved due dates and refund reservations cannot be bypassed. */
    @Test
    public void nullDueDateAndPendingRefundBlockBothGates() {
        var row = new FinancialObligation(1, new BigDecimal("100"),
                new BigDecimal("100"), null);
        var result = FinancialReadinessPolicy.evaluate(1, 2,
                new BigDecimal("100"), List.of(row), now, true);

        assertThat(result.departureBlockers()).contains("REFUND_PENDING",
                "SCHEDULE_DUE_DATE_PENDING");
        assertThat(result.settlementBlockers()).contains("REFUND_PENDING",
                "SCHEDULE_DUE_DATE_PENDING");
    }

    /** Exact schedule and full reconciled net receipts pass financial only. */
    @Test
    public void fullyPaidCompleteSchedulePassesBothGates() {
        var result = FinancialReadinessPolicy.evaluate(1, 2,
                new BigDecimal("100"), List.of(stage("100", "100", -1)),
                now, false);

        assertThat(result.departureReady()).isTrue();
        assertThat(result.settlementReady()).isTrue();
    }

    /** A missing schedule does not become a vacuous passing gate. */
    @Test
    public void missingScheduleBlocksBothGates() {
        var result = FinancialReadinessPolicy.evaluate(1, 2,
                new BigDecimal("100"), List.of(), now, false);

        assertThat(result.departureBlockers()).contains("SCHEDULE_INCOMPLETE");
        assertThat(result.settlementReady()).isFalse();
    }

    private FinancialObligation stage(String due, String paid, int days) {
        return new FinancialObligation(1, new BigDecimal(due),
                new BigDecimal(paid), now.plusDays(days));
    }
}
