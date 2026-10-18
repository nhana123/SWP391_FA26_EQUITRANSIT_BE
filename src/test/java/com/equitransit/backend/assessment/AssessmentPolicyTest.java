/*
 * AssessmentPolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify exact-revision assessment and deadline boundaries.
 */
package com.equitransit.backend.assessment;


import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import com.equitransit.backend.dto.assessment.request.AssessmentRequest;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.DecisionCode;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.service.assessment.AssessmentPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * Policy tests do not claim database transaction or assignment verification.
 */
public class AssessmentPolicyTest {

    private final AssessmentPolicy policy = new AssessmentPolicy(Clock.fixed(
            Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC));
    private final CaseOrder submitted = new CaseOrder(1, "TR-1", "SUBMITTED",
            "000000000000001a", 10L);

    /** Decisions must identify the current revision and parent version. */
    @Test
    public void rejectsSupersededRevisionAndStaleVersion() {
        AssessmentRequest stale = request(9, DecisionCode.APPROVED, null,
                1L, 2L);

        assertThatThrownBy(() -> policy.decision(submitted, stale))
                .isInstanceOf(OrderException.class)
                .extracting("code").isEqualTo("ASSESSMENT_REVISION_CONFLICT");
        assertThatThrownBy(() -> policy.version(submitted, "000000000000001b"))
                .isInstanceOf(OrderException.class);
        policy.version(submitted, "000000000000001A");
    }

    /** Approval starts preparation only and requires both case roles. */
    @Test
    public void requiresCompleteTeamAndSubmittedState() {
        AssessmentRequest missing = request(10, DecisionCode.APPROVED, null,
                1L, null);
        CaseOrder assessed = new CaseOrder(1, "TR-1", "APPROVED",
                submitted.version(), 10L);

        assertThatThrownBy(() -> policy.decision(submitted, missing))
                .isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> policy.decision(assessed,
                request(10, DecisionCode.APPROVED, null, 1L, 2L)))
                .isInstanceOf(OrderException.class);
        policy.decision(submitted,
                request(10, DecisionCode.APPROVED, null, 1L, 2L));
    }

/**
 * Negative decisions carry a public reason and cannot smuggle assignments.
 */
    @Test
    public void requiresReasonsWithoutAllocationForNegativeDecisions() {
        for (DecisionCode outcome : new DecisionCode[] {
                DecisionCode.REJECTED, DecisionCode.CHANGES_REQUESTED}) {
            assertThatThrownBy(() -> policy.decision(submitted,
                    request(10, outcome, "  ", null, null)))
                    .isInstanceOf(OrderException.class);
            assertThatThrownBy(() -> policy.decision(submitted,
                    request(10, outcome, "Explain", 1L, null)))
                    .isInstanceOf(OrderException.class);
            policy.decision(submitted,
                    request(10, outcome, "Explain", null, null));
        }
    }

    /** UTC deadline validation is separated from historic replay detection. */
    @Test
    public void normalizesDeadlineAndChecksFutureOnlyForNewActions() {
        OffsetDateTime future = OffsetDateTime.parse(
                "2026-10-15T07:00:00+07:00");
        OffsetDateTime past = OffsetDateTime.parse("2026-10-13T00:00:00Z");

        assertThat(policy.deadline(future).toString())
                .isEqualTo("2026-10-15T00:00Z");
        policy.requireFuture(policy.deadline(future));
        assertThat(policy.deadline(past)).isEqualTo(past);
        assertThatThrownBy(() -> policy.requireFuture(past))
                .isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> policy.deadline(OffsetDateTime.parse(
                "9999-12-31T23:00:00-02:00")))
                .isInstanceOf(OrderException.class);
    }

    /** Typed command validation excludes missing IDs and oversized reasons. */
    @Test
    public void validatesRetryAndColumnBounds() {
        AssessmentRequest invalid = new AssessmentRequest(0, "bad", null,
                null, "x".repeat(1001), -1L, null, null, null);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(invalid))
                    .hasSize(6);
        }
    }

    private AssessmentRequest request(long revision, DecisionCode code,
            String reason, Long specialist, Long coordinator) {
        return new AssessmentRequest(revision, submitted.version(),
                UUID.randomUUID(), code, reason, specialist, coordinator,
                null, null);
    }
}
