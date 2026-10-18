/*
 * ComplianceColdCacheTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Diagnostic verification of disk fallback and persisted metadata
 *     ownership isolation when in-memory cache is empty.
 */
package com.equitransit.backend.compliance;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.entity.compliance.DocumentTypeCatalog;
import com.equitransit.backend.entity.compliance.DocumentVersion;
import com.equitransit.backend.entity.compliance.HorseDocument;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Review-copy probes: discard cache to exercise real disk fallback and ensure
 * different owners cannot rebind victim's file keys across cache clearances.
 */
class ComplianceColdCacheTest {

    private final DocumentTypeCatalogRepository catalog =
            mock(DocumentTypeCatalogRepository.class);
    private final HorseDocumentRepository documents =
            mock(HorseDocumentRepository.class);
    private final DocumentVersionRepository versions =
            mock(DocumentVersionRepository.class);
    private final CurrentActorService actors =
            mock(CurrentActorService.class);
    private ComplianceService service;
    private DocumentVersion version;

    @BeforeEach
    void setup() {
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
        version = new DocumentVersion();
        version.setDocumentVersionId(11L);
        version.setDocumentId(10L);
        when(versions.insert(any())).thenReturn(11L);
        when(versions.findById(11L)).thenReturn(Optional.of(version));
        service = new ComplianceService(catalog, documents, versions,
                mock(DocumentRequirementRepository.class),
                mock(DocumentSubmissionRepository.class),
                mock(DocumentReviewRepository.class),
                mock(AuthorityProcedureRepository.class),
                mock(ComplianceClearanceRepository.class),
                mock(CaseAssignmentAccess.class),
                actors, mock(ComplianceAuditRepository.class),
                mock(ComplianceRuleRepository.class),
                mock(NamedParameterJdbcTemplate.class));
    }

    private void clearProcessCache() throws Exception {
        var field = ComplianceService.class
                .getDeclaredField("PERSISTENT_REGISTRY");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(null)).clear();
    }

    private String uploadVictimFile(byte[] bytes) {
        var stored = service.storeDocumentFile(
                9L, "passport.pdf", "application/pdf", bytes);
        service.uploadDocumentVersion(9L, "PASSPORT", stored.fileKey(),
                null, null, null, null, null, null, null);
        version.setFileKey(stored.fileKey());
        version.setFileHash(stored.fileHash());
        return stored.fileKey();
    }

    /** Owner can read actual disk after in-memory cache is cleared. */
    @Test
    void ownerCanReadActualDiskAfterAllCachedContentIsRemoved()
            throws Exception {
        byte[] bytes = "%PDF-1.5 disk fallback control"
                .getBytes(StandardCharsets.UTF_8);
        uploadVictimFile(bytes);
        clearProcessCache();
        assertThat(service.getDocumentVersionFile(9L, 11L)).isEqualTo(bytes);
    }

    /** Cold cache must not allow a different owner to rebind victim file. */
    @Test
    void coldCacheMustNotLetDifferentOwnerRebindVictimsFileKey()
            throws Exception {
        byte[] privateBytes = "%PDF-1.5 private customer 4"
                .getBytes(StandardCharsets.UTF_8);
        String key = uploadVictimFile(privateBytes);
        clearProcessCache();
        when(actors.current()).thenReturn(
                new CurrentActor(2L, "CUSTOMER", 5L, null));
        doThrow(new ComplianceException(HttpStatus.NOT_FOUND,
                "NOT_OWNED", "Not owned"))
                .when(documents).verifyOwnership(9L, 5L);
        doThrow(new ComplianceException(HttpStatus.NOT_FOUND,
                "NOT_OWNED", "Not owned"))
                .when(documents).verifyDocumentOwnership(10L, 5L);
        var ownDoc = new HorseDocument();
        ownDoc.setDocumentId(20L);
        ownDoc.setHorseId(99L);
        when(documents.findByHorseIdAndType(99L, "PASSPORT"))
                .thenReturn(Optional.of(ownDoc));
        when(documents.findById(20L)).thenReturn(Optional.of(ownDoc));
        var attackerVersion = new DocumentVersion();
        attackerVersion.setDocumentVersionId(12L);
        attackerVersion.setDocumentId(20L);
        attackerVersion.setFileKey(key);
        when(versions.insert(any())).thenReturn(12L);
        when(versions.findById(12L)).thenReturn(Optional.of(attackerVersion));
        assertThatThrownBy(() -> {
            service.uploadDocumentVersion(99L, "PASSPORT", key,
                    "stolen.pdf", "application/pdf", null, null, null,
                    null, null);
            assertThat(service.getDocumentVersionFile(99L, 12L))
                    .isEqualTo(privateBytes);
        }).isInstanceOf(ComplianceException.class);
    }
}
