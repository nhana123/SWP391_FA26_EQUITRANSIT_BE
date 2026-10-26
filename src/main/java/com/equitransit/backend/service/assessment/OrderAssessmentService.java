/*
 * OrderAssessmentService
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Own atomic Manager assessment and reassignment transactions.
 */
package com.equitransit.backend.service.assessment;


import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.equitransit.backend.dto.assessment.request.AssessmentRequest;
import com.equitransit.backend.dto.assessment.request.CaseAssignmentRequest;
import com.equitransit.backend.dto.assessment.request.EndCaseAssignmentRequest;
import com.equitransit.backend.dto.assessment.response.AssessmentReceipt;
import com.equitransit.backend.dto.assessment.response.CaseAssignmentResponse;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.assessment.DecisionCode;
import com.equitransit.backend.entity.order.OrderStatus;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentAuditRepository;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.service.BookingFlowService;
import com.equitransit.backend.service.ComplianceApprovalProvisioner;
import com.equitransit.backend.service.order.OrderCommitmentPolicy;


/** APPROVED request means preparation only, never customer quote acceptance. */
@Service
@Validated
@PreAuthorize("@domainAccessPolicy.isManager()")
public class OrderAssessmentService {

    private final AssessmentRepository cases;
    private final AssessmentAuditRepository audit;
    private final CurrentActorService actors;
    private final AssessmentPolicy policy;
    private final OrderCommitmentPolicy commitments;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private org.springframework.context.ApplicationEventPublisher events;
    private BookingFlowService booking;


    /**
     * Creates the Manager assessment facade.
     * @param cases parameterized case persistence
     * @param audit append-only audit and retry persistence
     * @param actors live domain identities
     * @param policy input and transition contract
     * @param commitments finance/operation commitment boundary
     * @param json versioned JSON and canonical command fingerprints
     * @param mapper typed replay deserialization
     */
    public OrderAssessmentService(AssessmentRepository cases,
            AssessmentAuditRepository audit, CurrentActorService actors,
            AssessmentPolicy policy, OrderCommitmentPolicy commitments,
            OrderJsonCodec json, ObjectMapper mapper) {
        this.cases = cases;
        this.audit = audit;
        this.actors = actors;
        this.policy = policy;
        this.commitments = commitments;
        this.json = json;
        this.mapper = mapper;
    }

    /** @param booking initial fixed-road approval boundary */
    @org.springframework.beans.factory.annotation.Autowired
    public void configureBooking(BookingFlowService booking) {
        this.booking = booking;
    }

    /** @param publisher same-transaction domain notifications */
    @org.springframework.beans.factory.annotation.Autowired
    public void configureEvents(
            org.springframework.context.ApplicationEventPublisher publisher) {
        events = publisher;
    }

    /**
     * Assesses only the reviewed current revision, with atomic team allocation.
     * @param id parent order key
     * @param command typed outcome, expected version and retry key
     * @return immutable completion receipt, also on identical retries
     */
    @Transactional
    public AssessmentReceipt decide(@Positive long id,
            @Valid AssessmentRequest command) {
        CaseOrder order = parent(id);
        CurrentActor actor = manager();
        AssessmentRequest request = new AssessmentRequest(command.revisionId(),
                command.recordVersion().toLowerCase(Locale.ROOT),
                command.operationId(), command.decision(),
                policy.optional(command.reason()), command.specialistId(),
                command.coordinatorId(), policy.deadline(command.dueAt()),
                policy.optional(command.assignmentNote()));
        String hash = hash("DECIDE", request);
        Optional<AssessmentReceipt> replay = replay(id, actor,
                request.operationId(), hash);
        List<CaseAssignmentResponse> before;

        if (replay.isPresent()) {
            return replay.get();
        }
        policy.version(order, request.recordVersion());
        policy.decision(order, request);
        boolean initialRoad = booking != null && order.revisionId() != null
                && "SUBMITTED".equals(order.status())
                && request.decision() == DecisionCode.APPROVED
                && booking.automaticRoad(id, order.revisionId());
        if (!initialRoad) {
            requireUncommitted(id);
        }
        policy.requireFuture(request.dueAt());
        before = active(id);
        if (request.decision() == DecisionCode.APPROVED) {
            validateTeam(request.specialistId(), request.coordinatorId());
        }
        cases.insertDecision(request.revisionId(), actor.staffId(),
                request.decision().name(), request.reason());
        cases.end(id, null);
        if (request.decision() == DecisionCode.APPROVED) {
            cases.assign(id, CaseRole.SPECIALIST, request.specialistId(),
                    actor.staffId(), request.dueAt(), request.assignmentNote());
            cases.assign(id, CaseRole.COORDINATOR, request.coordinatorId(),
                    actor.staffId(), request.dueAt(), request.assignmentNote());
        }
        transition(order, request.decision().name());
        if (booking != null
                && request.decision() == DecisionCode.APPROVED) {
            booking.approved(id, request.revisionId());
        }
        if (initialRoad && booking.internal(id).isDepositSatisfied()) {
            transition(parent(id), "IN_PREPARATION");
        }
        if (events != null && request.decision() == DecisionCode.APPROVED) {
            events.publishEvent(new ComplianceApprovalProvisioner.Approved(
                    id, request.revisionId(), actor.userId()));
        }
        return finish(id, actor, request.operationId(), "DECIDE", hash,
                request, before);
    }

    /**
     * Replaces case responsibility while preserving and revoking the old row.
     * @param id parent order
     * @param role required preparation role
     * @param command replacement fields, version, reason and retry key
     * @return immutable completion receipt
     */
    @Transactional
    public AssessmentReceipt assign(@Positive long id, CaseRole role,
            @Valid CaseAssignmentRequest command) {
        CaseOrder order = parent(id);
        CurrentActor actor = manager();
        CaseAssignmentRequest request = new CaseAssignmentRequest(
                command.recordVersion().toLowerCase(Locale.ROOT),
                command.operationId(), command.staffId(),
                command.reason().trim(), policy.deadline(command.dueAt()),
                policy.optional(command.assignmentNote()));
        String hash = hash("ASSIGN_" + role.name(), request);
        Optional<AssessmentReceipt> replay = replay(id, actor,
                request.operationId(), hash);
        List<CaseAssignmentResponse> before;

        if (replay.isPresent()) {
            return replay.get();
        }
        editableAssignment(order, request.recordVersion());
        policy.requireFuture(request.dueAt());
        validateStaff(request.staffId(), role.name());
        before = active(id);
        cases.end(id, role);
        cases.assign(id, role, request.staffId(), actor.staffId(),
                request.dueAt(), request.assignmentNote());
        commitments.invalidatePreparation(id);
        transition(order, order.status());
        return finish(id, actor, request.operationId(), "ASSIGN", hash,
                request, before);
    }

    /**
     * Revokes one responsibility; it never deletes historical rows.
     * @param id parent order
     * @param role preparation role being revoked
     * @param command version, retry key and reason
     * @return immutable completion receipt
     */
    @Transactional
    public AssessmentReceipt end(@Positive long id, CaseRole role,
            @Valid EndCaseAssignmentRequest command) {
        CaseOrder order = parent(id);
        CurrentActor actor = manager();
        EndCaseAssignmentRequest request = new EndCaseAssignmentRequest(
                command.recordVersion().toLowerCase(Locale.ROOT),
                command.operationId(), command.reason().trim());
        String hash = hash("END_" + role.name(), request);
        Optional<AssessmentReceipt> replay = replay(id, actor,
                request.operationId(), hash);
        List<CaseAssignmentResponse> before;

        if (replay.isPresent()) {
            return replay.get();
        }
        editableAssignment(order, request.recordVersion());
        before = active(id);
        if (cases.end(id, role) == 0) {
            throw policy.conflict("ASSIGNMENT_NOT_ACTIVE",
                    "No active assignment exists for that role.");
        }
        commitments.invalidatePreparation(id);
        transition(order, order.status());
        return finish(id, actor, request.operationId(), "END", hash,
                request, before);
    }

    private AssessmentReceipt finish(long id, CurrentActor actor,
            UUID operation, String action, String hash, Object command,
            List<CaseAssignmentResponse> before) {
        CaseOrder order = parent(id);
        AssessmentReceipt receipt = new AssessmentReceipt(id,
                OrderStatus.valueOf(order.status()),
                order.version(), cases.decision(order.revisionId())
                        .map(CaseViews::decision).orElse(null), active(id));

        audit.append(id, actor.userId(), action, json.write(Map.of(
                "schemaVersion", 1, "operationId", operation,
                "requestHash", hash, "command", command, "before", before,
                "response", receipt)));
        return receipt;
    }

    private List<CaseAssignmentResponse> active(long id) {
        return cases.assignments(id, true, 0, 100).stream()
                .map(CaseViews::assignment).toList();
    }

    private Optional<AssessmentReceipt> replay(long id, CurrentActor actor,
            UUID operation, String hash) {
        Optional<String> encoded = audit.operation(id, actor.userId(),
                operation);
        JsonNode node;

        if (encoded.isEmpty()) {
            return Optional.empty();
        }
        try {
            node = mapper.readTree(encoded.get());
            if (node.path("schemaVersion").asInt() != 1) {
                throw policy.conflict("ASSESSMENT_DATA_UNSUPPORTED",
                        "This action requires a supported data version.");
            }
            if (!hash.equals(node.path("requestHash").asText())) {
                throw policy.conflict("ASSESSMENT_RETRY_CONFLICT",
                        "This operation identifier was used for other input.");
            }
            return Optional.of(mapper.treeToValue(node.get("response"),
                    AssessmentReceipt.class));
        } catch (JsonProcessingException error) {
            throw policy.conflict("ASSESSMENT_DATA_UNSUPPORTED",
                    "This action requires a supported data version.");
        }
    }

    private String hash(String action, Object request) {
        return TokenTools.hash(json.canonical(Map.of("action", action,
                "request", request)));
    }

    private void editableAssignment(CaseOrder order, String version) {
        policy.version(order, version);
        if (!"APPROVED".equals(order.status())
                || order.revisionId() == null
                || cases.decision(order.revisionId()).isEmpty()
                || !"APPROVED".equals(cases.decision(order.revisionId())
                        .orElseThrow().decision())) {
            throw policy.conflict("ASSIGNMENT_STATE_CONFLICT",
                    "Assign only an approved current revision.");
        }
        requireUncommitted(order.orderId());
    }

    private void requireUncommitted(long id) {
        if (commitments.protectedOrder(id)) {
            throw policy.conflict("ORDER_CHANGE_REQUEST_REQUIRED",
                    "Protected commitments require controlled handling.");
        }
    }

    private void validateTeam(long specialist, long coordinator) {
        Map<Long, String> team = new TreeMap<>();

        if (specialist == coordinator) {
            throw policy.conflict("ASSIGNMENT_STAFF_INVALID",
                    "Select a distinct active Specialist and Coordinator.");
        }
        team.put(specialist, CaseRole.SPECIALIST.name());
        team.put(coordinator, CaseRole.COORDINATOR.name());
        team.forEach(this::validateStaff);
    }

    private void validateStaff(Long staff, String role) {
        long id = staff == null ? 0L : staff;

        if (!cases.activeStaff(id, role)) {
            throw policy.conflict("ASSIGNMENT_STAFF_INVALID",
                    "The selected staff member is not active in that role.");
        }
    }

    private CurrentActor manager() {
        CurrentActor actor = actors.current();

        if (!cases.activeStaff(actor.staffId(), "MANAGER")) {
            throw new AccessDeniedException("Access is denied.");
        }
        return actor;
    }

    private CaseOrder parent(long id) {
        return cases.order(id, true).orElseThrow(() -> new OrderException(
                HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                "The requested record was not found."));
    }

    private void transition(CaseOrder order, String state) {
        if (!cases.transition(order, state)) {
            throw policy.conflict("ORDER_VERSION_CONFLICT",
                    "Request changed. Reload it before acting again.");
        }
    }
}
