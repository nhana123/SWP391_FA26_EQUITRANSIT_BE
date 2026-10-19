/*
 * AuthFlowTest
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Verify account flows through the real HTTP/security stack.
 */
package com.equitransit.backend;


import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import com.equitransit.backend.entity.AccountRole;
import com.equitransit.backend.entity.AuthenticationToken;
import com.equitransit.backend.entity.Customer;
import com.equitransit.backend.entity.UserAccount;
import com.equitransit.backend.mail.EmailEvent;
import com.equitransit.backend.repository.AccountRoleRepository;
import com.equitransit.backend.repository.AuthenticationTokenRepository;
import com.equitransit.backend.repository.CustomerRepository;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.TokenTools;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;


/** Exercises registration, activation, access control and password recovery. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
public class AuthFlowTest {

    private static final String EMAIL = "flow-test@example.invalid";
    private static final String PASSWORD = "ExamplePassword123!";
    private static final String NEW_PASSWORD = "ChangedPassword456!";

    private boolean isolatedDatabase;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserAccountRepository users;
    @MockitoSpyBean
    private CustomerRepository customers;
    @Autowired
    private AuthenticationTokenRepository tokens;
    @Autowired
    private AccountRoleRepository roles;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private JwtEncoder encoder;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private DataSource dataSource;

    /**
     * Prepares an isolated fixture, refusing any production database.
     * @throws Exception if fixture setup fails
     */
    @BeforeEach
    public void prepare() throws Exception {
        String url;
        String database;
        AccountRole role;

        isolatedDatabase = false;
        try (var connection = dataSource.getConnection()) {
            url = connection.getMetaData().getURL();
            database = connection.getCatalog();
        }
        assertThat(url.split(";")[0].equals("jdbc:h2:mem:auth")
                || (url.startsWith("jdbc:sqlserver:")
                        && "EquiTransit_AuthTest".equals(database))).isTrue();
        isolatedDatabase = true;
        if (url.startsWith("jdbc:h2:")) {
            new org.springframework.jdbc.datasource.init.
                    ResourceDatabasePopulator(new org.springframework.core.io.
                            ClassPathResource("refresh-test-schema.sql"))
                    .execute(dataSource);
        }
        reset(customers);
        clean();
        if (roles.findByRoleCode("CUSTOMER").isEmpty()) {
            role = new AccountRole();
            role.setRoleCode("CUSTOMER");
            roles.saveAndFlush(role);
        }
    }

    /** Removes only the isolated authentication fixtures. */
    @AfterEach
    public void clean() {
        if (!isolatedDatabase) {
            return;
        }
        new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .execute("DELETE FROM dbo.RefreshTokenSession");
        tokens.deleteAll();
        customers.deleteAll();
        users.deleteAll();
    }

    /**
     * Verifies registration, activation, login and safe profile output.
     * @throws Exception if request execution fails
     */
    @Test
    public void customerMustVerifyBeforeLoginAndCanReadOwnProfile()
            throws Exception {
        String raw;
        String access;
        UserAccount user;

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        user = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo("INACTIVE");
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, user.getPasswordHash()))
                .isTrue();
        postJson("/api/auth/login", Map.of("email", EMAIL,
                "password", PASSWORD)).andExpect(status().isUnauthorized());
        raw = latestToken();
        assertThat(tokens.findByTokenHash(TokenTools.hash(raw))).isPresent();
        assertThat(tokens.findAll().getFirst().getTokenHash())
                .isNotEqualTo(raw);
        verify(raw).andExpect(status().isOk());
        verify(raw).andExpect(status().isBadRequest());
        access = login(EMAIL, PASSWORD);
        mvc.perform(get("/api/customers/me")
                .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("email").value(EMAIL))
                .andExpect(jsonPath("fullName").value("Nguyen Van Minh"))
                .andExpect(jsonPath("passwordHash").doesNotExist());
    }

    /**
     * Rejects case-insensitive duplicate emails without extra records.
     * @throws Exception if request execution fails
     */
    @Test
    public void duplicateEmailDoesNotCreateAnotherCustomer() throws Exception {
        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        register(EMAIL.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict());
        assertThat(users.count()).isEqualTo(1);
        assertThat(customers.count()).isEqualTo(1);
    }

    /**
     * Rejects password mismatch and attempts to assign staff privileges.
     * @throws Exception if request execution fails
     */
    @Test
    public void invalidRegistrationAndRoleInjectionAreRejected()
            throws Exception {
        postJson("/api/auth/register", Map.of("email", EMAIL,
                "password", PASSWORD, "confirmPassword", NEW_PASSWORD,
                "fullName", "Minh")).andExpect(status().isBadRequest());
        postJson("/api/auth/register", Map.of("email", EMAIL,
                "password", PASSWORD, "confirmPassword", PASSWORD,
                "fullName", "Minh", "role", "MANAGER"))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isZero();
    }

    /**
     * Rolls back the account if persisting its customer profile fails.
     * @throws Exception if request execution fails
     */
    @Test
    public void profileFailureRollsBackRegistration() throws Exception {
        doThrow(new DataIntegrityViolationException("Simulated insert failure"))
                .when(customers).saveAndFlush(any(Customer.class));
        register(EMAIL, PASSWORD).andExpect(status().isConflict());
        assertThat(users.count()).isZero();
        assertThat(tokens.count()).isZero();
    }

    /**
     * Rejects expired verification links and invalidates links on resend.
     * @throws Exception if request execution fails
     */
    @Test
    public void expiredAndSupersededLinksCannotActivateAccount()
            throws Exception {
        String raw;
        AuthenticationToken token;

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        raw = latestToken();
        token = tokens.findByTokenHash(TokenTools.hash(raw)).orElseThrow();
        token.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(2));
        token.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        tokens.saveAndFlush(token);
        verify(raw).andExpect(status().isBadRequest());
        postJson("/api/auth/resend-verification", Map.of("email", EMAIL))
                .andExpect(status().isAccepted());
        verify(latestToken()).andExpect(status().isOk());
        verify(raw).andExpect(status().isBadRequest());
    }

    /**
     * Resets a password once and rejects previous access credentials.
     * @throws Exception if request execution fails
     */
    @Test
    public void passwordResetInvalidatesOldPasswordAndAccessToken()
            throws Exception {
        String oldAccess;
        String resetToken;
        Map<String, String> resetInput;

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        verify(latestToken()).andExpect(status().isOk());
        oldAccess = login(EMAIL, PASSWORD);
        postJson("/api/auth/forgot-password", Map.of("email", EMAIL))
                .andExpect(status().isAccepted());
        resetToken = latestToken();
        verify(resetToken).andExpect(status().isBadRequest());
        resetInput = Map.of("token", resetToken, "password", NEW_PASSWORD,
                "confirmPassword", NEW_PASSWORD);
        postJson("/api/auth/reset-password", resetInput)
                .andExpect(status().isOk());
        postJson("/api/auth/reset-password", resetInput)
                .andExpect(status().isBadRequest());
        postJson("/api/auth/login", Map.of("email", EMAIL,
                "password", PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers/me")
                .header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized());
        assertThat(login(EMAIL, NEW_PASSWORD)).isNotBlank();
    }

    /**
     * Prevents anonymous and locked-account profile access.
     * @throws Exception if request execution fails
     */
    @Test
    public void profileRequiresAnActiveAuthenticatedAccount() throws Exception {
        String access;
        UserAccount user;

        mvc.perform(get("/api/customers/me"))
                .andExpect(status().isUnauthorized());
        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        verify(latestToken()).andExpect(status().isOk());
        access = login(EMAIL, PASSWORD);
        user = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        user.setStatus("LOCKED");
        users.saveAndFlush(user);
        mvc.perform(get("/api/customers/me")
                .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Gives unknown email addresses the same recovery response.
     * @throws Exception if request execution fails
     */
    @Test
    public void recoveryDoesNotRevealUnknownEmail() throws Exception {
        postJson("/api/auth/forgot-password", Map.of("email", EMAIL))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("message").value(
                        "If an eligible account exists, "
                                + "an email will be sent."));
        assertThat(tokens.count()).isZero();
    }

    /**
     * Does not reactivate a verified customer disabled by an administrator.
     * @throws Exception if request execution fails
     */
    @Test
    public void resendCannotReactivateAnAdministrativelyDisabledAccount()
            throws Exception {
        UserAccount user;
        long count;

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        verify(latestToken()).andExpect(status().isOk());
        user = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        user.setStatus("INACTIVE");
        users.saveAndFlush(user);
        count = tokens.count();
        postJson("/api/auth/resend-verification", Map.of("email", EMAIL))
                .andExpect(status().isAccepted());
        assertThat(tokens.count()).isEqualTo(count);
        verify(latestToken()).andExpect(status().isBadRequest());
        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().getStatus())
                .isEqualTo("INACTIVE");
    }

    /**
     * Never interprets customer identifiers in a query as profile ownership.
     * @throws Exception if request execution fails
     */
    @Test
    public void profileOwnershipComesFromTokenNotQueryParameters()
            throws Exception {
        String access;

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        verify(latestToken()).andExpect(status().isOk());
        access = login(EMAIL, PASSWORD);
        mvc.perform(get("/api/customers/me").param("customerId", "999999")
                .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("email").value(EMAIL));
    }

    /**
     * Gives unknown accounts and wrong passwords the same login failure.
     * @throws Exception if request execution fails
     */
    @Test
    public void loginFailureDoesNotRevealWhetherAnAccountExists()
            throws Exception {
        String unknown = postJson("/api/auth/login", Map.of("email", EMAIL,
                "password", PASSWORD)).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrong;

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        verify(latestToken()).andExpect(status().isOk());
        wrong = postJson("/api/auth/login", Map.of("email", EMAIL,
                "password", NEW_PASSWORD)).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(wrong).isEqualTo(unknown);
    }

    /**
     * Keeps locked accounts inactive even with an unused verification link.
     * @throws Exception if request execution fails
     */
    @Test
    public void verificationCannotUnlockAnAccount() throws Exception {
        String raw;
        UserAccount user;

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        raw = latestToken();
        user = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        user.setStatus("LOCKED");
        users.saveAndFlush(user);
        verify(raw).andExpect(status().isBadRequest());
        assertThat(users.findById(user.getUserId()).orElseThrow().getStatus())
                .isEqualTo("LOCKED");
        assertThat(tokens.findByTokenHash(TokenTools.hash(raw))
                .orElseThrow().getUsedAt()).isNull();
    }

    /**
     * Leaves the password unchanged when a reset token has expired.
     * @throws Exception if request execution fails
     */
    @Test
    public void expiredResetTokenCannotChangePassword() throws Exception {
        String raw;
        AuthenticationToken token;

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        verify(latestToken()).andExpect(status().isOk());
        postJson("/api/auth/forgot-password", Map.of("email", EMAIL))
                .andExpect(status().isAccepted());
        raw = latestToken();
        token = tokens.findByTokenHash(TokenTools.hash(raw)).orElseThrow();
        token.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(2));
        token.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        tokens.saveAndFlush(token);
        postJson("/api/auth/reset-password", Map.of("token", raw,
                "password", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
        assertThat(login(EMAIL, PASSWORD)).isNotBlank();
        assertThat(tokens.findByTokenHash(TokenTools.hash(raw))
                .orElseThrow().getUsedAt()).isNull();
    }

    /**
     * Allows exactly one of two simultaneous verification attempts.
     * @throws Exception if request execution or synchronization fails
     */
    @Test
    public void concurrentVerificationConsumesTheTokenOnlyOnce()
            throws Exception {
        String raw;
        List<Integer> statuses;

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        raw = latestToken();
        statuses = race(() -> verify(raw).andReturn()
                .getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        assertThat(tokens.findByTokenHash(TokenTools.hash(raw))
                .orElseThrow().getUsedAt()).isNotNull();
        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().getStatus())
                .isEqualTo("ACTIVE");
    }

    /**
     * Allows exactly one of two simultaneous password-reset attempts.
     * @throws Exception if request execution or synchronization fails
     */
    @Test
    public void concurrentResetConsumesTheTokenOnlyOnce() throws Exception {
        Map<String, String> input;
        String raw;
        List<Integer> statuses;

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        verify(latestToken()).andExpect(status().isOk());
        postJson("/api/auth/forgot-password", Map.of("email", EMAIL))
                .andExpect(status().isAccepted());
        raw = latestToken();
        input = Map.of("token", raw, "password", NEW_PASSWORD,
                "confirmPassword", NEW_PASSWORD);
        statuses = race(() -> postJson("/api/auth/reset-password", input)
                .andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        assertThat(tokens.findByTokenHash(TokenTools.hash(raw))
                .orElseThrow().getUsedAt()).isNotNull();
        assertThat(login(EMAIL, NEW_PASSWORD)).isNotBlank();
    }

    /**
     * Rejects a correctly signed access token after its expiration.
     * @throws Exception if request execution fails
     */
    @Test
    public void expiredAccessTokenCannotReadProfile() throws Exception {
        UserAccount user;
        JwtClaimsSet claims;
        String access;
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        verify(latestToken()).andExpect(status().isOk());
        user = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        claims = JwtClaimsSet.builder().issuer("equitransit")
                .subject(user.getUserId().toString())
                .issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().minusSeconds(300))
                .claim("purpose", "ACCESS")
                .claim("credential", TokenTools.hash(user.getPasswordHash()))
                .build();
        access = encoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
        mvc.perform(get("/api/customers/me")
                .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    private List<Integer> race(Callable<Integer> operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> attempt = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent start timed out.");
            }
            return operation.call();
        };
        var first = executor.submit(attempt);
        var second = executor.submit(attempt);

        try {
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ResultActions register(
            String email, String password) throws Exception {
        return postJson("/api/auth/register", Map.of("email", email,
                "password", password, "confirmPassword", password,
                "fullName", "Nguyen Van Minh", "phone", "+84901234567"));
    }

    private ResultActions verify(
            String token) throws Exception {
        return postJson("/api/auth/verify-email", Map.of("token", token));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", Map.of("email", email,
                "password", password)).andExpect(status().isOk()).andReturn();

        return mapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String latestToken() {
        String body = events.stream(EmailEvent.class).reduce((a, b) -> b)
                .orElseThrow().getBody();

        return body.substring(body.indexOf("?token=") + "?token=".length())
                .split("\\n")[0];
    }

    private ResultActions postJson(
            String path, Object input) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)));
    }
}
