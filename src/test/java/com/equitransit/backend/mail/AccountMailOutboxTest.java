package com.equitransit.backend.mail;

import com.equitransit.backend.dto.request.RegisterRequest;
import com.equitransit.backend.entity.AccountRole;
import com.equitransit.backend.entity.AuthenticationToken;
import com.equitransit.backend.entity.UserAccount;
import com.equitransit.backend.repository.AccountRoleRepository;
import com.equitransit.backend.repository.AuthenticationTokenRepository;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.AuthRequestLimiter;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.service.AuthService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H2 transactions, deterministic deadlines and isolated mail failures. */
@SpringBootTest
@ActiveProfiles("test")
public class AccountMailOutboxTest {
    @Autowired private AccountMailOutbox outbox;
    @MockitoSpyBean private AccountMailRepository data;
    @Autowired private AccountMailCipher cipher;
    @Autowired private AuthenticationTokenRepository tokens;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountRoleRepository roles;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private AuthRequestLimiter limiter;
    @Autowired private AuthService auth;
    @Autowired private DataSource source;
    @MockitoBean private AccountMailSender sender;
    @MockitoBean private Clock clock;
    private final AtomicReference<Instant> time = new AtomicReference<>();
    private UserAccount user;

    /** @throws Exception if a non-isolated database is selected */
    @BeforeEach
    public void setup() throws Exception {
        try (var connection = source.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .startsWith("jdbc:h2:mem:auth");
        }
        time.set(Instant.parse("2030-01-01T00:00:00Z"));
        when(clock.instant()).thenAnswer(call -> time.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        reset(data, sender);
        data.deleteAll();
        var role = roles.findByRoleCode("CUSTOMER").orElseGet(() -> {
            var created = new AccountRole();
            created.setRoleCode("CUSTOMER");
            return roles.saveAndFlush(created);
        });
        user = new UserAccount();
        user.setEmail(UUID.randomUUID() + "@example.invalid");
        user.setPasswordHash("test-only-credential-hash");
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCreatedAt(now());
        user = users.saveAndFlush(user);
    }

    /** Producer rollback must also remove the pending encrypted email. */
    @Test
    public void queueIsAtomicAndPayloadIsEncrypted() {
        var event = event();
        assertThatThrownBy(() -> new TransactionTemplate(manager)
                .executeWithoutResult(status -> {
                    outbox.enqueue(event);
                    throw new IllegalStateException("fixture rollback");
                })).isInstanceOf(IllegalStateException.class);
        assertThat(data.count()).isZero();
        enqueue(event);
        var row = row(event);
        assertThat(row.payload).doesNotContain("654321", "private-token",
                user.getEmail());
        assertThat(cipher.open(row.id, row.payload)).contains("654321");
        assertThatThrownBy(() -> cipher.open(UUID.randomUUID(), row.payload))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Outbox failure must prevent account creation, not silently lose mail. */
    @Test
    public void queueFailureRollsBackRegistration() {
        var request = new RegisterRequest();
        request.setEmail(UUID.randomUUID() + "@example.invalid");
        request.setPassword("ExamplePassword123!");
        request.setConfirmPassword("ExamplePassword123!");
        request.setFullName("Outbox atomic fixture");
        doThrow(new DataIntegrityViolationException("fixture queue failure"))
                .when(data).saveAndFlush(any(AccountMail.class));
        assertThatThrownBy(() -> auth.register(request))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.findByEmailIgnoreCase(request.getEmail())).isEmpty();
        assertThat(data.count()).isZero();
    }

    /** A new worker instance must recover a retry from durable storage. */
    @Test
    public void transientFailureRetriesAfterRestartAndErasesPayload() {
        var event = event();
        enqueue(event);
        doThrow(new MailSendException("fixture SMTP failure"))
                .doNothing().when(sender).send(any());
        worker().poll();
        assertThat(row(event).status).isEqualTo("PENDING");
        assertThat(row(event).attempts).isEqualTo(1);
        worker().poll();
        verify(sender, times(1)).send(any());
        advance(31);
        worker().poll();
        assertThat(row(event).status).isEqualTo("SMTP_ACCEPTED");
        assertThat(row(event).payload).isNull();
        verify(sender, times(2)).send(any());
    }

    /** Five failed sends reach a terminal state with a safe reason only. */
    @Test
    public void retryBudgetIsBounded() {
        var event = event();
        event.setExpiresAt(now().plusDays(1));
        enqueue(event);
        doThrow(new MailSendException("private provider details"))
                .when(sender).send(any());
        for (int attempt = 0; attempt < 5; attempt++) {
            worker().poll();
            advance(301);
        }
        assertThat(row(event).status).isEqualTo("FAILED");
        assertThat(row(event).attempts).isEqualTo(5);
        assertThat(row(event).payload).isNull();
        assertThat(row(event).errorCode).isEqualTo("SEND_FAILED");
        worker().poll();
        verify(sender, times(5)).send(any());
    }

    /** Dead OTP mail must not reach a provider, and its secrets are erased. */
    @Test
    public void expiredAndSupersededChallengesAreNotSent() {
        var expired = event();
        enqueue(expired);
        advance(601);
        worker().poll();
        assertThat(row(expired).status).isEqualTo("EXPIRED");
        assertThat(row(expired).payload).isNull();
        var superseded = event();
        superseded.setChallengeHash(TokenTools.hash("missing-fixture"));
        enqueue(superseded);
        worker().poll();
        assertThat(row(superseded).status).isEqualTo("CANCELLED");
        verify(sender, never()).send(any());
    }

    /** A successful verification invalidates the corresponding queued email. */
    @Test
    public void consumedChallengeIsCancelled() {
        var event = event();
        var token = new AuthenticationToken();
        token.setUser(user);
        token.setTokenHash(TokenTools.hash(UUID.randomUUID().toString()));
        token.setPurpose("VERIFY_EMAIL");
        token.setCreatedAt(now());
        token.setExpiresAt(now().plusMinutes(10));
        token.setUsedAt(now());
        tokens.saveAndFlush(token);
        event.setChallengeHash(token.getTokenHash());
        enqueue(event);
        worker().poll();
        assertThat(row(event).status).isEqualTo("CANCELLED");
        verify(sender, never()).send(any());
    }

    /** A crashed owner cannot acknowledge a replacement worker's lease. */
    @Test
    public void leasesRecoverAndStaleAcknowledgementsAreFenced() {
        var event = event();
        enqueue(event);
        var first = outbox.claim().orElseThrow();
        assertThat(outbox.claim()).isEmpty();
        advance(121);
        var second = outbox.claim().orElseThrow();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.owner()).isNotEqualTo(first.owner());
        outbox.complete(first, "SMTP_ACCEPTED", null);
        assertThat(row(event).status).isEqualTo("PROCESSING");
        outbox.complete(second, "SMTP_ACCEPTED", null);
        assertThat(row(event).status).isEqualTo("SMTP_ACCEPTED");
    }

    /** @throws Exception if simultaneous worker claims are not exclusive */
    @Test
    public void parallelWorkersHaveOnlyOneLease() throws Exception {
        enqueue(event());
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> claim = () -> {
                start.await(5, TimeUnit.SECONDS);
                return outbox.claim().isPresent();
            };
            var first = pool.submit(claim);
            var second = pool.submit(claim);
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)
                    ^ second.get(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    private EmailEvent event() {
        var event = new EmailEvent();
        event.setRecipient(user.getEmail());
        event.setUserId(user.getUserId());
        event.setPurpose("VERIFY_EMAIL");
        event.setSubject("Fixture notification");
        event.setBody("OTP 654321 private-token");
        event.setExpiresAt(now().plusMinutes(10));
        return event;
    }

    private void enqueue(EmailEvent event) {
        new TransactionTemplate(manager).executeWithoutResult(
                status -> outbox.enqueue(event));
    }

    private AccountMail row(EmailEvent event) {
        return data.findById(event.getMessageId()).orElseThrow();
    }

    private AccountMailWorker worker() {
        return new AccountMailWorker(outbox, sender, "smtp", limiter);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(time.get(), ZoneOffset.UTC);
    }

    private void advance(long seconds) {
        time.updateAndGet(value -> value.plusSeconds(seconds));
    }
}
