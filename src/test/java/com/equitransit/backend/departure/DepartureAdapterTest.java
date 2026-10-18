package com.equitransit.backend.departure;

import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.compliance.
        ComplianceClearanceRepository;
import com.equitransit.backend.repository.compliance.
        DocumentRequirementRepository;
import com.equitransit.backend.service.compliance.ComplianceRevisionService;
import com.equitransit.backend.service.departure.DepartureLegalAdapter;
import com.equitransit.backend.service.pricing.ServiceAssessmentFacade;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Disabled compliance must block departure without preventing app startup. */
public class DepartureAdapterTest {

    /** Missing route evaluator cannot manufacture a legal PASS. */
    @Test
    public void disabledComplianceKeepsLegalGateClosed() {
        var cases = mock(AssessmentRepository.class);
        var clearances = mock(ComplianceClearanceRepository.class);
        var requirements = mock(DocumentRequirementRepository.class);
        var assessments = mock(ServiceAssessmentFacade.class);
        var provider = new StaticListableBeanFactory()
                .getBeanProvider(ComplianceRevisionService.class);
        var adapter = new DepartureLegalAdapter(clearances, requirements,
                provider, assessments, cases);

        assertThat(adapter.blockers(101, 201, OffsetDateTime.parse(
                "2026-10-18T00:00:00Z")))
                .containsExactly("COMPLIANCE_SCHEMA_PENDING");
        verifyNoInteractions(cases, clearances, requirements, assessments);
    }
}
