/*
 * ComplianceExpiryWindowRegressionTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Regression test for departure window expiry and query failure.
 */
package com.equitransit.backend;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import com.equitransit.backend.repository.AuthorityProcedureRepository;
import com.equitransit.backend.repository.ComplianceAuditRepository;
import com.equitransit.backend.repository.ComplianceClearanceRepository;
import com.equitransit.backend.repository.ComplianceRuleRepository;
import com.equitransit.backend.repository.DocumentRequirementRepository;
import com.equitransit.backend.repository.DocumentReviewRepository;
import com.equitransit.backend.repository.DocumentSubmissionRepository;
import com.equitransit.backend.repository.DocumentTypeCatalogRepository;
import com.equitransit.backend.repository.DocumentVersionRepository;
import com.equitransit.backend.repository.HorseDocumentRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.CaseAssignmentAccess;
import com.equitransit.backend.service.ComplianceService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Review-copy regression for evidence expiring AFTER an earlier approval.
 */
class ComplianceExpiryWindowRegressionTest {

    private JdbcTemplate sql;
    private ComplianceService service;

    /** Sets up ephemeral in-memory database and compliance service. */
    @BeforeEach
    void schema() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:f08_gate_" + UUID.randomUUID()
                        + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(
                new ClassPathResource("compliance-jdbc-schema.sql"))
                        .execute(source);
        sql = new JdbcTemplate(source);
        var named = new NamedParameterJdbcTemplate(source);
        var actors = mock(CurrentActorService.class);
        when(actors.current()).thenReturn(
                new CurrentActor(1L, "CUSTOMER", 4L, null));
        service = new ComplianceService(
                new DocumentTypeCatalogRepository(named),
                new HorseDocumentRepository(named),
                new DocumentVersionRepository(named),
                new DocumentRequirementRepository(named),
                new DocumentSubmissionRepository(named),
                new DocumentReviewRepository(named),
                new AuthorityProcedureRepository(named),
                new ComplianceClearanceRepository(named),
                mock(CaseAssignmentAccess.class),
                actors,
                new ComplianceAuditRepository(named),
                new ComplianceRuleRepository(named),
                named);
    }

    /** Shuts down the ephemeral in-memory database. */
    @AfterEach
    void close() {
        sql.execute("SHUTDOWN");
    }

    /**
     * Seeds an approved case fixture with specified passport expiry date.
     * @param expiry the passport expiry date
     */
    private void approvedCaseWithPassportExpiry(LocalDate expiry) {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var approvedAt = Timestamp.valueOf(now.minusDays(3));
        var future = Timestamp.valueOf(now.plusDays(15));
        sql.update("INSERT INTO dbo.Horse VALUES (9,4)");
        sql.update("INSERT INTO dbo.TransportOrder VALUES (20,4,30)");
        sql.update("INSERT INTO dbo.TransportOrderDetail VALUES (20,9)");
        sql.execute("CREATE TABLE dbo.RevisionRouteSource ("
                + "RevisionID BIGINT PRIMARY KEY, "
                + "OrderID BIGINT, PreferredDepartureAt DATETIME2(0))");
        sql.update("INSERT INTO dbo.RevisionRouteSource VALUES (30,20,?)",
                Timestamp.valueOf(now.plusDays(7)));
        sql.update("INSERT INTO dbo.ComplianceRuleVersion"
                + " (RuleID,RuleCode,VersionNo,OriginCountryCode,"
                + "DestinationCountryCode,DocumentType,PhaseCode,SourceURL,"
                + "ReviewedAt,EffectiveFrom)"
                + " VALUES (1,'TEST_PASSPORT',1,'VN','FR','PASSPORT',"
                + "'PRE_EXPORT','https://example.invalid/test-rule',?,?)",
                approvedAt, Timestamp.valueOf(now.minusDays(30)));
        var stored = service.storeDocumentFile(9L, "passport.pdf",
                "application/pdf",
                "%PDF-1.5 synthetic passport bytes"
                        .getBytes(StandardCharsets.UTF_8));
        sql.update("INSERT INTO dbo.HorseDocument"
                + " (DocumentID,HorseID,DocumentType,CreatedAt)"
                + " VALUES (10,9,'PASSPORT',?)", approvedAt);
        sql.update("INSERT INTO dbo.DocumentVersion"
                + " (DocumentVersionID,DocumentID,VersionNo,FileKey,FileHash,"
                + "IssuedDate,ExpiryDate,UploadedAt,UploadedBy,ContentType,"
                + "FileSizeBytes)"
                + " VALUES (11,10,1,?,?,?,?,?,1,'application/pdf',?)",
                stored.fileKey(), stored.fileHash(),
                LocalDate.now(ZoneOffset.UTC).minusDays(30),
                expiry, approvedAt, stored.fileSizeBytes());
        sql.update("INSERT INTO dbo.DocumentRequirement"
                + " (RequirementID,RevisionID,RuleID,DocumentType,PhaseCode,"
                + "IsMandatory,Status,ScopeCode,HorseID)"
                + " VALUES (100,30,1,'PASSPORT','PRE_EXPORT',1,"
                + "'COMPLIANT','HORSE',9)");
        sql.update("INSERT INTO dbo.DocumentSubmission"
                + " (SubmissionID,RequirementID,DocumentVersionID,"
                + "Status,SubmittedAt)"
                + " VALUES (101,100,11,'SUBMITTED',?)", approvedAt);
        sql.update("INSERT INTO dbo.DocumentReview"
                + " (ReviewID,SubmissionID,ReviewedBy,Decision,ReviewedAt)"
                + " VALUES (102,101,2,'APPROVED',?)", approvedAt);
        sql.update("INSERT INTO dbo.ComplianceClearance"
                + " (ClearanceID,RevisionID,SpecialistID,PhaseCode,Status,"
                + "CheckedAt,ExpiresAt)"
                + " VALUES (103,30,2,'PRE_EXPORT','PASS',?,?)",
                approvedAt, future);
        sql.update("INSERT INTO dbo.RevisionQuarantineAssessment"
                + " (RevisionID,Applicability,RequiredDays,SpecialistID,"
                + "AssessedAt,ValidUntil,SourceReference)"
                + " VALUES (30,'NOT_REQUIRED',NULL,2,?,?,'TEST SOURCE')",
                approvedAt, future);
    }

    /** Unexpired approved passport keeps legal gate open. */
    @Test
    void unexpiredApprovedPassportKeepsLegalGateOpen() {
        approvedCaseWithPassportExpiry(
                LocalDate.now(ZoneOffset.UTC).plusDays(30));
        assertThat(service.readiness(20L).legalGatePass()).isTrue();
    }

    /** Passport expiring after approval must close gate when expired. */
    @Test
    void passportExpiringAfterApprovalMustCloseGateWhenExpired() {
        approvedCaseWithPassportExpiry(
                LocalDate.now(ZoneOffset.UTC).minusDays(1));
        assertThat(sql.queryForObject(
                "SELECT COUNT(*) FROM dbo.DocumentVersion"
                        + " WHERE ExpiryDate < CURRENT_DATE",
                Integer.class)).isEqualTo(1);
        assertThat(service.readiness(20L).legalGatePass()).isFalse();
    }

    /** Passport valid today but expiring before departure must block. */
    @Test
    void passportStillValidTodayButExpiredBeforeDepartureMustBlock() {
        approvedCaseWithPassportExpiry(
                LocalDate.now(ZoneOffset.UTC).plusDays(2));
        assertThat(service.readiness(20L).legalGatePass()).isFalse();
    }

    /** Newly approved replacement must not be blocked by old version. */
    @Test
    void newlyApprovedValidReplacementMustNotBeBlockedByOldExpiredVersion() {
        approvedCaseWithPassportExpiry(
                LocalDate.now(ZoneOffset.UTC).minusDays(1));
        sql.update("INSERT INTO dbo.DocumentVersion"
                + " (DocumentVersionID,DocumentID,VersionNo,FileKey,FileHash,"
                + "IssuedDate,ExpiryDate,UploadedAt,UploadedBy,ContentType,"
                + "FileSizeBytes)"
                + " SELECT 12,DocumentID,2,FileKey,FileHash,IssuedDate,?,"
                + "CURRENT_TIMESTAMP,UploadedBy,ContentType,FileSizeBytes"
                + " FROM dbo.DocumentVersion WHERE DocumentVersionID=11",
                LocalDate.now(ZoneOffset.UTC).plusDays(30));
        sql.update("INSERT INTO dbo.DocumentSubmission"
                + " (SubmissionID,RequirementID,DocumentVersionID,"
                + "Status,SubmittedAt)"
                + " VALUES (201,100,12,'SUBMITTED',CURRENT_TIMESTAMP)");
        sql.update("INSERT INTO dbo.DocumentReview"
                + " (ReviewID,SubmissionID,ReviewedBy,Decision,ReviewedAt)"
                + " VALUES (202,201,2,'APPROVED',CURRENT_TIMESTAMP)");
        assertThat(service.readiness(20L).legalGatePass()).isTrue();
    }

    /** Query failure must fail-closed and not open a blocked gate. */
    @Test
    void departureQueryFailureMustNotOpenAnOtherwiseBlockedGate() {
        approvedCaseWithPassportExpiry(
                LocalDate.now(ZoneOffset.UTC).plusDays(2));
        assertThat(service.readiness(20L).legalGatePass()).isFalse();
        // Model an incomplete migration. Dates cannot be read, but evidence
        // still expires before the previously known seven-day departure.
        sql.execute("ALTER TABLE dbo.RevisionRouteSource"
                + " DROP COLUMN PreferredDepartureAt");
        try {
            assertThat(service.readiness(20L).legalGatePass()).isFalse();
        } catch (DataAccessException expected) {
            // Propagating a database failure is also fail-closed: no READY.
        }
    }
}
