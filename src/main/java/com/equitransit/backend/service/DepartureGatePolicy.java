/*
 * DepartureGatePolicy
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Pure domain policy for F11 departure gates and horse receipt.
 */
package com.equitransit.backend.service;

import com.equitransit.backend.dto.departure.DepartureModels.
        DepartureReadinessResponse;
import com.equitransit.backend.dto.departure.DepartureModels.GateDetailView;
import com.equitransit.backend.dto.departure.DepartureModels.HorseReceiptView;
import com.equitransit.backend.dto.departure.DepartureModels.IdentityResult;
import com.equitransit.backend.dto.departure.DepartureModels.ReceiptState;
import com.equitransit.backend.dto.plan.PlanModels.GateState;
import java.util.ArrayList;
import java.util.List;

/** Evaluates 3-gate readiness, physical horse identity and redaction rules. */
public final class DepartureGatePolicy {

    private DepartureGatePolicy() { }

    /**
     * Compares driver-observed microchip and passport against order record.
     *
     * @param expectedChip expected horse microchip from order snapshot
     * @param expectedPassport expected horse passport from horse record
     * @param observedChip microchip scanned by driver at pickup
     * @param observedPassport passport number inspected by driver at pickup
     * @return MATCHED only when both expected and observed identifiers match
     */
    public static IdentityResult verifyIdentity(String expectedChip,
            String expectedPassport, String observedChip,
            String observedPassport) {
        if (blank(expectedChip) || blank(expectedPassport)) {
            return IdentityResult.PENDING;
        }
        if (blank(observedChip) || blank(observedPassport)) {
            return IdentityResult.MISMATCHED;
        }
        boolean chipMatch = expectedChip.trim()
                .equalsIgnoreCase(observedChip.trim());
        boolean passportMatch = expectedPassport.trim()
                .equalsIgnoreCase(observedPassport.trim());

        return chipMatch && passportMatch
                ? IdentityResult.MATCHED : IdentityResult.MISMATCHED;
    }

    /**
     * Determines physical receipt state from identity check and fitness flag.
     *
     * @param identity microchip and passport comparison result
     * @param fitToTravel whether driver observed horse fit for road departure
     * @return ACCEPTED only when identity is MATCHED and horse is fit
     */
    public static ReceiptState receiptStatus(IdentityResult identity,
            boolean fitToTravel) {
        return identity == IdentityResult.MATCHED && fitToTravel
                ? ReceiptState.ACCEPTED : ReceiptState.REJECTED;
    }

    /**
     * Builds a gate detail view from computed blocker codes.
     *
     * @param blockers raw blocker codes
     * @return PASS when empty, BLOCKED when any blocker exists
     */
    public static GateDetailView gateView(List<String> blockers) {
        var distinct = blockers == null ? List.<String>of()
                : blockers.stream().distinct().toList();

        return new GateDetailView(
                distinct.isEmpty() ? GateState.PASS : GateState.BLOCKED,
                distinct);
    }

    /**
     * Computes full departure blocker list across 3 gates, auth and receipt.
     *
     * @param financial evaluated financial gate
     * @param legal evaluated legal/document gate
     * @param operational evaluated operational gate
     * @param authorized whether an active manager authorized departure
     * @param receipt latest horse receipt on the first road leg
     * @param recheckedIdentity live re-verification of receipt identifiers
     * @param horseOverlap whether the horse overlaps another active order
     * @return deduplicated list of departure blocker codes
     */
    public static List<String> departureBlockers(GateDetailView financial,
            GateDetailView legal, GateDetailView operational,
            boolean authorized, HorseReceiptView receipt,
            IdentityResult recheckedIdentity, boolean horseOverlap) {
        var blockers = new ArrayList<String>();

        blockers.addAll(financial.blockers());
        blockers.addAll(legal.blockers());
        blockers.addAll(operational.blockers());
        if (!authorized) {
            blockers.add("DEPARTURE_AUTHORIZATION_REQUIRED");
        }
        if (receipt == null) {
            blockers.add("HORSE_RECEIPT_MISSING");
        } else {
            if (receipt.identityCheckResult() != IdentityResult.MATCHED
                    || recheckedIdentity != IdentityResult.MATCHED) {
                blockers.add("HORSE_IDENTITY_MISMATCHED");
            }
            if (receipt.receiptStatus() != ReceiptState.ACCEPTED) {
                blockers.add("HORSE_RECEIPT_NOT_ACCEPTED");
            }
        }
        if (horseOverlap) {
            blockers.add("HORSE_SCHEDULE_OVERLAP");
        }
        return blockers.stream().distinct().toList();
    }

    /**
     * Redacts internal staff IDs, raw microchip/passport and notes for customer
     * reads while preserving high-level gate and receipt status.
     *
     * @param raw unredacted readiness response
     * @return customer-safe readiness response
     */
    public static DepartureReadinessResponse redactForCustomer(
            DepartureReadinessResponse raw) {
        var safeReceipts = raw.receipts().stream()
                .map(r -> new HorseReceiptView(r.receiptId(), r.planId(),
                        r.legId(), null, null, null, r.identityCheckResult(),
                        r.receiptStatus(), null, null, r.locationDescription(),
                        r.receivedAt()))
                .toList();

        return new DepartureReadinessResponse(raw.orderId(),
                raw.recordVersion(), raw.orderStatus(),
                raw.currentRevisionId(), raw.planId(), raw.planVersionNo(),
                raw.planStatus(), raw.acceptedQuoteId(), raw.firstLegId(),
                raw.firstLegStatus(), raw.firstLegActualDeparture(),
                raw.asOf(), raw.gatesCheckedAt(), raw.financialGate(),
                raw.legalGate(), raw.operationalGate(),
                raw.readyForAuthorization(), null,
                raw.departureAuthorizedAt(), raw.readyForDeparture(),
                raw.departureBlockers(), safeReceipts);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
