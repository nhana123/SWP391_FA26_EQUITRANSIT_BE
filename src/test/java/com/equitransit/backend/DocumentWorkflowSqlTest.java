package com.equitransit.backend;

import com.equitransit.backend.dto.ApiExamples;
import com.equitransit.backend.entity.Customer;
import com.equitransit.backend.entity.UserAccount;
import com.equitransit.backend.repository.AccountRoleRepository;
import com.equitransit.backend.repository.CustomerRepository;
import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
        .AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

/** HTTP/security and SQL; business fixtures and disk files roll back. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_DOCUMENT_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.storage.directory=.local/module-test-files",
        "app.compliance.auto-approval=true"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_DOCUMENT_TEST", matches = "true")
@Transactional
public class DocumentWorkflowSqlTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountRoleRepository roles;
    @Autowired private CustomerRepository customers;
    @Autowired private JwtTokenService tokens;
    private long order;

    /** Approval creates checklists; files are private, reusable and immutable.
     * @throws Exception on HTTP or fixture errors */
    @Test
    public void lookupApprovalUploadReuseAndClear() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class))
                .isIn("EquiTransit_Demo", "EquiTransit_Test");
        jdbc.update("INSERT dbo.LocationCatalog(CountryCode,CountryName,"
                + "LocationType,LocationName,IsActive,IsPrimaryHub) SELECT "
                + "c.CountryCode,MAX(c.CountryName),'HUB',"
                + "'Document workflow fixture '+c.CountryCode,1,1 FROM "
                + "dbo.LocationCatalog c WHERE c.IsActive=1 AND NOT EXISTS("
                + "SELECT 1 FROM dbo.LocationCatalog h WHERE "
                + "h.CountryCode=c.CountryCode AND h.LocationType='HUB' "
                + "AND h.IsActive=1) GROUP BY c.CountryCode");
        String owner = customer();
        String other = customer();
        String manager = staff("MANAGER");
        String specialist = staff("SPECIALIST");
        assertThat(get("/api/lookups/countries", null, 200).size())
                .isGreaterThanOrEqualTo(15);
        assertThat(get("/api/lookups/locations?country=VN&type=AIRPORT",
                null, 200).size()).isEqualTo(2);
        get("/api/lookups/locations?country=VN&type=INVALID", null, 400);
        var horse = post("/api/horses", owner,
                json.readTree("{\"name\":\"Module workflow fixture\"}"), 201);
        jdbc.update("UPDATE dbo.Horse SET SexCode='STALLION' WHERE HorseID=?",
                horse.path("horseId").asLong());
        var draft = (ObjectNode) json.readTree(ApiExamples.DRAFT);
        draft.put("horseId", horse.path("horseId").asLong());
        draft.put("originCountryCode", "IS");
        draft.put("destinationCountryCode", "GB");
        draft.putArray("transitAirports");
        draft.put("preferredDepartureAt", time(10));
        draft.put("deliveryDeadline", time(15));
        draft.put("eventStartAt", time(20));
        draft.set("services", json.valueToTree(Map.of("roadArrangement",
                "EQUITRANSIT", "veterinaryArrangement", "EQUITRANSIT",
                "insurance", "NONE")));
        var saved = post("/api/transport-orders", owner, draft, 201);
        order = saved.path("orderId").asLong();
        post(base() + "/submit", owner, command(), 200);
        var approval = command();
        approval.put("revisionId", jdbc.queryForObject("SELECT "
                + "CurrentRevisionID FROM dbo.TransportOrder WHERE OrderID=?",
                Long.class, order));
        approval.put("decision", "APPROVED");
        approval.put("specialistId", staffId("SPECIALIST"));
        approval.put("coordinatorId", staffId("COORDINATOR"));
        var receipt = post("/api/manager/transport-orders/" + order
                + "/decisions", manager, approval, 200);
        assertThat(post("/api/manager/transport-orders/" + order + "/decisions",
                manager, approval, 200)).isEqualTo(receipt);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.ComplianceChecklist WHERE RevisionID=(SELECT "
                + "CurrentRevisionID FROM dbo.TransportOrder WHERE OrderID=?)",
                Integer.class, order)).isEqualTo(3);
        var listing = get(legal() + "/documents", owner, 200);
        assertThat(listing.path("requirements").size()).isEqualTo(8);
        assertThat(listing.path("versions").size()).isZero();
        for (var requirement : listing.path("requirements")) {
            assertThat(requirement.path("status").asText())
                    .isEqualTo("PENDING_SUBMISSION");
        }
        byte[] image = png();
        var upload = mvc.perform(MockMvcRequestBuilders.multipart(
                "/api/files/upload").file(new MockMultipartFile("file",
                        "certificate.png", "image/png", image))
                .header("Authorization", owner)).andReturn().getResponse();
        assertThat(upload.getStatus()).as(upload.getContentAsString())
                .isEqualTo(200);
        var key = json.readTree(upload.getContentAsString()).path("fileKey")
                .asText();
        mvc.perform(MockMvcRequestBuilders.get("/api/files/" + key)
                .header("Authorization", owner)).andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(200);
                    assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo(image);
                    assertThat(result.getResponse().getHeader(
                            "Content-Disposition")).startsWith("inline;");
                });
        get("/api/files/" + key, other, 404);
        get("/api/files/" + key, null, 401);
        var otherHorse = post("/api/horses", owner,
                json.readTree("{\"name\":\"Other horse fixture\"}"), 201);
        boolean foreignVersionChecked = false;
        for (var requirement : listing.path("requirements")) {
            long id = requirement.path("requirementId").asLong();
            var input = command();
            input.put("fileKey", key);
            input.put("documentNumber", "TEST-PAPER");
            input.put("issuedDate", LocalDate.now(ZoneOffset.UTC).toString());
            input.put("expiryDate", LocalDate.now(ZoneOffset.UTC).plusDays(60)
                    .toString());
            String path = legal() + "/requirements/" + id
                    + "/upload-and-submit";
            if ("COGGINS_EIA".equals(requirement.path("documentType")
                    .asText())) {
                input.put("issuedDate", LocalDate.now(ZoneOffset.UTC)
                        .minusDays(31).toString());
                post(path, owner, input, 409);
                input.put("issuedDate", LocalDate.now(ZoneOffset.UTC)
                        .toString());
            }
            var submitted = post(path, owner, input, 200);
            assertThat(post(path, owner, input, 200)).isEqualTo(submitted);
            long version = jdbc.queryForObject("SELECT DocumentVersionID "
                    + "FROM dbo.DocumentSubmission WHERE SubmissionID=?",
                    Long.class, submitted.path("entityId").asLong());
            if (!foreignVersionChecked) {
                long foreignDocument = jdbc.queryForObject("INSERT "
                        + "dbo.HorseDocument(HorseID,DocumentType) OUTPUT "
                        + "INSERTED.DocumentID VALUES(?,?)", Long.class,
                        otherHorse.path("horseId").asLong(),
                        requirement.path("documentType").asText());
                long foreignVersion = jdbc.queryForObject("INSERT "
                        + "dbo.DocumentVersion(DocumentID,VersionNo,FileKey,"
                        + "IssuedDate,ExpiryDate,UploadedBy,OriginalFileName,"
                        + "ContentType,FileSizeBytes,FileHash) OUTPUT "
                        + "INSERTED.DocumentVersionID SELECT ?,1,FileKey,"
                        + "IssuedDate,ExpiryDate,UploadedBy,OriginalFileName,"
                        + "ContentType,FileSizeBytes,FileHash FROM "
                        + "dbo.DocumentVersion WHERE DocumentVersionID=?",
                        Long.class, foreignDocument, version);
                var invalid = command();
                invalid.put("existingDocumentVersionId", foreignVersion);
                post(path, owner, invalid, 404);
                invalid = command();
                invalid.put("existingDocumentVersionId", version);
                invalid.put("fileKey", key);
                post(path, owner, invalid, 409);
                foreignVersionChecked = true;
            }
            var reuse = command();
            reuse.put("existingDocumentVersionId", version);
            var reused = post(path, owner, reuse, 200);
            long submission = reused.path("entityId").asLong();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                    + "dbo.DocumentVersion WHERE DocumentVersionID=?",
                    Integer.class, version)).isEqualTo(1);
            var review = json.createObjectNode();
            review.set("command", command());
            review.put("decision", "APPROVED");
            post(legal() + "/submissions/" + submission + "/reviews",
                    specialist, review, 200);
        }
        for (String phase : new String[]{"PRE_EXPORT", "ARRIVAL",
                "QUARANTINE_RELEASE"}) {
            var clear = json.createObjectNode();
            clear.set("command", command());
            clear.put("phase", phase);
            clear.put("expiresAt", time(20));
            post(legal() + "/clearances", specialist, clear, 200);
        }
        get("/api/files/" + key, specialist, 200);
        assertThat(get(legal() + "/documents", owner, 200)
                .path("versions").size()).isEqualTo(8);
        // A route rule provisioned for a stallion must not affect a mare.
        jdbc.update("UPDATE dbo.Horse SET SexCode='MARE' WHERE HorseID=?",
                otherHorse.path("horseId").asLong());
        draft.put("horseId", otherHorse.path("horseId").asLong());
        var mareOrder = post("/api/transport-orders", owner, draft, 201);
        order = mareOrder.path("orderId").asLong();
        post(base() + "/submit", owner, command(), 200);
        approval = command();
        approval.put("revisionId", jdbc.queryForObject("SELECT "
                + "CurrentRevisionID FROM dbo.TransportOrder WHERE OrderID=?",
                Long.class, order));
        approval.put("decision", "APPROVED");
        approval.put("specialistId", staffId("SPECIALIST"));
        approval.put("coordinatorId", staffId("COORDINATOR"));
        post("/api/manager/transport-orders/" + order + "/decisions", manager,
                approval, 200);
        var mareDocuments = get(legal() + "/documents", owner, 200)
                .path("requirements");
        assertThat(mareDocuments.size()).isEqualTo(7);
        for (var requirement : mareDocuments) {
            assertThat(requirement.path("documentType").asText())
                    .isNotEqualTo("EVA_TEST");
        }
    }

    /** Invalid uploads never create metadata or files.
     * @throws Exception on HTTP failures */
    @Test
    public void rejectsUnsafeAndOversizedUploads() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class))
                .isIn("EquiTransit_Demo", "EquiTransit_Test");
        String owner = customer();
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.LocalFileObject", Integer.class);
        assertUpload(owner, "../evidence.png", "image/png", png(), 422);
        assertUpload(owner, "evidence.exe", "application/octet-stream",
                png(), 409);
        assertUpload(owner, "evidence.png", "image/png",
                new byte[]{1, 2, 3}, 409);
        assertUpload(owner, "evidence.png", "image/png",
                new byte[10 * 1024 * 1024 + 1], 422);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.LocalFileObject", Integer.class)).isEqualTo(before);
    }

    private void assertUpload(String token, String name, String mime,
            byte[] bytes, int expected) throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.multipart(
                "/api/files/upload").file(new MockMultipartFile("file", name,
                        mime, bytes)).header("Authorization", token))
                .andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString())
                .isEqualTo(expected);
    }

    private String customer() {
        var user = new UserAccount();
        user.setRole(roles.findByRoleCode("CUSTOMER").orElseThrow());
        user.setEmail(UUID.randomUUID() + "@example.invalid");
        user.setPasswordHash("{bcrypt}fixture-not-for-login");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        users.saveAndFlush(user);
        var customer = new Customer();
        customer.setUser(user);
        customer.setFullName("Document module fixture");
        customers.saveAndFlush(customer);
        return "Bearer " + tokens.issue(user).getAccessToken();
    }

    private long staffId(String role) {
        return jdbc.queryForObject("SELECT TOP(1) s.StaffID FROM "
                + "dbo.StaffProfile s JOIN dbo.[User] u ON u.UserID=s.UserID "
                + "JOIN dbo.[Role] r ON r.RoleID=u.RoleID WHERE "
                + "r.RoleCode=? AND u.Status='ACTIVE' AND "
                + "s.StaffStatus='ACTIVE' ORDER BY s.StaffID",
                Long.class, role);
    }

    private String staff(String role) {
        long user = jdbc.queryForObject("SELECT UserID FROM dbo.StaffProfile "
                + "WHERE StaffID=?", Long.class, staffId(role));
        return "Bearer " + tokens.issue(users.findById(user).orElseThrow())
                .getAccessToken();
    }

    private JsonNode get(String path, String token, int expected)
            throws Exception {
        var request = MockMvcRequestBuilders.get(path);
        if (token != null) {
            request.header("Authorization", token);
        }
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString())
                .isEqualTo(expected);
        return response.getContentType() != null
                && response.getContentType().contains("json")
                ? json.readTree(response.getContentAsString())
                : json.nullNode();
    }

    private JsonNode post(String path, String token, JsonNode body,
            int expected)
            throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.post(path)
                .header("Authorization", token).contentType("application/json")
                .content(body.toString())).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString())
                .isEqualTo(expected);
        return json.readTree(response.getContentAsString());
    }

    private ObjectNode command() {
        var body = json.createObjectNode();
        body.put("recordVersion", HexFormat.of().formatHex(jdbc.queryForObject(
                "SELECT RecordVersion FROM dbo.TransportOrder WHERE OrderID=?",
                byte[].class, order)));
        body.put("operationId", UUID.randomUUID().toString());
        return body;
    }

    private String base() {
        return "/api/transport-orders/" + order;
    }

    private String legal() {
        return base() + "/compliance";
    }

    private String time(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(days).withNano(0)
                .toString();
    }

    private byte[] png() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
}
