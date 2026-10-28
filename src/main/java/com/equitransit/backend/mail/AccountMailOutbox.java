package com.equitransit.backend.mail;

import com.equitransit.backend.repository.AuthenticationTokenRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Atomic producer writes and independent delivery lease transactions. */
@Service
public class AccountMailOutbox {
    private static final int MAX_ATTEMPTS = 5;
    private final AccountMailRepository data;
    private final AuthenticationTokenRepository tokens;
    private final AccountMailCipher cipher;
    private final ObjectMapper json;
    private final Clock clock;

    /** @param data durable rows @param tokens live challenge status
     * @param cipher body encryption @param json codec @param clock UTC */
    public AccountMailOutbox(AccountMailRepository data,
            AuthenticationTokenRepository tokens, AccountMailCipher cipher,
            ObjectMapper json, Clock clock) {
        this.data = data;
        this.tokens = tokens;
        this.cipher = cipher;
        this.json = json;
        this.clock = clock;
    }

    /** Private worker transport, never returned to an API or logged.
     * @param id identity @param owner fencing token
     * @param payload ciphertext */
    public record Lease(UUID id, UUID owner, String payload) {
        @Override
        public String toString() {
            return "MailLease[REDACTED]";
        }
    }

    /** @param event notification inside its account transaction */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(EmailEvent event) {
        if (event.getUserId() == null || event.getPurpose() == null
                || event.getExpiresAt() == null) {
            throw new IllegalStateException("Account mail metadata missing.");
        }
        UUID id = event.getMessageId();
        if (data.existsById(id)) {
            return;
        }
        var row = new AccountMail();
        row.id = id;
        row.userId = event.getUserId();
        row.purpose = event.getPurpose();
        row.challengeHash = event.getChallengeHash();
        row.status = "PENDING";
        row.createdAt = now();
        row.availableAt = now();
        row.expiresAt = event.getExpiresAt();
        try {
            row.payload = cipher.seal(id, json.writeValueAsString(event));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Account mail encoding failed.");
        }
        data.saveAndFlush(row);
    }

    /** @return one exclusive lease; selection never caches mutable entities */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 15)
    public Optional<Lease> claim() {
        LocalDateTime time = now();
        for (UUID id : data.candidates(time, PageRequest.of(0, 10))) {
            var row = data.locked(id).orElse(null);
            if (row == null || !due(row, time)) {
                continue;
            }
            if (!row.expiresAt.isAfter(time)) {
                finish(row, "EXPIRED", null);
                continue;
            }
            if (row.challengeHash != null && tokens.findByTokenHash(
                    row.challengeHash).filter(t -> t.getUsedAt() == null
                            && t.getExpiresAt().isAfter(time)).isEmpty()) {
                finish(row, "CANCELLED", null);
                continue;
            }
            if (row.attempts >= MAX_ATTEMPTS) {
                finish(row, "FAILED", "ATTEMPTS_EXHAUSTED");
                continue;
            }
            row.status = "PROCESSING";
            row.attempts++;
            row.leaseUntil = time.plusMinutes(2);
            row.leaseToken = UUID.randomUUID();
            return Optional.of(new Lease(row.id, row.leaseToken, row.payload));
        }
        return Optional.empty();
    }

    /** @param lease ciphertext envelope @return decoded notification */
    public EmailEvent decode(Lease lease) {
        try {
            var event = json.readValue(cipher.open(lease.id(), lease.payload()),
                    EmailEvent.class);
            event.setMessageId(lease.id());
            return event;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Account mail decoding failed.");
        }
    }

    /** @param lease owner fence @param result successful mode or null
     * @param failure safe internal code, never SMTP exception text */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 15)
    public void complete(Lease lease, String result, String failure) {
        var row = data.locked(lease.id()).orElse(null);
        if (row == null || !"PROCESSING".equals(row.status)
                || !lease.owner().equals(row.leaseToken)) {
            return;
        }
        if (result != null) {
            finish(row, result, null);
        } else if (!row.expiresAt.isAfter(now())) {
            finish(row, "EXPIRED", failure);
        } else if (row.attempts >= MAX_ATTEMPTS
                || "PAYLOAD_UNAVAILABLE".equals(failure)) {
            finish(row, "FAILED", failure);
        } else {
            row.status = "PENDING";
            row.errorCode = failure;
            row.availableAt = now().plusSeconds(Math.min(300,
                    30L << (row.attempts - 1)));
            row.leaseToken = null;
            row.leaseUntil = null;
        }
    }

    /** @return counts only; no recipient, code, token or body */
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<String, Long> result = new TreeMap<>();
        for (Object[] row : data.counts()) {
            result.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return result;
    }

    /** @return safe delivery error aggregates only */
    @Transactional(readOnly = true)
    public Map<String, Long> errorCounts() {
        Map<String, Long> result = new TreeMap<>();
        for (Object[] row : data.errorCounts()) {
            result.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return result;
    }

    /** Erase expired queued secrets and bound terminal metadata retention. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 15)
    public void cleanup() {
        data.expire(now());
        data.purge(now().minusDays(30));
    }

    private boolean due(AccountMail row, LocalDateTime time) {
        return "PENDING".equals(row.status) && !row.availableAt.isAfter(time)
                || "PROCESSING".equals(row.status) && row.leaseUntil != null
                        && !row.leaseUntil.isAfter(time);
    }

    private void finish(AccountMail row, String state, String error) {
        row.status = state;
        row.errorCode = error;
        row.completedAt = now();
        row.payload = null;
        row.leaseToken = null;
        row.leaseUntil = null;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }
}
