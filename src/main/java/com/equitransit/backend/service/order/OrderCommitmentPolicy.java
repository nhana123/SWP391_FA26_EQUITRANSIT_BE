/*
 * OrderCommitmentPolicy
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Provide the typed cross-flow boundary for protected orders.
 */
package com.equitransit.backend.service.order;


import org.springframework.stereotype.Component;
import com.equitransit.backend.repository.order.OrderCommitmentRepository;


/** F06/F10/F11 must use the same parent lock before accepting/authorizing. */
@Component
public class OrderCommitmentPolicy {

    private final OrderCommitmentRepository commitments;

    /**
     * Creates the lifecycle commitment boundary.
     * @param commitments the persisted finance/operation evidence reader
     */
    public OrderCommitmentPolicy(OrderCommitmentRepository commitments) {
        this.commitments = commitments;
    }

    /**
     * Checks protected commitments on a previously locked owned order.
     * @param order the locked order key
     * @return whether a direct customer rewrite must be refused
     */
    public boolean protectedOrder(long order) {
        return commitments.protectedOrder(order);
    }

    /**
     * Invalidates pending preparation for a newly submitted revision.
     * @param order the locked order key
     */
    public void invalidatePreparation(long order) {
        commitments.invalidatePreparation(order);
    }

    /**
     * Legal quantities may change without rewriting an accepted obligation.
     * @param order parent locked by the F08 assessment transaction
     */
    public void invalidateLegalPricing(long order) {
        commitments.invalidatePreparation(order);
    }
}
