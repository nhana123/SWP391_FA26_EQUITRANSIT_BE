/*
 * QuotationCommitmentFacade
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify parent-bound accepted quote references for F07/F10.
 */
package com.equitransit.backend.service;


import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.entity.quote.CommercialReadiness;
import com.equitransit.backend.entity.quote.QuoteStatus;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.repository.QuoteRepository;


/** Internal consumer owns actor access; this facade has no HTTP endpoint. */
@Service
public class QuotationCommitmentFacade {

    private final AssessmentRepository orders;
    private final QuoteRepository quotes;

    /**
     * @param orders shared parent-lock boundary
     * @param quotes immutable commercial evidence
     */
    public QuotationCommitmentFacade(AssessmentRepository orders,
            QuoteRepository quotes) {
        this.orders = orders;
        this.quotes = quotes;
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
        return new CommercialReadiness(quote, revision, accepted.total());
    }

    private OrderException conflict() {
        return new OrderException(HttpStatus.CONFLICT,
                "ACCEPTED_QUOTE_REQUIRED",
                "Select an accepted quote for this order and revision.");
    }
}
