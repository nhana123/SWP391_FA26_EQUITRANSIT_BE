package com.equitransit.backend;


import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.equitransit.backend.controller.ComplianceController;
import com.equitransit.backend.service.ComplianceService;
import com.equitransit.backend.dto.request.DocumentSubmissionRequest;
import com.equitransit.backend.dto.response.DocumentVersionResponse;
import com.equitransit.backend.entity.AuthorityProcedure;
import com.equitransit.backend.entity.HorseDocument;
import com.equitransit.backend.entity.DocumentVersion;
import com.equitransit.backend.repository.AuthorityProcedureRepository;
import com.equitransit.backend.repository.HorseDocumentRepository;
import com.equitransit.backend.repository.DocumentVersionRepository;
import com.equitransit.backend.repository.ComplianceAuditRepository;
import com.equitransit.backend.repository.ComplianceRuleRepository;
import com.equitransit.backend.exception.ComplianceException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/** Checks real JDBC mapping against the snapshot's columns, in isolated H2. */
public class CompliancePersistenceTest {
    private DriverManagerDataSource source;
    private JdbcTemplate sql;
    private NamedParameterJdbcTemplate named;

    /** Creates a new synthetic database for each test. */
    @BeforeEach
    public void schema() {
        source = new DriverManagerDataSource("jdbc:h2:mem:f08_"
                + UUID.randomUUID() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
                "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource(
                "f08-jdbc-schema.sql")).execute(source);
        sql = new JdbcTemplate(source);
        named = new NamedParameterJdbcTemplate(source);
    }

    /** Round-trips identity, nullable metadata and UTC measurement dates. */
    @Test
    public void versionMappingUsesActualColumns() {
        var documents = new HorseDocumentRepository(named);
        var versions = new DocumentVersionRepository(named);
        var document = new HorseDocument();

        document.setHorseId(10);
        document.setDocumentType("PASSPORT");
        long documentId = documents.insert(document);
        var version = new DocumentVersion();

        version.setDocumentId(documentId);
        version.setVersionNo(1);
        version.setFileKey("private/opaque-key");
        version.setIssuedDate(LocalDate.of(2026, 1, 1));
        version.setExpiryDate(LocalDate.of(2027, 1, 1));
        version.setFileSizeBytes(123L);
        long id = versions.insert(version);
        var saved = versions.findById(id).orElseThrow();

        assertThat(saved.getDocumentId()).isEqualTo(documentId);
        assertThat(saved.getFileKey()).isEqualTo("private/opaque-key");
        assertThat(saved.getIssuedDate()).isEqualTo(version.getIssuedDate());
        assertThat(saved.getExpiryDate()).isEqualTo(version.getExpiryDate());
        assertThat(saved.getFileSizeBytes()).isEqualTo(123L);
        assertThat(saved.getUploadedAt().getOffset().getTotalSeconds())
                .isZero();
        assertThat(saved.getUploadedBy()).isNull();
        assertThat(versions.maxVersionNo(documentId)).isEqualTo(1);
    }

    /** Wrong-owner lookups never expose the other customer's document. */
    @Test
    public void ownershipIsPersistedNotClientProvided() {
        sql.update("INSERT INTO dbo.Horse VALUES (10,20)");
        var documents = new HorseDocumentRepository(named);

        documents.verifyOwnership(10, 20);
        assertThatThrownBy(() -> documents.verifyOwnership(10, 21))
                .isInstanceOf(ComplianceException.class);
    }

    /** A missing rule cannot become an exemption or an approved requirement. */
    @Test
    public void missingRuleIsPendingAssessment() {
        var rules = new ComplianceRuleRepository(named);

        assertThatThrownBy(() -> rules.requireReviewedRule(
                123, "PASSPORT", "PRE_EXPORT"))
                .isInstanceOf(ComplianceException.class)
                .extracting("code").isEqualTo("RULE_PENDING_ASSESSMENT");
    }

    /** Domain rows and the real audit record share a single transaction. */
    @Test
    public void failedTransactionRollsBackDocumentAndAudit() {
        var documents = new HorseDocumentRepository(named);
        var audit = new ComplianceAuditRepository(named);
        var transaction = new TransactionTemplate(
                new DataSourceTransactionManager(source));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            var document = new HorseDocument();

            document.setHorseId(10);
            document.setDocumentType("PASSPORT");
            long id = documents.insert(document);

            audit.append(30, 40, "TEST_ONLY", id);
            throw new IllegalStateException("Synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.HorseDocument",
                Integer.class)).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent",
                Integer.class)).isZero();
    }

    /** Public JSON omits private storage keys and hashes. */
    @Test
    public void versionResponseDoesNotExposeStorage() throws Exception {
        var response = new DocumentVersionResponse(1, 2, 1,
                null, null, null, null, "passport.pdf", "application/pdf",
                123L);
        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).doesNotContain("fileKey", "fileHash");
    }

    /** Missing or nonpositive version identities fail request validation. */
    @Test
    public void submissionRequiresARealPositiveVersionId() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new DocumentSubmissionRequest(null)))
                    .isNotEmpty();
            assertThat(validator.validate(new DocumentSubmissionRequest(0L)))
                    .isNotEmpty();
            assertThat(validator.validate(new DocumentSubmissionRequest(1L)))
                    .isEmpty();
        }
    }

    /** Unverified endpoints stay disabled during component scanning. */
    @Test
    public void apiRemainsDisabledByDefault() {
        new ApplicationContextRunner().withUserConfiguration(
                ComplianceController.class, ComplianceService.class)
                .run(context -> {
                    assertThat(context)
                            .doesNotHaveBean(ComplianceService.class);
                    assertThat(context)
                            .doesNotHaveBean(ComplianceController.class);
                });
    }

    /** Authority procedure repository supports pagination and exact count. */
    @Test
    public void authorityProcedureSupportsPaginationAndCount() {
        var repo = new AuthorityProcedureRepository(named);
        for (int i = 1; i <= 5; i++) {
            var proc = new AuthorityProcedure();
            proc.setRevisionId(42L);
            proc.setProcedureCode("PROC_" + i);
            proc.setAuthorityName("Authority " + i);
            proc.setApprovalStatus("PENDING");
            repo.insert(proc);
        }

        assertThat(repo.countByRevisionId(42L)).isEqualTo(5);
        var page0 = repo.findByRevisionIdPaged(42L, 0, 2);
        assertThat(page0).hasSize(2);
        assertThat(page0.get(0).getProcedureCode()).isEqualTo("PROC_1");
        assertThat(page0.get(1).getProcedureCode()).isEqualTo("PROC_2");

        var page2 = repo.findByRevisionIdPaged(42L, 2, 2);
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).getProcedureCode()).isEqualTo("PROC_5");
    }
}
