package com.equitransit.backend.controller.compliance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.equitransit.backend.dto.compliance.request.
        ComplianceRulePolicyRequest;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.exception.compliance.ComplianceException;
import com.equitransit.backend.repository.compliance.ComplianceRuleRepository;
import com.equitransit.backend.repository.compliance.ComplianceAuditRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.assessment.CaseAssignmentAccess;
import com.equitransit.backend.service.compliance.ComplianceRevisionService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Configures explicit source policies under the existing BE1 case boundary. */
@RestController
@Validated
@ConditionalOnProperty(prefix = "be2.compliance",
        name = "api-enabled", havingValue = "true")
public class ComplianceRulePolicyController {
    private final CaseAssignmentAccess cases;
    private final CurrentActorService actors;
    private final ComplianceRuleRepository rules;
    private final ComplianceRevisionService revisions;
    private final NamedParameterJdbcTemplate jdbc;
    private final ComplianceAuditRepository audit;

    /** Wires policy review under live staff authorization.
     * @param cases BE1 case access
     * @param actors live identity
     * @param rules source validation
     * @param revisions checklist synchronizer
     * @param jdbc SQL adapter
     * @param audit transactional audit
     */
    public ComplianceRulePolicyController(CaseAssignmentAccess cases,
            CurrentActorService actors, ComplianceRuleRepository rules,
            ComplianceRevisionService revisions,
                NamedParameterJdbcTemplate jdbc,
            ComplianceAuditRepository audit) {
        this.cases = cases;
        this.actors = actors;
        this.rules = rules;
        this.revisions = revisions;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** Reviews a rule policy without inventing missing regulatory data.
     * @param orderId assigned order
     * @param ruleId sourced rule version
     * @param policy explicit specialist policy
     */
    @PostMapping("/api/staff/transport-orders/{orderId}/compliance/rules"
            + "/{ruleId}/policy")
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    @Transactional
    public void reviewPolicy(@PathVariable @Positive long orderId,
            @PathVariable @Positive long ruleId,
            @Valid @RequestBody ComplianceRulePolicyRequest policy) {
        long revision = cases.lockRevision(orderId, CaseRole.SPECIALIST);
        var source = jdbc.query("SELECT DocumentType,PhaseCode"
                + " FROM dbo.ComplianceRuleVersion WHERE RuleID=:rule",
                new MapSqlParameterSource("rule", ruleId),
                (row, i) -> new String[]{row.getString(1), row.getString(2)});
        if (source.isEmpty()) {
            throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "RULE_PENDING_ASSESSMENT", "A reviewed source is "
                    + "required.");
        }
        rules.requireRouteMatchedRule(ruleId, source.getFirst()[0],
                source.getFirst()[1], revision);
        revisions.savePolicy(revision, ruleId, actors.current().staffId(),
                policy);
        revisions.synchronize(orderId, revision);
        audit.append(orderId, actors.current().userId(),
                "RULE_POLICY_REVIEWED", ruleId);
    }
}
