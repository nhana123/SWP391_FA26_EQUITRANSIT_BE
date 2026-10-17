/*
 * ComplianceController
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose FR-08 legal document and compliance endpoints.
 */
package com.equitransit.backend.controller;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import com.equitransit.backend.dto.response.DocumentHistoryPageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.equitransit.backend.dto.request.AuthorityProcedureRequest;
import com.equitransit.backend.dto.request.ComplianceClearanceRequest;
import com.equitransit.backend.dto.request.DocumentRequirementRequest;
import com.equitransit.backend.dto.request.DocumentReviewRequest;
import com.equitransit.backend.dto.request.DocumentSubmissionRequest;
import com.equitransit.backend.dto.request.QuarantineAssessmentRequest;
import com.equitransit.backend.dto.response.AuthorityProcedurePageResponse;
import com.equitransit.backend.dto.response.AuthorityProcedureResponse;
import com.equitransit.backend.dto.response.ComplianceClearanceResponse;
import com.equitransit.backend.dto.response.ComplianceReadinessResponse;
import com.equitransit.backend.dto.response.DocumentRequirementPageResponse;
import com.equitransit.backend.dto.response.DocumentRequirementResponse;
import com.equitransit.backend.dto.response.DocumentReviewResponse;
import com.equitransit.backend.dto.response.DocumentSubmissionResponse;
import com.equitransit.backend.dto.response.DocumentTypeCatalogResponse;
import com.equitransit.backend.dto.response.DocumentVersionResponse;
import com.equitransit.backend.dto.response.HorseDocumentResponse;
import com.equitransit.backend.dto.response.QuarantineAssessmentResponse;
import com.equitransit.backend.exception.ComplianceException;
import com.equitransit.backend.service.ComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Thin HTTP adapter; all compliance state transitions live in the service. */
@RestController
@ConditionalOnProperty(prefix = "be2.compliance",
        name = "api-enabled", havingValue = "true")
@Validated
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Compliance & Legal Documents (FR-08)")
public class ComplianceController {

    private final ComplianceService compliance;

    /**
     * Creates the compliance HTTP adapter.
     * @param compliance the compliance service
     */
    public ComplianceController(ComplianceService compliance) {
        this.compliance = compliance;
    }

    // Catalog

    /**
     * Lists all active document type codes in the catalog.
     * @return active document types
     */
    @GetMapping("/api/compliance/document-types")
    @Operation(summary = "List active document types",
            description = "Returns all active entries in the"
                    + " DocumentTypeCatalog. "
                    + "Accessible to staff and customer.")
    public List<DocumentTypeCatalogResponse> listDocumentTypes() {
        return compliance.listActiveCatalog();
    }

    // Customer: Horse Documents & File Versions

    /**
     * Lists document records for a horse owned by the authenticated customer.
     * @param horseId the owned horse key
     * @return the horse's document list
     */
    @GetMapping("/api/horses/{horseId}/documents")
    @Operation(summary = "List my horse's legal documents",
            description = "Customer only. Returns document records for the"
                    + " owned horse.")
    public List<HorseDocumentResponse> listHorseDocuments(
            @PathVariable @Positive long horseId) {
        return compliance.listMyHorseDocuments(horseId);
    }

    /**
     * Returns all immutable versions of a document.
     * @param horseId the owned horse key
     * @param documentId the document record key
     * @return version history
     */
    @GetMapping("/api/horses/{horseId}/documents/{documentId}/versions")
    @Operation(summary = "List document version history",
            description = "Customer only. Version history is immutable;"
                    + " old versions are never removed.")
    public List<DocumentVersionResponse> listDocumentVersions(
            @PathVariable @Positive long horseId,
            @PathVariable @Positive long documentId) {
        return compliance.listMyDocumentVersions(horseId, documentId);
    }

    /**
     * Customer uploads a new document version (append-only).
     * The fileKey is an opaque storage reference already issued by the
     * upstream storage service; this endpoint only persists metadata.
     * @param horseId the owned horse key
     * @param documentType the document type code
     * @param fileKey the opaque storage key from the file upload service
     * @param originalFileName the original file name
     * @param contentType the MIME type
     * @param fileSizeBytes the file size in bytes
     * @param documentNumber optional official document number
     * @param issuedDate optional date of issue
     * @param expiryDate optional expiry date
     * @param fileHash optional integrity hash
     * @return 201 and the new version record
     */
    @PostMapping("/api/horses/{horseId}/documents")
    @ApiResponse(responseCode = "201",
            description = "New document version created",
            content = @Content(schema =
                    @Schema(implementation = DocumentVersionResponse.class)))
    @Operation(summary = "Upload a new document version",
            description = "Customer only. Persists metadata for a file"
                    + " already uploaded to the storage service."
                    + " fileKey is the opaque key issued by that service.")
    public ResponseEntity<DocumentVersionResponse> uploadDocumentVersion(
            @PathVariable @Positive long horseId,
            @RequestParam @NotBlank @Size(max = 50) String documentType,
            @RequestParam @NotBlank @Size(max = 1024) String fileKey,
            @RequestParam(required = false) @Size(max = 255)
            String originalFileName,
            @RequestParam(required = false) @Size(max = 100)
            String contentType,
            @RequestParam(required = false) Long fileSizeBytes,
            @RequestParam(required = false) @Size(max = 50)
            String documentNumber,
            @RequestParam(required = false) LocalDate issuedDate,
            @RequestParam(required = false) LocalDate expiryDate,
            @RequestParam(required = false) @Size(max = 128)
            String fileHash) {
        DocumentVersionResponse created = compliance.uploadDocumentVersion(
                horseId, documentType, fileKey, originalFileName,
                contentType, fileSizeBytes, documentNumber,
                issuedDate, expiryDate, fileHash);
        return ResponseEntity.created(URI.create(
                "/api/horses/" + horseId + "/documents/"
                        + created.documentId() + "/versions/"
                        + created.documentVersionId()))
                .body(created);
    }

    /**
     * Customer uploads a document binary file directly (multipart/form-data).
     * Validates magic bytes (PDF %PDF-, JPEG, PNG), size limit (<= 10MB),
     * computes real SHA-256 hash, stores file bytes, and creates version.
     *
     * @param horseId the owned horse key
     * @param documentType the document type code
     * @param file the binary file payload
     * @param documentNumber optional official document number
     * @param issuedDate optional issue date
     * @param expiryDate optional expiry date
     * @return 201 and created version record
     * @throws IOException on multipart read error
     */
    @PostMapping(value = "/api/horses/{horseId}/documents/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "201",
            description = "Binary document uploaded and version created",
            content = @Content(schema =
                    @Schema(implementation = DocumentVersionResponse.class)))
    @Operation(summary = "Upload document file with binary verification",
            description = "Customer only. Accepts binary file (PDF/JPEG/PNG),"
                    + " verifies magic bytes, computes SHA-256, and stores"
                    + " the document version.")
    public ResponseEntity<DocumentVersionResponse> uploadDocumentBinary(
            @PathVariable @Positive long horseId,
            @RequestParam("documentType") @NotBlank @Size(max = 50)
            String documentType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 50)
            String documentNumber,
            @RequestParam(required = false) LocalDate issuedDate,
            @RequestParam(required = false) LocalDate expiryDate)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_EMPTY",
                    "Uploaded file must not be empty.");
        }
        var stored = compliance.storeDocumentFile(
                horseId, file.getOriginalFilename(),
                file.getContentType(), file.getBytes());
        DocumentVersionResponse created = compliance.uploadDocumentVersion(
                horseId, documentType, stored.fileKey(),
                stored.originalFileName(), stored.contentType(),
                stored.fileSizeBytes(), documentNumber,
                issuedDate, expiryDate, stored.fileHash());
        return ResponseEntity.created(URI.create(
                "/api/horses/" + horseId + "/documents/"
                        + created.documentId() + "/versions/"
                        + created.documentVersionId()))
                .body(created);
    }

    /**
     * Downloads the verified binary content of a stored document version.
     * Accessible to the owning customer.
     *
     * @param horseId the owned horse key
     * @param versionId the document version key
     * @return binary file bytes
     */
    @GetMapping(
            value = "/api/horses/{horseId}/documents/versions/"
                    + "{versionId}/file",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Download verified document version binary",
            description = "Streams verified binary content. Accessible to"
                    + " owning customer and assigned compliance staff.")
    public ResponseEntity<byte[]> downloadDocumentFile(
            @PathVariable @Positive long horseId,
            @PathVariable @Positive long versionId) {
        byte[] content = compliance.getDocumentVersionFile(horseId, versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"version-"
                                + versionId + ".bin\"")
                .body(content);
    }

    /**
     * Downloads binary file submission for an assigned order requirement.
     * Accessible to assigned compliance staff.
     *
     * @param orderId the assigned order key
     * @param requirementId the requirement key
     * @param versionId the document version key
     * @return binary file bytes
     */
    @GetMapping(
            value = "/api/transport-orders/{orderId}/compliance/requirements"
                    + "/{requirementId}/versions/{versionId}/file",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Staff download document submission binary",
            description = "Streams binary evidence for assigned specialist.")
    public ResponseEntity<byte[]> downloadStaffSubmissionFile(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long requirementId,
            @PathVariable @Positive long versionId) {
        byte[] content = compliance.getOrderDocumentVersionFile(
                orderId, requirementId, versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"version-"
                                + versionId + ".bin\"")
                .body(content);
    }

    // Document Requirements (checklist)

    /**
     * Lists the paged document requirement checklist for an order.
     * @param orderId the order key
     * @param page zero-based page index (default 0)
     * @param size rows per page (default 20, max 100)
     * @return requirement checklist page
     */
    @GetMapping("/api/transport-orders/{orderId}/compliance/requirements")
    @Operation(summary = "List document requirements checklist",
            description = "Returns a page of the compliance checklist for"
                    + " the order revision. Accessible to staff and customer."
                    + " Use 'page' and 'size' to paginate large checklists.")
    public DocumentRequirementPageResponse listRequirements(
            @PathVariable @Positive long orderId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100)
            int size) {
        return compliance.listRequirementsPaged(orderId, page, size);
    }

    /**
     * Specialist adds a document requirement to an assigned order's checklist.
     * @param orderId the assigned order key
     * @param request the requirement details
     * @return 201 and the created requirement
     */
    @PostMapping("/api/staff/transport-orders/{orderId}"
            + "/compliance/requirements")
    @ApiResponse(responseCode = "201", description = "Requirement added",
            content = @Content(schema =
                    @Schema(implementation =
                            DocumentRequirementResponse.class)))
    @Operation(summary = "Add a document requirement",
            description = "Specialist only. Adds a requirement to the"
                    + " checklist of an assigned order. The chosen rule must"
                    + " match the revision's origin, destination, optional"
                    + " transit country, and departure date window.")
    public ResponseEntity<DocumentRequirementResponse> addRequirement(
            @PathVariable @Positive long orderId,
            @RequestBody @Valid DocumentRequirementRequest request) {
        DocumentRequirementResponse created =
                compliance.addRequirement(orderId, request);
        return ResponseEntity.created(URI.create(
                "/api/transport-orders/" + orderId
                        + "/compliance/requirements/"
                        + created.requirementId()))
                .body(created);
    }

    // Document Submissions

    /**
     * Customer submits a document version to fulfil a specific requirement.
     * @param orderId the order key
     * @param requirementId the requirement to fulfil
     * @param request submission with the documentVersionId
     * @return 201 and the submission record
     */
    @PostMapping("/api/transport-orders/{orderId}/compliance"
            + "/requirements/{requirementId}/submissions")
    @ApiResponse(responseCode = "201", description = "Document submitted",
            content = @Content(schema =
                    @Schema(implementation =
                            DocumentSubmissionResponse.class)))
    @Operation(summary = "Submit a document version for review",
            description = "Customer only. Submits an already-uploaded"
                    + " document version to fulfil a requirement. Each"
                    + " submission is immutable; a new submission replaces"
                    + " the pending one.")
    public ResponseEntity<DocumentSubmissionResponse> submitDocument(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long requirementId,
            @RequestBody @Valid DocumentSubmissionRequest request) {
        DocumentSubmissionResponse created =
                compliance.submitDocument(orderId, requirementId, request);
        return ResponseEntity.created(URI.create(
                "/api/transport-orders/" + orderId
                        + "/compliance/requirements/" + requirementId
                        + "/submissions/" + created.submissionId()))
                .body(created);
    }

    /** Reads submission history and public review feedback.
     * @param orderId owned or assigned order
     * @param requirementId requirement belonging to the order
     * @param page zero-based page
     * @param size bounded page size
     * @return public history page
     */
    @GetMapping("/api/transport-orders/{orderId}/compliance/requirements"
            + "/{requirementId}/submissions")
    public DocumentHistoryPageResponse submissionHistory(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long requirementId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return compliance.submissionHistory(orderId, requirementId, page, size);
    }

    // Document Reviews

    /**
     * Specialist records a review decision on a submitted document.
     * APPROVED marks the requirement COMPLIANT. CHANGES_REQUESTED or
     * REJECTED returns it to PENDING and requires a public note.
     * @param orderId the assigned order key
     * @param submissionId the submission being reviewed
     * @param request the review decision
     * @return 201 and the review record (no internal notes exposed)
     */
    @PostMapping("/api/staff/transport-orders/{orderId}"
            + "/compliance/submissions/{submissionId}/reviews")
    @ApiResponse(responseCode = "201", description = "Review recorded",
            content = @Content(schema =
                    @Schema(implementation = DocumentReviewResponse.class)))
    @Operation(summary = "Record a document review",
            description = "Specialist only. Append-only audit record."
                    + " APPROVED marks the requirement COMPLIANT."
                    + " CHANGES_REQUESTED or REJECTED requires a publicNote"
                    + " and returns the requirement to PENDING.")
    public ResponseEntity<DocumentReviewResponse> reviewSubmission(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long submissionId,
            @RequestBody @Valid DocumentReviewRequest request) {
        DocumentReviewResponse created =
                compliance.reviewSubmission(orderId, submissionId, request);
        return ResponseEntity.created(URI.create(
                "/api/staff/transport-orders/" + orderId
                        + "/compliance/reviews/" + created.reviewId()))
                .body(created);
    }

    // Authority Procedures

    /**
     * Lists authority procedure records for an assigned order with pagination.
     * @param orderId the assigned order key
     * @param page zero-based page index (default 0)
     * @param size rows per page (default 20, max 100)
     * @return authority procedure page response
     */
    @GetMapping("/api/staff/transport-orders/{orderId}/compliance/procedures")
    @Operation(summary = "List authority procedures",
            description = "Specialist only. Lists external authority contacts"
                    + " and their current approval status with pagination."
                    + " EvidenceFileKey is not exposed in responses.")
    public AuthorityProcedurePageResponse listProcedures(
            @PathVariable @Positive long orderId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return compliance.listProceduresPaged(orderId, page, size);
    }

    /**
     * Records a new authority procedure contact for an assigned order.
     * @param orderId the assigned order key
     * @param request the procedure details
     * @return 201 and the procedure record
     */
    @PostMapping(
            "/api/staff/transport-orders/{orderId}/compliance/procedures")
    @ApiResponse(responseCode = "201", description = "Procedure recorded",
            content = @Content(schema =
                    @Schema(implementation = AuthorityProcedureResponse.class)))
    @Operation(summary = "Record an authority procedure",
            description = "Specialist only. Records a contact with an"
                    + " external authority."
                    + " EvidenceFileKey is an opaque storage reference.")
    public ResponseEntity<AuthorityProcedureResponse> recordProcedure(
            @PathVariable @Positive long orderId,
            @RequestBody @Valid AuthorityProcedureRequest request) {
        AuthorityProcedureResponse created =
                compliance.recordProcedure(orderId, request);
        return ResponseEntity.created(URI.create(
                "/api/staff/transport-orders/" + orderId
                        + "/compliance/procedures/"
                        + created.procedureId()))
                .body(created);
    }

    /**
     * Updates the approval status of an authority procedure.
     * All statuses including APPROVED are accepted; APPROVED reflects
     * real confirmation received from the external authority.
     * @param orderId the assigned order key
     * @param procedureId the procedure to update
     * @param status the new approval status
     * @return the updated procedure
     */
    @PutMapping("/api/staff/transport-orders/{orderId}"
            + "/compliance/procedures/{procedureId}/status")
    @Operation(summary = "Update authority procedure status",
            description = "Specialist only. Allowed values: PENDING,"
                    + " SUBMITTED, APPROVED, REJECTED, ON_HOLD."
                    + " APPROVED reflects real external authority decision.")
    public AuthorityProcedureResponse updateProcedureStatus(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long procedureId,
            @Parameter(schema = @Schema(type = "string",
                    allowableValues = {"PENDING", "SUBMITTED",
                            "APPROVED", "REJECTED", "ON_HOLD"}))
            @RequestParam @NotBlank String status) {
        return compliance.updateProcedureStatus(
                orderId, procedureId, status);
    }

    // Compliance Clearance

    /**
     * Lists all compliance clearances for an order.
     * @param orderId the order key
     * @return clearance list
     */
    @GetMapping("/api/transport-orders/{orderId}/compliance/clearances")
    @Operation(summary = "List compliance clearances",
            description = "Returns clearances per phase. Accessible to"
                    + " staff and customer. Each phase is independent.")
    public List<ComplianceClearanceResponse> listClearances(
            @PathVariable @Positive long orderId) {
        return compliance.listClearances(orderId);
    }

    /**
     * Specialist issues a compliance clearance for a specific phase.
     * PASS is allowed when the specialist has verified all mandatory
     * requirements are COMPLIANT for the phase.
     * @param orderId the assigned order key
     * @param request the clearance decision
     * @return 201 and the new clearance
     */
    @PostMapping(
            "/api/staff/transport-orders/{orderId}/compliance/clearances")
    @ApiResponse(responseCode = "201", description = "Clearance issued",
            content = @Content(schema =
                    @Schema(implementation =
                            ComplianceClearanceResponse.class)))
    @Operation(summary = "Issue a compliance clearance",
            description = "Specialist only. Issues clearance per phase:"
                    + " PRE_EXPORT, TRANSIT, ARRIVAL, QUARANTINE_RELEASE."
                    + " Phases are independent. PASS, FAIL and PENDING are"
                    + " all accepted; PASS means the specialist confirms"
                    + " all mandatory requirements are met.")
    public ResponseEntity<ComplianceClearanceResponse> issueClearance(
            @PathVariable @Positive long orderId,
            @RequestBody @Valid ComplianceClearanceRequest request) {
        ComplianceClearanceResponse created =
                compliance.issueClearance(orderId, request);
        return ResponseEntity.created(URI.create(
                "/api/transport-orders/" + orderId
                        + "/compliance/clearances/"
                        + created.clearanceId()))
                .body(created);
    }

    // Quarantine Assessment

    /**
     * Specialist records the quarantine applicability decision and required
     * days. Append-only; each call creates a new assessment record.
     * @param orderId the assigned order key
     * @param request quarantine assessment details
     * @return 201 and the recorded assessment
     */
    @PostMapping("/api/staff/transport-orders/{orderId}"
            + "/compliance/quarantine")
    @ApiResponse(responseCode = "201",
            description = "Quarantine assessment recorded",
            content = @Content(schema =
                    @Schema(implementation =
                            QuarantineAssessmentResponse.class)))
    @Operation(summary = "Record quarantine applicability assessment",
            description = "Specialist only. Records whether quarantine is"
                    + " PENDING, NOT_REQUIRED or REQUIRED."
                    + " When REQUIRED, requiredDays must be a positive integer."
                    + " This assessment drives the F14 quarantine release gate"
                    + " and is published to BE1 for F05.")
    public ResponseEntity<QuarantineAssessmentResponse>
            recordQuarantineAssessment(
            @PathVariable @Positive long orderId,
            @RequestBody @Valid QuarantineAssessmentRequest request) {
        QuarantineAssessmentResponse created =
                compliance.recordQuarantineAssessment(orderId, request);
        return ResponseEntity.created(URI.create(
                "/api/staff/transport-orders/" + orderId
                        + "/compliance/quarantine"))
                .body(created);
    }

    // Readiness Facade

    /**
     * Returns the legal gate readiness summary for an order.
     * Used by FR-11 (departure gate) and FR-14 (final handover).
     * @param orderId the order key
     * @return readiness summary with legal gate pass status
     */
    @GetMapping("/api/transport-orders/{orderId}/compliance/readiness")
    @Operation(summary = "Get compliance readiness",
            description = "Returns the authorized revision and blockers."
                    + " legalGatePass is true when PRE_EXPORT is cleared,"
                    + " no mandatory blockers remain, and quarantine has"
                    + " been assessed. Used by FR-11 and FR-14.")
    public ComplianceReadinessResponse readiness(
            @PathVariable @Positive long orderId) {
        return compliance.readiness(orderId);
    }

    /**
     * Returns true when the revision holds a valid QUARANTINE_RELEASE PASS.
     * This is the FR-14 final handover gate check.
     * @param orderId the order key
     * @return quarantine release gate result
     */
    @GetMapping("/api/transport-orders/{orderId}"
            + "/compliance/quarantine-release")
    @Operation(summary = "Check quarantine release gate (FR-14)",
            description = "Returns true when a valid QUARANTINE_RELEASE"
                    + " PASS clearance exists for the current revision."
                    + " FR-14 final handover must not proceed when false.")
    public boolean quarantineReleasePassed(
            @PathVariable @Positive long orderId) {
        return compliance.quarantineReleasePassed(orderId);
    }
}
