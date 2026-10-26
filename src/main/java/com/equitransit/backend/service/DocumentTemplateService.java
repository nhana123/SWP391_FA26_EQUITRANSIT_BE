package com.equitransit.backend.service;

import com.equitransit.backend.dto.compliance.DocumentTemplateFileRequest;
import com.equitransit.backend.dto.compliance.DocumentTemplateView;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.DocumentTemplateRepository;
import com.equitransit.backend.security.CurrentActorService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Blank template lookup and explicit publication of staff-owned PDFs. */
@Service
@Transactional(readOnly = true)
public class DocumentTemplateService {
    private final DocumentTemplateRepository data;
    private final CurrentActorService actors;
    private final FileStorageService files;

    /** @param data SQL @param actors live identities @param files storage */
    public DocumentTemplateService(DocumentTemplateRepository data,
            CurrentActorService actors, FileStorageService files) {
        this.data = data;
        this.actors = actors;
        this.files = files;
    }

    /** @return active catalog templates */
    public List<DocumentTemplateView> all() {
        ready();
        return data.all();
    }

    /** @param type type code @return active template or 404 */
    public DocumentTemplateView detail(String type) {
        ready();
        return one(type, false);
    }

    /** @param type target @param request verified upload identity
     * @return updated template; publishing grants authenticated download */
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','SPECIALIST')")
    public DocumentTemplateView attach(String type,
            DocumentTemplateFileRequest request) {
        ready();
        var actor = actors.current();
        if (!List.of("MANAGER", "SPECIALIST").contains(actor.roleCode())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Template administration is not permitted.");
        }
        one(type, true);
        var owned = data.ownedFile(request.templateFileKey(), actor.userId());
        if (owned.isEmpty()) {
            throw new OrderException(HttpStatus.NOT_FOUND,
                    "TEMPLATE_FILE_NOT_FOUND", "Owned upload was not found.");
        }
        if (!"application/pdf".equals(owned.getFirst().get("ContentType"))) {
            throw new OrderException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TEMPLATE_PDF_REQUIRED", "A blank PDF is required.");
        }
        if (data.usedAsEvidence(request.templateFileKey())) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "TEMPLATE_FILE_ALREADY_USED",
                    "Upload a separate blank template PDF.");
        }
        files.loadAsResource(request.templateFileKey().toString());
        data.attach(type, request.templateFileKey());
        return one(type, false);
    }

    private DocumentTemplateView one(String type, boolean lock) {
        return data.find(type, lock).stream().findFirst().orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND,
                        "DOCUMENT_TEMPLATE_NOT_FOUND",
                        "Active document template was not found."));
    }

    private void ready() {
        if (!data.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DOCUMENT_TEMPLATE_SCHEMA_PENDING",
                    "Deploy migration025.");
        }
    }
}
