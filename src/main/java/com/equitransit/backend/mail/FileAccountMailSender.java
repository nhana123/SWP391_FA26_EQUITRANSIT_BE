/*
 * FileAccountMailSender
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Save development emails locally without external delivery.
 */
package com.equitransit.backend.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Writes development emails to an ignored local outbox. */
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "file")
public class FileAccountMailSender implements AccountMailSender {

    private final Path outbox;

    /**
     * Creates the development sender.
     * @param directory the ignored outbox directory
     */
    public FileAccountMailSender(
            @Value("${app.mail.outbox}") String directory) {
        this.outbox = Path.of(directory);
    }

    /**
     * Saves an email without sending it to an external recipient.
     * @param event the email content
     */
    @Override
    public void send(EmailEvent event) {
        Path file;
        String content;

        try {
            Files.createDirectories(outbox);
            file = outbox.resolve(UUID.randomUUID() + ".txt");
            content = "To: " + event.getRecipient() + "\nSubject: "
                    + event.getSubject() + "\n\n" + event.getBody();
            if (event.getHtmlBody() != null) {
                content += "\n\n--- HTML VERSION ---\n"
                        + event.getHtmlBody();
            }
            Files.writeString(file, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException error) {
            throw new IllegalStateException("Local email delivery failed.",
                    error);
        }
    }
}
