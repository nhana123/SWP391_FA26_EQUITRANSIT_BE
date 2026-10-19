/*
 * DepartureGatePolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Unit test F11 pure departure gate, identity and redaction rules.
 */
package com.equitransit.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.equitransit.backend.dto.departure.DepartureModels.
        DepartureReadinessResponse;
import com.equitransit.backend.dto.departure.DepartureModels.HorseReceiptView;
import com.equitransit.backend.dto.departure.DepartureModels.IdentityResult;
import com.equitransit.backend.dto.departure.DepartureModels.ReceiptState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.service.departure.DepartureGatePolicy;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DepartureGatePolicy}. */
public class DepartureGatePolicyTest {

    /** Verifies microchip and passport matching and mismatch detection. */
    @Test
    public void verifyIdentityMatchesAndDetectsMismatch() {
        assertThat(DepartureGatePolicy.verifyIdentity(
                "981020000123456", "FEI-VN-2026-009",
                " 981020000123456 ", "fei-vn-2026-009"))
                .isEqualTo(IdentityResult.MATCHED);
        assertThat(DepartureGatePolicy.verifyIdentity(
                "981020000123456", "FEI-VN-2026-009",
                "981020000999999", "FEI-VN-2026-009"))
                .isEqualTo(IdentityResult.MISMATCHED);
        assertThat(DepartureGatePolicy.verifyIdentity(
                "981020000123456", "FEI-VN-2026-009",
                "981020000123456", "WRONG-PASS"))
                .isEqualTo(IdentityResult.MISMATCHED);
        assertThat(DepartureGatePolicy.verifyIdentity(
                null, "FEI-VN-2026-009",
                "981020000123456", "FEI-VN-2026-009"))
                .isEqualTo(IdentityResult.PENDING);
    }

    /** Verifies receipt status requires both MATCHED identity and fitness. */
    @Test
    public void receiptStatusRequiresMatchedIdentityAndFitness() {
        assertThat(DepartureGatePolicy.receiptStatus(
                IdentityResult.MATCHED, true))
                .isEqualTo(ReceiptState.ACCEPTED);
        assertThat(DepartureGatePolicy.receiptStatus(
                IdentityResult.MATCHED, false))
                .isEqualTo(ReceiptState.REJECTED);
        assertThat(DepartureGatePolicy.receiptStatus(
                IdentityResult.MISMATCHED, true))
                .isEqualTo(ReceiptState.REJECTED);
    }

    /** Verifies departure blockers and customer redaction. */
    @Test
    public void departureBlockersAndCustomerRedactionEnforced() {
        var now = OffsetDateTime.parse("2027-02-01T05:30:00Z");
        var passGate = DepartureGatePolicy.gateView(List.of());
        var blockedGate = DepartureGatePolicy.gateView(
                List.of("NO_RECONCILED_PAYMENT"));
        var receipt = new HorseReceiptView(1001L, 801L, 901L, 41L,
                "981020000123456", "FEI-VN-2026-009",
                IdentityResult.MATCHED, ReceiptState.ACCEPTED,
                "Internal vet/driver note", "evidence-key-1",
                "Stable Gate A", now);

        var blockers = DepartureGatePolicy.departureBlockers(
                blockedGate, passGate, passGate, false, null,
                IdentityResult.PENDING, false);
        assertThat(blockers).containsExactly(
                "NO_RECONCILED_PAYMENT",
                "DEPARTURE_AUTHORIZATION_REQUIRED",
                "HORSE_RECEIPT_MISSING");

        var readyBlockers = DepartureGatePolicy.departureBlockers(
                passGate, passGate, passGate, true, receipt,
                IdentityResult.MATCHED, false);
        assertThat(readyBlockers).isEmpty();

        var full = new DepartureReadinessResponse(
                101L, "000000000001A2B7", "CONFIRMED", 501L, 801L, 1,
                PlanState.CONFIRMED, 301L, 901L, LegState.CONFIRMED, null,
                now, now, passGate, passGate, passGate, true, 11L, now,
                true, List.of(), List.of(receipt));
        var redacted = DepartureGatePolicy.redactForCustomer(full);

        assertThat(redacted.departureAuthorizedBy()).isNull();
        assertThat(redacted.departureAuthorizedAt()).isEqualTo(now);
        assertThat(redacted.receipts()).hasSize(1);
        var redReceipt = redacted.receipts().getFirst();
        assertThat(redReceipt.receivedBy()).isNull();
        assertThat(redReceipt.observedMicrochip()).isNull();
        assertThat(redReceipt.observedPassportNumber()).isNull();
        assertThat(redReceipt.conditionNotes()).isNull();
        assertThat(redReceipt.evidenceFileKey()).isNull();
        assertThat(redReceipt.identityCheckResult())
                .isEqualTo(IdentityResult.MATCHED);
        assertThat(redReceipt.receiptStatus())
                .isEqualTo(ReceiptState.ACCEPTED);
    }
}
