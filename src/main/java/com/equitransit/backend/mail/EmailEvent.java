/*
 * EmailEvent
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Carry an after-commit email notification.
 */
package com.equitransit.backend.mail;





/** Contains an email dispatched after commit. */
public class EmailEvent {
    private String recipient;

    private String subject;

    private String body;

    /**
     * Returns recipient.
     * @return the recipient
     */
    public String getRecipient() {
        return recipient;
    }

    /**
     * Sets recipient.
     * @param recipient the value to store
     */
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    /**
     * Returns subject.
     * @return the subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets subject.
     * @param subject the value to store
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Returns body.
     * @return the body
     */
    public String getBody() {
        return body;
    }

    /**
     * Sets body.
     * @param body the value to store
     */
    public void setBody(String body) {
        this.body = body;
    }


}
