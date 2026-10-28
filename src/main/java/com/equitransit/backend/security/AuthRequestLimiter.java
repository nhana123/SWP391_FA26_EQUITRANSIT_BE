package com.equitransit.backend.security;

import com.equitransit.backend.exception.AuthThrottleException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable throttling commits independently of failed authentication writes. */
@Service
public class AuthRequestLimiter {
    private final AuthRequestBudgetRepository data;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final int limit;
    private final String secret;

    /** @param data budgets @param manager transaction manager @param clock UTC
     * @param limit requests per account/action/15min @param secret HMAC key */
    public AuthRequestLimiter(AuthRequestBudgetRepository data,
            PlatformTransactionManager manager, Clock clock,
            @Value("${app.auth.request-limit:10}") int limit,
            @Value("${app.jwt.secret}") String secret) {
        this.data = data;
        this.clock = clock;
        this.limit = limit;
        this.secret = secret;
        if (limit < 1 || limit > 10000) {
            throw new IllegalStateException("Invalid auth request budget.");
        }
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(10);
    }

    /** @param email account input (including unknown accounts)
     * @param action REGISTER, LOGIN or RECOVERY; not user-controlled */
    public void reserve(String email, String action) {
        String key = TokenTools.otpHash(email.trim().toLowerCase(Locale.ROOT),
                "AUTH_BUDGET", action, secret);
        for (int retry = 0; retry < 3; retry++) {
            try {
                transaction.executeWithoutResult(status -> take(key));
                return;
            } catch (DataIntegrityViolationException
                    | TransientDataAccessException race) {
                if (retry == 2) {
                    throw new AuthThrottleException(60);
                }
            }
        }
    }

    /** Expired buckets contain no recoverable addresses and are removed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanup() {
        data.purge(LocalDateTime.now(clock));
    }

    private void take(String key) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        var budget = data.locked(key).orElse(null);
        if (budget == null) {
            if (data.count() >= 100000) {
                data.purge(now);
            }
            if (data.count() >= 100000) {
                throw new AuthThrottleException(60);
            }
            budget = new AuthRequestBudget();
            budget.key = key;
            budget.until = now.plusMinutes(15);
        } else if (!budget.until.isAfter(now)) {
            budget.until = now.plusMinutes(15);
            budget.requests = 0;
        }
        if (budget.requests >= limit) {
            throw new AuthThrottleException(Duration.between(now,
                    budget.until).toSeconds());
        }
        budget.requests++;
        data.saveAndFlush(budget);
    }
}
