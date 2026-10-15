/*
 * AccountMailSender
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Separate account email delivery from business logic.
 */
package com.equitransit.backend.mail;





/** Sends a verification or recovery message. */
public interface AccountMailSender {

    /**
     * Sends an account message.
     * @param event the message to deliver
     */
    void send(EmailEvent event);
}
