package com.equitransit.backend;

import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
        .AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

/** SQL API authorization, primary replacement and coordinate validation. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_HUB_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_HUB_TEST", matches = "true")
@Transactional
public class CompanyHubSqlTest {
    private static final String ADMIN = "/api/manager/locations";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenService tokens;
    @Autowired private UserAccountRepository users;

    /** Guard all test writes against production databases. */
    @BeforeEach
    public void sandbox() {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class))
                .isIn("EquiTransit_Demo", "EquiTransit_Test");
        for (String country : List.of("VN", "CN", "TH", "KH", "LA")) {
            jdbc.update("INSERT dbo.LocationCatalog(CountryCode,CountryName,"
                    + "LocationType,LocationName,IsActive,IsPrimaryHub) "
                    + "SELECT ?,?,'HUB',?,1,1 WHERE NOT EXISTS(SELECT 1 "
                    + "FROM dbo.LocationCatalog WHERE CountryCode=? "
                    + "AND IsPrimaryHub=1 AND IsActive=1)", country, country,
                    "Hub fixture " + UUID.randomUUID(), country);
        }
    }

    /** @throws Exception if seeds or public isolation are incorrect */
    @Test
    public void publicSeedsAndLegacyLookups() throws Exception {
        for (String country : List.of("VN", "CN", "TH", "KH", "LA")) {
            var hubs = send("GET", "/api/lookups/hubs?country=" + country
                    + "&primaryOnly=true", null, null, 200);
            assertThat(hubs.size()).isEqualTo(1);
            assertThat(hubs.get(0).path("countryCode").asText())
                    .isEqualTo(country);
            assertThat(hubs.get(0).path("isPrimaryHub").asBoolean()).isTrue();
            assertThat(hubs.get(0).path("locationType").asText())
                    .isEqualTo("HUB");
        }
        var vn = send("GET", "/api/lookups/hubs?country=VN", null, null, 200);
        assertThat(vn.get(0).path("latitude").decimalValue())
                .isEqualByComparingTo("11.0067000");
        assertThat(vn.get(0).path("longitude").decimalValue())
                .isEqualByComparingTo("106.5132000");
        assertThat(send("GET", "/api/lookups/locations?country=VN&type=AIRPORT",
                null, null, 200).size()).isGreaterThanOrEqualTo(2);
        send("GET", "/api/lookups/hubs?country=VNN", null, null, 400);
    }

    /** @throws Exception if either authorized role is blocked or others pass */
    @Test
    public void rolesAreScopedToLocationAdministration() throws Exception {
        send("POST", ADMIN, null, body(), 401);
        for (String role : List.of("CUSTOMER", "SPECIALIST", "DRIVER")) {
            send("POST", ADMIN, token(role), body(), 403);
            send("GET", ADMIN, token(role), null, 403);
        }
        for (String role : List.of("MANAGER", "COORDINATOR")) {
            String auth = token(role);
            var request = body();
            int id = send("POST", ADMIN, auth, request, 201)
                    .path("locationId").asInt();
            request.put("address", "Updated depot address");
            send("PUT", ADMIN + "/" + id, auth, request, 200);
            send("GET", ADMIN + "/" + id, auth, null, 200);
            send("DELETE", ADMIN + "/" + id, auth, null, 204);
        }
        send("GET", "/api/manager/transport-orders", token("COORDINATOR"),
                null, 403);
    }

    /** @throws Exception if primary replacement, update or hiding is wrong */
    @Test
    public void primarySwitchAndSoftDelete() throws Exception {
        String auth = token("COORDINATOR");
        var first = body();
        first.put("isPrimaryHub", true);
        int a = send("POST", ADMIN, auth, first, 201)
                .path("locationId").asInt();
        var second = body();
        second.put("isPrimaryHub", true);
        int b = send("POST", ADMIN, auth, second, 201)
                .path("locationId").asInt();
        assertThat(send("GET", ADMIN + "/" + a, auth, null, 200)
                .path("isPrimaryHub").asBoolean()).isFalse();
        assertThat(send("GET", "/api/lookups/hubs?country=IS&primaryOnly=true",
                null, null, 200).get(0).path("locationId").asInt())
                .isEqualTo(b);
        second.remove("isPrimaryHub");
        assertThat(send("PUT", ADMIN + "/" + b, auth, second, 200)
                .path("isPrimaryHub").asBoolean()).isTrue();
        send("DELETE", ADMIN + "/" + b, auth, null, 204);
        assertThat(send("GET", "/api/lookups/hubs?country=IS&primaryOnly=true",
                null, null, 200).size()).isZero();
        var hidden = send("GET", ADMIN + "/" + b, auth, null, 200);
        assertThat(hidden.path("isActive").asBoolean()).isFalse();
        assertThat(hidden.path("isPrimaryHub").asBoolean()).isFalse();
        assertThat(send("GET", ADMIN + "?country=IS&includeInactive=true",
                auth, null, 200).size()).isEqualTo(2);
    }

    /** @throws Exception if invalid coordinates or flags reach SQL */
    @Test
    public void inputGuardsAndMissingRows() throws Exception {
        String auth = token("MANAGER");
        var edit = body();
        edit.put("latitude", "91");
        edit.put("longitude", "106.5");
        send("POST", ADMIN, auth, edit, 400);
        edit.put("latitude", "11.12345678");
        send("POST", ADMIN, auth, edit, 400);
        edit.put("latitude", "11.1");
        edit.remove("longitude");
        send("POST", ADMIN, auth, edit, 422);
        edit.remove("latitude");
        edit.put("isPrimaryHub", true);
        edit.put("locationType", "CITY");
        send("POST", ADMIN, auth, edit, 422);
        edit.put("locationType", "HUB");
        edit.put("isActive", false);
        send("POST", ADMIN, auth, edit, 422);
        edit.put("isPrimaryHub", false);
        edit.put("countryCode", "ZZ");
        send("POST", ADMIN, auth, edit, 422);
        send("DELETE", ADMIN + "/2147483647", auth, null, 404);
        send("GET", ADMIN + "?size=201", auth, null, 400);
    }

    private ObjectNode body() {
        return json.createObjectNode().put("countryCode", "IS")
                .put("countryName", "Iceland").put("locationType", "HUB")
                .put("locationName", "Hub test " + UUID.randomUUID());
    }

    private String token(String role) {
        long user = jdbc.queryForObject("SELECT TOP(1) u.UserID "
                + "FROM dbo.[User] u JOIN dbo.[Role] r ON r.RoleID=u.RoleID "
                + "WHERE r.RoleCode=? AND u.Status='ACTIVE' ORDER BY u.UserID",
                Long.class, role);
        return "Bearer " + tokens.issue(users.findById(user).orElseThrow())
                .getAccessToken();
    }

    private JsonNode send(String method, String path, String auth,
            JsonNode body, int expected) throws Exception {
        var request = MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), path);
        if (auth != null) {
            request.header("Authorization", auth);
        }
        if (body != null) {
            request.contentType("application/json").content(body.toString());
        }
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString())
                .isEqualTo(expected);
        return response.getContentAsString().isEmpty() ? json.nullNode()
                : json.readTree(response.getContentAsString());
    }
}
