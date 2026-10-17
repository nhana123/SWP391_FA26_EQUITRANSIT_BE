/*
 * ComplianceService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.service;


import com.equitransit.backend.dto.compliance.ComplianceAssessmentRequest;
import com.equitransit.backend.dto.compliance.ComplianceAssessmentView;
import com.equitransit.backend.dto.compliance.ComplianceAuthorityRequest;
import com.equitransit.backend.dto.compliance.ComplianceAuthorityView;
import com.equitransit.backend.dto.compliance.ComplianceChecklistRequest;
import com.equitransit.backend.dto.compliance.ComplianceClearanceRequest;
import com.equitransit.backend.dto.compliance.ComplianceDocumentView;
import com.equitransit.backend.dto.compliance.ComplianceFileRequest;
import com.equitransit.backend.dto.compliance.ComplianceFileView;
import com.equitransit.backend.dto.compliance.ComplianceRequirementView;
import com.equitransit.backend.dto.compliance.ComplianceReviewRequest;
import com.equitransit.backend.dto.compliance.ComplianceRevokeRequest;
import com.equitransit.backend.dto.compliance.ComplianceRuleRequest;
import com.equitransit.backend.dto.compliance.ComplianceRuleView;
import com.equitransit.backend.dto.compliance.ComplianceSubmissionRequest;
import com.equitransit.backend.dto.compliance.ComplianceUploadRequest;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.dto.compliance.ComplianceReceipt;
import com.equitransit.backend.dto.compliance.ComplianceOverview;
import com.equitransit.backend.entity.compliance.CompliancePhase;
import com.equitransit.backend.entity.compliance.AuthorityDecision;
import com.equitransit.backend.entity.compliance.
        QuarantineApplicability;
import com.equitransit.backend.entity.activity.ActivityCode;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.json.OrderJsonCodec;
import com.equitransit.backend.repository.ComplianceRepository;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.repository.OrderRepository;
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
import static com.equitransit.backend.service.LegalEvidencePolicy.require;
import static com.equitransit.backend.service.ComplianceReadinessFacade.at;
import static com.equitransit.backend.service.ComplianceReadinessFacade.number;


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

    /**
     * @param data legal persistence @param cases parent locks
     * @param orders ownership @param actors live identities
     * @param assignments BE1 assigned-case facade @param readiness F11 evidence
     * @param commitments estimate invalidation @param audit append-only events
     * @param files inspected private files @param json receipt encoding
     * @param mapper receipt decoding @param clock UTC clock
     */
    public ComplianceService(ComplianceRepository data,
            AssessmentRepository cases, OrderRepository orders,
            CurrentActorService actors, CaseAssignmentAccess assignments,
            ComplianceReadinessFacade readiness,
            OrderCommitmentPolicy commitments, AuditWriter audit,
            LegalFilePolicy files, OrderJsonCodec json, ObjectMapper mapper,
            Clock clock) {
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

            require(airports.equals("null") || airports.equals("[]")
                    || !body.transitCountryCodes().isEmpty(),
                    "TRANSIT_COUNTRIES_PENDING");
            require(body.transitCountryCodes().stream().distinct().count()
                    == body.transitCountryCodes().size(), "TRANSIT_DUPLICATE");
            args.put("phase", body.phase().name());
            args.put("transits", json.write(body.transitCountryCodes()));
            var rules = data.rows("rules", args);

            require(rules.size() <= 100, "RULE_LIMIT_EXCEEDED");
            args.put("rules", json.write(rules.stream().map(r ->
                    number(r, "RuleID")).toList()));
            args.put("source", body.sourceReference());
            args.put("until", time(body.validUntil()));
            args.put("checklistSnapshot", json.write(Map.of(
                    "schemaVersion", 1, "transitCountryCodes",
                    body.transitCountryCodes(), "ruleIds", rules.stream()
                            .map(r -> number(r, "RuleID")).toList(),
                    "sourceReference", body.sourceReference(),
                    "validUntil", body.validUntil())));
            data.update("checklistHistory", args);
            data.update("checklistDelete", args);
            data.update("checklistInsert", args);
            data.update("oldRequirements", args);
            data.update("requirementInsert", args);
            data.update("requirementReactivate", args);
            data.update("invalidate", args);
            return ((Number) args.get("revision")).longValue();
        });
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
            data.update("invalidate", args);
            return data.insert("versionInsert", args);
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

            require(assessment.blockers().isEmpty(), "CLEARANCE_BLOCKED");
            require(body.expiresAt().isAfter(now())
                    && assessment.expiresAt() != null
                    && !body.expiresAt().isAfter(assessment.expiresAt()),
                    "CLEARANCE_EXPIRY_INVALID");
            args.put("phase", body.phase().name());
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
        var args = access(order, false);

        args.put("phase", phase.name());
        var rows = data.rows("requirements", args);

        require(rows.size() <= 100, "RULE_LIMIT_EXCEEDED");
        var views = rows.stream().skip((long) page * size).limit(size)
                .map(r -> new ComplianceRequirementView(
                        number(r, "RequirementID"), number(r, "RuleID"),
                        r.get("DocumentType").toString(), phase,
                        com.equitransit.backend.entity.compliance.
                                RequirementStatus
                                .valueOf(r.get("Status").toString()),
                        nullableId(r,
                                "SubmissionID"), nullableId(r,
                                        "DocumentVersionID"),
                        r.get("Decision") == null ? null
                                : com.equitransit.backend.entity.compliance.
                                        LegalDecision.valueOf(r.get(
                                                "Decision").toString()),
                        (String) r.get("Instructions")))
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

    /** @param order owned/assigned @param key opaque identity 
     * @return content */
    public ComplianceFileView file(long order, UUID key) {
        var args = access(order, false);

        args.put("key", key);
        var row = one("fileRead", args);

        return new ComplianceFileView(key, row.get("ContentType").toString(),
                row.get("OriginalFileName").toString(), Base64.getEncoder()
                        .encodeToString((byte[]) row.get("ContentBytes")));
    }

    /** @param order owned/assigned @param phase phase @return scoped rules */
    public List<ComplianceRuleView> rules(long order, CompliancePhase phase) {
        var args = access(order, false);

        args.put("phase", phase.name());
        var checklist = data.rows("checklist", args);

        args.put("transits", checklist.isEmpty() ? "[]"
                : checklist.getFirst().get("TransitCountriesJSON"));
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
        var q = valid ? quarantine.getFirst() : null;
        var v = valid ? veterinary.getFirst() : null;

        return new ComplianceAssessmentView(valid
                ? QuarantineApplicability.valueOf(q.get("Applicability")
                        .toString()) : null,
                valid ? (Integer) q.get("RequiredDays") : null,
                valid ? (Integer) v.get("VisitCount") : null,
                valid ? (Integer) v.get("TestCount") : null,
                valid ? (Integer) v.get("DoseCount") : null,
                valid ? (Integer) v.get("CertificateCount") : null,
                valid ? at(q, "ValidUntil") : null);
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
                    "COMPLIANCE_SCHEMA_PENDING", "Deploy migration009.");
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
