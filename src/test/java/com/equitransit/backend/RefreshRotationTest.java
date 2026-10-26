/*
 * RefreshRotationTest
 * Author: EquiTransit development team
 * Date: 2026-10-20
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify cookie contracts and committed JDBC family revocations.
 */
package com.equitransit.backend;

import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.service.activity.AuditRedactor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;

/** Refuses all databases except memory or the exact disposable test catalog. */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure
        .web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("test")
@SuppressWarnings("null")
public class RefreshRotationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private DataSource source;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private AuditRedactor redactor;
    @Autowired
    private com.equitransit.backend.service.auth.AuthService auth;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager tx;
    private JdbcTemplate jdbc;
    private long user;
    private boolean staffFixture;
    private String email;

    /** Validates the target before Hibernate can initialize any schema.
     * @param properties guarded test datasource settings */
    @org.springframework.test.context.DynamicPropertySource
    public static void database(org.springframework.test.context.
            DynamicPropertyRegistry properties) {
        String sql = "jdbc:sqlserver://localhost:1433;databaseName="
                + "EquiTransit_FigmaMigrationTest_20261013_143729;"
                + "encrypt=true;trustServerCertificate=true;"
                + "integratedSecurity=true";
        String configured = System.getenv("EQ_REFRESH_TEST_DB_URL");
        if (configured != null && !configured.equals(sql)) {
            throw new IllegalArgumentException("Unsafe refresh test DB.");
        }
        String url = configured == null ? "jdbc:h2:mem:refresh;"
                + "MODE=MSSQLServer;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS dbo" : sql;
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.jpa.hibernate.ddl-auto",
                () -> configured == null ? "create-drop" : "none");
    }

    /** @throws Exception if the isolated fixture cannot be created */
    @BeforeEach
    public void prepare() throws Exception {
        try (var connection = source.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertThat(url.startsWith("jdbc:h2:mem:refresh")
                    || "EquiTransit_FigmaMigrationTest_20261013_143729"
                            .equals(connection.getCatalog())).isTrue();
            if (url.startsWith("jdbc:h2:")) {
                new ResourceDatabasePopulator(new ClassPathResource(
                        "refresh-test-schema.sql")).execute(source);
            }
        }
        jdbc = new JdbcTemplate(source);
        email = "refresh-" + UUID.randomUUID() + "@example.invalid";
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Role WHERE "
                + "RoleCode='CUSTOMER'", Integer.class) == 0) {
            jdbc.update("INSERT INTO dbo.Role(RoleCode) VALUES ('CUSTOMER')");
        }
        jdbc.update("INSERT INTO dbo.[User](Email,RoleID,PasswordHash,"
                + "Status,CreatedAt) SELECT ?,RoleID,?,'ACTIVE',"
                + "CURRENT_TIMESTAMP FROM dbo.Role WHERE RoleCode='CUSTOMER'",
                email, passwords.encode("RefreshPassword123!"));
        user = jdbc.queryForObject("SELECT UserID FROM dbo.[User] "
                + "WHERE Email=?", Long.class, email);
    }

    /** Only fixture-owned rows are removed, after the catalog assertion. */
    @AfterEach
    public void clean() {
        if (user != 0 && jdbc != null) {
            jdbc.update("DELETE FROM dbo.RefreshTokenSession WHERE UserID=?",
                    user);
            if (staffFixture) {
                jdbc.update("DELETE FROM dbo.StaffProfile WHERE UserID=?",
                        user);
            }
            jdbc.update("DELETE FROM dbo.[User] WHERE UserID=?", user);
        }
    }

    /** @throws Exception if HTTP or persistence assertions fail */
    @Test
    public void loginAndRotationKeepTheJsonContract() throws Exception {
        var login = login();
        String raw = raw(login);
        String cookie = login.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("HttpOnly", "SameSite=Strict",
                "Path=/api/auth", "Max-Age=28800").doesNotContain("Secure");
        var body = mapper.readTree(login.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.path("expiresIn").asInt()).isEqualTo(900);
        assertThat(body.path("role").asText()).isEqualTo("CUSTOMER");
        assertThat(body.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.toString()).doesNotContain(raw);
        var next = refresh(raw, "CUSTOMER", 200);
        assertThat(raw(next)).isNotEqualTo(raw);
        assertThat(jdbc.queryForObject("SELECT TokenHash FROM "
                + "dbo.RefreshTokenSession WHERE RevokeReason='ROTATED' "
                + "AND UserID=?", String.class, user))
                .isEqualTo(TokenTools.hash(raw));
        assertThat(redactor.redact(mapper.createObjectNode()
                .put("cookie", raw).put("refreshToken", raw)
                .put("metadata", raw)).toString()).doesNotContain(raw);
    }

    /** @throws Exception if replay revocation was rolled back */
    @Test
    public void replayCommitsFamilyRevocation() throws Exception {
        String old = raw(login());
        String next = raw(refresh(old, null, 200));
        assertCode(refresh(old, null, 401), "REFRESH_TOKEN_REUSED");
        assertCode(refresh(next, null, 401), "REFRESH_TOKEN_REVOKED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.RefreshTokenSession WHERE UserID=? "
                + "AND RevokedAt IS NULL", Integer.class, user)).isZero();
    }

    /** @throws Exception if a tab mismatch invalidates another role's family */
    @Test
    public void roleMismatchDoesNotRevokeOrReplaceCookie() throws Exception {
        String token = raw(login());
        var mismatch = refresh(token, "MANAGER", 401);
        assertCode(mismatch, "REFRESH_ROLE_MISMATCH");
        assertThat(mismatch.getResponse().getHeader("Set-Cookie")).isNull();
        refresh(token, "CUSTOMER", 200);
    }

    /** @throws Exception if logout fails to clear or revoke a session */
    @Test
    public void logoutIsIdempotentAndClearsCookie() throws Exception {
        String token = raw(login());
        for (int i = 0; i < 2; i++) {
            var result = mvc.perform(post("/api/auth/logout")
                    .cookie(new Cookie("eq_refresh_token", token)))
                    .andExpect(status().isNoContent()).andReturn();
            assertThat(result.getResponse().getHeader("Set-Cookie"))
                    .contains("Max-Age=0", "Path=/api/auth", "HttpOnly",
                            "SameSite=Strict");
            assertThat(result.getResponse().getContentAsString()).isEmpty();
        }
        mvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer expired-or-invalid"))
                .andExpect(status().isNoContent());
        assertCode(refresh(token, null, 401), "REFRESH_TOKEN_REVOKED");
    }

    /** @throws Exception if unavailable/changed credentials are accepted */
    @Test
    public void accountStateAndPasswordAreRechecked() throws Exception {
        for (String state : new String[] {"LOCKED", "INACTIVE"}) {
            String token = raw(login());
            jdbc.update("UPDATE dbo.[User] SET Status=? WHERE UserID=?",
                    state, user);
            assertCode(refresh(token, null, 401),
                    "ACCOUNT_INACTIVE_OR_LOCKED");
            jdbc.update("UPDATE dbo.[User] SET Status='ACTIVE' WHERE UserID=?",
                    user);
        }
        String token = raw(login());
        jdbc.update("UPDATE dbo.[User] SET PasswordHash=? WHERE UserID=?",
                passwords.encode("NewRefreshPassword123!"), user);
        assertCode(refresh(token, null, 401), "CREDENTIALS_CHANGED");
    }

    /** @throws Exception if absence/invalidity/expiry yields the wrong code */
    @Test
    public void missingInvalidAndExpiredAreDistinct() throws Exception {
        assertCode(mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized()).andReturn(),
                "REFRESH_TOKEN_MISSING");
        assertCode(refresh(TokenTools.randomToken(), null, 401),
                "REFRESH_TOKEN_INVALID");
        String token = raw(login());
        jdbc.update("UPDATE dbo.RefreshTokenSession SET IssuedAt=?,"
                + "ExpiresAt=? WHERE UserID=?",
                java.time.LocalDateTime.now().minusDays(2),
                java.time.LocalDateTime.now().minusDays(1), user);
        assertCode(refresh(token, null, 401), "REFRESH_TOKEN_EXPIRED");
    }

    /** @throws Exception if reset/F15 hooks leave an open refresh family */
    @Test
    public void revocationHooksInvalidateAllFamilies() throws Exception {
        for (String reason : new String[] {
                "PASSWORD_CHANGED", "STAFF_DEACTIVATED"}) {
            String first = raw(login());
            String second = raw(login());
            new org.springframework.transaction.support.TransactionTemplate(tx)
                    .executeWithoutResult(status ->
                            auth.revokeSessions(user, reason));
            assertCode(refresh(first, null, 401), "REFRESH_TOKEN_REVOKED");
            assertCode(refresh(second, null, 401), "REFRESH_TOKEN_REVOKED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                    + "dbo.RefreshTokenSession WHERE UserID=? "
                    + "AND RevokedAt IS NULL", Integer.class, user)).isZero();
        }
    }

    /** @throws Exception if replay in one login affects another family */
    @Test
    public void familiesRemainIndependent() throws Exception {
        String first = raw(login());
        String second = raw(login());
        refresh(first, null, 200);
        assertCode(refresh(first, null, 401), "REFRESH_TOKEN_REUSED");
        refresh(second, null, 200);
    }

    /** @throws Exception if disabled staff with an active User can renew */
    @Test
    public void staffStateIsRecheckedOnSqlServer() throws Exception {
        try (var connection = source.getConnection()) {
            org.junit.jupiter.api.Assumptions.assumeTrue(connection
                    .getMetaData().getURL().startsWith("jdbc:sqlserver:"));
        }
        jdbc.update("UPDATE dbo.[User] SET RoleID=(SELECT RoleID FROM "
                + "dbo.Role WHERE RoleCode='COORDINATOR') WHERE UserID=?",
                user);
        jdbc.update("INSERT INTO dbo.StaffProfile(UserID,FullName,"
                + "StaffStatus) VALUES (?,'Refresh test fixture','ACTIVE')",
                user);
        staffFixture = true;
        for (String state : new String[] {"INACTIVE", "LOCKED"}) {
            String token = raw(login());
            jdbc.update("UPDATE dbo.StaffProfile SET StaffStatus=? "
                    + "WHERE UserID=?", state, user);
            assertCode(refresh(token, null, 401),
                    "ACCOUNT_INACTIVE_OR_LOCKED");
            jdbc.update("UPDATE dbo.StaffProfile SET StaffStatus='ACTIVE' "
                    + "WHERE UserID=?", user);
        }
    }

    /** SQL Server proves UPDLOCK/HOLDLOCK serialization, not an H2 emulation.
     * @throws Exception if simultaneous refreshes both succeed */
    @Test
    public void concurrentRefreshSerializesOnSqlServer() throws Exception {
        try (var connection = source.getConnection()) {
            org.junit.jupiter.api.Assumptions.assumeTrue(connection
                    .getMetaData().getURL().startsWith("jdbc:sqlserver:"));
        }
        String token = raw(login());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors
                .newFixedThreadPool(2)) {
            java.util.concurrent.Callable<MvcResult> call = () -> {
                start.await();
                return mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("eq_refresh_token", token)))
                        .andReturn();
            };
            var first = workers.submit(call);
            var second = workers.submit(call);
            start.countDown();
            var one = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            var two = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(java.util.List.of(one.getResponse().getStatus(),
                    two.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 401);
            var success = one.getResponse().getStatus() == 200 ? one : two;
            assertCode(refresh(raw(success), null, 401),
                    "REFRESH_TOKEN_REVOKED");
        }
    }

    private MvcResult login() throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType("application/json").content(mapper
                        .createObjectNode().put("email", email)
                        .put("password", "RefreshPassword123!").toString()))
                .andExpect(status().isOk()).andReturn();
    }

    private MvcResult refresh(String raw, String role, int status)
            throws Exception {
        var request = post("/api/auth/refresh")
                .cookie(new Cookie("eq_refresh_token", raw));
        if (role != null) {
            request.header("X-Expected-Role", role);
        }
        return mvc.perform(request).andExpect(status().is(status)).andReturn();
    }

    private String raw(MvcResult result) {
        return result.getResponse().getHeader("Set-Cookie")
                .split(";", 2)[0].substring("eq_refresh_token=".length());
    }

    private void assertCode(MvcResult result, String expected)
            throws Exception {
        assertThat(mapper.readTree(result.getResponse().getContentAsString())
                .path("errorCode").asText()).isEqualTo(expected);
    }
}
