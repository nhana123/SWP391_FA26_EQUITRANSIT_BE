/*
 * AccountEmailListener
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Deliver notifications only after a successful commit.
 */
package com.equitransit.backend.mail;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/** Delivers email only after the account transaction commits. */
@Component
public class AccountEmailListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AccountEmailListener.class);

    private final AccountMailSender sender;

    /**
     * Creates the delivery listener.
     * @param sender the environment-specific sender
     */
    public AccountEmailListener(AccountMailSender sender) {
        this.sender = sender;
    }

    /**
     * Delivers committed account notifications.
     * @param event the committed message
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmail(EmailEvent event) {
        try {
            sender.send(event);
        } catch (RuntimeException error) {
            LOGGER.error("Account email delivery failed; request a new link.");
        }
    }
}
