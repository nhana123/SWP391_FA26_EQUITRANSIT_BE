package com.equitransit.backend.dto.payment;

/** Booking gates reflect current-revision net reconciled money only.
 * @param orderId order @param transportMode mode @param orderStatus state
 * @param hasAcceptedQuote current quote accepted @param quoteTotal USD
 * @param depositRequired duty exists @param depositAmount duty USD
 * @param depositPaid net paid USD @param isDepositSatisfied fully paid
 * @param canAssignDriver financial/order gate
 * @param blockReason blocker, including AWAITING_MANAGER_APPROVAL */
public record BookingFlowStatus(long orderId, String transportMode,
        String orderStatus, boolean hasAcceptedQuote, String quoteTotal,
        boolean depositRequired, String depositAmount, String depositPaid,
        boolean isDepositSatisfied, boolean canAssignDriver,
        String blockReason) { }
