package com.equitransit.backend;

import com.equitransit.backend.dto.resource.ResourceExamples;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;

/** HTTP, persistence and retry contract for generated fleet codes. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_HUB_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_HUB_TEST", matches = "true")
@Transactional
public class ResourceVehicleCodeSqlTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccountRepository users;
    @Autowired private JwtTokenService tokens;

    /** @throws Exception if optional codes or retries fail */
    @Test
    public void generateCodesAndRetainClientCodeWithStableRetries()
            throws Exception {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class))
                .isIn("EquiTransit_Demo", "EquiTransit_Test");
        long user = jdbc.queryForObject("SELECT TOP(1) u.UserID FROM "
                + "dbo.[User] u JOIN dbo.[Role] r ON r.RoleID=u.RoleID "
                + "JOIN dbo.StaffProfile s ON s.UserID=u.UserID WHERE "
                + "r.RoleCode='MANAGER' AND u.Status='ACTIVE' AND "
                + "s.StaffStatus='ACTIVE'", Long.class);
        String token = "Bearer " + tokens.issue(users.findById(user)
                .orElseThrow()).getAccessToken();
        for (String input : List.of("OMITTED", "NULL", "", "   ", "manual-i")) {
            String plate = "51c-" + UUID.randomUUID().toString()
                    .substring(0, 8) + ".01";
            var data = (ObjectNode) json.readTree(ResourceExamples.VEHICLE);
            data.put("plateNumber", plate);
            if ("OMITTED".equals(input)) {
                data.remove("vehicleCode");
            } else if ("NULL".equals(input)) {
                data.putNull("vehicleCode");
            } else {
                data.put("vehicleCode", input);
            }
            var body = json.createObjectNode();
            body.putObject("command").put("operationId",
                    UUID.randomUUID().toString());
            body.set("data", data);
            var response = mvc.perform(post("/api/manager/resources/vehicles")
                    .header("Authorization", token)
                    .contentType("application/json").content(body.toString()))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).as(response.getContentAsString())
                    .isEqualTo(200);
            var result = json.readTree(response.getContentAsString());
            String expected = "manual-i".equals(input) ? input
                    : "VEH-" + plate.replaceAll("[^A-Za-z0-9]", "")
                            .toUpperCase(Locale.ROOT);
            assertThat(result.at("/data/vehicleCode").asText())
                    .isEqualTo(expected);
            assertThat(jdbc.queryForObject("SELECT VehicleCode FROM "
                    + "dbo.FleetVehicle WHERE VehicleID=?", String.class,
                    result.path("id").asLong())).isEqualTo(expected);
            var replay = mvc.perform(post("/api/manager/resources/vehicles")
                    .header("Authorization", token)
                    .contentType("application/json").content(body.toString()))
                    .andReturn().getResponse();
            assertThat(replay.getStatus()).isEqualTo(200);
            assertThat(json.readTree(replay.getContentAsString()))
                    .isEqualTo(result);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                    + "dbo.FleetVehicle WHERE VehicleCode=?", Integer.class,
                    expected)).isEqualTo(1);
        }
    }
}
