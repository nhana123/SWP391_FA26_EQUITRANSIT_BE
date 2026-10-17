/*
 * ComplianceAcceptanceTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify acceptance criteria and business guards for FR-08.
 */
package com.equitransit.backend;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import com.equitransit.backend.dto.request.ComplianceClearanceRequest;
import com.equitransit.backend.dto.request.DocumentReviewRequest;
import com.equitransit.backend.dto.request.QuarantineAssessmentRequest;
import com.equitransit.backend.dto.response.DocumentRequirementPageResponse;
import com.equitransit.backend.entity.AuthorityProcedure;
import com.equitransit.backend.entity.CaseRole;
import com.equitransit.backend.entity.ComplianceClearance;
import com.equitransit.backend.entity.DocumentRequirement;
import com.equitransit.backend.entity.DocumentReview;
import com.equitransit.backend.entity.DocumentSubmission;
import com.equitransit.backend.entity.DocumentTypeCatalog;
import com.equitransit.backend.entity.DocumentVersion;
import com.equitransit.backend.entity.HorseDocument;
import com.equitransit.backend.exception.ComplianceException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Validates the 5 essential acceptance criteria for closing Feature #8:
 * upload metadata validation, clearance PASS blocker check, authority approval
 * evidence, document version expiry on review, and superseded clearance
 * invalidation.
 */
public class ComplianceAcceptanceTest {

    private DocumentTypeCatalogRepository catalog;
    private HorseDocumentRepository documents;
    private DocumentVersionRepository versions;
    private DocumentRequirementRepository requirements;
    private DocumentSubmissionRepository submissions;
    private DocumentReviewRepository reviews;
    private AuthorityProcedureRepository procedures;
    private ComplianceClearanceRepository clearances;
    private CurrentActorService actors;
    private ComplianceService service;

    /** Sets up mock dependencies before each test execution. */
    @BeforeEach
    public void setup() {
        catalog = mock(DocumentTypeCatalogRepository.class);
        documents = mock(HorseDocumentRepository.class);
        versions = mock(DocumentVersionRepository.class);
        requirements = mock(DocumentRequirementRepository.class);
        when(requirements.validityCutoff(any())).thenReturn(
                LocalDate.now(ZoneOffset.UTC));
        submissions = mock(DocumentSubmissionRepository.class);
        reviews = mock(DocumentReviewRepository.class);
        procedures = mock(AuthorityProcedureRepository.class);
        clearances = mock(ComplianceClearanceRepository.class);
        actors = mock(CurrentActorService.class);

        when(actors.current()).thenReturn(
                new CurrentActor(1L, "SPECIALIST", null, 2L));
        var access = mock(CaseAssignmentAccess.class);
        when(access.lockRevision(20L, CaseRole.SPECIALIST)).thenReturn(30L);
        service = new ComplianceService(
                catalog, documents, versions, requirements,
                submissions, reviews, procedures, clearances, access, actors,
                mock(ComplianceAuditRepository.class),
                mock(ComplianceRuleRepository.class),
                mock(NamedParameterJdbcTemplate.class));
    }

    /** Unverified client metadata (oversized, invalid hash) rejected. */
    @Test
    public void unverifiedClientMetadataMustNotCreateAnUpload() {
        when(actors.current()).thenReturn(
                new CurrentActor(1L, "CUSTOMER", 4L, null));
        var type = new DocumentTypeCatalog();
        type.setTypeCode("PASSPORT");
        type.setActive(true);
        when(catalog.listActive()).thenReturn(List.of(type));
        var doc = new HorseDocument();
        doc.setDocumentId(10L);
        doc.setHorseId(9L);
        when(documents.findByHorseIdAndType(9L, "PASSPORT"))
                .thenReturn(Optional.of(doc));
        var saved = new DocumentVersion();
        saved.setDocumentVersionId(11L);
        saved.setDocumentId(10L);
        when(versions.insert(any())).thenReturn(11L);
        when(versions.findById(11L)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.uploadDocumentVersion(9L, "PASSPORT",
                "unverified-client-key", "fake.pdf", "application/pdf",
                11L * 1024 * 1024, null, null, null, "not-a-sha256-hash"))
                .isInstanceOf(ComplianceException.class);
    }

    /** PASS must be rejected when mandatory blockers remain for the phase. */
    @Test
    public void passMustBeRejectedWhenMandatoryEvidenceIsMissing() {
        when(requirements.hasMandatoryBlocker(30L, "PRE_EXPORT"))
                .thenReturn(true);
        var saved = new ComplianceClearance();
        saved.setClearanceId(11L);
        saved.setRevisionId(30L);
        when(clearances.insert(any())).thenReturn(11L);
        when(clearances.findById(11L)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.issueClearance(20L,
                new ComplianceClearanceRequest("PRE_EXPORT", "PASS",
                        null, null)))
                .isInstanceOf(ComplianceException.class);
    }

    /** Authority approval requires application reference or evidence. */
    @Test
    public void authorityApprovalMustRequireReferenceAndDecisionEvidence() {
        var procedure = new AuthorityProcedure();
        procedure.setProcedureId(11L);
        procedure.setRevisionId(30L);
        procedure.setApprovalStatus("PENDING");
        when(procedures.findById(11L)).thenReturn(Optional.of(procedure));

        assertThatThrownBy(() -> service.updateProcedureStatus(
                20L, 11L, "APPROVED"))
                .isInstanceOf(ComplianceException.class);
    }

    /** Expired submitted document versions must not be approved. */
    @Test
    public void expiredSubmittedVersionMustNotBeApproved() {
        var requirement = new DocumentRequirement();
        requirement.setRequirementId(7L);
        requirement.setRevisionId(30L);
        var submission = new DocumentSubmission();
        submission.setSubmissionId(8L);
        submission.setRequirementId(7L);
        submission.setDocumentVersionId(9L);
        when(submissions.findById(8L)).thenReturn(Optional.of(submission));
        when(submissions.latestId(7L)).thenReturn(8L);
        when(requirements.findById(7L)).thenReturn(Optional.of(requirement));

        var expired = new DocumentVersion();
        expired.setDocumentVersionId(9L);
        expired.setExpiryDate(LocalDate.of(2020, 1, 1));
        when(versions.findById(9L)).thenReturn(Optional.of(expired));

        var review = new DocumentReview();
        review.setReviewId(11L);
        review.setSubmissionId(8L);
        when(reviews.insert(any())).thenReturn(11L);
        when(reviews.findById(11L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.reviewSubmission(20L, 8L,
                new DocumentReviewRequest("APPROVED", null)))
                .isInstanceOf(ComplianceException.class);
    }

    /** A newer FAIL clearance must supersede and invalidate an older PASS. */
    @Test
    public void newerFailMustInvalidateAnOlderPass() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:review_" + UUID.randomUUID()
                + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(
                new ClassPathResource("compliance-jdbc-schema.sql"))
                        .execute(source);
        var sql = new JdbcTemplate(source);
        sql.update("INSERT INTO dbo.ComplianceClearance"
                + " (RevisionID,SpecialistID,PhaseCode,Status,CheckedAt) "
                + "VALUES (30,2,'QUARANTINE_RELEASE','PASS',"
                + "TIMESTAMP '2026-01-01 00:00:00')");
        sql.update("INSERT INTO dbo.ComplianceClearance"
                + " (RevisionID,SpecialistID,PhaseCode,Status,CheckedAt) "
                + "VALUES (30,2,'QUARANTINE_RELEASE','FAIL',"
                + "CURRENT_TIMESTAMP)");
        var repository = new ComplianceClearanceRepository(
                new NamedParameterJdbcTemplate(source));
        assertThat(repository.hasValidPass(30L, "QUARANTINE_RELEASE"))
                .isFalse();
        sql.execute("SHUTDOWN");
    }

    /** Quarantine assessment for REQUIRED requires source and future date. */
    @Test
    public void quarantineAssessmentRequiresSourceReferenceAndValidUntil() {
        assertThatThrownBy(() -> service.recordQuarantineAssessment(20L,
                new QuarantineAssessmentRequest("REQUIRED", 14, 1L,
                        null, null, "notes")))
                .isInstanceOf(ComplianceException.class);

        assertThatThrownBy(() -> service.recordQuarantineAssessment(20L,
                new QuarantineAssessmentRequest("REQUIRED", 14, 1L,
                        "Legal Source",
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                        "notes")))
                .isInstanceOf(ComplianceException.class);
    }

    /** Requirements checklist returns bounded page response. */
    @Test
    public void checklistReturnsBoundedPageResponse() {
        when(requirements.countByRevisionId(30L)).thenReturn(42L);
        when(requirements.findByRevisionIdPaged(30L, 0, 10))
                .thenReturn(List.of());

        DocumentRequirementPageResponse response =
                service.listRequirementsPaged(20L, 0, 10);
        assertThat(response.totalElements()).isEqualTo(42L);
        assertThat(response.totalPages()).isEqualTo(5);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.items()).isEmpty();
    }
}
