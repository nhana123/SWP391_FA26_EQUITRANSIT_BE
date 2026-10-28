/*
 * AccountEmailListener
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Deliver notifications only after a successful commit.
 */
package com.equitransit.backend.mail;


import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/** Queues account mail atomically before the account transaction commits. */
@Component
public class AccountEmailListener {

    private final AccountMailOutbox outbox;

    /**
     * Creates the delivery listener.
     * @param outbox same-transaction durable account mail
     */
    public AccountEmailListener(AccountMailOutbox outbox) {
        this.outbox = outbox;
    }

    /**
     * Persists the request before commit; worker delivery is independent.
     * @param event the committed message
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onEmail(EmailEvent event) {
        outbox.enqueue(event);
    }
}
