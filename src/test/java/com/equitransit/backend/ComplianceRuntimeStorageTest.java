/*
 * ComplianceRuntimeStorageTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Diagnostic verification of persistent storage, authorization,
 *     immutability, and authority procedure clearance requirements.
 */
package com.equitransit.backend;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.dto.request.ComplianceClearanceRequest;
import com.equitransit.backend.entity.AuthorityProcedure;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Diagnostic runtime storage, authorization, immutability, and clearance
 * coverage tests aligned with Review 4 findings.
 */
class ComplianceRuntimeStorageTest {

    private final DocumentTypeCatalogRepository catalog =
            mock(DocumentTypeCatalogRepository.class);
    private final HorseDocumentRepository documents =
            mock(HorseDocumentRepository.class);
    private final DocumentVersionRepository versions =
            mock(DocumentVersionRepository.class);
    private final DocumentRequirementRepository requirements =
            mock(DocumentRequirementRepository.class);
    private final AuthorityProcedureRepository procedures =
            mock(AuthorityProcedureRepository.class);
    private final ComplianceClearanceRepository clearances =
            mock(ComplianceClearanceRepository.class);
    private final CurrentActorService actors =
            mock(CurrentActorService.class);
    private final CaseAssignmentAccess access =
            mock(CaseAssignmentAccess.class);
    private ComplianceService service;
    private DocumentVersion version;

    private ComplianceService newService() {
        return new ComplianceService(catalog, documents, versions, requirements,
                mock(DocumentSubmissionRepository.class),
                mock(DocumentReviewRepository.class),
                procedures, clearances, access, actors,
                mock(ComplianceAuditRepository.class),
                mock(ComplianceRuleRepository.class),
                mock(NamedParameterJdbcTemplate.class));
    }

    @BeforeEach
    void setup() {
        when(actors.current()).thenReturn(
                new CurrentActor(1L, "CUSTOMER", 4L, null));
        var type = new DocumentTypeCatalog();
        type.setTypeCode("PASSPORT");
        type.setActive(true);
        when(catalog.listActive()).thenReturn(List.of(type));
        var document = new HorseDocument();
        document.setDocumentId(10L);
        document.setHorseId(9L);
        when(documents.findByHorseIdAndType(9L, "PASSPORT"))
                .thenReturn(Optional.of(document));
        when(documents.findById(10L)).thenReturn(Optional.of(document));
        version = new DocumentVersion();
        version.setDocumentVersionId(11L);
        version.setDocumentId(10L);
        when(versions.insert(any())).thenReturn(11L);
        when(versions.findById(11L)).thenReturn(Optional.of(version));
        service = newService();
    }

    private void upload(byte[] bytes) {
        var stored = service.storeDocumentFile(
                9L, "passport.pdf", "application/pdf", bytes);
        service.uploadDocumentVersion(9L, "PASSPORT", stored.fileKey(),
                null, null, null, null, null, null, null);
        // Model persisted metadata surviving application service lifetime.
        version.setFileKey(stored.fileKey());
    }

    /** Owning another horse must not authorize access to victim's version. */
    @Test
    void owningAnotherHorseMustNotAuthorizeVictimsVersion() {
        upload("%PDF-1.5 private victim passport"
                .getBytes(StandardCharsets.UTF_8));
        when(actors.current()).thenReturn(
                new CurrentActor(2L, "CUSTOMER", 5L, null));
        doThrow(new ComplianceException(HttpStatus.NOT_FOUND,
                "NOT_FOUND", "Not owned"))
                .when(documents).verifyOwnership(9L, 5L);
        doThrow(new ComplianceException(HttpStatus.NOT_FOUND,
                "NOT_FOUND", "Not owned"))
                .when(documents).verifyDocumentOwnership(10L, 5L);
        // Customer 5 owns horse 99, but version 11 belongs to horse 9/cust 4.
        assertThatThrownBy(() -> service.getDocumentVersionFile(99L, 11L))
                .isInstanceOf(ComplianceException.class);
    }

    /** Uploaded content must survive across newly instantiated services. */
    @Test
    void uploadedContentMustSurviveNewServiceInstance() {
        byte[] bytes = "%PDF-1.5 persistent passport"
                .getBytes(StandardCharsets.UTF_8);
        upload(bytes);
        ComplianceService restartedService = newService();
        assertThatCode(() -> assertThat(
                restartedService.getDocumentVersionFile(9L, 11L))
                .isEqualTo(bytes)).doesNotThrowAnyException();
    }

    /** Mutating caller buffer after upload must not alter stored bytes. */
    @Test
    void changingCallerBufferMustNotAlterStoredEvidence() {
        byte[] bytes = "%PDF-1.5 immutable passport"
                .getBytes(StandardCharsets.UTF_8);
        byte[] original = bytes.clone();
        upload(bytes);
        bytes[10] = (byte) 'X';
        assertThat(service.getDocumentVersionFile(9L, 11L)).isEqualTo(original);
    }

    /** Pending authority procedure without documents must not permit PASS. */
    @Test
    void pendingProcedureWithoutDocumentsMustNotPermitPass() {
        when(actors.current()).thenReturn(
                new CurrentActor(1L, "SPECIALIST", null, 2L));
        when(access.lockRevision(20L, CaseRole.SPECIALIST)).thenReturn(30L);
        when(requirements.findByRevisionId(30L)).thenReturn(List.of());
        var procedure = new AuthorityProcedure();
        procedure.setPhaseCode("ARRIVAL");
        procedure.setApprovalStatus("PENDING");
        when(procedures.findByRevisionId(30L)).thenReturn(List.of(procedure));
        var saved = new ComplianceClearance();
        saved.setClearanceId(12L);
        saved.setRevisionId(30L);
        saved.setPhaseCode("ARRIVAL");
        saved.setStatus("PASS");
        when(clearances.insert(any())).thenReturn(12L);
        when(clearances.findById(12L)).thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> service.issueClearance(20L,
                new ComplianceClearanceRequest("ARRIVAL", "PASS", null, null)))
                .isInstanceOf(ComplianceException.class);
    }
}
