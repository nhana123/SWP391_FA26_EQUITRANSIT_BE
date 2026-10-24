package com.equitransit.backend.controller;

import com.equitransit.backend.dto.compliance.DocumentTemplateFileRequest;
import com.equitransit.backend.dto.compliance.DocumentTemplateView;
import com.equitransit.backend.service.DocumentTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public form metadata and restricted blank-PDF publication endpoints. */
@RestController
@Validated
public class DocumentTemplateController {
    private final DocumentTemplateService service;

    /** @param service form lookup and publication */
    public DocumentTemplateController(DocumentTemplateService service) {
        this.service = service;
    }

    /** @return all active document templates */
    @GetMapping("/api/lookups/document-templates")
    @Operation(summary = "List active blank form metadata")
    public List<DocumentTemplateView> list() {
        return service.all();
    }

    /** @param typeCode catalog identity @return template metadata */
    @GetMapping("/api/lookups/document-templates/{typeCode}")
    @Operation(summary = "Read one active blank form")
    public DocumentTemplateView detail(@PathVariable
            @Pattern(regexp = "[A-Z0-9_]{1,50}") String typeCode) {
        return service.detail(typeCode);
    }

    /** @param typeCode target @param body blank PDF @return updated template */
    @PutMapping("/api/compliance/document-templates/{typeCode}/file")
    @Operation(summary = "Publish an uploaded blank PDF as a form template")
    @SecurityRequirement(name = "bearerAuth")
    public DocumentTemplateView attach(@PathVariable
            @Pattern(regexp = "[A-Z0-9_]{1,50}") String typeCode,
            @Valid @RequestBody DocumentTemplateFileRequest body) {
        return service.attach(typeCode, body);
    }
}
