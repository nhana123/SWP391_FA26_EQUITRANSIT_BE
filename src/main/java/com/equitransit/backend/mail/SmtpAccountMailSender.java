/*
 * SmtpAccountMailSender
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Deliver account emails using configured SMTP.
 */
package com.equitransit.backend.mail;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Sends account emails through explicitly configured SMTP. */
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
public class SmtpAccountMailSender implements AccountMailSender {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SmtpAccountMailSender.class);

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
    @SuppressWarnings("null")
    public void send(EmailEvent event) {
        LOGGER.info("Sending email to {} ({}) via SMTP...",
                event.getRecipient(), event.getSubject());
        if (event.getHtmlBody() != null) {
            try {
                MimeMessage mime = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(
                        mime, true, StandardCharsets.UTF_8.name());
                helper.setFrom(from);
                helper.setTo(event.getRecipient());
                helper.setSubject(event.getSubject());
                helper.setText(event.getBody(), event.getHtmlBody());
                sender.send(mime);
                LOGGER.info("HTML email delivered to {}", event.getRecipient());
                return;
            } catch (Exception ex) {
                LOGGER.warn("HTML email delivery failed: {}", ex.getMessage());
            }
        }
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(event.getRecipient());
        message.setSubject(event.getSubject());
        message.setText(event.getBody());
        sender.send(message);
        LOGGER.info("Plain text email delivered to {}", event.getRecipient());
    }
}
