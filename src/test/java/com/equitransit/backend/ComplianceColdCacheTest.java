/*
 * ComplianceColdCacheTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify disk persistence and metadata ownership isolation across
 *     fresh service instances.
 */
package com.equitransit.backend;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fresh service instances read actual disk evidence and cannot let different
 * owners rebind a victim's file keys.
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
        service = newService();
    }

    private ComplianceService newService() {
        return new ComplianceService(catalog, documents, versions,
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

    private String uploadVictimFile(byte[] bytes) {
        var stored = service.storeDocumentFile(
                9L, "passport.pdf", "application/pdf", bytes);
        service.uploadDocumentVersion(9L, "PASSPORT", stored.fileKey(),
                null, null, null, null, null, null, null);
        version.setFileKey(stored.fileKey());
        version.setFileHash(stored.fileHash());
        return stored.fileKey();
    }

    /** A fresh service instance can read the owner's actual disk evidence. */
    @Test
    void ownerCanReadActualDiskWithFreshServiceInstance() {
        byte[] bytes = "%PDF-1.5 disk fallback control"
                .getBytes(StandardCharsets.UTF_8);
        uploadVictimFile(bytes);
        service = newService();
        assertThat(service.getDocumentVersionFile(9L, 11L)).isEqualTo(bytes);
    }

    /** A fresh service cannot let another owner rebind persisted evidence. */
    @Test
    void freshServiceMustNotLetDifferentOwnerRebindVictimsFileKey() {
        byte[] privateBytes = "%PDF-1.5 private customer 4"
                .getBytes(StandardCharsets.UTF_8);
        String key = uploadVictimFile(privateBytes);
        service = newService();
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
