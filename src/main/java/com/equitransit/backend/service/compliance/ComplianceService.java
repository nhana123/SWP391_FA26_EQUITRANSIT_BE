/*
 * ComplianceService
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement FR-08 legal document management and compliance
 *     gatekeeping.
 */
package com.equitransit.backend.service.compliance;

import com.equitransit.backend.service.assessment.CaseAssignmentAccess;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.equitransit.backend.dto.compliance.response.
        DocumentHistoryPageResponse;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.equitransit.backend.dto.compliance.request.AuthorityProcedureRequest;
import com.equitransit.backend.dto.compliance.request.
        ComplianceClearanceRequest;
import com.equitransit.backend.dto.compliance.request.
        DocumentRequirementRequest;
import com.equitransit.backend.dto.compliance.request.DocumentReviewRequest;
import com.equitransit.backend.dto.compliance.request.DocumentSubmissionRequest;
import com.equitransit.backend.dto.compliance.request.
        QuarantineAssessmentRequest;
import com.equitransit.backend.dto.compliance.response.
        AuthorityProcedurePageResponse;
import com.equitransit.backend.dto.compliance.response.
        AuthorityProcedureResponse;
import com.equitransit.backend.dto.compliance.response.
        ComplianceClearanceResponse;
import com.equitransit.backend.dto.compliance.response.
        ComplianceReadinessResponse;
import com.equitransit.backend.dto.compliance.response.
        DocumentRequirementPageResponse;
import com.equitransit.backend.dto.compliance.response.
        DocumentRequirementResponse;
import com.equitransit.backend.dto.compliance.response.DocumentReviewResponse;
import com.equitransit.backend.dto.compliance.response.
        DocumentSubmissionResponse;
import com.equitransit.backend.dto.compliance.response.
        DocumentTypeCatalogResponse;
import com.equitransit.backend.dto.compliance.response.DocumentVersionResponse;
import com.equitransit.backend.dto.compliance.response.HorseDocumentResponse;
import com.equitransit.backend.dto.compliance.response.
        QuarantineAssessmentResponse;
import com.equitransit.backend.entity.compliance.AuthorityProcedure;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.compliance.ComplianceClearance;
import com.equitransit.backend.entity.compliance.DocumentRequirement;
import com.equitransit.backend.entity.compliance.DocumentReview;
import com.equitransit.backend.entity.compliance.DocumentSubmission;
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
import com.equitransit.backend.security.CurrentActorService;

/**
 * Owns all compliance state transitions; enforces immutable audit trail.
 * Specialist-only write paths use CaseAssignmentAccess to verify case
 * responsibility before any mutation.
 */
@Service
@ConditionalOnProperty(prefix = "be2.compliance",
        name = "api-enabled", havingValue = "true")
@Validated
public class ComplianceService {

    private static final String PHASE_PRE_EXPORT = "PRE_EXPORT";
    private static final String PHASE_TRANSIT = "TRANSIT";
    private static final String PHASE_ARRIVAL = "ARRIVAL";
    private static final String PHASE_QR = "QUARANTINE_RELEASE";
    private static final String APPL_PENDING = "PENDING";
    private static final String APPL_NOT_REQUIRED = "NOT_REQUIRED";
    private static final String APPL_REQUIRED = "REQUIRED";

    /**
     * Stored binary file metadata verified by internal storage adapter.
     * Defensive copy is performed on content bytes array.
     * @param fileKey opaque server-issued storage key
     * @param horseId associated horse identifier
     * @param customerId owning customer identifier
     * @param originalFileName original uploaded file name
     * @param contentType verified MIME type
     * @param fileSizeBytes verified size in bytes
     * @param fileHash verified lowercase SHA-256 hex string
     * @param content actual binary content bytes
     * @param storedAt UTC timestamp when stored
     */
    public record VerifiedStorageFile(
            String fileKey,
            long horseId,
            long customerId,
            String originalFileName,
            String contentType,
            long fileSizeBytes,
            String fileHash,
            byte[] content,
            OffsetDateTime storedAt) {

        /**
         * Returns defensive copy of binary content.
         * @return cloned byte array or null
         */
        public byte[] content() {
            return content == null ? null : content.clone();
        }
    }

    private static Path storageRoot = Path.of(System.getProperty(
            "equitransit.compliance.storage-root", ".local/compliance-files"))
            .toAbsolutePath().normalize();
    private static final Map<String, VerifiedStorageFile>
            PERSISTENT_REGISTRY = new ConcurrentHashMap<>();

    private final DocumentTypeCatalogRepository catalogRepo;
    private final HorseDocumentRepository horseDocRepo;
    private final DocumentVersionRepository docVersionRepo;
    private final DocumentRequirementRepository requirementRepo;
    private final DocumentSubmissionRepository submissionRepo;
    private final DocumentReviewRepository reviewRepo;
    private final AuthorityProcedureRepository procedureRepo;
    private final ComplianceClearanceRepository clearanceRepo;
    private final CaseAssignmentAccess caseAccess;
    private final CurrentActorService actors;
    private final ComplianceAuditRepository audit;
    private final ComplianceRuleRepository rules;
    private final NamedParameterJdbcTemplate jdbc;
    private ComplianceRevisionService revisionService;

    /**
     * Creates the compliance service with all required dependencies.
     * @param catalogRepo document type catalog repository
     * @param horseDocRepo horse document repository
     * @param docVersionRepo document version repository
     * @param requirementRepo document requirement repository
     * @param submissionRepo document submission repository
     * @param reviewRepo document review repository
     * @param procedureRepo authority procedure repository
     * @param clearanceRepo compliance clearance repository
     * @param caseAccess case assignment boundary for staff operations
     * @param actors live actor resolver
     * @param audit transactional audit writer
     * @param rules sourced rule validator
     * @param jdbc named-parameter JDBC template for inline inserts
     */
    public ComplianceService(DocumentTypeCatalogRepository catalogRepo,
            HorseDocumentRepository horseDocRepo,
            DocumentVersionRepository docVersionRepo,
            DocumentRequirementRepository requirementRepo,
            DocumentSubmissionRepository submissionRepo,
            DocumentReviewRepository reviewRepo,
            AuthorityProcedureRepository procedureRepo,
            ComplianceClearanceRepository clearanceRepo,
            CaseAssignmentAccess caseAccess,
            CurrentActorService actors,
            ComplianceAuditRepository audit,
            ComplianceRuleRepository rules,
            NamedParameterJdbcTemplate jdbc) {
        this.catalogRepo = catalogRepo;
        this.horseDocRepo = horseDocRepo;
        this.docVersionRepo = docVersionRepo;
        this.requirementRepo = requirementRepo;
        this.submissionRepo = submissionRepo;
        this.reviewRepo = reviewRepo;
        this.procedureRepo = procedureRepo;
        this.clearanceRepo = clearanceRepo;
        this.caseAccess = caseAccess;
        this.actors = actors;
        this.audit = audit;
        this.rules = rules;
        this.jdbc = jdbc;
    }


    /** Wires revision reassessment in the running application.
     * @param value sourced checklist synchronizer
     */
    @Autowired
    public void setRevisionService(ComplianceRevisionService value) {
        revisionService = value;
    }

    /** Configures a private durable volume, independent of the temp folder.
     * @param root persistent storage directory
     */
    @Value("${be2.compliance.storage-root:.local/compliance-files}")
    public void configureStorageRoot(String root) {
        Path configured = Path.of(root).toAbsolutePath().normalize();
        synchronized (PERSISTENT_REGISTRY) {
            if (!configured.equals(storageRoot)) {
                PERSISTENT_REGISTRY.clear();
                storageRoot = configured;
            }
        }
    }

    // Catalog (public read)

    /**
     * Returns all active document type codes available for use.
     * @return active catalog entries
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff() or"
            + " @domainAccessPolicy.isCustomer()")
    public List<DocumentTypeCatalogResponse> listActiveCatalog() {
        return catalogRepo.listActive().stream()
                .map(c -> new DocumentTypeCatalogResponse(
                        c.getTypeCode(), c.getDisplayName(),
                        c.getDescription(), c.isActive()))
                .toList();
    }

    // Horse Documents (customer read, specialist manage)

    /**
     * Lists all document records for a horse owned by the current customer.
     * @param horseId the horse key
     * @return document list
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCustomer()")
    public List<HorseDocumentResponse> listMyHorseDocuments(
            @Positive long horseId) {
        long customerId = actors.current().customerId();
        horseDocRepo.verifyOwnership(horseId, customerId);
        return horseDocRepo.findByHorseId(horseId).stream()
                .map(this::toHorseDocResponse).toList();
    }

    /**
     * Returns all versions of a specific horse document visible to the
     * customer.
     * @param horseId the owned horse key
     * @param documentId the document key
     * @return version list
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCustomer()")
    public List<DocumentVersionResponse> listMyDocumentVersions(
            @Positive long horseId, @Positive long documentId) {
        long customerId = actors.current().customerId();
        horseDocRepo.verifyOwnership(horseId, customerId);
        horseDocRepo.verifyDocumentOwnership(documentId, customerId);
        HorseDocument document = horseDocRepo.findById(documentId)
                .orElseThrow(this::notFound);
        require(document.getHorseId() == horseId);
        return docVersionRepo.findByDocumentId(documentId).stream()
                .map(this::toDocVersionResponse).toList();
    }

    /**
     * Uploads a new version of a horse document (append-only).
     * The fileKey is an opaque storage reference already issued by the
     * upstream storage service; this service only persists metadata.
     * @param horseId the horse key
     * @param documentType the document type code
     * @param fileKey the opaque storage key (never a public URL)
     * @param originalFileName the original file name for display
     * @param contentType the MIME type
     * @param fileSizeBytes the file size in bytes
     * @param documentNumber optional document number on the physical document
     * @param issuedDate optional issue date
     * @param expiryDate optional expiry date
     * @param fileHash optional content hash for integrity
     * @return the new version response
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCustomer()")
    public DocumentVersionResponse uploadDocumentVersion(
            @Positive long horseId,
            String documentType,
            String fileKey,
            String originalFileName,
            String contentType,
            Long fileSizeBytes,
            String documentNumber,
            java.time.LocalDate issuedDate,
            java.time.LocalDate expiryDate,
            String fileHash) {
        long customerId = actors.current().customerId();
        horseDocRepo.verifyOwnership(horseId, customerId);
        if (fileKey == null || fileKey.isBlank()) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_KEY_REQUIRED",
                    "A non-blank storage fileKey is required.");
        }
        String type = documentType.trim().toUpperCase(Locale.ROOT);
        boolean typeKnown = catalogRepo.listActive().stream()
                .anyMatch(c -> c.getTypeCode().equals(type));
        if (!typeKnown) {
            throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "COMPLIANCE_UNKNOWN_DOCUMENT_TYPE",
                    "Document type is not in the active catalog.");
        }
        VerifiedStorageFile stored = loadStoredFile(fileKey);
        if (stored == null) {
            throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "COMPLIANCE_FILE_NOT_VERIFIED",
                    "File key has not been verified by storage service.");
        }
        if (stored.horseId() != horseId) {
            throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "COMPLIANCE_FILE_NOT_VERIFIED",
                    "File key belongs to another horse.");
        }
        if (stored.customerId() != customerId) {
            throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "COMPLIANCE_FILE_NOT_VERIFIED",
                    "File key belongs to another customer.");
        }
        long size = fileSizeBytes != null
                ? fileSizeBytes : stored.fileSizeBytes();
        if (size <= 0 || size > 10L * 1024 * 1024) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_SIZE_INVALID",
                    "File size must be positive and not exceed 10MB.");
        }
        if (fileSizeBytes != null && fileSizeBytes != stored.fileSizeBytes()) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_SIZE_MISMATCH",
                    "Declared file size does not match verified size.");
        }
        String effectiveHash = fileHash != null
                ? fileHash.trim().toLowerCase(Locale.ROOT)
                : stored.fileHash();
        if (!effectiveHash.matches("^[a-f0-9]{64}$")) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_HASH_INVALID",
                    "File hash must be a valid 64-character SHA-256 hex"
                            + " string.");
        }
        if (fileHash != null && !fileHash.equalsIgnoreCase(stored.fileHash())) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_HASH_MISMATCH",
                    "Declared file hash does not match verified storage hash.");
        }
        horseDocRepo.lockHorse(horseId, customerId);
        var existing = docVersionRepo.findByFileKey(fileKey);
        if (existing.isPresent()) {
            DocumentVersion prior = existing.get();
            HorseDocument owner = horseDocRepo.findById(prior.getDocumentId())
                    .orElseThrow(this::notFound);
            if (owner.getHorseId() != horseId
                    || !type.equals(owner.getDocumentType())
                    || !Objects.equals(prior.getDocumentNumber(),
                        documentNumber)
                    || !Objects.equals(prior.getIssuedDate(), issuedDate)
                    || !Objects.equals(prior.getExpiryDate(), expiryDate)) {
                throw new ComplianceException(HttpStatus.CONFLICT,
                        "COMPLIANCE_UPLOAD_REPLAY_CONFLICT",
                        "Storage key is already registered with other "
                        + "metadata.");
            }
            return toDocVersionResponse(prior);
        }
        HorseDocument doc = horseDocRepo
                .findByHorseIdAndType(horseId, type)
                .orElseGet(() -> {
                    HorseDocument nd = new HorseDocument();
                    nd.setHorseId(horseId);
                    nd.setDocumentType(type);
                    long newId = horseDocRepo.insert(nd);
                    return horseDocRepo.findById(newId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "HorseDocument identity unavailable."));
                });
        int nextVer = docVersionRepo.maxVersionNo(
                doc.getDocumentId()) + 1;
        DocumentVersion ver = new DocumentVersion();
        ver.setDocumentId(doc.getDocumentId());
        ver.setVersionNo(nextVer);
        ver.setFileKey(fileKey);
        ver.setOriginalFileName(originalFileName != null
                ? originalFileName : stored.originalFileName());
        ver.setContentType(stored.contentType());
        ver.setFileSizeBytes(size);
        ver.setDocumentNumber(documentNumber);
        ver.setIssuedDate(issuedDate);
        ver.setExpiryDate(expiryDate);
        ver.setFileHash(effectiveHash);
        ver.setUploadedBy(actors.current().userId());
        long versionId = docVersionRepo.insert(ver);
        audit.append(0L, actors.current().userId(),
                "DOCUMENT_VERSION_UPLOADED", versionId);
        return toDocVersionResponse(docVersionRepo.findById(versionId)
                .orElseThrow(() -> new IllegalStateException(
                        "DocumentVersion identity unavailable.")));
    }

    /**
     * Verifies and stores uploaded binary file bytes for an owned horse.
     * Enforces magic bytes verification (PDF %PDF-, JPEG, PNG), size <= 10MB,
     * computes real SHA-256 hash, generates an opaque storage key, and writes
     * defensively to persistent storage.
     *
     * @param horseId the owned horse key
     * @param originalFileName original name of the file
     * @param contentType declared MIME type
     * @param bytes binary file payload
     * @return the verified file record
     */
    public VerifiedStorageFile storeDocumentFile(
            long horseId,
            String originalFileName,
            String contentType,
            byte[] bytes) {
        long customerId = actors.current().customerId();
        horseDocRepo.verifyOwnership(horseId, customerId);
        if (bytes == null || bytes.length == 0) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_EMPTY",
                    "Uploaded file content must not be empty.");
        }
        if (bytes.length > 10L * 1024 * 1024) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_SIZE_INVALID",
                    "File size exceeds maximum permitted 10MB.");
        }
        byte[] safeBytes = bytes.clone();
        String cleanType = validateMagicBytes(contentType, safeBytes);
        String sha256 = computeSha256(safeBytes);
        String ext = switch (cleanType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
        String fileKey = "compliance/h" + horseId + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + ext;
        persistFileContent(fileKey, safeBytes);
        VerifiedStorageFile stored = new VerifiedStorageFile(
                fileKey, horseId, customerId, originalFileName,
                cleanType, (long) safeBytes.length, sha256,
                safeBytes.clone(),
                        OffsetDateTime.now(ZoneOffset.UTC).withNano(0));
        persistStorageMetadata(stored);
        PERSISTENT_REGISTRY.put(fileKey, stored);
        return stored;
    }

    /**
     * Retrieves the verified binary content of a stored document version.
     * Ensures horse ownership and document linkage before streaming content.
     * Returns a defensive clone of the binary content.
     *
     * @param horseId the owned horse key
     * @param versionId the document version key
     * @return binary content of the verified file
     */
    public byte[] getDocumentVersionFile(long horseId, long versionId) {
        DocumentVersion version = docVersionRepo.findById(versionId)
                .orElseThrow(() -> new ComplianceException(
                        HttpStatus.NOT_FOUND,
                        "DOCUMENT_VERSION_NOT_FOUND",
                        "Document version not found."));
        HorseDocument doc = horseDocRepo.findById(version.getDocumentId())
                .orElseThrow(() -> new ComplianceException(
                        HttpStatus.NOT_FOUND,
                        "HORSE_DOCUMENT_NOT_FOUND",
                        "Horse document not found."));
        if (doc.getHorseId() != horseId) {
            throw new ComplianceException(HttpStatus.NOT_FOUND,
                    "DOCUMENT_VERSION_NOT_FOUND",
                    "Document version does not belong to the specified horse.");
        }
        if (actors.current().customerId() != null) {
            long customerId = actors.current().customerId();
            horseDocRepo.verifyOwnership(doc.getHorseId(), customerId);
            horseDocRepo.verifyDocumentOwnership(
                    doc.getDocumentId(), customerId);
        } else {
            throw new ComplianceException(HttpStatus.FORBIDDEN,
                    "COMPLIANCE_FORBIDDEN",
                    "Customer ownership is required for horse document"
                            + " file access.");
        }
        byte[] content = retrieveFileContent(version.getFileKey());
        if (content == null) {
            throw new ComplianceException(HttpStatus.NOT_FOUND,
                    "COMPLIANCE_FILE_NOT_FOUND",
                    "Underlying file content not found in storage.");
        }
        if (version.getFileHash() != null
                && !computeSha256(content).equals(version.getFileHash())) {
            throw new ComplianceException(HttpStatus.CONFLICT,
                    "COMPLIANCE_FILE_INTEGRITY_FAILED",
                    "Stored bytes differ from the immutable version hash.");
        }
        return content.clone();
    }

    /**
     * Retrieves the binary content of a document version submitted for a
     * requirement on an assigned order. Enforces specialist case assignment.
     *
     * @param orderId the assigned transport order key
     * @param requirementId the requirement key
     * @param versionId the document version key
     * @return binary file bytes
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public byte[] getOrderDocumentVersionFile(
            long orderId, long requirementId, long versionId) {
        long revisionId = caseAccess.lockRevision(
                orderId, CaseRole.SPECIALIST);
        DocumentRequirement req = requirementRepo.findById(requirementId)
                .orElseThrow(this::notFound);
        if (req.getRevisionId() != revisionId) {
            throw new ComplianceException(HttpStatus.NOT_FOUND,
                    "COMPLIANCE_REQUIREMENT_NOT_FOUND",
                    "Requirement does not belong to the assigned revision.");
        }
        long latestSubId = submissionRepo.latestId(requirementId);
        if (latestSubId <= 0) {
            throw new ComplianceException(HttpStatus.NOT_FOUND,
                    "COMPLIANCE_SUBMISSION_NOT_FOUND",
                    "No submission found for this requirement.");
        }
        DocumentSubmission sub = submissionRepo.findById(latestSubId)
                .orElseThrow(() -> new ComplianceException(
                        HttpStatus.NOT_FOUND,
                        "COMPLIANCE_SUBMISSION_NOT_FOUND",
                        "Submission record not found."));
        if (sub.getDocumentVersionId() != versionId) {
            throw new ComplianceException(HttpStatus.NOT_FOUND,
                    "COMPLIANCE_VERSION_NOT_SUBMITTED",
                    "Document version was not submitted for this"
                            + " requirement.");
        }
        DocumentVersion version = docVersionRepo.findById(versionId)
                .orElseThrow(() -> new ComplianceException(
                        HttpStatus.NOT_FOUND,
                        "DOCUMENT_VERSION_NOT_FOUND",
                        "Document version not found."));
        HorseDocument doc = horseDocRepo.findById(version.getDocumentId())
                .orElseThrow(() -> new ComplianceException(
                        HttpStatus.NOT_FOUND,
                        "HORSE_DOCUMENT_NOT_FOUND",
                        "Horse document not found."));
        if (req.getHorseId() != null
                && req.getHorseId() != doc.getHorseId()) {
            throw new ComplianceException(HttpStatus.NOT_FOUND,
                    "COMPLIANCE_HORSE_MISMATCH",
                    "Document belongs to another horse.");
        }
        if (req.getDocumentType() != null
                && !req.getDocumentType().equals(doc.getDocumentType())) {
            throw new ComplianceException(HttpStatus.NOT_FOUND,
                    "COMPLIANCE_TYPE_MISMATCH",
                    "Document type does not match requirement.");
        }
        byte[] content = retrieveFileContent(version.getFileKey());
        if (content == null) {
            throw new ComplianceException(HttpStatus.NOT_FOUND,
                    "COMPLIANCE_FILE_NOT_FOUND",
                    "Underlying file content not found in storage.");
        }
        if (version.getFileHash() != null
                && !computeSha256(content).equals(version.getFileHash())) {
            throw new ComplianceException(HttpStatus.CONFLICT,
                    "COMPLIANCE_FILE_INTEGRITY_FAILED",
                    "Stored bytes differ from the immutable version hash.");
        }
        return content.clone();
    }

    /**
     * Registers a pre-verified file into the storage registry.
     *
     * @param horseId the horse key
     * @param customerId the owning customer key
     * @param fileKey the unique storage key
     * @param originalFileName the file name
     * @param contentType MIME type
     * @param bytes binary content
     * @return the verified file record
     */
    public VerifiedStorageFile registerVerifiedFile(
            long horseId,
            long customerId,
            String fileKey,
            String originalFileName,
            String contentType,
            byte[] bytes) {
        byte[] safeBytes = bytes == null ? new byte[0] : bytes.clone();
        String cleanType = validateMagicBytes(contentType, safeBytes);
        String sha256 = computeSha256(safeBytes);
        persistFileContent(fileKey, safeBytes);
        VerifiedStorageFile stored = new VerifiedStorageFile(
                fileKey, horseId, customerId, originalFileName,
                cleanType, (long) safeBytes.length, sha256,
                safeBytes.clone(),
                        OffsetDateTime.now(ZoneOffset.UTC).withNano(0));
        persistStorageMetadata(stored);
        PERSISTENT_REGISTRY.put(fileKey, stored);
        return stored;
    }

    private static void persistFileContent(String fileKey, byte[] data) {
        try {
            Path target = resolveSecurePath(fileKey);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, data,
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed writing file to persistent storage", ex);
        }
    }

    private static void persistStorageMetadata(VerifiedStorageFile file) {
        try {
            Path metaTarget = resolveSecurePath(file.fileKey() + ".meta");
            if (metaTarget.getParent() != null) {
                Files.createDirectories(metaTarget.getParent());
            }
            Properties props = new Properties();
            props.setProperty("horseId", String.valueOf(file.horseId()));
            props.setProperty("customerId",
                    String.valueOf(file.customerId()));
            props.setProperty("originalFileName",
                    file.originalFileName() == null ? ""
                            : file.originalFileName());
            props.setProperty("contentType",
                    file.contentType() == null ? "" : file.contentType());
            props.setProperty("fileSizeBytes",
                    String.valueOf(file.fileSizeBytes()));
            props.setProperty("fileHash",
                    file.fileHash() == null ? "" : file.fileHash());
            props.setProperty("storedAt",
                    file.storedAt() == null ? ""
                            : file.storedAt().toString());
            try (OutputStream out = Files.newOutputStream(metaTarget,
                    StandardOpenOption.CREATE_NEW)) {
                props.store(out, "Compliance Storage Metadata");
            }
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed writing storage metadata", ex);
        }
    }

    private static VerifiedStorageFile loadStoredFile(String fileKey) {
        try {
            Path diskPath = resolveSecurePath(fileKey);
            Path metaPath = resolveSecurePath(fileKey + ".meta");
            if (Files.exists(diskPath) && Files.exists(metaPath)) {
                if (Files.size(diskPath) > 10L * 1024 * 1024) {
                    return null;
                }
                byte[] diskBytes = Files.readAllBytes(diskPath);
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(metaPath)) {
                    props.load(in);
                }
                long horseId = Long.parseLong(
                        props.getProperty("horseId"));
                long customerId = Long.parseLong(
                        props.getProperty("customerId"));
                String originalFileName =
                        props.getProperty("originalFileName");
                String contentType = props.getProperty("contentType");
                long size = Long.parseLong(
                        props.getProperty("fileSizeBytes"));
                String hash = props.getProperty("fileHash");
                if (size <= 0 || size > 10L * 1024 * 1024
                        || size != diskBytes.length
                        || !computeSha256(diskBytes).equals(hash)) {
                    return null;
                }
                String storedAtStr = props.getProperty("storedAt");
                OffsetDateTime storedAt = (storedAtStr == null
                        || storedAtStr.isBlank())
                        ? OffsetDateTime.now(ZoneOffset.UTC)
                        : OffsetDateTime.parse(storedAtStr);
                VerifiedStorageFile loaded = new VerifiedStorageFile(
                        fileKey, horseId, customerId,
                        originalFileName, contentType,
                        size, hash, diskBytes.clone(), storedAt);
                PERSISTENT_REGISTRY.put(fileKey, loaded);
                return loaded;
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }

    private static byte[] retrieveFileContent(String fileKey) {
        VerifiedStorageFile stored = loadStoredFile(fileKey);
        return stored == null ? null : stored.content();
    }

    private static Path resolveSecurePath(String fileKey) {
        if (fileKey == null || fileKey.isBlank() || fileKey.contains("..")) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_KEY_INVALID",
                    "Invalid storage key path.");
        }
        Path normalized = storageRoot.resolve(
                fileKey.replace('/', File.separatorChar)).normalize();
        if (!normalized.startsWith(storageRoot.normalize())) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_KEY_INVALID",
                    "Path traversal detected.");
        }
        return normalized;
    }

    private String validateMagicBytes(String contentType, byte[] bytes) {
        if (contentType == null || contentType.isBlank()) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FILE_CONTENT_TYPE_INVALID",
                    "Content type must not be blank.");
        }
        String mime = contentType.trim().toLowerCase(Locale.ROOT);
        if ("application/pdf".equals(mime)) {
            if (bytes.length < 5
                    || bytes[0] != 0x25 || bytes[1] != 0x50
                    || bytes[2] != 0x44 || bytes[3] != 0x46
                    || bytes[4] != 0x2D) {
                throw new ComplianceException(HttpStatus.BAD_REQUEST,
                        "COMPLIANCE_FILE_MAGIC_BYTES_MISMATCH",
                        "File content does not match PDF magic bytes (%PDF-).");
            }
            return "application/pdf";
        }
        if ("image/jpeg".equals(mime) || "image/jpg".equals(mime)) {
            if (bytes.length < 3
                    || (bytes[0] & 0xFF) != 0xFF
                    || (bytes[1] & 0xFF) != 0xD8
                    || (bytes[2] & 0xFF) != 0xFF) {
                throw new ComplianceException(HttpStatus.BAD_REQUEST,
                        "COMPLIANCE_FILE_MAGIC_BYTES_MISMATCH",
                        "File content does not match JPEG magic bytes.");
            }
            return "image/jpeg";
        }
        if ("image/png".equals(mime)) {
            if (bytes.length < 8
                    || (bytes[0] & 0xFF) != 0x89
                    || bytes[1] != 0x50 || bytes[2] != 0x4E || bytes[3] != 0x47
                    || bytes[4] != 0x0D || bytes[5] != 0x0A || bytes[6] != 0x1A
                    || bytes[7] != 0x0A) {
                throw new ComplianceException(HttpStatus.BAD_REQUEST,
                        "COMPLIANCE_FILE_MAGIC_BYTES_MISMATCH",
                        "File content does not match PNG magic bytes.");
            }
            return "image/png";
        }
        throw new ComplianceException(HttpStatus.BAD_REQUEST,
                "COMPLIANCE_FILE_CONTENT_TYPE_UNSUPPORTED",
                "Unsupported file type: " + mime + ". Permitted types are"
                        + " application/pdf, image/jpeg, image/png.");
    }

    private static String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 algorithm unavailable", ex);
        }
    }

    // Document Requirements (specialist manages, customer reads)

    /**
     * Returns the paged checklist of document requirements for an order
     * revision. Page and size parameters avoid unbounded result sets.
     * @param orderId the order key
     * @param page zero-based page index
     * @param size number of rows to return (1-100)
     * @return requirement checklist page
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff() or"
            + " @domainAccessPolicy.isCustomer()")
    public List<DocumentRequirementResponse> listRequirements(
            @Positive long orderId,
            @Min(0) int page,
            @Min(1) int size) {
        long revisionId = resolveRevisionId(orderId);
        return requirementRepo
                .findByRevisionIdPaged(revisionId, page, size).stream()
                .map(this::toRequirementResponse).toList();
    }

    /**
     * Returns a bounded page response of the document requirements checklist.
     * @param orderId the order key
     * @param page zero-based page index
     * @param size number of rows per page
     * @return bounded requirement page
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff() or"
            + " @domainAccessPolicy.isCustomer()")
    public DocumentRequirementPageResponse listRequirementsPaged(
            @Positive long orderId,
            @Min(0) int page,
            @Min(1) int size) {
        List<DocumentRequirementResponse> items = listRequirements(
                orderId, page, size);
        long totalElements = countRequirements(orderId);
        int totalPages = size == 0 ? 0
                : (int) Math.ceil((double) totalElements / size);
        return new DocumentRequirementPageResponse(
                items, page, size, totalElements, totalPages);
    }

    /**
     * Counts the total document requirements for an order; used by callers
     * to compute pagination totals.
     * @param orderId the order key
     * @return total requirement count
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff() or"
            + " @domainAccessPolicy.isCustomer()")
    public long countRequirements(@Positive long orderId) {
        long revisionId = resolveRevisionId(orderId);
        return requirementRepo.countByRevisionId(revisionId);
    }

    /**
     * Specialist adds a document requirement to an assigned order's checklist.
     * The chosen rule must match the revision's origin, destination, optional
     * transit country, and departure date window.
     * @param orderId the assigned order key
     * @param request the requirement details
     * @return the created requirement
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public DocumentRequirementResponse addRequirement(
            @Positive long orderId,
            @Valid DocumentRequirementRequest request) {
        long revisionId = caseAccess.lockRevision(
                orderId, CaseRole.SPECIALIST);
        rules.requireRouteMatchedRule(request.ruleId(),
                request.documentType(), request.phaseCode(), revisionId);
        if (request.horseId() != null) {
            rules.requireOrderHorse(orderId, request.horseId());
        }
        DocumentRequirement req = new DocumentRequirement();
        req.setRevisionId(revisionId);
        req.setRuleId(request.ruleId());
        req.setDocumentType(request.documentType()
                .trim().toUpperCase(Locale.ROOT));
        req.setPhaseCode(request.phaseCode()
                .trim().toUpperCase(Locale.ROOT));
        req.setMandatory(request.mandatory());
        req.setStatus("PENDING");
        req.setHorseId(request.horseId());
        req.setScopeCode(
                request.horseId() == null ? "SHIPMENT" : "HORSE");
        req.setInstructions(request.instructions());
        req.setCaseJustification(request.caseJustification());
        req.setDueAt(request.dueAt());
        long id = requirementRepo.insert(req);
        audit.append(orderId, actors.current().userId(),
                "REQUIREMENT_CREATED", id);
        return toRequirementResponse(requirementRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Requirement identity unavailable.")));
    }

    // Document Submissions (customer submits)

    /**
     * Customer submits a document version against a specific requirement.
     * Creates an append-only submission record and marks the requirement
     * as PENDING_REVIEW.
     * @param orderId the order key
     * @param requirementId the requirement to fulfil
     * @param request submission payload with documentVersionId
     * @return the created submission
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCustomer()")
    public DocumentSubmissionResponse submitDocument(
            @Positive long orderId,
            @Positive long requirementId,
            @Valid DocumentSubmissionRequest request) {
        long customerId = actors.current().customerId();
        long revision = resolveRevisionId(orderId);
        DocumentRequirement requirement = requirementRepo
                .findById(requirementId)
                .orElseThrow(this::notFound);
        require(requirement.getRevisionId() == revision);
        DocumentVersion ver = docVersionRepo.findById(
                request.documentVersionId()).orElseThrow(
                        () -> new ComplianceException(HttpStatus.NOT_FOUND,
                                "COMPLIANCE_VERSION_NOT_FOUND",
                                "Document version was not found."));
        horseDocRepo.verifyDocumentOwnership(
                ver.getDocumentId(), customerId);
        require(ver.getVersionNo() == docVersionRepo.maxVersionNo(
                ver.getDocumentId()));
        HorseDocument document = horseDocRepo
                .findById(ver.getDocumentId())
                .orElseThrow(this::notFound);
        require(document.getDocumentType()
                .equals(requirement.getDocumentType()));
        if (requirement.getHorseId() != null) {
            require(requirement.getHorseId() == document.getHorseId());
        }
        DocumentSubmission submission = new DocumentSubmission();
        submission.setRequirementId(requirementId);
        submission.setDocumentVersionId(request.documentVersionId());
        long id = submissionRepo.insert(submission);
        audit.append(orderId, actors.current().userId(),
                "DOCUMENT_SUBMITTED", id);
        requirementRepo.updateStatus(requirementId, "PENDING_REVIEW");
        return toSubmissionResponse(submissionRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Submission identity unavailable.")));
    }

    /** Reads public review feedback without exposing internal storage keys.
     * @param orderId owned or assigned order
     * @param requirementId current or historical requirement of that order
     * @param page zero-based page
     * @param size bounded page size
     * @return immutable submission history
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff() or"
            + " @domainAccessPolicy.isCustomer()")
    public DocumentHistoryPageResponse submissionHistory(long orderId,
            long requirementId, @Min(0) int page,
            @Min(1) @jakarta.validation.constraints.Max(100) int size) {
        resolveRevisionId(orderId);
        Long owned = jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.DocumentRequirement q JOIN dbo.OrderRevision r"
                + " ON r.RevisionID=q.RevisionID WHERE q.RequirementID=:req"
                + " AND r.OrderID=:order", new MapSqlParameterSource(
                        "req", requirementId).addValue("order", orderId),
                Long.class);
        if (owned == null || owned != 1) {
            throw notFound();
        }
        return submissionRepo.history(requirementId, page, size);
    }

    // Document Reviews (specialist reviews)

    /**
     * Specialist records an append-only review decision on a submission.
     * APPROVED marks the requirement COMPLIANT. CHANGES_REQUESTED or
     * REJECTED returns it to PENDING; public feedback is mandatory for
     * those states.
     * @param orderId the assigned order key
     * @param submissionId the submission being reviewed
     * @param request the review decision
     * @return the created review entry
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public DocumentReviewResponse reviewSubmission(
            @Positive long orderId,
            @Positive long submissionId,
            @Valid DocumentReviewRequest request) {
        long revision = caseAccess.lockRevision(
                orderId, CaseRole.SPECIALIST);
        long staffId = actors.current().staffId();
        DocumentSubmission submission = submissionRepo
                .findById(submissionId)
                .orElseThrow(() -> new ComplianceException(
                        HttpStatus.NOT_FOUND,
                        "COMPLIANCE_SUBMISSION_NOT_FOUND",
                        "Submission was not found."));
        DocumentRequirement requirement = requirementRepo
                .findById(submission.getRequirementId())
                .orElseThrow(this::notFound);
        require(requirement.getRevisionId() == revision);
        require(submissionRepo.latestId(
                requirement.getRequirementId()) == submissionId);
        if (reviewRepo.alreadyReviewed(submissionId)) {
            throw new ComplianceException(HttpStatus.CONFLICT,
                    "COMPLIANCE_REVIEW_IMMUTABLE",
                    "Review already exists.");
        }
        String state = request.reviewState()
                .trim().toUpperCase(Locale.ROOT);
        validateReviewState(state);
        if ("APPROVED".equals(state)) {
            DocumentVersion ver = docVersionRepo.findById(
                    submission.getDocumentVersionId())
                    .orElseThrow(this::notFound);
            LocalDate cutoff = requirementRepo.validityCutoff(requirement);
            if ((ver.getExpiryDate() != null
                    && ver.getExpiryDate().isBefore(cutoff))
                    || (requirement.getValidityConditionsJson() != null
                        && ver.getExpiryDate() == null)
                    || (ver.getIssuedDate() != null && ver.getIssuedDate()
                        .isAfter(LocalDate.now(ZoneOffset.UTC)))) {
                throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "DOCUMENT_VERSION_EXPIRED",
                        "Cannot approve an expired document version.");
            }
        }
        if (!"APPROVED".equals(state)
                && (request.publicNote() == null
                        || request.publicNote().isBlank())) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_FEEDBACK_REQUIRED",
                    "Review feedback is required for non-approval.");
        }
        DocumentReview review = new DocumentReview();
        review.setSubmissionId(submissionId);
        review.setReviewedBy(staffId);
        review.setDecision(state);
        review.setFeedback(request.publicNote());
        long id = reviewRepo.insert(review);
        audit.append(orderId, actors.current().userId(),
                "DOCUMENT_REVIEWED", id);
        String newStatus = "APPROVED".equals(state)
                ? "COMPLIANT" : "PENDING";
        requirementRepo.updateStatus(
                submission.getRequirementId(), newStatus);
        DocumentReview saved = reviewRepo.findById(id).orElseThrow(
                () -> new IllegalStateException(
                        "Review identity unavailable."));
        return new DocumentReviewResponse(saved.getReviewId(),
                saved.getSubmissionId(), saved.getReviewedBy(),
                saved.getDecision(), saved.getFeedback(),
                saved.getReviewedAt());
    }

    // Authority Procedures (specialist manages)

    /**
     * Lists all authority procedures recorded for an order revision.
     * @param orderId the order key
     * @return procedure list
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public List<AuthorityProcedureResponse> listProcedures(
            @Positive long orderId) {
        long revisionId = resolveRevisionId(orderId);
        return procedureRepo.findByRevisionId(revisionId).stream()
                .map(this::toProcedureResponse).toList();
    }

    /**
     * Returns a bounded page of authority procedures for an order revision.
     * @param orderId the order key
     * @param page zero-based page index
     * @param size number of rows per page
     * @return bounded authority procedure page
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public AuthorityProcedurePageResponse listProceduresPaged(
            @Positive long orderId,
            @Min(0) int page,
            @Min(1) int size) {
        long revisionId = resolveRevisionId(orderId);
        List<AuthorityProcedureResponse> items = procedureRepo
                .findByRevisionIdPaged(revisionId, page, size).stream()
                .map(this::toProcedureResponse)
                .toList();
        long total = procedureRepo.countByRevisionId(revisionId);
        int totalPages = total == 0 ? 0
                : (int) Math.ceil((double) total / size);
        return new AuthorityProcedurePageResponse(
                items, page, size, total, totalPages);
    }

    /**
     * Records a new authority procedure contact for an assigned order.
     * @param orderId the assigned order key
     * @param request the procedure details
     * @return the created procedure record
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public AuthorityProcedureResponse recordProcedure(
            @Positive long orderId,
            @Valid AuthorityProcedureRequest request) {
        long revisionId = caseAccess.lockRevision(
                orderId, CaseRole.SPECIALIST);
        long staffId = actors.current().staffId();
        AuthorityProcedure proc = new AuthorityProcedure();
        proc.setRevisionId(revisionId);
        proc.setProcedureCode(request.procedureCode()
                .trim().toUpperCase(Locale.ROOT));
        proc.setAuthorityName(request.authorityName().trim());
        proc.setApplicationReference(request.applicationReference());
        proc.setApprovalStatus(request.approvalStatus()
                .trim().toUpperCase(Locale.ROOT));
        proc.setEvidenceFileKey(request.evidenceFileKey());
        proc.setPhaseCode(request.phaseCode() == null ? null
                : request.phaseCode().trim().toUpperCase(Locale.ROOT));
        proc.setAuthorityContact(request.authorityContact());
        proc.setHoldReason(request.holdReason());
        proc.setExpiresAt(request.expiresAt() == null ? null
                : request.expiresAt()
                        .withOffsetSameInstant(ZoneOffset.UTC));
        proc.setDecidedAt(request.decidedAt() == null ? null
                : request.decidedAt()
                        .withOffsetSameInstant(ZoneOffset.UTC));
        proc.setRecordedBy(staffId);
        validatePhaseCode(proc.getPhaseCode());
        validateProcedureStatus(proc.getApprovalStatus());
        if ("APPROVED".equals(proc.getApprovalStatus())) {
            if (proc.getApplicationReference() == null
                    || proc.getApplicationReference().isBlank()) {
                throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "AUTHORITY_APPROVAL_EVIDENCE_REQUIRED",
                        "Authority approval requires application reference"
                                + " or evidence.");
            }
            if (proc.getDecidedAt() == null) {
                proc.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC)
                        .withNano(0));
            }
        }
        long id = procedureRepo.insert(proc);
        audit.append(orderId, actors.current().userId(),
                "PROCEDURE_RECORDED", id);
        return toProcedureResponse(procedureRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Procedure identity unavailable.")));
    }

    /**
     * Updates the approval status of an existing authority procedure.
     * All five status values including APPROVED are valid here; APPROVED
     * reflects real confirmation received from the external authority.
     * @param orderId the assigned order key
     * @param procedureId the procedure to update
     * @param newStatus the new approval status
     * @return the updated procedure
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public AuthorityProcedureResponse updateProcedureStatus(
            @Positive long orderId,
            @Positive long procedureId,
            String newStatus) {
        long revision = caseAccess.lockRevision(
                orderId, CaseRole.SPECIALIST);
        String status = newStatus.trim().toUpperCase(Locale.ROOT);
        AuthorityProcedure procedure = procedureRepo
                .findById(procedureId)
                .orElseThrow(this::notFound);
        require(procedure.getRevisionId() == revision);
        validateProcedureStatus(status);
        if ("APPROVED".equals(status)) {
            if (procedure.getApplicationReference() == null
                    || procedure.getApplicationReference().isBlank()) {
                throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "AUTHORITY_APPROVAL_EVIDENCE_REQUIRED",
                        "Authority approval requires application reference"
                                + " or evidence.");
            }
        }
        OffsetDateTime decidedAt = "APPROVED".equals(status)
                ? (procedure.getDecidedAt() != null ? procedure.getDecidedAt()
                        : OffsetDateTime.now(ZoneOffset.UTC))
                : null;
        procedureRepo.updateStatus(procedureId, status, decidedAt);
        audit.append(orderId, actors.current().userId(),
                "PROCEDURE_STATUS", procedureId);
        return toProcedureResponse(procedureRepo.findById(procedureId)
                .orElseThrow(() -> new ComplianceException(
                        HttpStatus.NOT_FOUND,
                        "COMPLIANCE_PROCEDURE_NOT_FOUND",
                        "Authority procedure was not found.")));
    }

    // Compliance Clearance (specialist issues)

    /**
     * Lists all compliance clearance records for an order revision.
     * Accessible to both staff and customer (customer view hides specialist
     * identity and assessment notes).
     * @param orderId the order key
     * @return clearance list
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff() or"
            + " @domainAccessPolicy.isCustomer()")
    public List<ComplianceClearanceResponse> listClearances(
            @Positive long orderId) {
        long revisionId = resolveRevisionId(orderId);
        return clearanceRepo.findByRevisionId(revisionId).stream()
                .map(this::toClearanceResponse).toList();
    }

    /**
     * Specialist issues a compliance clearance for a specific phase.
     * Each phase is independent; PRE_EXPORT passing does not imply
     * QUARANTINE_RELEASE. PASS status is allowed when the specialist has
     * verified that all mandatory requirements for the phase are COMPLIANT.
     * @param orderId the assigned order key
     * @param request the clearance decision
     * @return the new clearance record
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public ComplianceClearanceResponse issueClearance(
            @Positive long orderId,
            @Valid ComplianceClearanceRequest request) {
        long revisionId = caseAccess.lockRevision(
                orderId, CaseRole.SPECIALIST);
        long staffId = actors.current().staffId();
        String phase = request.phaseCode()
                .trim().toUpperCase(Locale.ROOT);
        validatePhaseCode(phase);
        String status = request.status()
                .trim().toUpperCase(Locale.ROOT);
        validateClearanceStatus(status);
        reassess(orderId, revisionId);
        if ("PASS".equals(status)) {
            if (revisionService != null
                    && !revisionService.phaseCovered(revisionId, phase)) {
                throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "RULE_PENDING_ASSESSMENT",
                        "Every matching rule needs a reviewed checklist "
                        + "policy.");
            }
            List<DocumentRequirement> allReqs = requirementRepo
                    .findByRevisionId(revisionId);
            List<DocumentRequirement> phaseReqs = allReqs.stream()
                    .filter(r -> phase.equalsIgnoreCase(r.getPhaseCode()))
                    .toList();
            List<AuthorityProcedure> phaseProcedures = procedureRepo
                    .findByRevisionId(revisionId).stream()
                    .filter(p -> phase.equalsIgnoreCase(p.getPhaseCode()))
                    .toList();
            boolean hasApprovedProcedure = phaseProcedures.stream()
                    .anyMatch(p -> "APPROVED".equalsIgnoreCase(
                            p.getApprovalStatus())
                            && p.getApplicationReference() != null
                            && !p.getApplicationReference().isBlank());
            boolean hasUnapprovedProcedure = phaseProcedures.stream()
                    .anyMatch(p -> !"APPROVED".equalsIgnoreCase(
                            p.getApprovalStatus()));
            boolean hasQuarantineExemption = PHASE_QR.equalsIgnoreCase(phase)
                    && clearanceRepo.findLatestQuarantine(revisionId)
                            .map(qa -> APPL_NOT_REQUIRED.equalsIgnoreCase(
                                    qa.getApplicability()))
                            .orElse(false);
            if (phaseReqs.isEmpty() && !hasApprovedProcedure
                    && !hasQuarantineExemption) {
                throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "PHASE_RULE_COVERAGE_MISSING",
                        "Cannot issue PASS clearance without requirements or"
                                + " approved authority procedures for phase "
                                + phase + ".");
            }
            if (hasUnapprovedProcedure) {
                throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "PHASE_PROCEDURE_NOT_APPROVED",
                        "Cannot issue PASS clearance while authority procedures"
                                + " for phase " + phase
                                + " remain unapproved.");
            }
            if (requirementRepo.hasMandatoryBlocker(revisionId, phase)) {
                throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "PHASE_HAS_MANDATORY_BLOCKERS",
                        "Cannot issue PASS when phase has mandatory blockers.");
            }
        }
        if ("PASS".equals(status) && (request.expiresAt() == null
                || !request.expiresAt().isAfter(OffsetDateTime.now(
                        ZoneOffset.UTC)))) {
            throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "COMPLIANCE_CLEARANCE_EXPIRY_REQUIRED",
                    "PASS clearance requires a future expiry timestamp.");
        }
        ComplianceClearance clearance = new ComplianceClearance();
        clearance.setRevisionId(revisionId);
        clearance.setSpecialistId(staffId);
        clearance.setPhaseCode(phase);
        clearance.setStatus(status);
        clearance.setExpiresAt(request.expiresAt() == null ? null
                : request.expiresAt()
                        .withOffsetSameInstant(ZoneOffset.UTC));
        clearance.setAssessmentNotes(request.assessmentNotes());
        long id = clearanceRepo.insert(clearance);
        audit.append(orderId, actors.current().userId(),
                "PHASE_ASSESSED", id);
        return toClearanceResponse(clearanceRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Clearance identity unavailable.")));
    }

    // Quarantine Assessment (specialist records F08 quarantine result)

    /**
     * Specialist records the quarantine applicability and required days.
     * RequiredDays must be null for PENDING and NOT_REQUIRED; it must be
     * a positive integer for REQUIRED (DB enforces CHECK RequiredDays > 0).
     * @param orderId the assigned order key
     * @param request the quarantine assessment details
     * @return the recorded assessment summary
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public QuarantineAssessmentResponse recordQuarantineAssessment(
            @Positive long orderId,
            @Valid QuarantineAssessmentRequest request) {
        long revisionId = caseAccess.lockRevision(
                orderId, CaseRole.SPECIALIST);
        long userId = actors.current().userId();
        long staffId = actors.current().staffId();
        String appl = request.applicability()
                .trim().toUpperCase(Locale.ROOT);
        validateApplicability(appl, request.requiredDays(),
                request.sourceReference(), request.validUntil());
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC)
                .withNano(0);
        var params = new MapSqlParameterSource();
        params.addValue("RevisionID", revisionId);
        params.addValue("Applicability", appl);
        params.addValue("RequiredDays", request.requiredDays());
        params.addValue("RuleID", request.ruleId());
        params.addValue("AssessmentNotes", request.assessmentNotes());
        params.addValue("SpecialistID", staffId);
        params.addValue("AssessedBy", staffId);
        params.addValue("AssessedAt", nowUtc);
        params.addValue("ValidUntil", request.validUntil() == null ? null
                : request.validUntil()
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime());
        params.addValue("SourceReference", request.sourceReference());

        jdbc.update(
                "UPDATE dbo.RevisionQuarantineAssessment"
                + " SET SupersededAt = :AssessedAt"
                + " WHERE RevisionID = :RevisionID"
                + " AND SupersededAt IS NULL", params);
        jdbc.update("UPDATE dbo.QuarantineAssessment"
                + " SET SupersededAt=:AssessedAt WHERE RevisionID=:RevisionID"
                + " AND SupersededAt IS NULL", params);
        Long qaId = jdbc.queryForObject(
                "INSERT INTO dbo.QuarantineAssessment"
                + " (RevisionID,Applicability,RequiredDays,RuleID,"
                + "AssessmentNotes,SpecialistID,AssessedAt,"
                + "ValidUntil,SourceReference)"
                + " OUTPUT INSERTED.AssessmentID VALUES"
                + " (:RevisionID,:Applicability,:RequiredDays,:RuleID,"
                + ":AssessmentNotes,:SpecialistID,:AssessedAt,"
                + ":ValidUntil,:SourceReference)",
                params, Long.class);
        if (qaId == null) {
            throw new IllegalStateException(
                    "QuarantineAssessment identity unavailable.");
        }
        if (!APPL_PENDING.equals(appl)) {
            jdbc.update("INSERT INTO dbo.RevisionQuarantineAssessment"
                    + " (RevisionID,Applicability,RequiredDays,SpecialistID,"
                    + "AssessedAt,ValidUntil,SourceReference) VALUES"
                    + " (:RevisionID,:Applicability,:RequiredDays,"
                    + ":SpecialistID,"
                    + ":AssessedAt,:ValidUntil,:SourceReference)", params);
        }
        audit.append(orderId, userId, "QUARANTINE_ASSESSED", qaId);
        return new QuarantineAssessmentResponse(
                revisionId, appl, request.requiredDays(),
                OffsetDateTime.now(ZoneOffset.UTC).withNano(0));
    }

    // Compliance Readiness Facade (used by FR-11, FR-14, FR-05)

    /**
     * Returns the legal gate status for an order revision by evaluating
     * PRE_EXPORT clearance, mandatory blockers, and quarantine assessment.
     * legalGatePass is true only when PRE_EXPORT is cleared with no
     * mandatory blockers and a quarantine decision has been recorded.
     * @param orderId the order key
     * @return readiness summary with legal gate pass status
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff() or"
            + " @domainAccessPolicy.isCustomer()")
    public ComplianceReadinessResponse readiness(
            @Positive long orderId) {
        long revisionId = resolveRevisionId(orderId);
        boolean preExportPass =
                (revisionService == null
                    || revisionService.phaseCovered(revisionId,
                        PHASE_PRE_EXPORT))
                && clearanceRepo.hasValidPass(revisionId, PHASE_PRE_EXPORT)
                && !requirementRepo.hasMandatoryBlocker(
                        revisionId, PHASE_PRE_EXPORT);
        java.util.Optional<QuarantineAssessment> qOpt =
                clearanceRepo.findLatestQuarantine(revisionId);
        boolean quarantineReady;
        Integer days;
        if (qOpt.isEmpty()
                || APPL_PENDING.equals(qOpt.get().getApplicability())) {
            quarantineReady = false;
            days = null;
        } else if (APPL_NOT_REQUIRED.equals(
                qOpt.get().getApplicability())) {
            quarantineReady = true;
            days = null;
        } else {
            quarantineReady = true;
            days = qOpt.get().getRequiredDays();
        }
        List<String> blockerCodes = new ArrayList<>();
        if (!preExportPass) {
            blockerCodes.add("PRE_EXPORT_NOT_CLEARED");
        }
        if (!quarantineReady) {
            blockerCodes.add("QUARANTINE_NOT_ASSESSED");
        }
        List<String> blockerPhases = new ArrayList<>();
        if (!preExportPass) {
            blockerPhases.add(PHASE_PRE_EXPORT);
        }
        if (!quarantineReady) {
            blockerPhases.add(PHASE_QR);
        }
        return new ComplianceReadinessResponse(
                orderId, revisionId,
                PHASE_PRE_EXPORT,
                OffsetDateTime.now(ZoneOffset.UTC),
                preExportPass && quarantineReady,
                blockerPhases, days, blockerCodes);
    }

    /**
     * Returns true when the revision holds a valid QUARANTINE_RELEASE PASS.
     * FR-14 final handover gate must call this before allowing delivery.
     * @param orderId the order key
     * @return true only when quarantine release is cleared
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff() or"
            + " @domainAccessPolicy.isCustomer()")
    public boolean quarantineReleasePassed(@Positive long orderId) {
        long revisionId = resolveRevisionId(orderId);
        return (revisionService == null
                || revisionService.phaseCovered(revisionId, PHASE_QR))
                && clearanceRepo.hasValidPass(revisionId, PHASE_QR)
                && !requirementRepo.hasMandatoryBlocker(revisionId, PHASE_QR);

    }

    // Private helpers

    private long resolveRevisionId(long orderId) {
        var actor = actors.current();
        if ("CUSTOMER".equals(actor.roleCode())) {
            long revision = requirementRepo.currentOwnedRevisionId(
                    orderId, actor.customerId())
                    .orElseThrow(this::notFound);
            reassess(orderId, revision);
            return revision;
        }
        long revision = caseAccess.lockRevision(
                orderId, CaseRole.valueOf(actor.roleCode()));
        reassess(orderId, revision);
        return revision;
    }

    private void reassess(long orderId, long revision) {
        if (revisionService != null) {
            revisionService.synchronize(orderId, revision);
        }
    }

    private void validateReviewState(String state) {
        if (!"APPROVED".equals(state)
                && !"CHANGES_REQUESTED".equals(state)
                && !"REJECTED".equals(state)) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_INVALID_REVIEW_STATE",
                    "Review state must be APPROVED,"
                            + " CHANGES_REQUESTED or REJECTED.");
        }
    }

    private void validatePhaseCode(String phase) {
        if (phase == null) {
            return;
        }
        if (!PHASE_PRE_EXPORT.equals(phase)
                && !PHASE_TRANSIT.equals(phase)
                && !PHASE_ARRIVAL.equals(phase)
                && !PHASE_QR.equals(phase)) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_INVALID_PHASE",
                    "Phase must be PRE_EXPORT, TRANSIT, ARRIVAL or"
                            + " QUARANTINE_RELEASE.");
        }
    }

    private void validateClearanceStatus(String status) {
        if (!"PASS".equals(status) && !"FAIL".equals(status)
                && !"PENDING".equals(status)) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_INVALID_CLEARANCE_STATUS",
                    "Clearance status must be PASS, FAIL or PENDING.");
        }
    }

    private void validateProcedureStatus(String status) {
        if (!List.of("PENDING", "SUBMITTED", "APPROVED",
                "REJECTED", "ON_HOLD").contains(status)) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_INVALID_STATUS",
                    "Invalid procedure status.");
        }
    }

    private void validateApplicability(String appl, Integer days,
            String sourceRef, OffsetDateTime validUntil) {
        if (!APPL_PENDING.equals(appl)
                && !APPL_NOT_REQUIRED.equals(appl)
                && !APPL_REQUIRED.equals(appl)) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_INVALID_APPLICABILITY",
                    "Applicability must be PENDING, NOT_REQUIRED"
                            + " or REQUIRED.");
        }
        if (APPL_REQUIRED.equals(appl)
                && (days == null || days <= 0)) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_DAYS_REQUIRED",
                    "RequiredDays must be a positive integer for REQUIRED.");
        }
        if (!APPL_REQUIRED.equals(appl) && days != null) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_DAYS_NOT_APPLICABLE",
                    "RequiredDays must be null for non-REQUIRED states.");
        }
        if (APPL_REQUIRED.equals(appl) || APPL_NOT_REQUIRED.equals(appl)) {
            if (sourceRef == null || sourceRef.isBlank()) {
                throw new ComplianceException(HttpStatus.BAD_REQUEST,
                        "COMPLIANCE_SOURCE_REFERENCE_REQUIRED",
                        "Source reference is required for confirmed"
                                + " quarantine assessments.");
            }
            if (validUntil == null || validUntil.isBefore(
                    OffsetDateTime.now(ZoneOffset.UTC))) {
                throw new ComplianceException(HttpStatus.BAD_REQUEST,
                        "COMPLIANCE_VALID_UNTIL_REQUIRED",
                        "ValidUntil must be a future timestamp for confirmed"
                                + " quarantine assessments.");
            }
        }
        if (APPL_PENDING.equals(appl) && validUntil != null) {
            throw new ComplianceException(HttpStatus.BAD_REQUEST,
                    "COMPLIANCE_PENDING_VALID_UNTIL_NULL",
                    "ValidUntil must be null for PENDING assessments.");
        }
    }

    private HorseDocumentResponse toHorseDocResponse(
            HorseDocument doc) {
        return new HorseDocumentResponse(doc.getDocumentId(),
                doc.getHorseId(), doc.getDocumentType(),
                doc.getCreatedAt());
    }

    private DocumentVersionResponse toDocVersionResponse(
            DocumentVersion v) {
        return new DocumentVersionResponse(v.getDocumentVersionId(),
                v.getDocumentId(), v.getVersionNo(),
                v.getDocumentNumber(),
                v.getIssuedDate(), v.getExpiryDate(),
                v.getUploadedAt(), v.getOriginalFileName(),
                v.getContentType(), v.getFileSizeBytes());
    }

    private DocumentRequirementResponse toRequirementResponse(
            DocumentRequirement r) {
        return new DocumentRequirementResponse(r.getRequirementId(),
                r.getRevisionId(), r.getRuleId(), r.getDocumentType(),
                r.getPhaseCode(), r.isMandatory(), r.getStatus(),
                r.getInstructions(), r.getDueAt());
    }

    private DocumentSubmissionResponse toSubmissionResponse(
            DocumentSubmission s) {
        return new DocumentSubmissionResponse(s.getSubmissionId(),
                s.getRequirementId(), s.getDocumentVersionId(),
                s.getSubmittedAt());
    }

    private AuthorityProcedureResponse toProcedureResponse(
            AuthorityProcedure p) {
        return new AuthorityProcedureResponse(p.getProcedureId(),
                p.getRevisionId(), p.getProcedureCode(),
                p.getAuthorityName(), p.getApplicationReference(),
                p.getApprovalStatus(), p.getPhaseCode(),
                p.getAuthorityContact(), p.getHoldReason(),
                p.getDecidedAt(), p.getExpiresAt());
    }

    private ComplianceClearanceResponse toClearanceResponse(
            ComplianceClearance c) {
        return new ComplianceClearanceResponse(c.getClearanceId(),
                c.getRevisionId(),
                customerView() ? null : c.getSpecialistId(),
                c.getPhaseCode(), c.getStatus(), c.getCheckedAt(),
                c.getExpiresAt(),
                customerView() ? null : c.getAssessmentNotes());
    }

    private boolean customerView() {
        return "CUSTOMER".equals(actors.current().roleCode());
    }

    private void require(boolean condition) {
        if (!condition) {
            throw notFound();
        }
    }

    private ComplianceException notFound() {
        return new ComplianceException(HttpStatus.NOT_FOUND,
                "COMPLIANCE_NOT_FOUND",
                "Compliance resource not found.");
    }
}
