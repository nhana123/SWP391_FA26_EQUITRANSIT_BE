/*
 * ComplianceServiceBoundaryTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Test FR-08 compliance service boundary invariants and gate logic.
 */
package com.equitransit.backend;


import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.entity.HorseDocument;
import com.equitransit.backend.entity.QuarantineAssessment;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


/** Checks ownership, validation and gate evaluation at the service boundary. */
public class ComplianceServiceBoundaryTest {

    private HorseDocumentRepository documents;
    private DocumentVersionRepository versions;
    private DocumentRequirementRepository requirements;
    private DocumentTypeCatalogRepository catalog;
    private ComplianceClearanceRepository clearances;
    private ComplianceService service;

    /** Uses explicitly synthetic identities only inside this unit test. */
    @BeforeEach
    public void setup() {
        documents = mock(HorseDocumentRepository.class);
        versions = mock(DocumentVersionRepository.class);
        requirements = mock(DocumentRequirementRepository.class);
        catalog = mock(DocumentTypeCatalogRepository.class);
        clearances = mock(ComplianceClearanceRepository.class);
        var actors = mock(CurrentActorService.class);

        when(actors.current()).thenReturn(
                new CurrentActor(1L, "CUSTOMER", 2L, null));
        service = new ComplianceService(
                catalog, documents, versions,
                requirements, mock(DocumentSubmissionRepository.class),
                mock(DocumentReviewRepository.class),
                mock(AuthorityProcedureRepository.class),
                clearances,
                mock(CaseAssignmentAccess.class), actors,
                mock(ComplianceAuditRepository.class),
                mock(ComplianceRuleRepository.class),
                mock(NamedParameterJdbcTemplate.class));
    }

    /** Reusing an owned document under another horse path is rejected. */
    @Test
    public void versionHistoryRequiresTheExactHorsePath() {
        var document = new HorseDocument();

        document.setDocumentId(11);
        document.setHorseId(12);
        when(documents.findById(11)).thenReturn(Optional.of(document));
        assertThatThrownBy(() -> service.listMyDocumentVersions(13, 11))
                .isInstanceOf(ComplianceException.class);
        verifyNoInteractions(versions);
    }

    /** Blank storage fileKey fails request validation before DB access. */
    @Test
    public void uploadRequiresNonBlankFileKey() {
        assertThatThrownBy(() -> service.uploadDocumentVersion(10, "PASSPORT",
                "   ", "file.pdf", "application/pdf", 123L,
                null, null, null, "client-hash"))
                .isInstanceOf(ComplianceException.class)
                .extracting("code")
                .isEqualTo("COMPLIANCE_FILE_KEY_REQUIRED");
        verifyNoInteractions(versions);
    }

    /** Uploading a document type not in the active catalog is rejected. */
    @Test
    public void uploadRejectsUnknownDocumentType() {
        when(catalog.listActive()).thenReturn(List.of());
        assertThatThrownBy(() -> service.uploadDocumentVersion(10, "UNKNOWN",
                "valid/key", "file.pdf", "application/pdf", 123L,
                null, null, null, "client-hash"))
                .isInstanceOf(ComplianceException.class)
                .extracting("code")
                .isEqualTo("COMPLIANCE_UNKNOWN_DOCUMENT_TYPE");
        verifyNoInteractions(versions);
    }

    /** An uncleared revision remains blocked and reports exact blockers. */
    @Test
    public void readinessReportsBlockersWhenUncleared() {
        when(requirements.currentOwnedRevisionId(20, 2))
                .thenReturn(Optional.of(30L));
        when(clearances.hasValidPass(30L, "PRE_EXPORT")).thenReturn(false);
        when(clearances.findLatestQuarantine(30L)).thenReturn(Optional.empty());

        var result = service.readiness(20);

        assertThat(result.revisionId()).isEqualTo(30);
        assertThat(result.asOf()).isNotNull();
        assertThat(result.legalGatePass()).isFalse();
        assertThat(result.requiredQuarantineDays()).isNull();
        assertThat(result.blockerCodes())
                .contains("PRE_EXPORT_NOT_CLEARED", "QUARANTINE_NOT_ASSESSED");
    }

    /** Readiness gate opens when PRE_EXPORT passed and quarantine assessed. */
    @Test
    public void readinessGatePassesWhenClearedAndAssessed() {
        when(requirements.currentOwnedRevisionId(20, 2))
                .thenReturn(Optional.of(30L));
        when(clearances.hasValidPass(30L, "PRE_EXPORT")).thenReturn(true);
        when(requirements.hasMandatoryBlocker(30L, "PRE_EXPORT"))
                .thenReturn(false);

        var qa = new QuarantineAssessment();
        qa.setApplicability("NOT_REQUIRED");
        qa.setRequiredDays(null);
        when(clearances.findLatestQuarantine(30L)).thenReturn(Optional.of(qa));

        var result = service.readiness(20);

        assertThat(result.legalGatePass()).isTrue();
        assertThat(result.blockerCodes()).isEmpty();
        assertThat(result.blockerPhases()).isEmpty();
        assertThat(result.requiredQuarantineDays()).isNull();
    }

    /** Readiness returns assessed quarantine days when REQUIRED. */
    @Test
    public void readinessReturnsAssessedDaysWhenRequired() {
        when(requirements.currentOwnedRevisionId(20, 2))
                .thenReturn(Optional.of(30L));
        when(clearances.hasValidPass(30L, "PRE_EXPORT")).thenReturn(true);
        when(requirements.hasMandatoryBlocker(30L, "PRE_EXPORT"))
                .thenReturn(false);

        var qa = new QuarantineAssessment();
        qa.setApplicability("REQUIRED");
        qa.setRequiredDays(14);
        when(clearances.findLatestQuarantine(30L)).thenReturn(Optional.of(qa));

        var result = service.readiness(20);

        assertThat(result.legalGatePass()).isTrue();
        assertThat(result.requiredQuarantineDays()).isEqualTo(14);
    }

    /** Quarantine release check delegates to clearance repository. */
    @Test
    public void quarantineReleaseCheckEvaluatesPhaseStatus() {
        when(requirements.currentOwnedRevisionId(20, 2))
                .thenReturn(Optional.of(30L));
        when(clearances.hasValidPass(30L, "QUARANTINE_RELEASE"))
                .thenReturn(true);

        assertThat(service.quarantineReleasePassed(20)).isTrue();
    }

    /** Unknown orders do not reveal another customer's compliance state. */
    @Test
    public void readinessRequiresAnOwnedOrder() {
        when(requirements.currentOwnedRevisionId(20, 2))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.readiness(20))
                .isInstanceOf(ComplianceException.class);
    }
}
