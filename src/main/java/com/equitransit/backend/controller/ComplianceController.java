/*
 * ComplianceController
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.controller;


import com.equitransit.backend.dto.compliance.ComplianceAssessmentRequest;
import com.equitransit.backend.dto.compliance.ComplianceAssessmentView;
import com.equitransit.backend.dto.compliance.ComplianceAuthorityRequest;
import com.equitransit.backend.dto.compliance.ComplianceAuthorityView;
import com.equitransit.backend.dto.compliance.ComplianceChecklistRequest;
import com.equitransit.backend.dto.compliance.ComplianceClearanceRequest;
import com.equitransit.backend.dto.compliance.ComplianceDocumentView;
import com.equitransit.backend.dto.compliance.ComplianceFileRequest;
import com.equitransit.backend.dto.compliance.ComplianceFileView;
import com.equitransit.backend.dto.compliance.ComplianceReviewRequest;
import com.equitransit.backend.dto.compliance.ComplianceRevokeRequest;
import com.equitransit.backend.dto.compliance.ComplianceRuleRequest;
import com.equitransit.backend.dto.compliance.ComplianceRuleView;
import com.equitransit.backend.dto.compliance.ComplianceSubmissionRequest;
import com.equitransit.backend.dto.compliance.ComplianceUploadRequest;
import com.equitransit.backend.dto.compliance.ComplianceUploadSubmitRequest;
import com.equitransit.backend.dto.compliance.ComplianceReceipt;
import com.equitransit.backend.dto.compliance.ComplianceOverview;
import com.equitransit.backend.entity.compliance.CompliancePhase;
import com.equitransit.backend.service.ComplianceService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;


/** Legal transport only; authorization and transitions belong to service. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/transport-orders/{order}/compliance",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class ComplianceController {

    private final ComplianceService service;

    /** @param service actor-scoped legal workflow */
    public ComplianceController(ComplianceService service) {
        this.service = service;
    }

    /** @param order parent @param body validated command
     * @return retained operation receipt */
    @PostMapping("/rules")
    public ComplianceReceipt rule(
            @PathVariable @Positive long order,
            @Valid @RequestBody ComplianceRuleRequest body) {
        return service.rule(order, body);
    }

    /** @param order parent @param body validated command
     * @return retained operation receipt */
    @PostMapping("/checklists")
    public ComplianceReceipt checklist(
            @PathVariable @Positive long order,
            @Valid @RequestBody ComplianceChecklistRequest body) {
        return service.checklist(order, body);
    }

    /** @param order parent @param body validated command
     * @return retained operation receipt */
    @PostMapping("/documents")
    public ComplianceReceipt upload(
            @PathVariable @Positive long order,
            @Valid @RequestBody ComplianceUploadRequest body) {
        return service.upload(order, body);
    }

    /** @param order parent @param body validated command
     * @return retained operation receipt */
    @PostMapping("/files")
    public ComplianceReceipt uploadFile(
            @PathVariable @Positive long order,
            @Valid @RequestBody ComplianceFileRequest body) {
        return service.uploadFile(order, body);
    }

    /** @param order parent @param id requirement @param body file command
     * @return atomic upload and submission receipt */
    @PostMapping("/requirements/{id}/upload-and-submit")
    @Operation(summary = "Submit base64, a private fileKey, or a horse version")
    public ComplianceReceipt uploadAndSubmit(
            @PathVariable @Positive long order,
            @PathVariable @Positive long id,
            @Valid @RequestBody ComplianceUploadSubmitRequest body) {
        return service.uploadAndSubmit(order, id, body);
    }

    /** @param order parent @param body validated command
     * @param id exact evidence @return retained operation receipt */
    @PostMapping("/requirements/{id}/submissions")
    public ComplianceReceipt submit(
            @PathVariable @Positive long order,
            @PathVariable @Positive long id,
            @Valid @RequestBody ComplianceSubmissionRequest body) {
        return service.submit(order, id, body);
    }

    /** @param order parent @param body validated command
     * @param id exact evidence @return retained operation receipt */
    @PostMapping("/submissions/{id}/reviews")
    public ComplianceReceipt review(
            @PathVariable @Positive long order,
            @PathVariable @Positive long id,
            @Valid @RequestBody ComplianceReviewRequest body) {
        return service.review(order, id, body);
    }

    /** @param order parent @param body validated command
     * @return retained operation receipt */
    @PostMapping("/procedures")
    public ComplianceReceipt authority(
            @PathVariable @Positive long order,
            @Valid @RequestBody ComplianceAuthorityRequest body) {
        return service.authority(order, body);
    }

    /** @param order parent @param body validated command
     * @return retained operation receipt */
    @PostMapping("/assessments")
    public ComplianceReceipt assessment(
            @PathVariable @Positive long order,
            @Valid @RequestBody ComplianceAssessmentRequest body) {
        return service.assessment(order, body);
    }

    /** @param order parent @param body validated command
     * @return retained operation receipt */
    @PostMapping("/clearances")
    public ComplianceReceipt clear(
            @PathVariable @Positive long order,
            @Valid @RequestBody ComplianceClearanceRequest body) {
        return service.clear(order, body);
    }

    /** @param order parent @param body validated command
     * @param id exact evidence @return retained operation receipt */
    @PostMapping("/documents/{id}/revocations")
    public ComplianceReceipt revoke(
            @PathVariable @Positive long order,
            @PathVariable @Positive long id,
            @Valid @RequestBody ComplianceRevokeRequest body) {
        return service.revoke(order, id, body);
    }

    /** @param order parent @param phase phase @param page zero based
     * @param size bounded limit @param bootstrap initialize missing checklists
     * @return phase-specific evidence */
    @GetMapping
    public ComplianceOverview overview(@PathVariable @Positive long order,
            @RequestParam CompliancePhase phase,
            @Parameter(schema = @Schema(example = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @Parameter(schema = @Schema(example = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "true") boolean bootstrap) {
        return service.overview(order, phase, page, size, bootstrap);
    }

    /** @param order parent @param page zero based @param size bounded limit
     * @return immutable file-version metadata */
    @GetMapping(value = "/documents", params = "view=versions")
    @Operation(hidden = true)
    public List<ComplianceDocumentView> documents(
            @PathVariable @Positive long order,
            @Parameter(schema = @Schema(example = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @Parameter(schema = @Schema(example = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.documents(order, page, size);
    }

    /** @param order case @param phase optional phase @param page page
     * @param size limit @return required papers and reusable version history */
    @GetMapping(value = "/documents", params = "!view")
    @Operation(summary = "Get required papers, review feedback and horse files")
    public com.equitransit.backend.dto.compliance.ComplianceDocumentsView
            documentChecklist(@PathVariable @Positive long order,
                    @RequestParam(required = false) CompliancePhase phase,
                    @RequestParam(defaultValue = "0")
                    @Min(0) @Max(100000) int page,
                    @RequestParam(defaultValue = "20")
                    @Min(1) @Max(100) int size) {
        return service.documentChecklist(order, phase, page, size);
    }

    /** @param order parent @param key opaque key @return private JSON file */
    @GetMapping("/files/{key}")
    public ComplianceFileView file(@PathVariable @Positive long order,
            @PathVariable UUID key) {
        return service.file(order, key);
    }

    /** @param order parent @param phase phase @return source-backed rules */
    @GetMapping("/rules")
    public List<ComplianceRuleView> rules(@PathVariable @Positive long order,
            @RequestParam CompliancePhase phase) {
        return service.rules(order, phase);
    }

    /** @param order parent @param phase phase 
     * @return recorded authority facts */
    @GetMapping("/procedures")
    public List<ComplianceAuthorityView> procedures(
            @PathVariable @Positive long order,
            @RequestParam CompliancePhase phase) {
        return service.procedures(order, phase);
    }
    /** @param order parent @return current revision clinical quantities */
    @GetMapping("/assessments")
    public ComplianceAssessmentView assessment(@PathVariable
            @Positive long order) {
        return service.assessment(order);
    }
}
