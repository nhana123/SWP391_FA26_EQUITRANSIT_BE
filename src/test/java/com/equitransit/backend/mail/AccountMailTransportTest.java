package com.equitransit.backend.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Real local SMTP exchange and idempotent offline output, no external mail. */
public class AccountMailTransportTest {
    @TempDir private Path directory;

    /** @throws Exception if actual MIME/SMTP delivery to a local sink fails */
    @Test
    public void smtpHasStableMessageIdAndAlternativeBodies() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try (var server = new ServerSocket(0, 1,
                InetAddress.getByName("127.0.0.1"))) {
            var received = pool.submit(() -> receive(server));
            var client = new JavaMailSenderImpl();
            client.setHost("127.0.0.1");
            client.setPort(server.getLocalPort());
            client.getJavaMailProperties().put("mail.smtp.timeout", "3000");
            client.getJavaMailProperties().put("mail.smtp.connectiontimeout",
                    "3000");
            var event = event();
            new SmtpAccountMailSender(client, "sender@example.invalid")
                    .send(event);
            String data = received.get(5, TimeUnit.SECONDS);
            var message = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(data.getBytes(
                            StandardCharsets.UTF_8)));
            assertThat(message.getMessageID()).contains(
                    event.getMessageId().toString());
            assertThat(message.getSubject()).isEqualTo(event.getSubject());
            assertThat(message.isMimeType("multipart/*")).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    /** Ambiguous send failure must propagate once, not trigger a second email.
     * @throws Exception if MIME setup fails */
    @Test
    public void smtpFailureDoesNotTriggerPlainTextFallback() throws Exception {
        var client = mock(JavaMailSender.class);
        when(client.createMimeMessage()).thenReturn(new MimeMessage(
                Session.getInstance(new Properties())));
        doThrow(new MailSendException("fixture transport failure"))
                .when(client).send(any(MimeMessage.class));
        var sender = new SmtpAccountMailSender(client,
                "sender@example.invalid");
        assertThatThrownBy(() -> sender.send(event()))
                .isInstanceOf(MailSendException.class);
        verify(client, times(1)).send(any(MimeMessage.class));
    }

    /** @throws Exception if an acknowledgement retry duplicates local mail */
    @Test
    public void fileDeliveryUsesStableIdForRetries() throws Exception {
        var sender = new FileAccountMailSender(directory.toString());
        var event = event();
        sender.send(event);
        sender.send(event);
        try (var files = Files.list(directory)) {
            assertThat(files.count()).isEqualTo(1);
        }
    }

    private EmailEvent event() {
        var event = new EmailEvent();
        event.setRecipient("receiver@example.invalid");
        event.setSubject("EquiTransit fixture verification");
        event.setBody("Fixture OTP 654321");
        event.setHtmlBody("<p>Fixture OTP <b>654321</b></p>");
        return event;
    }

    private String receive(ServerSocket server) throws Exception {
        server.setSoTimeout(5000);
        try (var socket = server.accept()) {
            socket.setSoTimeout(5000);
            var reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            var writer = new PrintWriter(socket.getOutputStream(), true,
                    StandardCharsets.UTF_8);
            writer.print("220 localhost SMTP ready\r\n");
            writer.flush();
            var content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String response = "250 OK";
                if (line.startsWith("DATA")) {
                    writer.print("354 Continue\r\n");
                    writer.flush();
                    while ((line = reader.readLine()) != null
                            && !line.equals(".")) {
                        content.append(line).append("\r\n");
                    }
                } else if (line.startsWith("QUIT")) {
                    writer.print("221 Bye\r\n");
                    writer.flush();
                    return content.toString();
                }
                writer.print(response + "\r\n");
                writer.flush();
            }
            return content.toString();
        }
    }
}
