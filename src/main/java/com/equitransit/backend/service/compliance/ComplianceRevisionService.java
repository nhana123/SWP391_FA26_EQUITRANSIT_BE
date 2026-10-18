package com.equitransit.backend.service.compliance;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.equitransit.backend.dto.compliance.request.
        ComplianceRulePolicyRequest;
import com.equitransit.backend.entity.compliance.DocumentRequirement;
import com.equitransit.backend.entity.compliance.DocumentSubmission;
import com.equitransit.backend.exception.compliance.ComplianceException;
import com.equitransit.backend.repository.compliance.
        DocumentRequirementRepository;
import com.equitransit.backend.repository.compliance.
        DocumentSubmissionRepository;
import com.equitransit.backend.repository.compliance.
        ComplianceClearanceRepository;

/** Reconciles current revision checklists using explicit source policies.
 * Reads on F08 boundaries make a new revision effective before any gate check.
 * Previous clearances and government procedures are never copied.
 */
@Service
@ConditionalOnProperty(prefix = "be2.compliance",
        name = "api-enabled", havingValue = "true")
public class ComplianceRevisionService {
    private static final String MATCH =
            " FROM dbo.ComplianceRuleVersion r"
            + " JOIN dbo.DocumentTypeCatalog c ON c.TypeCode=r.DocumentType"
            + " AND c.IsActive=1 JOIN dbo.RevisionRouteSource v"
            + " ON v.RevisionID=:rev"
            + " LEFT JOIN dbo.RuleRequirementPolicy p ON p.RuleID=r.RuleID"
            + " WHERE r.OriginCountryCode=v.OriginCountryCode"
            + " AND r.DestinationCountryCode=v.DestinationCountryCode"
            + " AND v.PreferredDepartureAt IS NOT NULL"
            + " AND r.ReviewedAt IS NOT NULL AND r.VersionNo>0"
            + " AND LEN(LTRIM(RTRIM(r.SourceURL)))>0"
            + " AND r.EffectiveFrom<=v.PreferredDepartureAt"
            + " AND (r.EffectiveTo IS NULL OR "
            + "r.EffectiveTo>=v.PreferredDepartureAt)"
            + " AND (r.TransitCountryCode IS NULL OR EXISTS ("
            + " SELECT 1 FROM dbo.TransitAirport t JOIN dbo.Airport a"
            + " ON a.AirportCode=t.AirportCode WHERE t.RevisionID=:rev"
            + " AND a.CountryCode=r.TransitCountryCode"
            + " AND a.ReviewedAt IS NOT NULL"
            + " AND LEN(LTRIM(RTRIM(a.SourceReference)))>0))";
    private final NamedParameterJdbcTemplate jdbc;
    private final DocumentRequirementRepository requirements;
    private final DocumentSubmissionRepository submissions;

    /** Creates the transactional revision synchronizer.
     * @param jdbc transaction-aware SQL
     * @param requirements requirement adapter
     * @param submissions immutable submission adapter
     */
    public ComplianceRevisionService(NamedParameterJdbcTemplate jdbc,
            DocumentRequirementRepository requirements,
            DocumentSubmissionRepository submissions) {
        this.jdbc = jdbc;
        this.requirements = requirements;
        this.submissions = submissions;
    }

    /** Stores an explicitly reviewed policy after caller authorization.
     * @param revision assigned revision
     * @param rule route-matched source rule
     * @param staff actual specialist
     * @param policy explicit policy
     */
    @Transactional
    public void savePolicy(long revision, long rule, long staff,
            ComplianceRulePolicyRequest policy) {
        lockRevision(revision);
        var params = new MapSqlParameterSource("rule", rule)
                .addValue("scope", policy.scopeCode())
                .addValue("mandatory", policy.mandatory())
                .addValue("basis", policy.validityBasis())
                .addValue("instructions", policy.instructions())
                .addValue("staff", staff).addValue("now", now());
        // Reviewed policies are immutable; a changed policy needs a new rule
        // version so previously approved requirements cannot silently change.
        Long exists = jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.RuleRequirementPolicy WITH (UPDLOCK,HOLDLOCK)"
                + " WHERE RuleID=:rule", params, Long.class);
        if (exists != null && exists > 0) {
            throw new ComplianceException(HttpStatus.CONFLICT,
                    "RULE_POLICY_ALREADY_REVIEWED",
                    "Create a new rule version to change a reviewed policy.");
        }
        jdbc.update("INSERT INTO dbo.RuleRequirementPolicy"
                + " (RuleID,ScopeCode,IsMandatory,ValidityBasis,Instructions,"
                + "ReviewedBy,ReviewedAt) VALUES (:rule,:scope,:mandatory,"
                + ":basis,:instructions,:staff,:now)", params);
    }

    /** Creates missing current-revision requirements exactly once.
     * @param order authorized order
     * @param revision exact authorized current revision
     */
    @Transactional
    public void synchronize(long order, long revision) {
        lockRevision(revision);
        var params = new MapSqlParameterSource("rev", revision)
                .addValue("order", order).addValue("now", now());
        Long current = jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.TransportOrder WHERE OrderID=:order"
                + " AND CurrentRevisionID=:rev", params, Long.class);
        if (current == null || current != 1) {
            throw new ComplianceException(HttpStatus.CONFLICT,
                    "COMPLIANCE_REVISION_CHANGED",
                        "Reload the current revision.");
        }
        List<PolicyRule> rules = rules(revision);
        boolean pending = rules.isEmpty() || unknownTransit(revision);
        for (PolicyRule rule : rules) {
            if (rule.scope() == null || rule.horse() == null
                    || rule.departure() == null
                    || ("DELIVERY".equals(rule.basis())
                        && rule.delivery() == null)) {
                pending = true;
                continue;
            }
            createRequirement(order, revision, rule);
        }
        params.addValue("pending", pending);
        int updated = jdbc.update("UPDATE dbo.RevisionChecklistState"
                + " SET AssessedAt=:now,PendingRuleAssessment=:pending"
                + " WHERE RevisionID=:rev", params);
        if (updated == 0) {
            jdbc.update("INSERT INTO dbo.RevisionChecklistState"
                    + " (RevisionID,AssessedAt,PendingRuleAssessment)"
                    + " VALUES (:rev,:now,:pending)", params);
        }
    }

    /** Checks complete live rule coverage and external-procedure validity.
     * @param revision current revision
     * @param phase independent legal phase
     * @return whether this phase has assessed source coverage
     */
    public boolean phaseCovered(long revision, String phase) {
        if (unknownTransit(revision)) {
            return false;
        }
        List<PolicyRule> matching = rules(revision).stream()
                .filter(r -> phase.equals(r.phase())).toList();
        if (matching.isEmpty() && !("QUARANTINE_RELEASE".equals(phase)
                && new ComplianceClearanceRepository(jdbc)
                        .findLatestQuarantine(revision)
                        .map(q -> "NOT_REQUIRED".equals(q.getApplicability()))
                        .orElse(false))) {
            return false;
        }
        var checklist = requirements.findByRevisionId(revision);
        for (PolicyRule rule : matching) {
            if (rule.scope() == null || ("DELIVERY".equals(rule.basis())
                    && rule.delivery() == null)
                    || checklist.stream().noneMatch(q ->
                        q.getRuleId() == rule.id()
                        && q.isMandatory() == rule.mandatory()
                        && rule.scope().equals(q.getScopeCode())
                        && ("SHIPMENT".equals(rule.scope())
                            || java.util.Objects.equals(q.getHorseId(),
                                    rule.horse())))) {
                return false;
            }
        }
        Long blockers = jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.AuthorityProcedure WHERE RevisionID=:rev"
                + " AND PhaseCode=:phase AND (ApprovalStatus<>'APPROVED'"
                + " OR ApplicationReference IS NULL"
                + " OR LEN(LTRIM(RTRIM(ApplicationReference)))=0"
                + " OR DecidedAt IS NULL OR DecidedAt>:now"
                + " OR (ExpiresAt IS NOT NULL AND ExpiresAt<=:now))",
                new MapSqlParameterSource("rev", revision)
                        .addValue("phase", phase).addValue("now", now()),
                Long.class);
        return blockers != null && blockers == 0;
    }

    private void createRequirement(long order, long revision, PolicyRule rule) {
        var checklist = requirements.findByRevisionId(revision);
        Long horse = "HORSE".equals(rule.scope()) ? rule.horse() : null;
        if (checklist.stream().anyMatch(q -> q.getRuleId() == rule.id()
                && java.util.Objects.equals(q.getHorseId(), horse))) {
            return;
        }
        var req = new DocumentRequirement();
        req.setRevisionId(revision);
        req.setRuleId(rule.id());
        req.setDocumentType(rule.type());
        req.setPhaseCode(rule.phase());
        req.setMandatory(rule.mandatory());
        req.setScopeCode(rule.scope());
        req.setHorseId(horse);
        req.setStatus("PENDING");
        req.setInstructions(rule.instructions());
        LocalDate cutoff = ("DELIVERY".equals(rule.basis())
                ? rule.delivery() : rule.departure()).toLocalDate();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (cutoff.isBefore(today)) {
            cutoff = today;
        }
        req.setValidityConditionsJson("{\"validThrough\":\"" + cutoff + "\"}");
        long id = requirements.insert(req);
        // Reuse actual evidence, while obtaining a fresh specialist decision
        // for the new revision. Never fabricate an approval or reviewer.
        var versions = jdbc.query("SELECT TOP 1 s.DocumentVersionID"
                + " FROM dbo.DocumentSubmission s JOIN dbo.DocumentReview r"
                + " ON r.SubmissionID=s.SubmissionID AND r.Decision='APPROVED'"
                + " JOIN dbo.DocumentRequirement q ON "
                + "q.RequirementID=s.RequirementID"
                + " JOIN dbo.OrderRevision old ON old.RevisionID=q.RevisionID"
                + " JOIN dbo.DocumentVersion v ON "
                + "v.DocumentVersionID=s.DocumentVersionID"
                + " JOIN dbo.HorseDocument d ON d.DocumentID=v.DocumentID"
                + " WHERE old.OrderID=:order AND q.RevisionID<>:rev"
                + " AND q.RuleID=:rule AND d.HorseID=:horse"
                + " AND d.DocumentType=:type AND v.ExpiryDate>=:cutoff"
                + " AND (v.IssuedDate IS NULL OR v.IssuedDate<=:today)"
                + " AND s.SubmissionID=(SELECT MAX(s2.SubmissionID)"
                + " FROM dbo.DocumentSubmission s2 WHERE "
                + "s2.RequirementID=q.RequirementID)"
                + " ORDER BY v.ExpiryDate DESC,s.SubmissionID DESC",
                new MapSqlParameterSource("order", order)
                        .addValue("rev", revision).addValue("rule", rule.id())
                        .addValue("horse", rule.horse()).addValue("type",
                            rule.type())
                        .addValue("cutoff", cutoff).addValue("today", today),
                (row, i) -> row.getLong(1));
        if (!versions.isEmpty()) {
            var submission = new DocumentSubmission();
            submission.setRequirementId(id);
            submission.setDocumentVersionId(versions.getFirst());
            submissions.insert(submission);
            requirements.updateStatus(id, "PENDING_REVIEW");
        }
    }

    private List<PolicyRule> rules(long revision) {
        return jdbc.query("SELECT r.RuleID,r.DocumentType,r.PhaseCode,"
                + "p.ScopeCode,p.IsMandatory,p.ValidityBasis,p.Instructions,"
                + "v.HorseID,v.PreferredDepartureAt,v.DeliveryDeadline" + MATCH,
                new MapSqlParameterSource("rev", revision),
                (row, i) -> new PolicyRule(row.getLong("RuleID"),
                        row.getString("DocumentType"),
                            row.getString("PhaseCode"),
                        row.getString("ScopeCode"),
                            row.getBoolean("IsMandatory"),
                        row.getString("ValidityBasis"),
                            row.getString("Instructions"),
                        row.getObject("HorseID", Long.class),
                        local(row.getTimestamp("PreferredDepartureAt")),
                        local(row.getTimestamp("DeliveryDeadline"))));
    }

    private boolean unknownTransit(long revision) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.TransitAirport t"
                + " LEFT JOIN dbo.Airport a ON a.AirportCode=t.AirportCode"
                + " AND a.ReviewedAt IS NOT NULL"
                + " AND LEN(LTRIM(RTRIM(a.SourceReference)))>0"
                + " WHERE t.RevisionID=:rev AND a.AirportCode IS NULL",
                new MapSqlParameterSource("rev", revision), Long.class);
        return count == null || count > 0;
    }

    private void lockRevision(long revision) {
        if (jdbc.query("SELECT RevisionID FROM dbo.OrderRevision"
                + " WITH (UPDLOCK,HOLDLOCK) WHERE RevisionID=:rev",
                new MapSqlParameterSource("rev", revision),
                (row, i) -> row.getLong(1)).isEmpty()) {
            throw new ComplianceException(HttpStatus.NOT_FOUND,
                    "COMPLIANCE_REVISION_NOT_FOUND", "Revision not found.");
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC)
                .withNano(0);
    }

    private static LocalDateTime local(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record PolicyRule(long id, String type, String phase, String scope,
            boolean mandatory, String basis, String instructions, Long horse,
            LocalDateTime departure, LocalDateTime delivery) {
    }
}
