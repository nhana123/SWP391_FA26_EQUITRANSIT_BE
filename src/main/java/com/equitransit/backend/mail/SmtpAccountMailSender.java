/*
 * SmtpAccountMailSender
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Deliver account emails using configured SMTP.
 */
package com.equitransit.backend.mail;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;


/** Sends account emails through explicitly configured SMTP. */
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
public class SmtpAccountMailSender implements AccountMailSender {

    private final JavaMailSender sender;
    private final String from;

    /**
     * Creates the SMTP sender.
     * @param sender the configured mail client
     * @param from the configured sender address
     */
    public SmtpAccountMailSender(JavaMailSender sender,
            @Value("${app.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    /**
     * Sends the configured account email.
     * @param event the email content
     */
    @Override
    public void send(EmailEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(event.getRecipient());
        message.setSubject(event.getSubject());
        message.setText(event.getBody());
        sender.send(message);
    }
}
