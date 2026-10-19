/*
 * CaseQueryService
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Serve role-scoped case queues and assessment read contracts.
 */
package com.equitransit.backend.service.assessment;


import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.equitransit.backend.dto.assessment.response.CaseAssignmentResponse;
import com.equitransit.backend.dto.assessment.response.CaseDecisionResponse;
import com.equitransit.backend.dto.assessment.response.CasePageResponse;
import com.equitransit.backend.dto.assessment.response.CaseResponse;
import com.equitransit.backend.dto.assessment.response.CaseStaffPageResponse;
import com.equitransit.backend.dto.assessment.response.CustomerAssessmentResponse;
import com.equitransit.backend.dto.order.response.OrderRevisionResponse;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.assessment.DecisionCode;
import com.equitransit.backend.entity.order.OrderStatus;
import com.equitransit.backend.entity.order.OrderRevisionRecord;
import com.equitransit.backend.entity.compliance.ReviewState;
import com.equitransit.backend.entity.order.TransportOrderRecord;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;


/** Scoped projections exclude internal approval notes from customer views. */
@Service
@Validated
public class CaseQueryService {

    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final CurrentActorService actors;
    private final CaseAssignmentAccess access;
    private final OrderJsonCodec json;

    /**
     * Creates scoped read projections.
     * @param cases case and assignment adapter
     * @param orders captured order-history adapter
     * @param actors live account identities
     * @param access active assignment authorization facade
     * @param json typed snapshot codec
     */
    public CaseQueryService(AssessmentRepository cases, OrderRepository orders,
            CurrentActorService actors, CaseAssignmentAccess access,
            OrderJsonCodec json) {
        this.cases = cases;
        this.orders = orders;
        this.actors = actors;
        this.access = access;
        this.json = json;
    }

    /**
     * Lists the Manager's bounded review queue.
     * @param state optional explicitly allowed state
     * @param page zero-based page
     * @param size bounded size
     * @return current queue summaries
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@domainAccessPolicy.isManager()")
    public CasePageResponse managerQueue(ReviewState state, @Min(0) int page,
            @Min(1) @Max(100) int size) {
        long total = cases.queueCount(state, null, null);

        return new CasePageResponse(cases.queue(state, null, null, page, size)
                .stream().map(CaseViews::summary).toList(),
                page, size, total, pages(total, size));
    }

    /**
     * Shows active candidates and workload without passwords or contacts.
     * @param role required case role
     * @param page zero-based page
     * @param size bounded size
     * @return eligible active staff, availability NOT_CONFIGURED
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@domainAccessPolicy.isManager()")
    public CaseStaffPageResponse staffDirectory(CaseRole role,
            @Min(0) int page, @Min(1) @Max(100) int size) {
        long total = cases.staffCount(role);

        return new CaseStaffPageResponse(cases.staff(role, page, size).stream()
                .map(CaseViews::staff).toList(), page,
                size, total, pages(total, size));
    }

    /**
     * Reads a submitted case for management, excluding private unsent drafts.
     * @param id parent key
     * @return captured current revision, decision and active assignments
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@domainAccessPolicy.isManager()")
    public CaseResponse managerCase(@Positive long id) {
        return view(parent(id), null);
    }

    /**
     * Lists decisions without deleting or rewriting earlier revisions.
     * @param id submitted parent key
     * @param page zero-based page
     * @param size bounded size
     * @return bounded decision history array
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@domainAccessPolicy.isManager()")
    public List<CaseDecisionResponse> decisions(@Positive long id,
            @Min(0) int page, @Min(1) @Max(100) int size) {
        parent(id);
        return cases.decisions(id, page, size).stream()
                .map(CaseViews::decision).toList();
    }

    /**
     * Lists ended and active assignments for Manager history review.
     * @param id submitted parent key
     * @param page zero-based page
     * @param size bounded size
     * @return bounded history, including original assigning actors
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@domainAccessPolicy.isManager()")
    public List<CaseAssignmentResponse> assignments(@Positive long id,
            @Min(0) int page, @Min(1) @Max(100) int size) {
        parent(id);
        return cases.assignments(id, false, page, size).stream()
                .map(CaseViews::assignment).toList();
    }

    /**
     * Lists only currently assigned approved cases for the live staff actor.
     * @param page zero-based page
     * @param size bounded size
     * @return current visible workload
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public CasePageResponse myQueue(@Min(0) int page,
            @Min(1) @Max(100) int size) {
        CurrentActor actor = actors.current();
        long total = cases.queueCount(null, actor.staffId(), actor.roleCode());

        return new CasePageResponse(cases.queue(null, actor.staffId(),
                actor.roleCode(), page, size).stream()
                .map(CaseViews::summary).toList(), page, size, total,
                pages(total, size));
    }

    /**
     * Rechecks revocable assignment under parent lock before reading a case.
     * @param id case key
     * @return current snapshot and only this staff's active instructions
     */
    @Transactional
    @PreAuthorize("@domainAccessPolicy.isCaseStaff()")
    public CaseResponse myCase(@Positive long id) {
        CurrentActor actor = actors.current();

        access.lockRevision(id, CaseRole.valueOf(actor.roleCode()));
        return view(parent(id), actor.staffId());
    }

    /**
     * Returns only a customer's own public assessment outcome.
     * @param id owned order key
     * @return current outcome, excluding staff IDs and internal approval notes
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@domainAccessPolicy.isCustomer()")
    public CustomerAssessmentResponse customerAssessment(@Positive long id) {
        TransportOrderRecord order = orders.findOwned(id,
                actors.current().customerId(), false)
                .orElseThrow(this::notFound);
        CaseDecisionResponse decision = order.currentRevisionId() == null
                ? null : cases.decision(order.currentRevisionId())
                        .map(CaseViews::decision).orElse(null);

        return new CustomerAssessmentResponse(order.currentRevisionId(),
                decision == null ? null : decision.decision().name(),
                decision == null || decision.decision() == DecisionCode.APPROVED
                        ? null : decision.reason());
    }

    private CaseResponse view(CaseOrder order, Long visibleStaff) {
        OrderRevisionRecord record = orders.revision(order.orderId(),
                order.revisionId()).orElseThrow(this::notFound);
        OrderRevisionResponse revision = new OrderRevisionResponse(
                record.revisionId(), record.orderId(), record.revisionNo(),
                record.changeReason(), record.submittedAt(),
                json.snapshot(record.snapshot()));
        List<CaseAssignmentResponse> assignments = cases.assignments(
                order.orderId(), true, 0, 100).stream()
                .filter(item -> visibleStaff == null
                        || item.staffId() == visibleStaff)
                .map(CaseViews::assignment).toList();

        return new CaseResponse(order.orderId(), order.orderNumber(),
                OrderStatus.valueOf(order.status()), order.version(),
                order.revisionId(), revision,
                cases.decision(order.revisionId()).map(CaseViews::decision)
                        .orElse(null), assignments);
    }

    private CaseOrder parent(long id) {
        CaseOrder order = cases.order(id, false).orElseThrow(this::notFound);

        if (order.revisionId() == null) {
            throw notFound();
        }
        return order;
    }

    private long pages(long total, int size) {
        return total / size + (total % size == 0 ? 0 : 1);
    }

    private OrderException notFound() {
        return new OrderException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                "The requested record was not found.");
    }
}
