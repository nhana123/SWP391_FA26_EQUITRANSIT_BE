package com.equitransit.backend.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** One transport attempt; durable worker owns retries, not HTML fallbacks. */
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
public class SmtpAccountMailSender implements AccountMailSender {
    private final JavaMailSender sender;
    private final String from;

    /** @param sender configured SMTP client @param from verified sender */
    public SmtpAccountMailSender(JavaMailSender sender,
            @Value("${app.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    /** @param event complete notification; no recipient/body logging */
    @Override
    public void send(EmailEvent event) {
        var mime = sender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(mime, true,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(event.getRecipient());
            helper.setSubject(event.getSubject());
            if (event.getHtmlBody() == null) {
                helper.setText(event.getBody());
            } else {
                helper.setText(event.getBody(), event.getHtmlBody());
            }
            String address = new InternetAddress(from).getAddress();
            mime.setHeader("Message-ID", "<" + event.getMessageId() + "@"
                    + address.substring(address.lastIndexOf('@') + 1) + ">");
        } catch (MessagingException error) {
            throw new MailPreparationException("Invalid account email.");
        }
        sender.send(mime);
    }
}
