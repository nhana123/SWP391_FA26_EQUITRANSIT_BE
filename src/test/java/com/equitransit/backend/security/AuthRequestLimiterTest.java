package com.equitransit.backend.security;

import com.equitransit.backend.exception.AuthThrottleException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Database budgets survive failed callers, instance replacement and races. */
@SpringBootTest
@ActiveProfiles("test")
public class AuthRequestLimiterTest {
    private static final String SECRET =
            "test-only-budget-key-material-32-bytes";
    @Autowired private AuthRequestBudgetRepository data;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private DataSource source;
    private final Instant start = Instant.parse("2030-01-01T00:00:00Z");

    /** @throws Exception if database isolation cannot be established */
    @BeforeEach
    public void isolate() throws Exception {
        try (var connection = source.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .startsWith("jdbc:h2:mem:auth");
        }
        data.deleteAll();
    }

    /** Failed login writes cannot roll back the rate counter. */
    @Test
    public void failedCallerCannotEraseReservedAttempt() {
        var limiter = limiter(start, 2);
        assertThatThrownBy(() -> new TransactionTemplate(manager)
                .executeWithoutResult(status -> {
                    limiter.reserve("user@example.invalid", "LOGIN");
                    throw new IllegalStateException("fixture login failure");
                })).isInstanceOf(IllegalStateException.class);
        limiter.reserve("USER@example.invalid", "LOGIN");
        assertThatThrownBy(() -> limiter.reserve("user@example.invalid",
                "LOGIN")).isInstanceOf(AuthThrottleException.class);
        assertThat(data.count()).isEqualTo(1);
        assertThat(data.findAll().getFirst().key).matches("[a-f0-9]{64}");
    }

    /** Instance state and changing case cannot bypass the same window. */
    @Test
    public void windowSurvivesNewInstanceAndResetsOnlyAfterDeadline() {
        limiter(start, 1).reserve("user@example.invalid", "RECOVERY");
        assertThatThrownBy(() -> limiter(start.plusSeconds(10), 1)
                .reserve("USER@example.invalid", "RECOVERY"))
                .isInstanceOf(AuthThrottleException.class);
        limiter(start.plusSeconds(901), 1)
                .reserve("user@example.invalid", "RECOVERY");
        assertThat(data.findAll().getFirst().requests).isEqualTo(1);
    }

    /** @throws Exception if concurrent creation loses counts */
    @Test
    public void simultaneousReservationsHaveOneWinner() throws Exception {
        var limiter = limiter(start, 1);
        var pool = Executors.newFixedThreadPool(2);
        var startGate = new CountDownLatch(1);
        try {
            Callable<Integer> request = () -> {
                startGate.await(5, TimeUnit.SECONDS);
                try {
                    limiter.reserve("user@example.invalid", "LOGIN");
                    return 200;
                } catch (AuthThrottleException rejected) {
                    return 429;
                }
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            startGate.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 429);
        } finally {
            pool.shutdownNow();
        }
    }

    private AuthRequestLimiter limiter(Instant time, int limit) {
        return new AuthRequestLimiter(data, manager,
                Clock.fixed(time, ZoneOffset.UTC), limit, SECRET);
    }
}
