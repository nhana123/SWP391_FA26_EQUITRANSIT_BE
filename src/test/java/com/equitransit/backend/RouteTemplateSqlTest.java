package com.equitransit.backend;

import com.equitransit.backend.dto.ApiExamples;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.service.GeoDistanceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/** Native SQL route matching, administration and immutable order decisions. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_ROUTE_TEMPLATE_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_ROUTE_TEMPLATE_TEST",
        matches = "true")
@Transactional
public class RouteTemplateSqlTest {
    private static final String CATALOG = "/api/coordinator/route-templates";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenService tokens;
    @Autowired private UserAccountRepository users;
    @Autowired private GeoDistanceService geo;

    /** Guard every native write. */
    @BeforeEach
    public void sandbox() {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class))
                .isIn("EquiTransit_Demo", "EquiTransit_Test");
        geo.setExternalNetworkEnabled(false);
        for (String country : List.of("FR", "IS", "BZ")) {
            jdbc.update("INSERT dbo.LocationCatalog(CountryCode,CountryName,"
                    + "LocationType,LocationName,IsActive,IsPrimaryHub) "
                    + "SELECT ?,?,'HUB','Routing test hub',1,1 WHERE NOT "
                    + "EXISTS(SELECT 1 FROM dbo.LocationCatalog WHERE "
                    + "CountryCode=? AND LocationType='HUB' AND IsActive=1)",
                    country, country, country);
        }
    }

    /** @throws Exception if seeded routes, slot math or input guards differ */
    @Test
    public void publicSeedsSeparateRoadAndAirCosts() throws Exception {
        var road = match("VN", "CN", "ROAD", null);
        assertThat(road.path("matched").asBoolean()).isTrue();
        assertThat(road.path("estimatedRoadCost").decimalValue())
                .isEqualByComparingTo("1950.00");
        assertThat(road.path("estimatedAirCost").decimalValue()).isZero();
        assertThat(match("VN", "LA", "ROAD", null)
                .path("totalEstimatedCost").decimalValue())
                .isEqualByComparingTo("1100.00");
        for (String preference : List.of("SHARED_ONE", "WIDE", "PRIVATE")) {
            var air = match("VN", "FR", "AIR", preference);
            String expected = preference.equals("PRIVATE") ? "3600.00"
                    : preference.equals("WIDE") ? "1800.00" : "1200.00";
            assertThat(air.path("estimatedAirCost").decimalValue())
                    .isEqualByComparingTo(expected);
            assertThat(air.path("estimatedRoadCost").decimalValue()).isZero();
        }
        var request = selection("VN", "FR", "AIR", "SHARED_ONE");
        request.put("originAirportCode", "SGN");
        request.put("destinationAirportCode", "CDG");
        var missing = send("POST", "/api/routes/match", null, request, 200);
        assertThat(missing.path("matched").asBoolean()).isFalse();
        assertThat(missing.path("requiresCustomCoordinatorPlan").asBoolean())
                .isTrue();
        assertThat(missing.path("totalEstimatedCost").isNull()).isTrue();
        request.put("originAirportCode", "HAN");
        assertThat(send("POST", "/api/routes/match", null, request, 200)
                .path("matched").asBoolean()).isTrue();
        request.put("transportMode", "SEA");
        send("POST", "/api/routes/match", null, request, 400);
        send("POST", "/api/routes/match", null,
                selection("VN", "VN", "ROAD", null), 422);
        send("POST", "/api/routes/match", null,
                selection("ZZ", "FR", "AIR", null), 422);
    }

    /** @throws Exception if role checks, precision or soft deletion differ */
    @Test
    public void coordinatorAndManagerCrudIsScopedAndSoftDeleted()
            throws Exception {
        var body = template("MULTIMODAL", "100.25");
        body.put("estimatedAirKm", "9200.00");
        body.put("baseRoadRate", "2.34");
        body.put("baseAirRate", "1200.00");
        String coordinator = token("COORDINATOR");
        send("GET", CATALOG, null, null, 401);
        for (String role : List.of("CUSTOMER", "SPECIALIST", "DRIVER")) {
            send("GET", CATALOG, token(role), null, 403);
            send("POST", CATALOG, token(role), body, 403);
        }
        var created = send("POST", CATALOG, coordinator, body, 201);
        long id = created.path("routeTemplateId").asLong();
        assertThat(created.path("createdBy").asLong())
                .isEqualTo(staffId("COORDINATOR"));
        assertThat(created.path("createdAt").asText()).isNotBlank();
        var estimate = match("IS", "BZ", "MULTIMODAL", "WIDE");
        assertThat(estimate.path("estimatedRoadCost").decimalValue())
                .isEqualByComparingTo("234.59");
        assertThat(estimate.path("estimatedAirCost").decimalValue())
                .isEqualByComparingTo("1800.00");
        assertThat(estimate.path("totalEstimatedCost").decimalValue())
                .isEqualByComparingTo("2034.59");
        send("POST", CATALOG, coordinator, body, 409);
        body.put("routeName", "Updated by Manager");
        var updated = send("PUT", CATALOG + "/" + id, token("MANAGER"),
                body, 200);
        assertThat(updated.path("routeName").asText())
                .isEqualTo("Updated by Manager");
        assertThat(updated.path("createdBy"))
                .isEqualTo(created.path("createdBy"));
        send("DELETE", CATALOG + "/" + id, coordinator, null, 204);
        assertThat(send("GET", CATALOG + "/" + id, coordinator, null, 200)
                .path("isActive").asBoolean()).isFalse();
        assertThat(match("IS", "BZ", "MULTIMODAL", null)
                .path("requiresCustomCoordinatorPlan").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RouteTemplate "
                + "WHERE RouteTemplateID=?", Integer.class, id)).isEqualTo(1);
        body.put("estimatedRoadKm", "-1");
        send("PUT", CATALOG + "/" + id, coordinator, body, 400);
        send("DELETE", CATALOG + "/999999999", coordinator, null, 404);
    }

    /** @throws Exception if exact city selection cannot beat generic routes */
    @Test
    public void specificEndpointsBeatCountryDefaults() throws Exception {
        String coordinator = token("COORDINATOR");
        var generic = template("ROAD", "100.00");
        send("POST", CATALOG, coordinator, generic, 201);
        var specific = template("ROAD", "200.00");
        specific.put("originCity", "Reykjavik");
        specific.put("destinationCity", "Belize City");
        var created = send("POST", CATALOG, coordinator, specific, 201);
        var selection = selection("IS", "BZ", "ROAD", null);
        selection.put("originCity", "  REYKJAVIK ");
        selection.put("destinationCity", "Belize City");
        assertThat(send("POST", "/api/routes/match", null, selection, 200)
                .path("templateId")).isEqualTo(created.path("routeTemplateId"));
        selection.put("originCity", "Hafnarfjordur");
        assertThat(send("POST", "/api/routes/match", null, selection, 200)
                .path("estimatedRoadKm").decimalValue())
                .isEqualByComparingTo("100.00");
    }

    /** @throws Exception if submission trusts old prices or loses flags */
    @Test
    public void submissionFreezesFreshMatchAndCustomCoordinatorFlag()
            throws Exception {
        String customer = token("CUSTOMER");
        var selected = selection("VN", "LA", "ROAD", null);
        int originHub = hubId("VN");
        int destinationHub = hubId("LA");
        selected.put("originHubId", originHub);
        selected.put("destinationHubId", destinationHub);
        var draft = draft(customer, "LA", selected);
        var saved = send("POST", "/api/transport-orders", customer, draft, 201);
        long order = saved.path("orderId").asLong();
        jdbc.update("UPDATE dbo.RouteTemplate SET BaseRoadRate=1.25 "
                + "WHERE RouteCode='ROUTE-VN-LA-ROAD'");
        var command = json.createObjectNode();
        command.put("recordVersion", saved.path("recordVersion").asText());
        command.put("operationId", UUID.randomUUID().toString());
        String submit = "/api/transport-orders/" + order + "/submit";
        var revision = send("POST", submit, customer, command, 200);
        assertThat(revision.at("/snapshot/routeMatch/originHubId").asInt())
                .isEqualTo(originHub);
        assertThat(revision.at("/snapshot/routeMatch/destinationHubId").asInt())
                .isEqualTo(destinationHub);
        assertThat(revision.at("/snapshot/routeMatch/totalEstimatedCost")
                .decimalValue()).isEqualByComparingTo("1375.00");
        jdbc.update("UPDATE dbo.RouteTemplate SET BaseRoadRate=2 "
                + "WHERE RouteCode='ROUTE-VN-LA-ROAD'");
        assertThat(send("POST", submit, customer, command, 200))
                .isEqualTo(revision);
        var custom = draft(customer, "IS", selection("VN", "IS", "ROAD", null));
        saved = send("POST", "/api/transport-orders", customer, custom, 201);
        order = saved.path("orderId").asLong();
        command.put("recordVersion", saved.path("recordVersion").asText());
        command.put("operationId", UUID.randomUUID().toString());
        revision = send("POST", "/api/transport-orders/" + order + "/submit",
                customer, command, 200);
        assertThat(revision.at("/snapshot/routeMatch/"
                + "requiresCustomCoordinatorPlan")
                .asBoolean()).isTrue();
        String stored = jdbc.queryForObject("SELECT OrderSnapshot FROM "
                + "dbo.OrderRevision WHERE RevisionID=?", String.class,
                revision.path("revisionId").asLong());
        assertThat(json.readTree(stored).at(
                "/routeMatch/requiresCustomCoordinatorPlan").asBoolean())
                .isTrue();
        var approval = json.createObjectNode();
        approval.put("revisionId", revision.path("revisionId").asLong());
        approval.put("recordVersion", java.util.HexFormat.of().formatHex(
                jdbc.queryForObject("SELECT RecordVersion FROM "
                        + "dbo.TransportOrder WHERE OrderID=?", byte[].class,
                        order)));
        approval.put("operationId", UUID.randomUUID().toString());
        approval.put("decision", "APPROVED");
        approval.put("specialistId", staffId("SPECIALIST"));
        approval.put("coordinatorId", staffId("COORDINATOR"));
        send("POST", "/api/manager/transport-orders/" + order + "/decisions",
                token("MANAGER"), approval, 200);
        var assigned = send("GET", "/api/staff/transport-orders/" + order,
                token("COORDINATOR"), null, 200);
        assertThat(assigned.at("/revision/snapshot/routeMatch/"
                + "requiresCustomCoordinatorPlan").asBoolean()).isTrue();
    }

    private ObjectNode draft(String customer, String destination,
            JsonNode route)
            throws Exception {
        var horse = send("POST", "/api/horses", customer,
                json.readTree("{\"name\":\"Route fixture\"}"), 201);
        var body = (ObjectNode) json.readTree(ApiExamples.DRAFT);
        body.put("horseId", horse.path("horseId").asLong());
        body.put("originCountryCode", "VN");
        body.put("destinationCountryCode", destination);
        body.put("preferredDepartureAt", time(90));
        body.put("deliveryDeadline", time(95));
        body.put("eventStartAt", time(100));
        body.putArray("transitAirports");
        body.set("services", json.readTree("{\"roadArrangement\":"
                + "\"EQUITRANSIT\",\"veterinaryArrangement\":\"EQUITRANSIT\","
                + "\"insurance\":\"NONE\"}"));
        body.set("route", route);
        return body;
    }

    /** @throws Exception if route tampering can create a submitted revision */
    @Test
    public void mismatchedEndpointsAndClientPricesCannotSubmit()
            throws Exception {
        String customer = token("CUSTOMER");
        var route = selection("VN", "FR", "ROAD", null);
        var body = draft(customer, "LA", route);
        var saved = send("POST", "/api/transport-orders", customer, body, 201);
        long order = saved.path("orderId").asLong();
        var command = json.createObjectNode();
        command.put("recordVersion", saved.path("recordVersion").asText());
        command.put("operationId", UUID.randomUUID().toString());
        var failed = send("POST", "/api/transport-orders/" + order + "/submit",
                customer, command, 422);
        assertThat(failed.path("code").asText())
                .isEqualTo("ROUTE_SELECTION_MISMATCH");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.OrderRevision"
                + " WHERE OrderID=?", Integer.class, order)).isZero();
        assertThat(jdbc.queryForObject("SELECT OrderStatus FROM "
                + "dbo.TransportOrder WHERE OrderID=?", String.class, order))
                .isEqualTo("DRAFT");
        route.put("totalEstimatedCost", "0.01");
        body.set("route", route);
        send("POST", "/api/transport-orders", customer, body, 400);
    }

    private JsonNode match(String origin, String destination, String mode,
            String stall) throws Exception {
        return send("POST", "/api/routes/match", null,
                selection(origin, destination, mode, stall), 200);
    }

    private ObjectNode selection(String origin, String destination, String mode,
            String stall) {
        var body = json.createObjectNode();
        body.put("originCountryCode", origin);
        body.put("destinationCountryCode", destination);
        if (mode != null) {
            body.put("transportMode", mode);
        }
        if (stall != null) {
            body.put("airStallPreference", stall);
        }
        return body;
    }

    private ObjectNode template(String mode, String road) {
        var body = json.createObjectNode();
        body.put("routeCode", "TEST-" + UUID.randomUUID());
        body.put("routeName", "Isolated route fixture");
        body.put("originCountryCode", "IS");
        body.put("destinationCountryCode", "BZ");
        body.put("transportMode", mode);
        body.put("estimatedRoadKm", road);
        body.put("estimatedHours", "10.0");
        body.put("routeSummary", "Isolated routing test only");
        return body;
    }

    private long staffId(String role) {
        return jdbc.queryForObject("SELECT TOP(1) s.StaffID FROM "
                + "dbo.StaffProfile s JOIN dbo.[User] u ON u.UserID=s.UserID "
                + "JOIN dbo.[Role] r ON r.RoleID=u.RoleID WHERE "
                + "r.RoleCode=? AND u.Status='ACTIVE' AND "
                + "s.StaffStatus='ACTIVE' ORDER BY s.StaffID",
                Long.class, role);
    }

    /** @throws Exception if HTTP selection ignores ranking or country guards */
    @Test
    public void publicHubChoicesUseAppliedIdsAndRejectForeignIds()
            throws Exception {
        int primary = hubId("VN");
        int alternate = jdbc.queryForObject("INSERT dbo.LocationCatalog("
                + "CountryCode,CountryName,LocationType,LocationName,Latitude,"
                + "Longitude,IsActive,IsPrimaryHub) OUTPUT INSERTED.LocationID "
                + "VALUES('VN',N'Việt Nam','HUB',?,10.7754,106.6996,1,0)",
                Integer.class, "Discovery test " + UUID.randomUUID());
        var request = selection("VN", "CN", "ROAD", null);
        request.put("pickupAddress", "Quận 1");
        var automatic = send("POST", "/api/routes/match", null, request, 200);
        assertThat(automatic.path("originHubId").asInt()).isEqualTo(alternate);
        assertThat(automatic.path("originHubOptions").get(0)
                .path("isRecommendedNearest").asBoolean()).isTrue();
        assertThat(automatic.path("firstMileCost").decimalValue()).isZero();
        request.put("originHubId", primary);
        var manual = send("POST", "/api/routes/match", null, request, 200);
        assertThat(manual.path("originHubId").asInt()).isEqualTo(primary);
        assertThat(manual.path("originHubOptions").get(0)
                .path("locationId").asInt()).isEqualTo(alternate);
        request.put("originHubId", hubId("CN"));
        assertThat(send("POST", "/api/routes/match", null, request, 422)
                .path("code").asText()).isEqualTo("INVALID_HUB_SELECTION");
        request.put("originHubId", -1);
        send("POST", "/api/routes/match", null, request, 400);
        jdbc.update("UPDATE dbo.LocationCatalog SET IsActive=0 "
                + "WHERE LocationID=?", alternate);
        request.put("originHubId", alternate);
        send("POST", "/api/routes/match", null, request, 422);
    }

    /** @throws Exception if strict selectors break the legacy submit API */
    @Test
    public void unsupportedHubDoesNotBreakLegacySubmissions() throws Exception {
        jdbc.update("UPDATE dbo.LocationCatalog SET IsPrimaryHub=0,IsActive=0 "
                + "WHERE CountryCode='NO' AND LocationType='HUB'");
        var selection = selection("VN", "NO", "ROAD", null);
        var preview = send("POST", "/api/routes/match", null, selection, 200);
        assertThat(preview.path("matched").asBoolean()).isFalse();
        assertThat(preview.path("requiresCustomCoordinatorPlan").asBoolean())
                .isFalse();
        String customer = token("CUSTOMER");
        for (boolean legacy : List.of(true, false)) {
            var request = draft(customer, "NO", selection);
            if (legacy) {
                request.remove("route");
            }
            var order = send("POST", "/api/transport-orders", customer,
                    request, 201);
            var command = json.createObjectNode().put("recordVersion",
                    order.path("recordVersion").asText())
                    .put("operationId", UUID.randomUUID().toString());
            send("POST", "/api/transport-orders/"
                    + order.path("orderId").asLong() + "/submit", customer,
                    command, legacy ? 200 : 422);
        }
    }

    private int hubId(String country) {
        return jdbc.queryForObject("SELECT TOP(1) LocationID FROM "
                + "dbo.LocationCatalog WHERE CountryCode=? AND "
                + "LocationType='HUB' AND IsActive=1 "
                + "ORDER BY IsPrimaryHub DESC,LocationID",
                Integer.class, country);
    }

    private String token(String role) {
        long user = jdbc.queryForObject("SELECT TOP(1) u.UserID "
                + "FROM dbo.[User] u"
                + " JOIN dbo.[Role] r ON r.RoleID=u.RoleID WHERE r.RoleCode=? "
                + "AND u.Status='ACTIVE' ORDER BY u.UserID", Long.class, role);
        return "Bearer " + tokens.issue(users.findById(user).orElseThrow())
                .getAccessToken();
    }

    private JsonNode send(String method, String path, String token,
            JsonNode body, int expected) throws Exception {
        var request = MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), path);
        if (token != null) {
            request.header("Authorization", token);
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

    private String time(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(days).withNano(0)
                .toString();
    }
}



