/*
 * ComplianceAutomation
 * Author: EquiTransit development team
 * Date: 2026-10-23
 * Purpose: Provision explicit default checklists and current graph transit.
 */
package com.equitransit.backend.service;

import com.equitransit.backend.entity.compliance.CompliancePhase;
import com.equitransit.backend.json.OrderJsonCodec;
import com.equitransit.backend.repository.ComplianceRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import static com.equitransit.backend.service.ComplianceReadinessFacade.at;
import static com.equitransit.backend.service.ComplianceReadinessFacade.number;
import static com.equitransit.backend.service.LegalEvidencePolicy.require;

/** Called only after the order/horse locks and authorization are retained. */
@Service
public class ComplianceAutomation {

    private final ComplianceRepository data;
    private final OrderJsonCodec json;
    private final Clock clock;

    /** @param data persistence @param json snapshots @param clock UTC */
    public ComplianceAutomation(ComplianceRepository data, OrderJsonCodec json,
            Clock clock) {
        this.data = data;
        this.json = json;
        this.clock = clock;
    }

    /** @param args authorized scope @param requested optional explicit route
     * @return validated explicit transit or current-plan countries */
    public List<String> transits(Map<String, Object> args,
            List<String> requested) {
        var countries = requested == null || requested.isEmpty()
                ? data.transitCountries(args) : List.copyOf(requested);
        require(countries.size() <= 15, "TRANSIT_COUNTRY_LIMIT");
        require(countries.stream().distinct().count() == countries.size(),
                "TRANSIT_DUPLICATE");
        require(countries.stream().allMatch(c -> c.matches("^[A-Z]{2}$")
                && !c.equals(args.get("origin"))
                && !c.equals(args.get("destination"))), "TRANSIT_INVALID");
        return countries.stream().sorted().toList();
    }

    /** Provision only absent active base/transit scopes under catalog lock.
     * @param args authorized scope including phase
     * @param transits actual checkpoint or explicit countries */
    public void ensureRules(Map<String, Object> args, List<String> transits) {
        args.put("resource", "EquiTransit:legal-rule-catalog");
        data.update("ruleLock", args);
        args.put("transits", json.write(transits));
        var existing = data.rows("rules", args);
        if (existing.stream().noneMatch(r ->
                r.get("TransitCountryCode") == null)) {
            var phase = CompliancePhase.valueOf(args.get("phase").toString());
            for (var type : defaults(phase)) {
                provision(args, type, null);
            }
        }
        for (String transit : transits) {
            if (existing.stream().noneMatch(r -> transit.equals(
                    r.get("TransitCountryCode")))) {
                provision(args, "TRANSIT_" + transit + "_"
                        + args.get("phase") + "_PERMIT", transit);
            }
        }
    }

    /** @param args authorized parent @return whether a checklist changed */
    public boolean bootstrap(Map<String, Object> args) {
        var specialists = data.rows("activeSpecialists", args);
        if (specialists.isEmpty()) {
            return false;
        }
        var write = new HashMap<>(args);
        write.put("staff", number(specialists.getFirst(), "StaffID"));
        var transit = transits(write, null);
        var route = data.rows("route", write).getFirst();
        var now = OffsetDateTime.now(clock).withNano(0);
        var until = now.plusDays(90);
        var deadline = at(route, "DeliveryDeadline");
        if (deadline != null && deadline.isAfter(until.minusDays(30))) {
            until = deadline.plusDays(30);
        }
        boolean changed = false;
        for (var phase : CompliancePhase.values()) {
            write.put("phase", phase.name());
            var saved = data.rows("checklist", write);
            boolean refresh = !saved.isEmpty()
                    && saved.getFirst().get("SourceReference").toString()
                            .startsWith("AUTO-BOOTSTRAP:")
                    && !json.write(transit).equals(saved.getFirst().get(
                            "TransitCountriesJSON"));
            if (saved.isEmpty() || refresh) {
                save(write, phase, transit, "AUTO-BOOTSTRAP:current-route",
                        until);
                if (refresh) {
                    data.update("invalidatePhase", write);
                }
                changed = true;
            }
        }
        return changed;
    }

    /** @param args authorized write @param phase phase @param transits route
     * @param source provenance @param until checklist expiry */
    public void save(Map<String, Object> args, CompliancePhase phase,
            List<String> transits, String source, OffsetDateTime until) {
        args.put("phase", phase.name());
        ensureRules(args, transits);
        var rules = data.rows("rules", args);
        require(!rules.isEmpty() && rules.size() <= 100,
                "RULE_LIMIT_EXCEEDED");
        var ids = rules.stream().map(r -> number(r, "RuleID")).toList();
        args.put("rules", json.write(ids));
        args.put("source", source);
        args.put("until", until.withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime());
        args.put("checklistSnapshot", json.write(Map.of("schemaVersion", 1,
                "transitCountryCodes", transits, "ruleIds", ids,
                "sourceReference", source, "validUntil", until)));
        data.update("checklistHistory", args);
        data.update("checklistDelete", args);
        data.update("checklistInsert", args);
        data.update("oldRequirements", args);
        data.update("requirementInsert", args);
        data.update("requirementReactivate", args);
    }

    private void provision(Map<String, Object> scope, String type,
            String transit) {
        var args = new HashMap<>(scope);
        args.put("type", type);
        args.put("display", display(type));
        var catalog = data.rows("catalog", args);
        if (catalog.isEmpty()) {
            data.update("catalogInsert", args);
        } else {
            require(Boolean.TRUE.equals(catalog.getFirst().get("IsActive")),
                    "DOCUMENT_TYPE_INACTIVE");
        }
        int index = type.equals("EXPORT_HEALTH_CERTIFICATE") ? 1
                : type.equals("EQUINE_PASSPORT_VACCINATION") ? 2 : 3;
        args.put("code", "ET-DEF-" + args.get("origin") + "-"
                + args.get("destination") + "-" + args.get("phase") + "-"
                + (transit == null ? "BASE" : transit) + "-" + index);
        args.put("transit", transit);
        args.put("source", "https://equitransit.example/compliance/defaults");
        args.put("from", args.get("now"));
        args.put("until", null);
        args.put("authority", true);
        args.put("instructions", display(type) + ". Default workflow checklist"
                + "; assigned Specialist verifies the applicable regulations."
                + (transit == null ? "" : " Transit country: " + transit));
        data.insert("ruleInsert", args);
    }

    private List<String> defaults(CompliancePhase phase) {
        return switch (phase) {
            case PRE_EXPORT -> List.of("EXPORT_HEALTH_CERTIFICATE",
                    "EQUINE_PASSPORT_VACCINATION");
            case ARRIVAL -> List.of("IMPORT_CUSTOMS_DECLARATION");
            case QUARANTINE_RELEASE -> List.of(
                    "QUARANTINE_RELEASE_CERTIFICATE");
        };
    }

    private String display(String type) {
        return switch (type) {
            case "EXPORT_HEALTH_CERTIFICATE" ->
                    "Export Veterinary Health Certificate";
            case "EQUINE_PASSPORT_VACCINATION" ->
                    "Equine Passport / Vaccination Record";
            case "IMPORT_CUSTOMS_DECLARATION" ->
                    "Import Permit & Customs Entry Declaration";
            case "QUARANTINE_RELEASE_CERTIFICATE" ->
                    "Post-Arrival Quarantine Release Certificate";
            default -> "Transit Permit";
        };
    }
}
