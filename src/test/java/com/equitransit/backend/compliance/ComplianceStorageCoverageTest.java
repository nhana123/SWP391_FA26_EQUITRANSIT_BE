/*
 * ComplianceStorageCoverageTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verification of verified file storage proof and phase coverage.
 */
package com.equitransit.backend.compliance;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.dto.compliance.request.
        ComplianceClearanceRequest;
import com.equitransit.backend.dto.compliance.response.
        ComplianceClearanceResponse;
import com.equitransit.backend.dto.compliance.response.DocumentVersionResponse;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.compliance.ComplianceClearance;
import com.equitransit.backend.entity.compliance.DocumentRequirement;
import com.equitransit.backend.entity.compliance.DocumentTypeCatalog;
import com.equitransit.backend.entity.compliance.DocumentVersion;
import com.equitransit.backend.entity.compliance.HorseDocument;
import com.equitransit.backend.entity.compliance.QuarantineAssessment;
import com.equitransit.backend.exception.compliance.ComplianceException;
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
import com.equitransit.backend.service.compliance.ComplianceService.
        VerifiedStorageFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Copy-only verification of the original file-proof and phase-coverage
 * requirements with Checkstyle compliance.
 */
public class ComplianceStorageCoverageTest {

    private DocumentTypeCatalogRepository catalog;
    private HorseDocumentRepository documents;
    private DocumentVersionRepository versions;
    private DocumentRequirementRepository requirements;
    private ComplianceClearanceRepository clearances;
    private CurrentActorService actors;
    private ComplianceService service;

    /** Sets up mock dependencies and initialized ComplianceService. */
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
        service = new ComplianceService(catalog, documents, versions,
                requirements, mock(DocumentSubmissionRepository.class),
                mock(DocumentReviewRepository.class),
                mock(AuthorityProcedureRepository.class), clearances,
                access, actors, mock(ComplianceAuditRepository.class),
                mock(ComplianceRuleRepository.class),
                mock(NamedParameterJdbcTemplate.class));
    }

    /**
     * Neither key was issued by a storage adapter, and no bytes exist;
     * metadata alone must not be accepted without verified storage proof.
     *
     * @param key the unverified storage key probe
     */
    @ParameterizedTest
    @ValueSource(strings = {"docs/8cf052b468.pdf",
            "bucket/horse-passport-2048.pdf"})
    public void formattedKeyWithoutUploadedBytesMustNotProveAFile(String key) {
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

        // Neither key was issued by a storage adapter, and no bytes exist.
        assertThatThrownBy(() -> service.uploadDocumentVersion(9L, "PASSPORT",
                key, "passport.pdf", "application/pdf", 123L, null, null,
                null, "a".repeat(64)))
                .isInstanceOf(ComplianceException.class);
    }

    /**
     * Checklist containing requirements only for another phase must not
     * permit PASS clearance for a phase with no assessed rules or evidence.
     */
    @Test
    public void anotherPhaseChecklistMustNotProveReleaseRuleCoverage() {
        var existing = new DocumentRequirement();
        existing.setRequirementId(1L);
        existing.setRevisionId(30L);
        existing.setPhaseCode("PRE_EXPORT");
        existing.setMandatory(true);
        existing.setStatus("COMPLIANT");
        when(requirements.countByRevisionId(30L)).thenReturn(1L);
        when(requirements.findByRevisionId(30L)).thenReturn(List.of(existing));
        when(requirements.hasMandatoryBlocker(30L, "QUARANTINE_RELEASE"))
                .thenReturn(false);
        var saved = new ComplianceClearance();
        saved.setClearanceId(11L);
        saved.setRevisionId(30L);
        when(clearances.insert(any())).thenReturn(11L);
        when(clearances.findById(11L)).thenReturn(Optional.of(saved));

        // No release rules, release evidence, or sourced exemption
        // were assessed.
        assertThatThrownBy(() -> service.issueClearance(20L,
                new ComplianceClearanceRequest("QUARANTINE_RELEASE", "PASS",
                        null, null)))
                .isInstanceOf(ComplianceException.class);
    }

    /** Positive test: verified uploaded file bytes permit document creation. */
    @Test
    public void uploadedVerifiedBytesMustPermitVersionCreationAndDownload() {
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
        when(documents.findById(10L)).thenReturn(Optional.of(doc));
        var saved = new DocumentVersion();
        saved.setDocumentVersionId(11L);
        saved.setDocumentId(10L);
        when(versions.insert(any())).thenReturn(11L);
        when(versions.findById(11L)).thenReturn(Optional.of(saved));

        byte[] validPdf = "%PDF-1.5 test content payload bytes"
                .getBytes(StandardCharsets.UTF_8);
        VerifiedStorageFile stored = service.storeDocumentFile(9L,
                "valid-passport.pdf", "application/pdf", validPdf);
        assertThat(stored.fileKey()).startsWith("compliance/h9/");
        assertThat(stored.fileSizeBytes()).isEqualTo(validPdf.length);

        DocumentVersionResponse res = service.uploadDocumentVersion(9L,
                "PASSPORT", stored.fileKey(), stored.originalFileName(),
                stored.contentType(), stored.fileSizeBytes(), null, null,
                null, stored.fileHash());
        assertThat(res).isNotNull();
        saved.setFileKey(stored.fileKey());

        byte[] downloaded = service.getDocumentVersionFile(9L, 11L);
        assertThat(downloaded).isEqualTo(validPdf);
    }

    /** Positive test: quarantine release clearance allowed when exempted. */
    @Test
    public void quarantineReleasePassAllowedWhenExemptionIsAssessed() {
        var qa = new QuarantineAssessment();
        qa.setApplicability("NOT_REQUIRED");
        qa.setRequiredDays(null);
        when(clearances.findLatestQuarantine(30L)).thenReturn(Optional.of(qa));
        when(requirements.countByRevisionId(30L)).thenReturn(0L);
        when(requirements.findByRevisionId(30L)).thenReturn(List.of());
        when(requirements.hasMandatoryBlocker(30L, "QUARANTINE_RELEASE"))
                .thenReturn(false);
        var saved = new ComplianceClearance();
        saved.setClearanceId(11L);
        saved.setRevisionId(30L);
        saved.setPhaseCode("QUARANTINE_RELEASE");
        saved.setStatus("PASS");
        when(clearances.insert(any())).thenReturn(11L);
        when(clearances.findById(11L)).thenReturn(Optional.of(saved));

        ComplianceClearanceResponse res = service.issueClearance(20L,
                new ComplianceClearanceRequest("QUARANTINE_RELEASE", "PASS",
                        OffsetDateTime.now(ZoneOffset.UTC).plusDays(2),
                        "Exempted based on bilateral protocol."));
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("PASS");
        assertThat(res.phaseCode()).isEqualTo("QUARANTINE_RELEASE");
    }

    /** Positive test: phase PASS allowed when requirements are compliant. */
    @Test
    public void phasePassAllowedWhenPhaseHasCompliantRequirement() {
        var req = new DocumentRequirement();
        req.setRequirementId(1L);
        req.setRevisionId(30L);
        req.setPhaseCode("PRE_EXPORT");
        req.setMandatory(true);
        req.setStatus("COMPLIANT");
        when(requirements.findByRevisionId(30L)).thenReturn(List.of(req));
        when(requirements.hasMandatoryBlocker(30L, "PRE_EXPORT"))
                .thenReturn(false);
        var saved = new ComplianceClearance();
        saved.setClearanceId(11L);
        saved.setRevisionId(30L);
        saved.setPhaseCode("PRE_EXPORT");
        saved.setStatus("PASS");
        when(clearances.insert(any())).thenReturn(11L);
        when(clearances.findById(11L)).thenReturn(Optional.of(saved));

        ComplianceClearanceResponse res = service.issueClearance(20L,
                new ComplianceClearanceRequest("PRE_EXPORT", "PASS",
                        OffsetDateTime.now(ZoneOffset.UTC).plusDays(2),
                        "All pre-export checks verified."));
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("PASS");
    }
}
