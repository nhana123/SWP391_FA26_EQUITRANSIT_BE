/*
 * ComplianceReadinessFacade
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.service;


import com.equitransit.backend.dto.compliance.ComplianceReadiness;
import com.equitransit.backend.entity.compliance.CompliancePhase;
import com.equitransit.backend.entity.compliance.ComplianceStatus;
import com.equitransit.backend.repository.ComplianceRepository;
import com.equitransit.backend.repository.AssessmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.sql.Date;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;


/** F11 consumers authorize first; locks survive through their transaction. */
@Service
public class ComplianceReadinessFacade {

    private final ComplianceRepository data;
    private final AssessmentRepository cases;
    private final ObjectMapper mapper;

    /** @param data legal evidence @param cases parent lock 
     * @param mapper JSON */
    public ComplianceReadinessFacade(ComplianceRepository data,
            AssessmentRepository cases, ObjectMapper mapper) {
        this.data = data;
        this.cases = cases;
        this.mapper = mapper;
    }

    /**
     * @param order already authorized parent
     * @param revision exact consumer revision
     * @param phase legal phase, never substituted with another phase
     * @param asOf UTC instant; never cached for a later departure
     * @return fresh evidence including current clearance and blockers
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ComplianceReadiness readiness(long order, long revision,
            CompliancePhase phase, OffsetDateTime asOf) {
        return evaluate(order, revision, phase, asOf, true);
    }

    /**
     * Internal Specialist clearance command uses the same prerequisite rules.
     * @param order authorized parent @param revision current revision
     * @param phase phase @param asOf horizon @param requireClearance gate mode
     * @return recomputed blockers; no authority approval is manufactured
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ComplianceReadiness evaluate(long order, long revision,
            CompliancePhase phase, OffsetDateTime asOf,
            boolean requireClearance) {
        var blocked = new ArrayList<String>();
        var parent = cases.order(order, true);

        if (parent.isEmpty() || parent.get().revisionId() == null
                || parent.get().revisionId() != revision) {
            return result(revision, phase, asOf, null,
                    List.of("REVISION_MISMATCH"));
        }
        if (!data.ready()) {
            return result(revision, phase, asOf, null,
                    List.of("COMPLIANCE_SCHEMA_PENDING"));
        }
        var args = new HashMap<String, Object>();

        args.put("order", order);
        args.put("revision", revision);
        args.put("phase", phase.name());
        args.put("now", asOf.withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime());
        var route = data.rows("route", args).getFirst();

        args.put("horse", route.get("HorseID"));
        data.rows("horseLock", args);
        data.update("ruleReadLock", args);
        args.put("origin", route.get("OriginCountryCode"));
        args.put("destination", route.get("DestinationCountryCode"));
        var checklists = data.rows("checklist", args);

        if (checklists.isEmpty()) {
            return result(revision, phase, asOf, null,
                    List.of("RULE_ASSESSMENT_PENDING"));
        }
        var checklist = checklists.getFirst();
        OffsetDateTime expires = at(checklist, "ValidUntil");

        args.put("transits", checklist.get("TransitCountriesJSON"));
        var rules = data.rows("rules", args);
        var ids = rules.stream().map(r -> number(r, "RuleID")).toList();

        if (rules.isEmpty() || rules.size() > 100
                || !json(ids).equals(checklist.get("RuleIDsJSON"))
                || !covered(rules, checklist.get("TransitCountriesJSON")
                        .toString())) {
            blocked.add("ROUTE_RULE_PENDING");
        }
        if (!expires.isAfter(asOf) || at(checklist, "AssessedAt")
                .isAfter(asOf)) {
            blocked.add("CHECKLIST_EXPIRED");
        }
        var requirements = data.rows("requirements", args);
        var procedures = data.rows("procedures", args);
        var specialists = data.rows("activeSpecialists", args).stream()
                .map(r -> number(r, "StaffID")).collect(
                        java.util.stream.Collectors.toSet());

        if (!specialists.contains(number(checklist, "SpecialistID"))) {
            blocked.add("SPECIALIST_ASSIGNMENT_CHANGED");
        }
        for (var rule : rules) {
            var until = at(rule, "EffectiveTo");

            if (until != null && !until.isAfter(asOf)) {
                blocked.add("RULE_EXPIRED");
            }
            if (!LegalEvidencePolicy.sourceUrl((String) rule.get(
                    "SourceURL"))) {
                blocked.add("RULE_SOURCE_PENDING");
            }
            expires = earlier(expires, until);
            boolean satisfied = false;

            for (var requirement : requirements) {
                if (number(rule, "RuleID")
                        != number(requirement, "RuleID")) {
                    continue;
                }
                Date issued = (Date) requirement.get("IssuedDate");
                Date expiry = (Date) requirement.get("ExpiryDate");
                boolean valid = LegalEvidencePolicy.documentValid(
                        issued == null ? null : issued.toLocalDate(),
                        expiry == null ? null : expiry.toLocalDate(),
                        requirement.get("RevokedAt") != null,
                        requirement.get("DocumentVersionID") != null
                                && requirement.get("DocumentVersionID")
                                        .equals(requirement.get(
                                                "LatestVersionID")),
                        "APPROVED".equals(requirement.get("Decision")), asOf);
                var exactExpiry = at(requirement, "ExpiresAt");
                valid = valid && (exactExpiry == null
                        || exactExpiry.isAfter(asOf));

                if (valid && at(requirement, "ReviewedAt") != null
                        && !at(requirement, "ReviewedAt").isAfter(asOf)
                        && number(requirement, "PrivateFilePresent") == 1
                        && specialists.contains(
                        number(requirement, "ReviewedBy"))) {
                    satisfied = true;
                    expires = earlier(expires, expiry.toLocalDate()
                            .plusDays(1).atStartOfDay()
                            .atOffset(ZoneOffset.UTC));
                    expires = earlier(expires, exactExpiry);
                }
            }
            if (!satisfied) {
                blocked.add("DOCUMENT_EVIDENCE_PENDING");
            }
            if (Boolean.TRUE.equals(rule.get("AuthorityRequired"))) {
                var matching = procedures.stream().filter(p ->
                        number(p, "RuleID") == number(rule, "RuleID"))
                        .findFirst();
                boolean valid = matching.isPresent()
                        && number(matching.get(), "PrivateFilePresent") == 1
                        && "APPROVED".equals(matching.get().get(
                                "ApprovalStatus"))
                        && matching.get().get("EvidenceFileKey") != null
                        && matching.get().get("SourceReference") != null
                        && at(matching.get(), "ExpiresAt") != null
                        && at(matching.get(), "ExpiresAt").isAfter(asOf)
                        && at(matching.get(), "DecidedAt") != null
                        && !at(matching.get(), "DecidedAt").isAfter(asOf)
                        && specialists.contains(number(matching.get(),
                                "RecordedBy"));

                if (!valid) {
                    blocked.add("AUTHORITY_EVIDENCE_PENDING");
                } else {
                    expires = earlier(expires,
                            at(matching.get(), "ExpiresAt"));
                }
            }
        }
        if (requireClearance) {
            var clearances = data.rows("clearance", args);
            var clearance = clearances.isEmpty() ? null
                    : clearances.getFirst();

            if (clearance == null || !"CLEARED".equals(clearance.get("Status"))
                    || at(clearance, "ExpiresAt") == null
                    || !at(clearance, "ExpiresAt").isAfter(asOf)
                    || at(clearance, "CheckedAt").isAfter(asOf)
                    || !specialists.contains(number(clearance,
                            "SpecialistID"))) {
                blocked.add("PHASE_CLEARANCE_PENDING");
            } else {
                expires = earlier(expires, at(clearance, "ExpiresAt"));
            }
        }
        return result(revision, phase, asOf, expires,
                blocked.stream().distinct().toList());
    }

    private boolean covered(List<Map<String, Object>> rules, String encoded) {
        try {
            var transits = mapper.readTree(encoded);

            if (rules.stream().noneMatch(r ->
                    r.get("TransitCountryCode") == null)) {
                return false;
            }
            for (var transit : transits) {
                if (rules.stream().noneMatch(r -> transit.asText().equals(
                        r.get("TransitCountryCode")))) {
                    return false;
                }
            }
            return true;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            return false;
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("Unsupported rule snapshot.");
        }
    }

    /** @param row internal SQL row @param key column @return UTC time/null */
    public static OffsetDateTime at(Map<String, Object> row, String key) {
        var value = (Timestamp) row.get(key);

        return value == null ? null
                : value.toLocalDateTime().atOffset(ZoneOffset.UTC);
    }

    /** @param row internal SQL row @param key column @return identity or0 */
    public static long number(Map<String, Object> row, String key) {
        return row.get(key) == null ? 0 : ((Number) row.get(key)).longValue();
    }

    private OffsetDateTime earlier(OffsetDateTime first,
            OffsetDateTime second) {
        return second != null && second.isBefore(first) ? second : first;
    }

    private ComplianceReadiness result(long revision, CompliancePhase phase,
            OffsetDateTime asOf, OffsetDateTime expires, List<String> blocked) {
        return new ComplianceReadiness(revision, phase, asOf, expires,
                blocked.isEmpty() ? ComplianceStatus.READY
                        : ComplianceStatus.PENDING, blocked);
    }
}
