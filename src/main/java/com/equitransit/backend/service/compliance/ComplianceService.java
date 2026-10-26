/*
 * ComplianceService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.service.compliance;


import com.equitransit.backend.dto.compliance.ComplianceAssessmentRequest;
import com.equitransit.backend.dto.compliance.ComplianceAssessmentView;
import com.equitransit.backend.dto.compliance.ComplianceAuthorityRequest;
import com.equitransit.backend.dto.compliance.ComplianceAuthorityView;
import com.equitransit.backend.dto.compliance.ComplianceChecklistRequest;
import com.equitransit.backend.dto.compliance.request.ComplianceClearanceRequest;
import com.equitransit.backend.dto.compliance.ComplianceDocumentView;
import com.equitransit.backend.dto.compliance.ComplianceDocumentsView;
import com.equitransit.backend.dto.compliance.ComplianceFileRequest;
import com.equitransit.backend.dto.compliance.ComplianceFileView;
import com.equitransit.backend.dto.compliance.ComplianceRequirementView;
import com.equitransit.backend.dto.compliance.ComplianceReviewRequest;
import com.equitransit.backend.dto.compliance.ComplianceRevokeRequest;
import com.equitransit.backend.dto.compliance.ComplianceRuleRequest;
import com.equitransit.backend.dto.compliance.ComplianceRuleView;
import com.equitransit.backend.dto.compliance.ComplianceSubmissionRequest;
import com.equitransit.backend.dto.compliance.ComplianceUploadRequest;
import com.equitransit.backend.dto.compliance.ComplianceUploadSubmitRequest;
import com.equitransit.backend.dto.compliance.ComplianceAttachmentView;
import com.equitransit.backend.entity.compliance.RequirementStatus;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.dto.compliance.ComplianceReceipt;
import com.equitransit.backend.dto.compliance.ComplianceOverview;
import com.equitransit.backend.entity.compliance.CompliancePhase;
import com.equitransit.backend.entity.compliance.AuthorityDecision;
import com.equitransit.backend.entity.compliance.
        QuarantineApplicability;
import com.equitransit.backend.entity.activity.ActivityCode;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.compliance.ComplianceRepository;
import com.equitransit.backend.service.ComplianceAutomation;
import com.equitransit.backend.service.FileStorageService;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import static com.equitransit.backend.service.compliance.LegalEvidencePolicy.require;
import static com.equitransit.backend.service.compliance.ComplianceReadinessFacade.at;
import static com.equitransit.backend.service.compliance.ComplianceReadinessFacade.number;
import com.equitransit.backend.service.assessment.CaseAssignmentAccess;
import com.equitransit.backend.service.order.OrderCommitmentPolicy;
import com.equitransit.backend.service.activity.AuditWriter;


/** One order/horse lock order for immutable legal writes and gate reads. */
@Service
@Validated
@PreAuthorize("isAuthenticated()")
@Transactional(timeout = 15)
public class ComplianceService {

    private final ComplianceRepository data;
    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final CurrentActorService actors;
    private final CaseAssignmentAccess assignments;
    private final ComplianceReadinessFacade readiness;
    private final OrderCommitmentPolicy commitments;
    private final AuditWriter audit;
    private final LegalFilePolicy files;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ComplianceAutomation automation;
    private FileStorageService localFiles;


    /**
     * @param data legal persistence @param cases parent locks
     * @param orders ownership @param actors live identities
     * @param assignments BE1 assigned-case facade @param readiness F11 evidence
     * @param commitments estimate invalidation @param audit append-only events
     * @param files inspected private files @param json receipt encoding
     * @param mapper receipt decoding @param clock UTC clock
     * @param automation route and default checklist provisioning
     */
    public ComplianceService(ComplianceRepository data,
            AssessmentRepository cases, OrderRepository orders,
            CurrentActorService actors, CaseAssignmentAccess assignments,
            ComplianceReadinessFacade readiness,
            OrderCommitmentPolicy commitments, AuditWriter audit,
            LegalFilePolicy files, OrderJsonCodec json, ObjectMapper mapper,
            Clock clock, ComplianceAutomation automation) {
        this.data = data;
        this.cases = cases;
        this.orders = orders;
        this.actors = actors;
        this.assignments = assignments;
        this.readiness = readiness;
        this.commitments = commitments;
        this.audit = audit;
        this.files = files;
        this.json = json;
        this.mapper = mapper;
        this.clock = clock;
        this.automation = automation;
    }

    /** @param order owned case @param body sourced rule @return new version */
    public ComplianceReceipt rule(long order,
            @Valid ComplianceRuleRequest body) {
        return mutate(order, body.command(), "RULE", body, true, args -> {
            require(LegalEvidencePolicy.sourceUrl(body.sourceUrl()),
                    "RULE_SOURCE_INVALID");
            require(body.effectiveTo() == null
                    || body.effectiveTo().isAfter(body.effectiveFrom()),
                    "RULE_DATES_INVALID");
            args.put("type", body.documentType());
            args.put("display", body.displayName());
            args.put("resource", "EquiTransit:legal-rule-catalog");
            data.update("ruleLock", args);
            var types = data.rows("catalog", args);

            if (types.isEmpty()) {
                data.update("catalogInsert", args);
            } else {
                require(Boolean.TRUE.equals(types.getFirst().get("IsActive")),
                        "DOCUMENT_TYPE_INACTIVE");
            }
            args.put("code", body.ruleCode());
            args.put("phase", body.phase().name());
            args.put("source", body.sourceUrl());
            args.put("from", time(body.effectiveFrom()));
            args.put("until", time(body.effectiveTo()));
            args.put("transit", body.transitCountryCode());
            args.put("authority", body.authorityRequired());
            args.put("instructions", body.instructions());
            var family = data.rows("ruleFamily", args);

            if (!family.isEmpty()) {
                var previous = family.getFirst();

                for (var entry : Map.of("OriginCountryCode", "origin",
                        "DestinationCountryCode", "destination",
                        "DocumentType", "type", "PhaseCode", "phase",
                        "TransitCountryCode", "transit").entrySet()) {
                    require(java.util.Objects.equals(previous.get(
                            entry.getKey()), args.get(entry.getValue())),
                            "RULE_VERSION_SCOPE_CONFLICT");
                }
            }
            return data.insert("ruleInsert", args);
        });
    }

    /** @param order assigned case @param body route source @return checklist */
    public ComplianceReceipt checklist(long order,
            @Valid ComplianceChecklistRequest body) {
        return mutate(order, body.command(), "CHECKLIST", body, true, args -> {
            require(body.validUntil().isAfter(now()), "CHECKLIST_DATES");
            var route = data.rows("route", args).getFirst();
            String airports = String.valueOf(route.get("TransitAirportsJSON"));
            var transits = automation.transits(args,
                    body.transitCountryCodes());
            require(airports.equals("null") || airports.equals("[]")
                    || !transits.isEmpty(),
                    "TRANSIT_COUNTRIES_PENDING");
            automation.save(args, body.phase(), transits,
                    body.sourceReference(), body.validUntil());
            data.update("invalidate", args);
            return ((Number) args.get("revision")).longValue();
        });
    }

    /** @param storage private storage shared by multipart and submission */
    @org.springframework.beans.factory.annotation.Autowired
    public void configureLocalStorage(FileStorageService storage) {
        localFiles = storage;
    }

    /** @param order owned case @param body immutable version 
     * @return identity */
    public ComplianceReceipt upload(long order,
            @Valid ComplianceUploadRequest body) {
        return mutate(order, body.command(), "UPLOAD", body, false, args -> {
            require("CUSTOMER".equals(args.get("role")), "CUSTOMER_REQUIRED");
            require(!body.expiryDate().isBefore(body.issuedDate()),
                    "DOCUMENT_DATES_INVALID");
            args.put("type", body.documentType());
            var types = data.rows("catalog", args);

            require(!types.isEmpty() && Boolean.TRUE.equals(
                    types.getFirst().get("IsActive")), "DOCUMENT_TYPE_UNKNOWN");
            storeFile(args, body.fileName(), body.contentType(),
                    body.contentBase64());
            var documents = data.rows("document", args);
            long document = documents.isEmpty()
                    ? data.insert("documentInsert", args)
                    : number(documents.getFirst(), "DocumentID");

            args.put("document", document);
            args.put("number", body.documentNumber());
            args.put("issued", java.sql.Date.valueOf(body.issuedDate()));
            args.put("expiry", java.sql.Date.valueOf(body.expiryDate()));
            args.put("expires", null);
            data.update("invalidate", args);
            return data.insert("versionInsert", args);
        });
    }

    /** @param order owned or assigned case @param id requirement
     * @param body file and concurrency command @return atomic submission */
    public ComplianceReceipt uploadAndSubmit(long order, long id,
            @Valid ComplianceUploadSubmitRequest body) {
        var command = new QuoteCommand(body.recordVersion(),
                body.operationId());
        return mutate(order, command, "UPLOAD_SUBMIT:" + id,
                submissionFingerprint(body), false,
                args -> {
                    args.put("id", id);
                    var requirement = one("requirement", args);
                    args.put("type", requirement.get("DocumentType"));
                    int choices = (body.fileKey() == null ? 0 : 1)
                            + (body.existingDocumentVersionId() == null ? 0 : 1)
                            + (body.contentBase64() == null ? 0 : 1);
                    require(choices == 1, "DOCUMENT_SOURCE_REQUIRED");
                    if (body.existingDocumentVersionId() != null) {
                        args.put("id", body.existingDocumentVersionId());
                        var version = one("version", args);
                        require(version.get("DocumentType").equals(
                                requirement.get("DocumentType")),
                                "DOCUMENT_TYPE_MISMATCH");
                        require(version.get("RevokedAt") == null
                                && date(version, "IssuedDate") != null
                                && !date(version, "IssuedDate").isAfter(
                                        now().toLocalDate())
                                && documentExpiry(version) != null
                                && documentExpiry(version).isAfter(now()),
                                "DOCUMENT_EXPIRED_OR_REVOKED");
                        checkPolicyAge(args, date(version, "IssuedDate"));
                        require(data.rows("latestVersion", args).stream()
                                .anyMatch(v -> number(v, "DocumentVersionID")
                                == body.existingDocumentVersionId()),
                                "DOCUMENT_VERSION_SUPERSEDED");
                        args.put("requirement", id);
                        args.put("version", body.existingDocumentVersionId());
                        data.update("invalidate", args);
                        return data.insert("submissionInsert", args);
                    }
                    var issued = body.issuedDate() == null
                            ? now().toLocalDate() : body.issuedDate();
                    require(body.fileKey() == null
                            || body.issuedDate() != null
                            && body.expiryDate() != null,
                            "DOCUMENT_DATES_REQUIRED");
                    require(body.expiryDate() == null
                            || body.expiryDate().getYear() <= 9997
                            && !body.expiryDate().isBefore(issued),
                            "DOCUMENT_DATES_INVALID");
                    var expires = body.expiresAt() != null ? body.expiresAt()
                            : body.expiryDate() == null ? null
                            : body.expiryDate().plusDays(1).atStartOfDay()
                                    .atOffset(ZoneOffset.UTC);
                    require(expires != null && expires.isAfter(now())
                            && expires.getYear() <= 9997
                            && !issued.isAfter(now().toLocalDate())
                            && !issued.isAfter(expires.toLocalDate()),
                            "DOCUMENT_DATES_INVALID");
                    checkPolicyAge(args, issued);
                    var types = data.rows("catalog", args);
                    require(!types.isEmpty() && Boolean.TRUE.equals(
                            types.getFirst().get("IsActive")),
                            "DOCUMENT_TYPE_UNKNOWN");
                    if (body.fileKey() == null) {
                        require(body.fileName() != null
                                && body.contentType() != null,
                                "FILE_TYPE_INVALID");
                        storeFile(args, body.fileName(), body.contentType(),
                                body.contentBase64());
                    } else {
                        var meta = localFiles.metadata(body.fileKey());
                        args.put("key", meta.fileKey().toString());
                        args.put("name", meta.fileName());
                        args.put("mime", meta.contentType());
                        args.put("length", meta.fileSizeBytes());
                        args.put("fileHash", localHash(meta.fileKey()));
                    }
                    var documents = data.rows("document", args);
                    args.put("document", documents.isEmpty()
                            ? data.insert("documentInsert", args)
                            : number(documents.getFirst(), "DocumentID"));
                    args.put("number", body.documentNumber() == null
                            ? body.referenceNumber() : body.documentNumber());
                    args.put("issued", java.sql.Date.valueOf(issued));
                    args.put("expiry", java.sql.Date.valueOf(
                            body.expiryDate() == null ? expires
                                    .withOffsetSameInstant(ZoneOffset.UTC)
                                    .toLocalDate() : body.expiryDate()));
                    args.put("expires", time(expires));
                    long version = data.insert("versionInsert", args);
                    args.put("requirement", id);
                    args.put("version", version);
                    data.update("invalidate", args);
                    return data.insert("submissionInsert", args);
                });
    }

    /** @param order assigned case @param body authority file @return key */
    public ComplianceReceipt uploadFile(long order,
            @Valid ComplianceFileRequest body) {
        return mutate(order, body.command(), "FILE", body, true, args ->
                storeFile(args, body.fileName(), body.contentType(),
                        body.contentBase64()));
    }

    /** @param order owned case @param id requirement @param body exact version
     * @return submission, retaining every older file/submission */
    public ComplianceReceipt submit(long order, long id,
            @Valid ComplianceSubmissionRequest body) {
        return mutate(order, body.command(), "SUBMIT:" + id, body, false,
                args -> {
                    require("CUSTOMER".equals(args.get("role")),
                            "CUSTOMER_REQUIRED");
                    args.put("id", id);
                    var requirement = one("requirement", args);

                    args.put("id", body.documentVersionId());
                    var version = one("version", args);

                    require(version.get("DocumentType").equals(
                            requirement.get("DocumentType")),
                            "DOCUMENT_TYPE_MISMATCH");
                    args.put("requirement", id);
                    args.put("version", body.documentVersionId());
                    data.update("invalidate", args);
                    return data.insert("submissionInsert", args);
                });
    }

    /** @param order assigned case @param id submission @param body verdict
     * @return exact-version review, never a file overwrite */
    public ComplianceReceipt review(long order, long id,
            @Valid ComplianceReviewRequest body) {
        return mutate(order, body.command(), "REVIEW:" + id, body, true,
                args -> {
                    args.put("id", id);
                    one("submission", args);
                    require(body.decision() != com.equitransit.backend.entity
                            .compliance.LegalDecision.REJECTED
                            || body.feedback() != null
                                    && !body.feedback().isBlank(),
                            "REJECTION_REASON_REQUIRED");
                    args.put("decision", body.decision().name());
                    args.put("feedback", body.feedback());
                    data.update("invalidate", args);
                    return data.insert("reviewInsert", args);
                });
    }

    /** @param order assigned case @param body actual authority result
     * @return append-only external-result record */
    public ComplianceReceipt authority(long order,
            @Valid ComplianceAuthorityRequest body) {
        return mutate(order, body.command(), "AUTHORITY", body, true, args -> {
            args.put("id", body.ruleId());
            var rules = routeRules(args, body.ruleId());

            require(!rules.isEmpty(), "RULE_NOT_IN_CHECKLIST");
            args.put("phase", rules.getFirst().get("PhaseCode"));
            args.put("procedure", "LEGAL_APPROVAL");
            args.put("rule", body.ruleId());
            args.put("decision", body.decision().name());
            args.put("authorityName", body.authorityName());
            args.put("reference", body.applicationReference());
            args.put("source", body.sourceReference());
            args.put("key", body.evidenceFileKey() == null ? null
                    : body.evidenceFileKey().toString());
            args.put("decided", time(body.decidedAt()));
            args.put("until", time(body.expiresAt()));
            args.put("hold", body.holdReason());
            if (body.decision() == AuthorityDecision.APPROVED) {
                require(body.evidenceFileKey() != null
                        && body.decidedAt() != null
                        && !body.decidedAt().isAfter(now())
                        && body.expiresAt() != null
                        && body.expiresAt().isAfter(now()),
                        "AUTHORITY_PROOF_REQUIRED");
                var file = one("file", args);

                require(number(file, "UserID") == (long) args.get("user"),
                        "AUTHORITY_PROOF_OWNER");
            } else {
                require(body.holdReason() != null
                        && !body.holdReason().isBlank(),
                        "HOLD_REASON_REQUIRED");
            }
            data.update("invalidate", args);
            return data.insert("procedureInsert", args);
        });
    }

    /** @param order assigned case @param body bounded clinical assessment
     * @return revision evidence consumed by F05/F06; no quote rewrite */
    public ComplianceReceipt assessment(long order,
            @Valid ComplianceAssessmentRequest body) {
        return mutate(order, body.command(), "ASSESSMENT", body, true, args -> {
            require(body.validUntil().isAfter(now()), "ASSESSMENT_DATES");
            require(body.applicability() == QuarantineApplicability.REQUIRED
                    ? body.requiredDays() != null && body.requiredDays() > 0
                    : body.requiredDays() == null, "QUARANTINE_DAYS_INVALID");
            args.put("applicability", body.applicability().name());
            args.put("days", body.requiredDays());
            args.put("until", time(body.validUntil()));
            args.put("source", body.sourceReference());
            args.put("visits", body.visitCount());
            args.put("tests", body.testCount());
            args.put("doses", body.doseCount());
            args.put("certificates", body.certificateCount());
            data.update("quarantineOld", args);
            data.update("vetOld", args);
            long id = data.insert("quarantineInsert", args);

            data.update("vetInsert", args);
            data.update("quarantineChoice", args);
            data.update("invalidate", args);
            commitments.invalidateLegalPricing(order);
            return id;
        });
    }

    /** @param order assigned case @param body requested phase validity
     * @return clearance only if every current prerequisite passes */
    public ComplianceReceipt clear(long order,
            @Valid ComplianceClearanceRequest body) {
        return mutate(order, body.command(), "CLEARANCE", body, true, args -> {
            long revision = (long) args.get("revision");
            var assessment = readiness.evaluate(order, revision, body.phase(),
                    now(), false);
            require(assessment.blockers().stream().allMatch(b ->
                    b.equals("AUTHORITY_EVIDENCE_PENDING")),
                    "CLEARANCE_BLOCKED");
            require(body.expiresAt().isAfter(now())
                    && assessment.expiresAt() != null
                    && !body.expiresAt().isAfter(assessment.expiresAt()),
                    "CLEARANCE_EXPIRY_INVALID");
            args.put("phase", body.phase().name());
            completeProcedures(args, body.expiresAt());
            require(readiness.evaluate(order, revision, body.phase(), now(),
                    false).blockers().isEmpty(), "CLEARANCE_BLOCKED");
            args.put("until", time(body.expiresAt()));
            return data.insert("clearanceInsert", args);
        });
    }

    /** @param order assigned case @param id exact version @param body reason
     * @return retained revoked evidence identity */
    public ComplianceReceipt revoke(long order, long id,
            @Valid ComplianceRevokeRequest body) {
        return mutate(order, body.command(), "REVOKE:" + id, body, true,
                args -> {
                    args.put("id", id);
                    one("version", args);
                    args.put("reason", body.reason());
                    require(data.update("revoke", args) == 1,
                            "DOCUMENT_ALREADY_REVOKED");
                    data.update("invalidate", args);
                    return id;
                });
    }

    /** @param order owned/assigned @param phase phase @param page zero based
     * @param size bounded limit @return safe checklist and fresh gate */
    public ComplianceOverview overview(long order, CompliancePhase phase,
            int page, int size) {
        return overview(order, phase, page, size, false);
    }

    /** @param order owned/assigned @param phase phase @param page page
     * @param size limit @param bootstrap provision absent phase checklists
     * @return requirements, latest attachments and review feedback */
    public ComplianceOverview overview(long order, CompliancePhase phase,
            int page, int size, boolean bootstrap) {
        var args = access(order, false);
        var parent = cases.order(order, false).orElseThrow();
        if (bootstrap && java.util.Set.of("APPROVED", "IN_PREPARATION",
                "CONFIRMED", "IN_TRANSIT").contains(parent.status())
                && automation.bootstrap(args)) {
            data.update("bump", args);
            audit.append(order, ActivityCode.UPDATED, UUID.randomUUID(),
                    mapper.valueToTree(Map.of("action",
                            "COMPLIANCE_AUTO_BOOTSTRAP")));
        }
        args.put("phase", phase.name());
        var rows = data.rows("requirements", args);

        require(rows.size() <= 100, "RULE_LIMIT_EXCEEDED");
        var views = rows.stream().skip((long) page * size).limit(size)
                .map(r -> new ComplianceRequirementView(
                        number(r, "RequirementID"), number(r, "RuleID"),
                        r.get("DocumentType").toString(), phase,
                        requirementStatus(r),
                        nullableId(r,
                                "SubmissionID"), nullableId(r,
                                        "DocumentVersionID"),
                        r.get("Decision") == null ? null
                                : com.equitransit.backend.entity.compliance.
                                        LegalDecision.valueOf(r.get(
                                                "Decision").toString()),
                        (String) r.get("Instructions"),
                        (String) r.get("Feedback"), attachment(r)))
                .toList();
        long revision = (long) args.get("revision");

        return new ComplianceOverview(revision,
                cases.order(order, false).orElseThrow().version(),
                readiness.readiness(order, revision, phase, now()), views,
                page, size, rows.size());
    }

    /** @param order owned/assigned @param page zero based @param size limit
     * @return immutable metadata, including retained older versions */
    public List<ComplianceDocumentView> documents(long order, int page,
            int size) {
        var args = access(order, false);

        args.put("offset", (long) page * size);
        args.put("size", size);
        return data.rows("documents", args).stream().map(r ->
                new ComplianceDocumentView(number(r, "DocumentVersionID"),
                        number(r, "DocumentID"), (int) r.get("VersionNo"),
                        (String) r.get("DocumentType"), opaqueKey(
                                (String) r.get("FileKey")),
                        date(r, "IssuedDate"), date(r, "ExpiryDate"),
                        at(r, "RevokedAt"))).toList();
    }

    /** @param order owned/assigned @param phase optional phase @param page page
     * @param size limit @return required documents and reusable horse papers */
    public ComplianceDocumentsView documentChecklist(long order,
                    CompliancePhase phase, int page,
                    int size) {
        var args = access(order, false);
        var entries = new java.util.ArrayList<
                ComplianceDocumentsView.Requirement>();
        var phases = phase == null ? List.of(CompliancePhase.values())
                : List.of(phase);
        for (var item : phases) {
            for (var r : overview(order, item, 0, 100, true).requirements()) {
                entries.add(new ComplianceDocumentsView.Requirement(
                                r.requirementId(),
                                number(args, "horse"), r.documentType(),
                                r.phase(), r.status()
                                        == RequirementStatus.PENDING
                                        ? "PENDING_SUBMISSION"
                                        : r.status().name(), r.submissionId(),
                                r.documentVersionId(), r.instructions(),
                                r.feedback(), r.attachment()));
            }
        }
        return new ComplianceDocumentsView(cases.order(order, false)
                        .orElseThrow().version(), entries.stream()
                                .skip((long) page * size).limit(size).toList(),
                        documents(order, page, size), entries.size(),
                        page, size);
    }

    /** @param order owned/assigned @param key opaque identity 
     * @return content */
    public ComplianceFileView file(long order, UUID key) {
        var args = access(order, false);

        args.put("key", key);
        var saved = data.rows("fileRead", args);
        if (saved.isEmpty() && localFiles != null) {
            args.put("key", key.toString());
            require(!data.rows("fileVersion", args).isEmpty(),
                    "LEGAL_EVIDENCE_NOT_FOUND");
            var meta = localFiles.metadata(key);
            try (var stream = localFiles.loadAsResource(key.toString())
                    .getInputStream()) {
                return new ComplianceFileView(key, meta.contentType(),
                        meta.fileName(), Base64.getEncoder().encodeToString(
                                stream.readAllBytes()));
            } catch (java.io.IOException error) {
                throw missing();
            }
        }
        if (saved.isEmpty()) {
            throw missing();
        }
        var row = saved.getFirst();

        return new ComplianceFileView(key, row.get("ContentType").toString(),
                row.get("OriginalFileName").toString(), Base64.getEncoder()
                        .encodeToString((byte[]) row.get("ContentBytes")));
    }

    /** @param order owned/assigned @param phase phase @return scoped rules */
    public List<ComplianceRuleView> rules(long order, CompliancePhase phase) {
        var args = access(order, false);

        args.put("phase", phase.name());
        var checklist = data.rows("checklist", args);
        var transits = automation.transits(args, null);
        if (!checklist.isEmpty()) {
            try {
                transits = automation.transits(args, java.util.Arrays.asList(
                        mapper.readValue(checklist.getFirst().get(
                                "TransitCountriesJSON").toString(),
                                String[].class)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Invalid transit snapshot.", e);
            }
        }
        automation.ensureRules(args, transits);
        return data.rows("rules", args).stream().map(r ->
                new ComplianceRuleView(number(r, "RuleID"),
                        (String) r.get("RuleCode"), (int) r.get("VersionNo"),
                        (String) r.get("DocumentType"), phase,
                        (String) r.get("SourceURL"), at(r, "EffectiveFrom"),
                        at(r, "EffectiveTo"), (boolean) r.get(
                                "AuthorityRequired"),
                        (String) r.get("TransitCountryCode"),
                        (String) r.get("RuleDescription"))).toList();
    }

    /** @param order owned/assigned @param phase phase @return external facts */
    public List<ComplianceAuthorityView> procedures(long order,
            CompliancePhase phase) {
        var args = access(order, false);

        args.put("phase", phase.name());
        return data.rows("procedures", args).stream().map(r ->
                new ComplianceAuthorityView(number(r, "ProcedureID"),
                        number(r, "RuleID"), phase,
                        AuthorityDecision.valueOf((String) r.get(
                                "ApprovalStatus")),
                        (String) r.get("AuthorityName"),
                        (String) r.get("SourceReference"),
                        r.get("EvidenceFileKey") == null ? null
                                : UUID.fromString(r.get("EvidenceFileKey")
                                        .toString()), at(r, "ExpiresAt")))
                .toList();
    }

    /** @param order owned/assigned @return assessed quantities or pending */
    public ComplianceAssessmentView assessment(long order) {
        var args = access(order, false);
        var quarantine = data.rows("quarantine", args);
        var veterinary = data.rows("vet", args);
        var specialists = data.rows("activeSpecialists", args).stream()
                .map(r -> number(r, "StaffID")).collect(
                        java.util.stream.Collectors.toSet());
        var horizon = at(data.rows("route", args).getFirst(),
                "DeliveryDeadline");
        boolean valid = !quarantine.isEmpty() && !veterinary.isEmpty();

        if (valid) {
            for (var row : List.of(quarantine.getFirst(),
                    veterinary.getFirst())) {
                valid = valid && at(row, "ValidUntil").isAfter(horizon)
                        && !at(row, "AssessedAt").isAfter(now())
                        && specialists.contains(number(row, "SpecialistID"));
            }
        }
        if (!valid) {
            return new ComplianceAssessmentView(null, null, null, null,
                    null, null, null);
        }
        var q = quarantine.getFirst();
        var v = veterinary.getFirst();

        return new ComplianceAssessmentView(
                QuarantineApplicability.valueOf(q.get("Applicability")
                        .toString()),
                (Integer) q.get("RequiredDays"),
                (Integer) v.get("VisitCount"),
                (Integer) v.get("TestCount"),
                (Integer) v.get("DoseCount"),
                (Integer) v.get("CertificateCount"),
                at(q, "ValidUntil"));
    }

    /**
     * Checks whether quarantine release clearance has passed for an order.
     * @param order the transport order key
     * @return true when quarantine release clearance passed
     */
    public boolean quarantineReleasePassed(long order) {
        return data.quarantineReleasePassed(order);
    }

    private void completeProcedures(Map<String, Object> args,
            OffsetDateTime expires) {
        var checklist = data.rows("checklist", args).getFirst();
        args.put("transits", checklist.get("TransitCountriesJSON"));
        var requirements = data.rows("requirements", args);
        var procedures = data.rows("procedures", args);
        for (var rule : data.rows("rules", args)) {
            long ruleId = number(rule, "RuleID");
            var existing = procedures.stream().filter(p ->
                    number(p, "RuleID") == ruleId).findFirst().orElse(null);
            if (!Boolean.TRUE.equals(rule.get("AuthorityRequired"))
                    || existing != null && (!"APPROVED".equals(existing.get(
                            "ApprovalStatus")) || !"SPECIALIST_DOC_CLEARANCE"
                                    .equals(existing.get("ProcedureCode")))) {
                continue;
            }
            var document = requirements.stream().filter(r ->
                    number(r, "RuleID") == ruleId
                            && "APPROVED".equals(r.get("Decision")))
                    .findFirst().orElseThrow(this::missing);
            var write = new HashMap<>(args);
            write.put("rule", ruleId);
            write.put("procedure", "SPECIALIST_DOC_CLEARANCE");
            write.put("authorityName", "Specialist document clearance");
            write.put("reference", "SUBMISSION:"
                    + number(document, "SubmissionID"));
            write.put("source", "SPECIALIST_DOCUMENT_REVIEW:"
                    + number(document, "ReviewID"));
            write.put("decision", "APPROVED");
            write.put("key", document.get("FileKey"));
            write.put("decided", time(now()));
            write.put("until", time(expires));
            write.put("hold", null);
            data.insert("procedureInsert", write);
        }
    }

    private RequirementStatus requirementStatus(Map<String, Object> row) {
        if (row.get("SubmissionID") == null) {
            return RequirementStatus.PENDING;
        }
        if (row.get("RevokedAt") != null) {
            return RequirementStatus.REVOKED;
        }
        if (row.get("MaxAgeDays") != null) {
            var issued = date(row, "IssuedDate");
            if (issued == null || issued.plusDays(Integer.parseInt(
                    row.get("MaxAgeDays").toString()))
                    .isBefore(now().toLocalDate())) {
                return RequirementStatus.EXPIRED;
            }
        }
        var expiry = documentExpiry(row);
        if (expiry != null && !expiry.isAfter(now())) {
            return RequirementStatus.EXPIRED;
        }
        if (!java.util.Objects.equals(row.get("DocumentVersionID"),
                row.get("LatestVersionID"))) {
            return RequirementStatus.PENDING;
        }
        return row.get("Decision") == null ? RequirementStatus.SUBMITTED
                : RequirementStatus.valueOf(row.get("Decision").toString());
    }

    private OffsetDateTime documentExpiry(Map<String, Object> row) {
        var exact = at(row, "ExpiresAt");
        var date = date(row, "ExpiryDate");
        var endOfDay = date == null ? null : date.plusDays(1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        return exact != null && (endOfDay == null || exact.isBefore(endOfDay))
                ? exact : endOfDay;
    }

    private ComplianceAttachmentView attachment(Map<String, Object> row) {
        if (row.get("DocumentVersionID") == null) {
            return null;
        }
        return new ComplianceAttachmentView(number(row, "PrivateFilePresent")
                == 1 ? opaqueKey((String) row.get("FileKey")) : null,
                (String) row.get("OriginalFileName"),
                (String) row.get("ContentType"), number(row, "FileSizeBytes"),
                (String) row.get("DocumentNumber"), documentExpiry(row));
    }

    private ComplianceReceipt mutate(long order, QuoteCommand command,
            String action, Object body, boolean specialist,
            Function<Map<String, Object>, Long> write) {
        var args = access(order, specialist);

        args.put("op", command.operationId());
        String hash = TokenTools.hash(action + ":" + json.canonical(body));
        var replays = data.rows("replay", args);

        if (!replays.isEmpty()) {
            require(hash.equals(replays.getFirst().get("RequestHash")),
                    "COMPLIANCE_RETRY_CONFLICT");
            try {
                return mapper.readValue((String) replays.getFirst().get(
                        "ReceiptJSON"), ComplianceReceipt.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                throw new IllegalStateException("Unsupported legal receipt.");
            }
        }
        var current = cases.order(order, false).orElseThrow();

        require(java.util.Set.of("APPROVED", "IN_PREPARATION", "CONFIRMED",
                "IN_TRANSIT", "DELIVERED").contains(current.status()),
                "LEGAL_WORK_STATE_CONFLICT");
        require(current.version().equalsIgnoreCase(command.recordVersion()),
                "ORDER_VERSION_CONFLICT");
        long id = write.apply(args);

        data.update("bump", args);
        var receipt = new ComplianceReceipt(id, command.operationId(),
                cases.order(order, false).orElseThrow().version(),
                args.get("key") == null ? null
                        : UUID.fromString(args.get("key").toString()));

        args.put("hash", hash);
        args.put("receipt", json.write(receipt));
        data.update("operation", args);
        audit.append(order, action.equals("CLEARANCE")
                ? ActivityCode.CLEARED : ActivityCode.UPDATED,
                command.operationId(), mapper.valueToTree(Map.of(
                        "entityId", id, "action", action, "phase",
                        args.getOrDefault("phase", "NOT_APPLICABLE"))));
        return receipt;
    }

    private Map<String, Object> access(long order, boolean specialist) {
        var actor = actors.current();
        var parent = cases.order(order, true).orElseThrow(() -> missing());

        require(parent.revisionId() != null, "SUBMITTED_REVISION_REQUIRED");
        if (specialist || !"CUSTOMER".equals(actor.roleCode())) {
            assignments.lockLegalRevision(order);
        } else if (orders.findOwned(order, actor.customerId(), false)
                .isEmpty()) {
            throw missing();
        }
        if (!data.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "COMPLIANCE_SCHEMA_PENDING",
                    "Deploy through migration020.");
        }
        var args = new HashMap<String, Object>();

        args.put("order", order);
        args.put("revision", parent.revisionId());
        args.put("user", actor.userId());
        args.put("staff", actor.staffId());
        args.put("role", actor.roleCode());
        args.put("now", time(now()));
        var route = data.rows("route", args).getFirst();

        args.put("horse", route.get("HorseID"));
        args.put("origin", route.get("OriginCountryCode"));
        args.put("destination", route.get("DestinationCountryCode"));
        data.rows("horseLock", args);
        return args;
    }

    private long storeFile(Map<String, Object> args, String name,
            String mime, String encoded) {
        byte[] bytes = files.inspect(name, mime, encoded);

        args.put("key", UUID.randomUUID().toString());
        args.put("name", name);
        args.put("mime", mime);
        args.put("bytes", bytes);
        args.put("length", bytes.length);
        try {
            args.put("fileHash", java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(bytes)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable.", error);
        }
        return data.insert("fileInsert", args);
    }

    private String localHash(UUID key) {
        try (var stream = localFiles.loadAsResource(key.toString())
                .getInputStream()) {
            return java.util.HexFormat.of().formatHex(java.security
                    .MessageDigest.getInstance("SHA-256")
                    .digest(stream.readAllBytes()));
        } catch (java.io.IOException
                | java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("Private file unavailable.", error);
        }
    }

    private Object submissionFingerprint(ComplianceUploadSubmitRequest body) {
        if (body.fileKey() == null && body.existingDocumentVersionId() == null
                && body.documentNumber() == null && body.issuedDate() == null
                && body.expiryDate() == null) {
            com.fasterxml.jackson.databind.node.ObjectNode tree = mapper
                    .valueToTree(body);
            tree.remove(List.of("fileKey", "existingDocumentVersionId",
                    "documentNumber", "issuedDate", "expiryDate"));
            return tree;
        }
        return body;
    }

    private void checkPolicyAge(Map<String, Object> args,
            java.time.LocalDate issued) {
        if (data.localFilesReady()) {
            for (var policy : data.rows("documentPolicies", args)) {
                if (args.get("type").equals(policy.get("DocumentType"))
                        && policy.get("ValidityDays") instanceof Number days) {
                    require(issued != null && !issued.isAfter(now()
                            .toLocalDate()) && !issued.plusDays(days.intValue())
                                    .isBefore(now().toLocalDate()),
                            "DOCUMENT_VALIDITY_EXCEEDED");
                }
            }
        }
    }

    private List<Map<String, Object>> routeRules(Map<String, Object> args,
            long rule) {
        return data.rows("rulesByChecklist", args).stream()
                .filter(r -> number(r, "RuleID") == rule).toList();
    }

    private Map<String, Object> one(String query, Map<String, Object> args) {
        var rows = data.rows(query, args);

        if (rows.size() != 1) {
            throw missing();
        }
        return rows.getFirst();
    }

    private UUID opaqueKey(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private java.time.LocalDate date(Map<String, Object> row, String key) {
        return row.get(key) == null ? null
                : ((java.sql.Date) row.get(key)).toLocalDate();
    }

    private Long nullableId(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : number(row, key);
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND,
                "LEGAL_EVIDENCE_NOT_FOUND", "Legal evidence was not found.");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withNano(0);
    }

    private LocalDateTime time(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(
                ZoneOffset.UTC).toLocalDateTime();
    }
}
