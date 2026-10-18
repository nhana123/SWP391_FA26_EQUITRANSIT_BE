package com.equitransit.backend.service.departure;

import com.equitransit.backend.repository.compliance.
        ComplianceClearanceRepository;
import com.equitransit.backend.repository.compliance.
        DocumentRequirementRepository;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.service.compliance.ComplianceRevisionService;
import com.equitransit.backend.service.pricing.ServiceAssessmentFacade;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal F11 consumer; caller retains authorized parent lock to commit. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DepartureLegalAdapter {

    private final ComplianceClearanceRepository clearances;
    private final DocumentRequirementRepository requirements;
    private final ObjectProvider<ComplianceRevisionService> revisions;
    private final ServiceAssessmentFacade assessments;
    private final AssessmentRepository cases;

    /** @param clearances current F08 clearance @param requirements documents
     * @param revisions route coverage @param assessments quarantine evidence
     * @param cases parent revision lock */
    public DepartureLegalAdapter(ComplianceClearanceRepository clearances,
            DocumentRequirementRepository requirements,
            ObjectProvider<ComplianceRevisionService> revisions,
            ServiceAssessmentFacade assessments, AssessmentRepository cases) {
        this.clearances = clearances;
        this.requirements = requirements;
        this.revisions = revisions;
        this.assessments = assessments;
        this.cases = cases;
    }

    /** @param order authorized parent @param revision exact current revision
     * @param now evaluation instant @return fresh blockers */
    public List<String> blockers(long order, long revision,
            OffsetDateTime now) {
        var coverage = revisions.getIfAvailable();
        if (coverage == null) {
            return List.of("COMPLIANCE_SCHEMA_PENDING");
        }
        var parent = cases.order(order, true).orElseThrow();
        if (parent.revisionId() == null || parent.revisionId() != revision) {
            return List.of("REVISION_MISMATCH");
        }
        var blockers = new ArrayList<String>();
        if (!coverage.phaseCovered(revision, "PRE_EXPORT")
                || !clearances.hasValidPass(revision, "PRE_EXPORT")
                || requirements.hasMandatoryBlocker(revision, "PRE_EXPORT")) {
            blockers.add("PRE_EXPORT_NOT_CLEARED");
        }
        var quarantine = assessments.quarantine(order, revision, now, now);
        if (!("NOT_REQUIRED".equals(quarantine.applicability())
                || ("REQUIRED".equals(quarantine.applicability())
                && quarantine.requiredDays() != null
                && quarantine.requiredDays() > 0))) {
            blockers.add("QUARANTINE_NOT_ASSESSED");
        }
        return blockers;
    }
}
