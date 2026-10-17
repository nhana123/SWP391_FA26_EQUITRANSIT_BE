/*
 * NotificationWorker
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Isolated in-process outbox polling without a new event bus.
 */
package com.equitransit.backend.service;


import com.equitransit.backend.mail.AccountMailSender;
import com.equitransit.backend.mail.EmailEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/** SMTP/file adapter is invoked after lease commit, outside domain writes. */
@Component
@ConditionalOnProperty(name = "app.notifications.worker-enabled",
        havingValue = "true", matchIfMissing = true)
public class NotificationWorker {

    private static final Logger LOG = LoggerFactory.getLogger(
            NotificationWorker.class);

    private final NotificationWriter writer;
    private final AccountMailSender sender;

    /**
     * @param writer independent durable projection and retry leases
     * @param sender existing environment-selected file/SMTP adapter
     */
    public NotificationWorker(NotificationWriter writer,
            AccountMailSender sender) {
        this.writer = writer;
        this.sender = sender;
    }

    /** Failures retain durable sources/leases and never log private bodies. */
    @Scheduled(fixedDelayString = "${app.notifications.poll-ms:5000}")
    public void poll() {
        try {
            writer.dispatch();
            for (long id : writer.claimMail()) {
                try {
                    String recipient = writer.recipient(id);

                    if (recipient != null) {
                        var mail = new EmailEvent();

                        mail.setRecipient(recipient);
                        mail.setSubject("EquiTransit order update");
                        mail.setBody("Open EquiTransit to view your update.");
                        sender.send(mail);
                    }
                    writer.completeMail(id, true, recipient == null);
                } catch (RuntimeException error) {
                    writer.completeMail(id, false, false);
                    LOG.warn("Mail deferred; domain commit retained.");
                }
            }
        } catch (RuntimeException error) {
            LOG.warn("Projection deferred; durable audit retained.");
        }
    }
}
