/*
 * ComplianceRemainingGapsTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify resolution of remaining F08 gaps identified by review.
 */
package com.equitransit.backend;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.equitransit.backend.dto.request.ComplianceClearanceRequest;
import com.equitransit.backend.dto.request.QuarantineAssessmentRequest;
import com.equitransit.backend.entity.CaseRole;
import com.equitransit.backend.entity.ComplianceClearance;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validates the resolution of the five specific gaps:
 * 1. Rejection of unverified client storage keys
 * 2. Rejection of PASS clearance when checklist is empty
 * 3. Expired canonical quarantine assessments excluded from active queries
 * 4. Storing ValidUntil as exact UTC instant
 * 5. Preserving canonical constraint errors without weaker fallback
 */
public class ComplianceRemainingGapsTest {

    private DocumentTypeCatalogRepository catalog;
    private HorseDocumentRepository documents;
    private DocumentVersionRepository versions;
    private DocumentRequirementRepository requirements;
    private ComplianceClearanceRepository clearances;
    private CurrentActorService actors;
    private NamedParameterJdbcTemplate jdbc;
    private ComplianceService service;

    /** Sets up mock dependencies before each test. */
    @BeforeEach
    public void setup() {
        catalog = mock(DocumentTypeCatalogRepository.class);
        documents = mock(HorseDocumentRepository.class);
        versions = mock(DocumentVersionRepository.class);
        requirements = mock(DocumentRequirementRepository.class);
        clearances = mock(ComplianceClearanceRepository.class);
        actors = mock(CurrentActorService.class);
        when(actors.current()).thenReturn(
                new CurrentActor(1L, "SPECIALIST", null, 2L));
        var access = mock(CaseAssignmentAccess.class);
        when(access.lockRevision(20L, CaseRole.SPECIALIST)).thenReturn(30L);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new ComplianceService(
                catalog, documents, versions, requirements,
                mock(DocumentSubmissionRepository.class),
                mock(DocumentReviewRepository.class),
                mock(AuthorityProcedureRepository.class),
                clearances, access, actors,
                mock(ComplianceAuditRepository.class),
                mock(ComplianceRuleRepository.class), jdbc);
    }

    /** Well-formed metadata with invented client key rejects version. */
    @Test
    public void wellFormedMetadataWithoutVerifiedFileMustNotCreateVersion() {
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
                "invented-opaque-client-key", "fake.pdf", "application/pdf",
                123L, null, null, null, "a".repeat(64)))
                .isInstanceOf(ComplianceException.class);
    }

    /** Empty checklist without assessed rule coverage must not permit PASS. */
    @Test
    public void emptyChecklistWithoutAssessedRuleCoverageMustNotPermitPass() {
        when(requirements.countByRevisionId(30L)).thenReturn(0L);
        when(requirements.hasMandatoryBlocker(30L, "PRE_EXPORT"))
                .thenReturn(false);
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

    /** Expired canonical quarantine assessments must not be returned active. */
    @Test
    public void expiredCanonicalQuarantineAssessmentMustNotBeReturnedActive() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:expired_" + UUID.randomUUID()
                + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "sa", "");
        var sql = new JdbcTemplate(source);
        sql.execute("CREATE SCHEMA dbo");
        sql.execute("CREATE TABLE dbo.RevisionQuarantineAssessment ("
                + "AssessmentID BIGINT, RevisionID BIGINT,"
                + "Applicability VARCHAR(20), RequiredDays INT,"
                + "AssessedAt TIMESTAMP, ValidUntil TIMESTAMP,"
                + "SourceReference VARCHAR(1000), SupersededAt TIMESTAMP)");
        sql.execute("CREATE TABLE dbo.QuarantineAssessment AS"
                + " SELECT * FROM dbo.RevisionQuarantineAssessment WHERE 1=0");
        sql.update("INSERT INTO dbo.RevisionQuarantineAssessment VALUES"
                + "(1,30,'NOT_REQUIRED',NULL,TIMESTAMP '2020-01-01 00:00:00',"
                + "TIMESTAMP '2021-01-01 00:00:00','TEST SOURCE',NULL)");
        var repository = new ComplianceClearanceRepository(
                new NamedParameterJdbcTemplate(source));
        try {
            assertThat(repository.findLatestQuarantine(30L)).isEmpty();
        } finally {
            sql.execute("SHUTDOWN");
        }
    }

    /** Assessment validity must be stored as exact UTC instant. */
    @Test
    public void assessmentValidityMustBeStoredAsUtcInstant() {
        var deadline = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2)
                .withOffsetSameInstant(ZoneOffset.ofHours(7));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class),
                eq(Long.class))).thenReturn(99L);

        service.recordQuarantineAssessment(20L,
                new QuarantineAssessmentRequest("REQUIRED", 14, 1L,
                        "TEST SOURCE", deadline, "test"));
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(
                contains("INSERT INTO dbo.RevisionQuarantineAssessment"),
                params.capture());
        assertThat(params.getValue().getValue("ValidUntil"))
                .isEqualTo(deadline.withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime());
    }

    /** Canonical constraint failure must preserve error without fallback. */
    @Test
    public void canonicalConstraintFailureMustNotFallBackToWeakerWrite() {
        doThrow(new DataIntegrityViolationException(
                "synthetic canonical constraint failure"))
                .when(jdbc).update(
                        startsWith("UPDATE dbo.RevisionQuarantineAssessment"),
                        any(SqlParameterSource.class));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class),
                eq(Long.class))).thenReturn(99L);

        assertThatThrownBy(() -> service.recordQuarantineAssessment(20L,
                new QuarantineAssessmentRequest("NOT_REQUIRED", null, 1L,
                        "TEST SOURCE",
                        OffsetDateTime.now(ZoneOffset.UTC).plusDays(2),
                        "test")))
                .isInstanceOf(RuntimeException.class);
    }
}
