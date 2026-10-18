package com.equitransit.backend.compliance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import com.equitransit.backend.dto.compliance.request.
        ComplianceRulePolicyRequest;
import com.equitransit.backend.dto.compliance.request.DocumentSubmissionRequest;
import com.equitransit.backend.dto.compliance.request.DocumentReviewRequest;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.repository.compliance.
        AuthorityProcedureRepository;
import com.equitransit.backend.repository.compliance.ComplianceAuditRepository;
import com.equitransit.backend.repository.compliance.
        ComplianceClearanceRepository;
import com.equitransit.backend.repository.compliance.ComplianceRuleRepository;
import com.equitransit.backend.repository.compliance.
        DocumentRequirementRepository;
import com.equitransit.backend.repository.compliance.DocumentReviewRepository;
import com.equitransit.backend.repository.compliance.
        DocumentSubmissionRepository;
import com.equitransit.backend.repository.compliance.
        DocumentTypeCatalogRepository;
import com.equitransit.backend.repository.compliance.DocumentVersionRepository;
import com.equitransit.backend.repository.compliance.HorseDocumentRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.assessment.CaseAssignmentAccess;
import com.equitransit.backend.service.compliance.ComplianceService;
import com.equitransit.backend.service.compliance.ComplianceRevisionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Executes F08 flows with real JDBC adapters and isolated synthetic data. */
class ComplianceCompletionTest {
    @TempDir(factory = ComplianceTestDirectoryFactory.class)
    protected Path storage;
    protected JdbcTemplate sql;
    protected ComplianceService service;
    protected ComplianceRevisionService revisions;
    protected DocumentRequirementRepository requirements;
    protected CurrentActorService actors;
    protected CaseAssignmentAccess cases;
    protected TransactionTemplate tx;

    @BeforeEach
    void setup() {
        var source = database();
        sql = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        var named = new NamedParameterJdbcTemplate(source);
        actors = mock(CurrentActorService.class);
        customer();
        cases = caseAccess(named);
        requirements = new DocumentRequirementRepository(named);
        var submissions = new DocumentSubmissionRepository(named);
        revisions = new ComplianceRevisionService(named, requirements,
                submissions);
        service = new ComplianceService(
                new DocumentTypeCatalogRepository(named),
                new HorseDocumentRepository(named),
                new DocumentVersionRepository(named), requirements, submissions,
                new DocumentReviewRepository(named),
                new AuthorityProcedureRepository(named),
                new ComplianceClearanceRepository(named), cases, actors,
                new ComplianceAuditRepository(named),
                new ComplianceRuleRepository(named), named);
        service.configureStorageRoot(storage.toString());
        service.setRevisionService(revisions);
        seedData();
        addRevision(1, "FR");
        sql.update("INSERT INTO dbo.ComplianceRuleVersion"
                + " (RuleCode,VersionNo,OriginCountryCode,"
                        + "DestinationCountryCode,"
                + "DocumentType,PhaseCode,SourceURL,ReviewedAt,EffectiveFrom)"
                + " VALUES ('SYNTHETIC',1,'VN','FR','PASSPORT','PRE_EXPORT',"
                + "'https://example.invalid/fixture',?,?)", now(),
                now().minusDays(10));
    }

    @AfterEach
    void close() {
        closeDatabase();
    }

    @Test
    void unknownPolicyBlocksAndReviewedPolicyCreatesChecklistOnce() {
        assertThat(tx.<Boolean>execute(
                s -> service.readiness(1).legalGatePass()))
                .isFalse();
        assertThat(requirements.countByRevisionId(1)).isZero();
        policy();
        tx.executeWithoutResult(s -> service.listRequirements(1, 0, 20));
        tx.executeWithoutResult(s -> service.listRequirements(1, 0, 20));
        assertThat(requirements.countByRevisionId(1)).isEqualTo(1);
        assertThat(revisions.phaseCovered(1, "PRE_EXPORT")).isTrue();
        assertThat(revisions.phaseCovered(1, "ARRIVAL")).isFalse();
    }

    @Test
    void changedRouteDoesNotCopyOldClearanceOrRequirements() {
        policy();
        tx.executeWithoutResult(s -> service.listRequirements(1, 0, 20));
        sql.update("INSERT INTO dbo.ComplianceClearance"
                + " (RevisionID,SpecialistID,PhaseCode,Status,"
                        + "CheckedAt,ExpiresAt)"
                + " VALUES (1,1,'PRE_EXPORT','PASS',?,?)", now(),
                now().plusDays(20));
        addRevision(2, "DE");
        sql.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=2");
        assertThat(tx.<Boolean>execute(
                s -> service.readiness(1).legalGatePass()))
                .isFalse();
        assertThat(requirements.countByRevisionId(1)).isEqualTo(1);
        assertThat(requirements.countByRevisionId(2)).isZero();
    }

    @Test
    void validEvidenceIsReattachedButNewRevisionStillNeedsReview() {
        policy();
        long requirement = requirement();
        long version = upload(LocalDate.now(ZoneOffset.UTC).plusDays(40));
        long submission = tx.execute(s -> service.submitDocument(1, requirement,
                new DocumentSubmissionRequest(version)).submissionId());
        specialist();
        tx.executeWithoutResult(s -> service.reviewSubmission(1, submission,
                new DocumentReviewRequest("APPROVED", null)));
        addRevision(2, "FR");
        sql.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=2");
        customer();
        tx.executeWithoutResult(s -> service.listRequirements(1, 0, 20));
        var fresh = requirements.findByRevisionId(2).getFirst();
        assertThat(fresh.getStatus()).isEqualTo("PENDING_REVIEW");
        var history = service.submissionHistory(1,
                fresh.getRequirementId(), 0, 20);
        assertThat(history.items()).hasSize(1);
        assertThat(history.items().getFirst().documentVersionId())
                .isEqualTo(version);
        assertThat(history.items().getFirst().decision()).isNull();
    }

    @Test
    void passportExpiringBeforeDeliveryCannotBeApprovedOrReused() {
        policy();
        long requirement = requirement();
        long version = upload(LocalDate.now(ZoneOffset.UTC).plusDays(9));
        long submission = tx.execute(s -> service.submitDocument(1, requirement,
                new DocumentSubmissionRequest(version)).submissionId());
        specialist();
        assertThatThrownBy(() -> tx.execute(s -> service.reviewSubmission(1,
                submission, new DocumentReviewRequest("APPROVED", null))))
                .hasMessageContaining("expired");
        addRevision(2, "FR");
        sql.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=2");
        customer();
        tx.executeWithoutResult(s -> service.listRequirements(1, 0, 20));
        assertThat(requirements.findByRevisionId(2).getFirst().getStatus())
                .isEqualTo("PENDING");
    }

    @Test
    void correctionFeedbackRemainsVisibleAcrossMoreThanOneHundredSubmissions() {
        policy();
        long requirement = requirement();
        long version = upload(LocalDate.now(ZoneOffset.UTC).plusDays(40));
        for (int i = 0; i < 105; i++) {
            customer();
            long submission = tx.execute(s -> service.submitDocument(1,
                    requirement, new DocumentSubmissionRequest(version))
                    .submissionId());
            specialist();
            tx.executeWithoutResult(s -> service.reviewSubmission(1, submission,
                    new DocumentReviewRequest("CHANGES_REQUESTED",
                            "Please upload a readable scan")));
        }
        customer();
        var page = tx.execute(s -> service.submissionHistory(1, requirement,
                1, 100));
        assertThat(page.totalElements()).isEqualTo(105);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).hasSize(5).allSatisfy(row ->
                assertThat(row.feedback()).contains("readable scan"));
        when(actors.current()).thenReturn(new CurrentActor(9L,
                "CUSTOMER",
                9L, null));
        assertThatThrownBy(() -> service.submissionHistory(1, requirement,
                0, 20))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void metadataReplayReturnsOriginalVersionAndRejectsChangedDates() {
        var stored = service.storeDocumentFile(1, "passport.pdf",
                "application/pdf", bytes());
        var expiry = LocalDate.now(ZoneOffset.UTC).plusDays(40);
        var first = tx.execute(s -> service.uploadDocumentVersion(1, "PASSPORT",
                stored.fileKey(), "passport.pdf", "application/pdf",
                stored.fileSizeBytes(), "P1", null, expiry, stored.fileHash()));
        var replay = tx.execute(s -> service.uploadDocumentVersion(
                1, "PASSPORT",
                
                stored.fileKey(), "passport.pdf", "application/pdf",
                stored.fileSizeBytes(), "P1", null, expiry, stored.fileHash()));
        assertThat(replay.documentVersionId())
                .isEqualTo(first.documentVersionId());
        assertThatThrownBy(() -> tx.execute(s -> service.uploadDocumentVersion(
                1,
                "PASSPORT", stored.fileKey(), "passport.pdf", "application/pdf",
                stored.fileSizeBytes(), "P1", null, expiry.plusDays(1),
                stored.fileHash()))).hasMessageContaining("other metadata");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.DocumentVersion",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void compliantFlagWithoutApprovedEvidenceStillBlocks() {
        policy();
        long requirement = requirement();
        requirements.updateStatus(requirement, "COMPLIANT");
        assertThat(requirements.hasMandatoryBlocker(1, "PRE_EXPORT")).isTrue();
    }

    @Test
    void expiredOwnQuarantineCannotFallBackToAnOlderConfirmedResult() {
        sql.update("INSERT INTO dbo.RevisionQuarantineAssessment"
                + " (RevisionID,Applicability,SpecialistID,"
                        + "AssessedAt,ValidUntil,SourceReference)"
                + " VALUES (1,'NOT_REQUIRED',1,?,?,'fixture "
                        + "source')", now().minusDays(5),
                now().plusDays(20));
        sql.update("INSERT INTO dbo.QuarantineAssessment"
                + " (RevisionID,Applicability,SpecialistID,"
                        + "AssessedAt,ValidUntil,SourceReference)"
                + " VALUES (1,'NOT_REQUIRED',1,?,?,'fixture "
                        + "source')", now().minusDays(2),
                now().minusDays(1));
        var adapter = new ComplianceClearanceRepository(
                new NamedParameterJdbcTemplate(sql));
        assertThat(adapter.findLatestQuarantine(1).orElseThrow()
                .getApplicability()).isEqualTo("PENDING");
    }

    /** Creates a disposable H2 fixture; native tests override this.
     * @return configured isolated datasource
     */
    protected DriverManagerDataSource database() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:f08_completion_"
                + UUID.randomUUID() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
                "sa", "");
        new ResourceDatabasePopulator(
                new ClassPathResource("compliance-jdbc-schema.sql"),
                new ClassPathResource("compliance-completion-schema.sql"))
                .execute(source);
        return source;
    }

    /** Builds the case boundary used by this fixture.
     * @param named SQL adapter
     * @return case access adapter
     */
    protected CaseAssignmentAccess caseAccess(
            NamedParameterJdbcTemplate named) {
        var cases = mock(CaseAssignmentAccess.class);
        when(cases.lockRevision(1, CaseRole.SPECIALIST)).thenAnswer(call ->
                sql.queryForObject("SELECT CurrentRevisionID"
                        + " FROM dbo.TransportOrder WHERE "
                                + "OrderID=1", Long.class));
        return cases;
    }

    /** Seeds identities and order data in the disposable H2 database. */
    protected void seedData() {
        sql.update("INSERT INTO dbo.Horse VALUES (1,1)");
        sql.update("INSERT INTO dbo.TransportOrder VALUES (1,1,1)");
        sql.update("INSERT INTO dbo.TransportOrderDetail VALUES (1,1)");
        sql.update("INSERT INTO dbo.DocumentTypeCatalog"
                + " VALUES ('PASSPORT','Passport',NULL,1)");
    }

    /** Closes only the disposable H2 database. */
    protected void closeDatabase() {
        if (sql != null) {
            sql.execute("SHUTDOWN");
        }
    }

    /** @return generated current requirement */
    protected long requirement() {
        tx.executeWithoutResult(s -> service.listRequirements(1, 0, 20));
        return requirements.findByRevisionId(1).getFirst().getRequirementId();
    }

    /** Registers the explicit synthetic passport policy. */
    protected void policy() {
        tx.executeWithoutResult(s -> revisions.savePolicy(1, 1, 1,
                new ComplianceRulePolicyRequest("HORSE", true, "DELIVERY",
                        "Synthetic reviewed fixture")));
    }

    /** @param expiry validity date
     * @return uploaded version
     */
    protected long upload(LocalDate expiry) {
        var file = service.storeDocumentFile(1, "passport.pdf",
                "application/pdf",
                
                bytes());
        return tx.execute(s -> service.uploadDocumentVersion(1, "PASSPORT",
                file.fileKey(), "passport.pdf", file.contentType(),
                file.fileSizeBytes(), null, null, expiry, file.fileHash())
                .documentVersionId());
    }

    protected void addRevision(long id, String country) {
        sql.update("INSERT INTO dbo.OrderRevision VALUES (?,1)", id);
        sql.update("INSERT INTO dbo.RevisionRouteSource VALUES"
                + " (?,1,'VN',?,1,?,?)", id, country, now().plusDays(7),
                now().plusDays(12));
    }

    protected void customer() {
        when(actors.current()).thenReturn(new CurrentActor(1L, "CUSTOMER",
                1L, null));
    }

    protected void specialist() {
        when(actors.current()).thenReturn(new CurrentActor(2L, "SPECIALIST",
                null, 1L));
    }

    protected static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static byte[] bytes() {
        return "%PDF-1.7 synthetic compliance fixture"
                .getBytes(StandardCharsets.UTF_8);
    }
}
