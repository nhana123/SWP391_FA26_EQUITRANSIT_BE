/*
 * QuotationCommitmentFacade
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify parent-bound accepted quote references for F07/F10.
 */
package com.equitransit.backend.service.quote;


import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import com.equitransit.backend.entity.quote.FinancialObligation;
import com.equitransit.backend.repository.payment.PaymentRepository;
import com.equitransit.backend.service.payment.FinancialReadinessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.entity.quote.CommercialReadiness;
import com.equitransit.backend.entity.quote.QuoteStatus;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.quote.QuoteRepository;
import com.equitransit.backend.service.BookingFlowService;


/** Internal consumer owns actor access; this facade has no HTTP endpoint. */
@Service
public class QuotationCommitmentFacade {

    private final AssessmentRepository orders;
    private final QuoteRepository quotes;
    private final PaymentRepository ledger;
    private BookingFlowService booking;

    /**
     * @param orders shared parent-lock boundary
     * @param quotes immutable commercial evidence
     * @param ledger net financial evidence
     */
    public QuotationCommitmentFacade(AssessmentRepository orders,
            QuoteRepository quotes,
            PaymentRepository ledger) {
        this.orders = orders;
        this.quotes = quotes;
        this.ledger = ledger;
    }

    /** @param booking explicit mode-aware balance-before-departure contract */
    @org.springframework.beans.factory.annotation.Autowired
    public void configureBooking(BookingFlowService booking) {
        this.booking = booking;
    }

    /**
     * Must run inside the authorized consumer's write transaction.
     * Locks order before quote, retaining the lock until consumer commit.
     * @param order authorized parent order
     * @param revision consumer plan/payment revision
     * @param quote selected accepted quote
     * @return accepted-price evidence; no financial/legal/operational PASS
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CommercialReadiness requireAccepted(long order, long revision,
            long quote) {
        var parent = orders.order(order, true).orElseThrow(this::conflict);
        var accepted = quotes.find(order, quote).orElseThrow(this::conflict);

        if (parent.revisionId() == null || parent.revisionId() != revision
                || accepted.revision() != revision
                || accepted.status() != QuoteStatus.ACCEPTED
                || accepted.acceptedAt() == null || accepted.total() == null) {
            throw conflict();
        }
        return readiness(order, revision, quote,
                OffsetDateTime.now(Clock.systemUTC()));
    }

    /**
     * Consumer must authorize access and retain the order lock to its commit.
     * @param order authorized parent
     * @param revision exact contractual revision
     * @param quote accepted quote
     * @param asOf explicit F11/F14 evaluation instant
     * @return separate departure and settlement financial evidence
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CommercialReadiness readiness(long order, long revision,
            long quote, OffsetDateTime asOf) {
        var parent = orders.order(order, true).orElseThrow(this::conflict);
        var accepted = quotes.find(order, quote).orElseThrow(this::conflict);

        if (parent.revisionId() == null || parent.revisionId() != revision
                || accepted.revision() != revision
                || accepted.status() != QuoteStatus.ACCEPTED
                || accepted.acceptedAt() == null || accepted.total() == null) {
            throw conflict();
        }
        if (!ledger.ready()) {
            return new CommercialReadiness(quote, revision, accepted.total(),
                    BigDecimal.ZERO, asOf, List.of(),
                    List.of("PAYMENT_SCHEMA_PENDING"),
                    List.of("PAYMENT_SCHEMA_PENDING"));
        }
        ledger.rows("quote", Map.of("order", order));
        ledger.lockLedger();
        var rows = ledger.rows("balances", Map.of("quote", quote,
                "asOf", Timestamp.from(asOf.toInstant())));
        boolean beforeDeparture = booking != null && booking.modeAware(order);
        var obligations = rows.stream().map(row -> {
            var at = (Timestamp) row.get("DueAt");

            return new FinancialObligation(
                    ((Number) row.get("ScheduleID")).longValue(),
                    (BigDecimal) row.get("AmountDue"),
                    (BigDecimal) row.get("PaidAmount"), at == null
                            ? null : at.toInstant().atOffset(
                                    ZoneOffset.UTC));
        }).toList();
        var result = FinancialReadinessPolicy.evaluate(quote, revision,
                accepted.total(), obligations, asOf,
                ledger.number("pendingRefunds",
                        Map.of("quote", quote)) > 0);
        boolean balanceMissing = rows.stream().anyMatch(row ->
                "BALANCE".equals(row.get("PaymentStage"))
                && ((BigDecimal) row.get("PaidAmount")).compareTo(
                        (BigDecimal) row.get("AmountDue")) < 0);
        if (!beforeDeparture || !balanceMissing) {
            return result;
        }
        var blockers = new java.util.ArrayList<>(result.departureBlockers());
        blockers.add("BALANCE_REQUIRED_BEFORE_DEPARTURE");
        return new CommercialReadiness(quote, revision, result.total(),
                result.paidAmount(), asOf, obligations, List.copyOf(blockers),
                result.settlementBlockers());
    }

    private OrderException conflict() {
        return new OrderException(HttpStatus.CONFLICT,
                "ACCEPTED_QUOTE_REQUIRED",
                "Select an accepted quote for this order and revision.");
    }
}
