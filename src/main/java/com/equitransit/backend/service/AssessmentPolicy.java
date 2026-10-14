/*
 * AssessmentPolicy
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.service;


import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.equitransit.backend.dto.request.AssessmentRequest;
import com.equitransit.backend.entity.CaseOrder;
import com.equitransit.backend.entity.DecisionCode;
import com.equitransit.backend.exception.OrderException;


/** Explicit assessment transitions; no legal, money or departure approval. */
@Component
public class AssessmentPolicy {

    private final Clock clock;

    /** @param clock the application UTC clock */
    public AssessmentPolicy(Clock clock) {
        this.clock = clock;
    }

    /**
     * Refuses stale versions before creating history.
     * @param order the locked parent
     * @param expected the observed SQL rowversion
     */
    public void version(CaseOrder order, String expected) {
        if (!order.version().equalsIgnoreCase(expected)) {
            throw conflict("ORDER_VERSION_CONFLICT",
                    "Request changed. Reload it before acting again.");
        }
    }

    /**
     * Requires the exact submitted revision and a typed outcome.
     * @param order the locked parent
     * @param request the manager command
     */
    public void decision(CaseOrder order, AssessmentRequest request) {
        if (order.revisionId() == null
                || order.revisionId() != request.revisionId()) {
            throw conflict("ASSESSMENT_REVISION_CONFLICT",
                    "Review the current submitted revision.");
        }
        if (!"SUBMITTED".equals(order.status())) {
            throw conflict("ASSESSMENT_STATE_CONFLICT",
                    "Only a submitted revision can receive a decision.");
        }
        if (request.decision() == DecisionCode.APPROVED) {
            if (request.specialistId() == null
                    || request.coordinatorId() == null) {
                throw invalid("Approval requires Specialist and Coordinator.");
            }
        } else {
            if (optional(request.reason()) == null) {
                throw invalid("Clarification or rejection requires a reason.");
            }
            if (request.specialistId() != null
                    || request.coordinatorId() != null
                    || request.dueAt() != null
                    || optional(request.assignmentNote()) != null) {
                throw invalid("Assignments are only allowed with approval.");
            }
        }
    }

    /**
     * Normalizes a supported deadline before historic replay detection.
     * @param value optional deadline, not a reserved travel interval
     * @return UTC seconds or null
     */
    public OffsetDateTime deadline(OffsetDateTime value) {
        OffsetDateTime normalized;

        if (value == null) {
            return null;
        }
        if (value.getYear() < 1 || value.getYear() > 9999) {
            throw invalid("Deadline is outside the supported range.");
        }
        normalized = value.withOffsetSameInstant(ZoneOffset.UTC).withNano(0);
        if (normalized.getYear() < 1 || normalized.getYear() > 9999) {
            throw invalid("Case deadline must be within the supported range.");
        }
        return normalized;
    }

    /**
     * Checks timeliness after replay detection so historic retries stay valid.
     * @param deadline normalized optional UTC deadline
     */
    public void requireFuture(OffsetDateTime deadline) {
        if (deadline != null
                && !deadline.isAfter(OffsetDateTime.now(clock))) {
            throw invalid("Case deadline must be in the future.");
        }
    }

    /**
     * Normalizes optional public reasons and staff-only notes.
     * @param value the bounded typed text
     * @return trimmed text or null
     */
    public String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Creates a safe state/concurrency conflict.
     * @param code stable machine-readable code
     * @param message safe user-facing message
     * @return domain exception
     */
    public OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }

    private OrderException invalid(String message) {
        return new OrderException(HttpStatus.BAD_REQUEST,
                "ASSESSMENT_INPUT_INVALID", message);
    }
}
