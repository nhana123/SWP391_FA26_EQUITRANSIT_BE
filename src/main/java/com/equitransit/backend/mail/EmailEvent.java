/*
 * EmailEvent
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Carry an after-commit email notification.
 */
package com.equitransit.backend.mail;

import java.time.LocalDateTime;
import java.util.UUID;





/** Contains an email dispatched after commit. */
public class EmailEvent {
    private String recipient;

    private String subject;

    private String body;

    private String htmlBody;

    private UUID messageId = UUID.randomUUID();
    private Long userId;
    private String purpose;
    private String challengeHash;
    private LocalDateTime expiresAt;

    /** @return durable message identity */
    public UUID getMessageId() {
        return messageId;
    }
    /** @param value durable message identity */
    public void setMessageId(UUID value) {
        messageId = value;
    }
    /** @return account identity, never exposed in public mail status */
    public Long getUserId() {
        return userId;
    }
    /** @param value account identity */
    public void setUserId(Long value) {
        userId = value;
    }
    /** @return notification purpose */
    public String getPurpose() {
        return purpose;
    }
    /** @param value notification purpose */
    public void setPurpose(String value) {
        purpose = value;
    }
    /** @return associated challenge hash, not the raw token */
    public String getChallengeHash() {
        return challengeHash;
    }
    /** @param value associated challenge hash */
    public void setChallengeHash(String value) {
        challengeHash = value;
    }
    /** @return latest useful delivery time */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    /** @param value latest useful delivery time */
    public void setExpiresAt(LocalDateTime value) {
        expiresAt = value;
    }

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

    /**
     * Returns htmlBody.
     * @return the htmlBody
     */
    public String getHtmlBody() {
        return htmlBody;
    }

    /**
     * Sets htmlBody.
     * @param htmlBody the value to store
     */
    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }
}
