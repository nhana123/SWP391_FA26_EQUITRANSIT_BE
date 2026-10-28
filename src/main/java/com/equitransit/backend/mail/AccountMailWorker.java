package com.equitransit.backend.mail;

import com.equitransit.backend.security.AuthRequestLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Network delivery happens outside producer and lease transactions. */
@Component
@ConditionalOnProperty(name = "app.mail.worker-enabled", havingValue = "true",
        matchIfMissing = true)
public class AccountMailWorker {
    private static final Logger LOG = LoggerFactory.getLogger(
            AccountMailWorker.class);
    private final AccountMailOutbox outbox;
    private final AccountMailSender sender;
    private final String successState;
    private final AuthRequestLimiter limiter;

    /** @param outbox independent lease transactions @param sender transport
     * @param mode configured transport, not a claim of inbox delivery */
    public AccountMailWorker(AccountMailOutbox outbox, AccountMailSender sender,
            @Value("${app.mail.mode:file}") String mode,
            AuthRequestLimiter limiter) {
        this.outbox = outbox;
        this.limiter = limiter;
        this.sender = sender;
        successState = "smtp".equals(mode) ? "SMTP_ACCEPTED" : "FILE_WRITTEN";
    }

    /** Bounded batch; one lease is acquired immediately before each send. */
    @Scheduled(fixedDelayString = "${app.mail.poll-ms:5000}")
    public void poll() {
        try {
            limiter.cleanup();
            outbox.cleanup();
            for (int i = 0; i < 10; i++) {
                var lease = outbox.claim();
                if (lease.isEmpty()) {
                    return;
                }
                deliver(lease.get());
            }
        } catch (RuntimeException error) {
            LOG.warn("Account mail worker deferred; durable rows retained.");
        }
    }

    private void deliver(AccountMailOutbox.Lease lease) {
        EmailEvent event;
        try {
            event = outbox.decode(lease);
        } catch (RuntimeException error) {
            outbox.complete(lease, null, "PAYLOAD_UNAVAILABLE");
            return;
        }
        try {
            sender.send(event);
            outbox.complete(lease, successState, null);
            LOG.info("Account mail {}: {}", lease.id(), successState);
        } catch (RuntimeException error) {
            String code = error instanceof MailAuthenticationException
                    ? "SMTP_AUTH_FAILED" : "SEND_FAILED";
            outbox.complete(lease, null, code);
            LOG.warn("Account mail {} deferred: {}", lease.id(), code);
        }
    }
}
