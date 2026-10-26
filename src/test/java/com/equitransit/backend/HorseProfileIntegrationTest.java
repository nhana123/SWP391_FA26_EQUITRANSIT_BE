/*
 * HorseProfileIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify the HTTP/security stack and actual SQL Server rowversion.
 */
package com.equitransit.backend;


import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.
        TransactionSynchronizationManager;
import com.equitransit.backend.entity.customer.Customer;
import com.equitransit.backend.entity.auth.UserAccount;
import com.equitransit.backend.entity.horse.Horse;
import com.equitransit.backend.repository.auth.AccountRoleRepository;
import com.equitransit.backend.repository.customer.CustomerRepository;
import com.equitransit.backend.repository.auth.UserAccountRepository;
import com.equitransit.backend.repository.horse.HorseRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.security.TokenTools;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;


/** Fixtures are transactionally rolled back on a full restored test copy. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_HORSE_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_HORSE_TEST_ENABLED",
        matches = "true")
@Transactional
@SuppressWarnings("null")
public class HorseProfileIntegrationTest {

    private final List<Long> fixtureUsers = new ArrayList<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private AccountRoleRepository roles;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private JwtTokenService tokens;
    @Autowired
    private HorseRepository horses;

    private String ownerToken;
    private String otherToken;
    private String managerToken;

    /**
     * Refuses every fixture write outside the restored test database.
     * @throws Exception if connection metadata cannot be inspected
     */
    @BeforeEach
    public void prepare() throws Exception {
        String database;

        try (var connection = dataSource.getConnection()) {
            database = connection.getCatalog();
        }
        assertThat(database).startsWith("EquiTransit_FigmaMigrationTest_");
        ownerToken = account("CUSTOMER");
        otherToken = account("CUSTOMER");
        managerToken = account("MANAGER");
    }

    /** Removes only committed fixtures from the concurrency test. */
    @AfterEach
    public void cleanCommittedFixtures() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            for (Long userId : fixtureUsers) {
                jdbc.update("DELETE dbo.Horse WHERE CustomerID IN"
                        + " (SELECT CustomerID FROM dbo.Customer"
                        + " WHERE UserID=?)", userId);
                jdbc.update("DELETE dbo.Customer WHERE UserID=?", userId);
                jdbc.update("DELETE dbo.[User] WHERE UserID=?", userId);
            }
        }
    }

    /**
     * Saves a minimal draft and distinguishes profile completeness.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void draftCreationReturnsLocationAndBinaryVersion()
            throws Exception {
        JsonNode horse = create("Thunder", null);

        assertThat(horse.get("recordVersion").asText())
                .matches("[0-9a-f]{16}");
        assertThat(horse.get("weightKg").isNull()).isTrue();
        assertThat(horse.get("measurementsComplete").asBoolean()).isFalse();
        assertThat(horse.has("customerId")).isFalse();
    }

    /**
     * Foreign keys cannot reveal or mutate another customer's profile.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void foreignReadAndUpdateReturnNotFound() throws Exception {
        JsonNode horse = create("Private", null);
        long id = horse.get("horseId").asLong();

        mvc.perform(get("/api/horses/{id}", id)
                .header("Authorization", otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("HORSE_NOT_FOUND"));
        mvc.perform(put("/api/horses/{id}", id)
                .header("Authorization", otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Stolen",
                        "recordVersion",
                        horse.get("recordVersion").asText()))))
                .andExpect(status().isNotFound());
    }

    /**
     * Actual SQL rowversion makes only the first stale replacement succeed.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void staleReplacementCannotOverwriteSavedProfile()
            throws Exception {
        JsonNode horse = create("Before", null);
        long id = horse.get("horseId").asLong();
        String body = mapper.writeValueAsString(Map.of("name", "After",
                "recordVersion", horse.get("recordVersion").asText()));
        MvcResult saved = mvc.perform(put("/api/horses/{id}", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();

        assertThat(mapper.readTree(saved.getResponse().getContentAsString())
                .get("recordVersion").asText())
                .isNotEqualTo(horse.get("recordVersion").asText());
        mvc.perform(put("/api/horses/{id}", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("HORSE_VERSION_CONFLICT"));
    }

    /**
     * Filtered SQL uniqueness rejects duplicates but permits unknown chips.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void duplicateMicrochipIsASafeConflict() throws Exception {
        String chip = "985" + String.format("%012d",
                Math.abs(UUID.randomUUID().getMostSignificantBits()
                        % 1000000000000L));

        create("First", chip);
        mvc.perform(post("/api/horses").header("Authorization", otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        Map.of("name", "Duplicate", "microchipId", chip))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("HORSE_CONFLICT"));
    }

    /**
     * Literal search does not treat percent as a SQL wildcard.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void paginationAndSearchRemainOwnerScoped() throws Exception {
        create("100% Thunder", null);
        create("Other", null);
        mvc.perform(get("/api/horses").header("Authorization", ownerToken)
                .param("q", "%").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("items[0].name").value("100% Thunder"));
        mvc.perform(get("/api/horses").header("Authorization", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(0));
    }

    /**
     * Role and authentication checks apply before profile persistence.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void onlyAuthenticatedCustomersCanUseProfiles() throws Exception {
        mvc.perform(get("/api/horses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/horses").header("Authorization", managerToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/horses").header("Authorization", managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * Unknown ownership fields and invalid measurements never persist.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void invalidBodiesAndPaginationReturnBadRequest() throws Exception {
        for (String body : new String[] {"{\"name\":\" \"}",
                "{\"name\":\"Bad\",\"weightKg\":0}",
                "{\"name\":\"Bad\",\"sexCode\":\"UNKNOWN\"}",
                "{\"name\":\"Bad\",\"customerId\":1}"}) {
            mvc.perform(post("/api/horses")
                    .header("Authorization", ownerToken)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/horses").header("Authorization", ownerToken)
                .param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/horses/no-number")
                .header("Authorization", ownerToken))
                .andExpect(status().isBadRequest());
    }

    /**
     * Browser preflight permits PUT only from the configured frontend.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void frontendCanPreflightProfileReplacement() throws Exception {
        mvc.perform(options("/api/horses/1")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk());
        mvc.perform(options("/api/horses/1")
                .header("Origin", "https://untrusted.example.invalid")
                .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden());
    }

    /**
     * Editing master data preserves an already submitted horse snapshot.
     * @throws Exception if an HTTP or SQL assertion fails
     */
    @Test
    public void profileChangesLeaveOrderSnapshotsUntouched() throws Exception {
        JsonNode horse = create("Snapshot horse", null);
        long id = horse.get("horseId").asLong();
        Long owner = jdbc.queryForObject(
                "SELECT CustomerID FROM dbo.Horse WHERE HorseID=?",
                Long.class, id);
        Long order = jdbc.queryForObject("INSERT dbo.TransportOrder"
                + " (CustomerID,OrderNumber,OrderStatus)"
                + " OUTPUT INSERTED.OrderID VALUES (?,?,'SUBMITTED')",
                Long.class, owner, TokenTools.randomToken().substring(0, 20));

        jdbc.update("INSERT dbo.TransportOrderDetail"
                + " (OrderID,HorseID,SnapshotWeightKg) VALUES (?,?,?)",
                order, id, 400);
        mvc.perform(put("/api/horses/{id}", id)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Edited",
                        "weightKg", 500, "recordVersion",
                        horse.get("recordVersion").asText()))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT SnapshotWeightKg"
                + " FROM dbo.TransportOrderDetail WHERE OrderID=?",
                Integer.class, order)).isEqualTo(400);
    }

    /**
     * SQL preserves Unicode and converts offset measurements to UTC.
     * @throws Exception if an HTTP assertion fails
     */
    @Test
    public void measurementsRoundTripWithUnitsAndUtc() throws Exception {
        String body = "{\"name\":\"Ng\u1ef1a test\",\"weightKg\":500.25,"
                + "\"heightCm\":170.50,\"bodyWidthCm\":80.00,"
                + "\"measuredAt\":\"2026-10-10T09:00:00+07:00\"}";

        mvc.perform(post("/api/horses").header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("name").value("Ng\u1ef1a test"))
                .andExpect(jsonPath("weightKg").value(500.25))
                .andExpect(jsonPath("measuredAt")
                        .value("2026-10-10T02:00:00Z"))
                .andExpect(jsonPath("measurementsComplete").value(true));
    }

    /**
     * Two real SQL connections compete for one version; exactly one wins.
     * @throws Exception if synchronization or SQL fails
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void competingUpdatesHaveExactlyOneWinner() throws Exception {
        JsonNode created = create("Concurrent", null);
        long id = created.get("horseId").asLong();
        Long owner = jdbc.queryForObject(
                "SELECT CustomerID FROM dbo.Horse WHERE HorseID=?",
                Long.class, id);
        Horse first = horses.findOwned(id, owner).orElseThrow();
        Horse second = horses.findOwned(id, owner).orElseThrow();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        first.setName("First contender");
        second.setName("Second contender");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> compete(first, ready, start));
            var b = pool.submit(() -> compete(second, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS),
                    b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
        }
    }

    private boolean compete(Horse horse, CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrency start timed out.");
        }
        return horses.update(horse, horse.getRecordVersion());
    }

    private JsonNode create(String name, String chip) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        MvcResult result;

        body.put("name", name);
        body.put("microchipId", chip);
        result = mvc.perform(post("/api/horses")
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();
        assertThat(result.getResponse().getHeader("Location"))
                .startsWith("/api/horses/");
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String account(String role) {
        UserAccount user = new UserAccount();
        Customer customer = new Customer();

        user.setRole(roles.findByRoleCode(role).orElseThrow());
        user.setEmail(TokenTools.randomToken() + "@example.invalid");
        user.setPasswordHash(passwords.encode("ExamplePassword123!"));
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        users.saveAndFlush(user);
        fixtureUsers.add(user.getUserId());
        if ("CUSTOMER".equals(role)) {
            customer.setUser(user);
            customer.setFullName("Rollback horse fixture");
            customers.saveAndFlush(customer);
        }
        return "Bearer " + tokens.issue(user).getAccessToken();
    }
}
