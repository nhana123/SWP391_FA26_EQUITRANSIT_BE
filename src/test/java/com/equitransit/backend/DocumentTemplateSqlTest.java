package com.equitransit.backend;

import com.equitransit.backend.repository.UserAccountRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
        .AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

/** Native SQL and HTTP template lookup/publication; every change rolls back. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_DOCUMENT_TEMPLATE_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.storage.directory=.local/template-test-files"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_DOCUMENT_TEMPLATE_TEST",
        matches = "true")
@Transactional
public class DocumentTemplateSqlTest {
    private static final String LOOKUP = "/api/lookups/document-templates";
    private static final String EDIT =
            "/api/compliance/document-templates/COGGINS_EIA/file";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccountRepository users;
    @Autowired private JwtTokenService tokens;

    /** No native fixture writes are permitted outside Demo/Test. */
    @BeforeEach
    public void sandbox() {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class))
                .isIn("EquiTransit_Demo", "EquiTransit_Test");
    }

    /** @throws Exception if public metadata or active filtering is incorrect */
    @Test
    public void publicLookupContainsSixSeededForms() throws Exception {
        var list = get(LOOKUP, null, 200);
        var codes = new HashSet<String>();
        for (var item : list) {
            codes.add(item.path("typeCode").asText());
        }
        assertThat(codes).contains("FEI_PASSPORT", "COGGINS_EIA",
                "EQUINE_INFLUENZA", "EXPORT_PERMIT", "GLANDERS_CFT",
                "EVA_TEST");
        for (String type : List.of("FEI_PASSPORT", "COGGINS_EIA",
                "EQUINE_INFLUENZA", "EXPORT_PERMIT", "GLANDERS_CFT",
                "EVA_TEST")) {
            var detail = get(LOOKUP + "/" + type, null, 200);
            assertThat(detail.path("formCode").asText()).isNotBlank();
            assertThat(detail.path("issuingAuthority").asText()).isNotBlank();
            assertThat(detail.path("subAuthority").asText()).isNotBlank();
            assertThat(detail.path("certTextTemplate").asText()).isNotBlank();
            assertThat(detail.path("badgeColor").asText()).isNotBlank();
            assertThat(detail.path("emblemIcon").asText()).isNotBlank();
            assertThat(detail.path("hasTemplateFile").asBoolean())
                    .isEqualTo(!detail.path("templateFileKey").isNull());
        }
        get(LOOKUP + "/NO_SUCH_FORM", null, 404);
        jdbc.update("UPDATE dbo.DocumentTypeCatalog SET IsActive=0 "
                + "WHERE TypeCode='EVA_TEST'");
        get(LOOKUP + "/EVA_TEST", null, 404);
        assertThat(get(LOOKUP, null, 200).toString())
                .doesNotContain("\"typeCode\":\"EVA_TEST\"");
    }

    /** @throws Exception if publication fails or exposes unrelated files */
    @Test
    public void staffPublishPdfAndAuthenticatedUsersDownload()
            throws Exception {
        String customer = token("CUSTOMER");
        String previous = null;
        for (String role : List.of("SPECIALIST", "MANAGER")) {
            String staff = token(role);
            var key = upload(staff, "blank.pdf", "application/pdf", pdf());
            String path = "/api/files/" + key;
            get(path, customer, 404);
            var body = json.valueToTree(Map.of("templateFileKey", key));
            var updated = put(EDIT, staff, body, 200);
            assertThat(updated.path("templateFileKey").asText()).isEqualTo(key);
            assertThat(updated.path("hasTemplateFile").asBoolean()).isTrue();
            assertThat(updated.path("templateDownloadUrl").asText())
                    .isEqualTo(path);
            assertThat(put(EDIT, staff, body, 200)).isEqualTo(updated);
            assertThat(get(LOOKUP + "/COGGINS_EIA", null, 200))
                    .isEqualTo(updated);
            var file = mvc.perform(MockMvcRequestBuilders.get(path)
                    .header("Authorization", customer))
                    .andReturn().getResponse();
            assertThat(file.getStatus()).isEqualTo(200);
            assertThat(file.getContentType()).isEqualTo("application/pdf");
            assertThat(file.getContentAsByteArray()).startsWith(
                    new byte[]{'%', 'P', 'D', 'F'});
            get(path, null, 401);
            if (previous != null) {
                get("/api/files/" + previous, customer, 404);
            }
            previous = key;
        }
        jdbc.update("UPDATE dbo.DocumentTypeCatalog SET IsActive=0 "
                + "WHERE TypeCode='COGGINS_EIA'");
        get("/api/files/" + previous, customer, 404);
    }

    /** @throws Exception if unauthorized/non-PDF/private papers can publish */
    @Test
    public void rejectWrongRolesMissingFilesAndPrivateEvidence()
            throws Exception {
        String specialist = token("SPECIALIST");
        var unknown = json.valueToTree(Map.of("templateFileKey",
                UUID.randomUUID()));
        put(EDIT, null, unknown, 401);
        put(EDIT, token("CUSTOMER"), unknown, 403);
        put(EDIT, token("COORDINATOR"), unknown, 403);
        put(EDIT, specialist, unknown, 404);
        put(EDIT, specialist, json.createObjectNode(), 400);
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        String imageKey = upload(specialist, "image.png", "image/png",
                png.toByteArray());
        put(EDIT, specialist, json.valueToTree(Map.of("templateFileKey",
                imageKey)), 422);
        String ownedByCustomer = upload(token("CUSTOMER"), "private.pdf",
                "application/pdf", pdf());
        put(EDIT, specialist, json.valueToTree(Map.of("templateFileKey",
                ownedByCustomer)), 404);
        String paper = upload(specialist, "evidence.pdf", "application/pdf",
                pdf());
        long customer = jdbc.queryForObject("SELECT TOP(1) CustomerID FROM "
                + "dbo.Customer ORDER BY CustomerID", Long.class);
        long horse = jdbc.queryForObject("INSERT dbo.Horse(CustomerID,Name) "
                + "OUTPUT INSERTED.HorseID VALUES(?,'Template test horse')",
                Long.class, customer);
        long document = jdbc.queryForObject("INSERT dbo.HorseDocument("
                + "HorseID,DocumentType) OUTPUT INSERTED.DocumentID "
                + "VALUES(?,'COGGINS_EIA')", Long.class, horse);
        jdbc.update("INSERT dbo.DocumentVersion(DocumentID,VersionNo,FileKey,"
                + "UploadedBy) SELECT ?,1,CONVERT(varchar(36),FileKey),UserID "
                + "FROM dbo.LocalFileObject WHERE FileKey=?", document, paper);
        var blocked = put(EDIT, specialist, json.valueToTree(Map.of(
                "templateFileKey", paper)), 409);
        assertThat(blocked.path("code").asText())
                .isEqualTo("TEMPLATE_FILE_ALREADY_USED");
    }

    private String token(String role) {
        long user = jdbc.queryForObject("SELECT TOP(1) u.UserID FROM "
                + "dbo.[User] u JOIN dbo.[Role] r ON r.RoleID=u.RoleID "
                + "WHERE r.RoleCode=? AND u.Status='ACTIVE' ORDER BY u.UserID",
                Long.class, role);
        return "Bearer " + tokens.issue(users.findById(user).orElseThrow())
                .getAccessToken();
    }

    private String upload(String token, String name, String mime, byte[] bytes)
            throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.multipart(
                "/api/files/upload").file(new MockMultipartFile("file", name,
                        mime, bytes)).header("Authorization", token))
                .andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString())
                .isEqualTo(200);
        return json.readTree(response.getContentAsString()).path("fileKey")
                .asText();
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

    private JsonNode put(String path, String token, JsonNode body, int expected)
            throws Exception {
        var request = MockMvcRequestBuilders.put(path)
                .contentType("application/json").content(body.toString());
        if (token != null) {
            request.header("Authorization", token);
        }
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString())
                .isEqualTo(expected);
        return json.readTree(response.getContentAsString());
    }

    private byte[] pdf() throws Exception {
        try (var document = new PDDocument();
                var output = new java.io.ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
