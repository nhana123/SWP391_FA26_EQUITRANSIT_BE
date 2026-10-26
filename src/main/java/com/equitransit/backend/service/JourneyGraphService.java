/*
 * JourneyGraphService
 * Author: EquiTransit development team
 * Date: 2026-10-21
 * Purpose: Atomic studio compilation and scoped graph tracking reads.
 */
package com.equitransit.backend.service;

import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphDraftRequest;
import com.equitransit.backend.dto.plan.JourneyGraphModels.
        GraphDriverCandidate;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphLayoutRequest;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphNodeInput;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphWireView;
import com.equitransit.backend.dto.plan.JourneyGraphModels.JourneyGraphView;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanSummaryView;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.activity.ActivityCode;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.JourneyGraphRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.plan.PlanRepository;
import com.equitransit.backend.service.activity.AuditWriter;
import com.equitransit.backend.service.plan.OperationalPlanPolicy;
import com.equitransit.backend.service.plan.OperationalPlanService;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Graph is a presentation of the real F10 plan, never a second scheduler. */
@Service
@Validated
@Transactional(timeout = 15)
@PreAuthorize("hasAnyRole('MANAGER','COORDINATOR','DRIVER','CUSTOMER')")
public class JourneyGraphService {

    private final JourneyGraphRepository graphs;
    private final OperationalPlanService planning;
    private final PlanRepository plans;
    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final CurrentActorService actors;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final AuditWriter audit;

    /** @param graphs graph JDBC @param planning existing F10 command facade
     * @param plans plan read/retry adapter @param cases parent lock/access
     * @param orders immutable revision snapshots
     * @param actors live identity @param json canonical receipt hashing
     * @param mapper typed receipt parsing @param audit append-only audit */
    public JourneyGraphService(JourneyGraphRepository graphs,
            OperationalPlanService planning, PlanRepository plans,
            AssessmentRepository cases, OrderRepository orders,
            CurrentActorService actors,
            OrderJsonCodec json, ObjectMapper mapper, AuditWriter audit) {
        this.graphs = graphs;
        this.planning = planning;
        this.plans = plans;
        this.cases = cases;
        this.orders = orders;
        this.actors = actors;
        this.json = json;
        this.mapper = mapper;
        this.audit = audit;
    }

    /** @param order parent @param plan selected plan @return scoped graph */
    public JourneyGraphView get(@Positive long order, @Positive long plan) {
        var ctx = access(order, false);
        var summary = plans.findPlan(order, plan).orElseThrow(this::missing);

        authorizeRead(ctx, summary);
        return view(ctx, summary);
    }

    /** @param order parent @param request whole graph and F10 command
     * @return safe graph linked to generated plan/leg IDs */
    @PreAuthorize("hasAnyRole('MANAGER','COORDINATOR')")
    public JourneyGraphView compile(@Positive long order,
            @Valid GraphDraftRequest request) {
        access(order, true);

        JourneyGraphPolicy.validate(request);
        validateReferences(order, request);
        var result = planning.createGraphDraft(order, request.draft(), request);

        // The F10 receipt and graph commit or roll back together.
        // An identical retry does not overwrite a later layout adjustment.
        if (graphs.nodes(result.planId()).isEmpty()) {
            graphs.insert(result.planId(), request.checkpointNodes(),
                    request.wires(), request.layout());
        }
        return get(order, result.planId());
    }

    /** Advisory picker; assignment rechecks eligibility under existing locks.
     * @param order case @param plan draft @param leg ROAD leg
     * @param search bounded literal prefix @param buffer separation minutes
     * @return at most 10 active eligible drivers */
    @PreAuthorize("hasAnyRole('MANAGER','COORDINATOR')")
    public List<GraphDriverCandidate> drivers(@Positive long order,
            @Positive long plan, @Positive long leg,
            @Size(max = 100) String search,
            @Min(0) @Max(525600) int buffer) {
        var ctx = access(order, false);
        var summary = plans.findPlan(order, plan).orElseThrow(this::missing);

        if (!OperationalPlanPolicy.editable(summary.planStatus())
                || ctx.parent().revisionId() == null
                || ctx.parent().revisionId() != summary.revisionId()
                || plans.legs(plan).stream().noneMatch(l -> l.legId() == leg
                    && l.transportMode() == PlanLegMode.ROAD)) {
            throw missing();
        }
        return graphs.driverCandidates(plan, leg,
                search == null ? "" : search.trim(), buffer);
    }

    /** @param order parent @param plan draft @param request positions only
     * @return retry-stable layout receipt; no operational leg mutation */
    @PreAuthorize("hasAnyRole('MANAGER','COORDINATOR')")
    public JourneyGraphView layout(@Positive long order, @Positive long plan,
            @Valid GraphLayoutRequest request) {
        var ctx = access(order, true);
        String hash = TokenTools.hash("GRAPH_LAYOUT:" + plan + ":"
                + json.canonical(request));
        var prior = plans.operation(order, ctx.actor().userId(),
                request.command().operationId());

        if (!prior.isEmpty()) {
            var receipt = prior.getFirst();

            if (!hash.equals(receipt.get("RequestHash"))) {
                throw conflict("PLAN_RETRY_CONFLICT");
            }
            try {
                return mapper.readValue(receipt.get("ReceiptJSON").toString(),
                        JourneyGraphView.class);
            } catch (JsonProcessingException error) {
                throw conflict("GRAPH_DATA_UNSUPPORTED");
            }
        }
        var summary = plans.findPlan(order, plan).orElseThrow(this::missing);

        if (!ctx.parent().version().equalsIgnoreCase(
                request.command().recordVersion())) {
            throw conflict("ORDER_VERSION_CONFLICT");
        }
        if (ctx.parent().revisionId() == null
                || ctx.parent().revisionId() != summary.revisionId()
                || !Set.of("APPROVED", "IN_PREPARATION", "CONFIRMED")
                        .contains(ctx.parent().status())
                || !OperationalPlanPolicy.editable(summary.planStatus())) {
            throw conflict("PLAN_IMMUTABLE");
        }
        var keys = new HashSet<String>();
        var nodes = graphs.nodes(plan);

        for (var position : request.positions()) {
            if (!keys.add(position.nodeKey())) {
                throw conflict("GRAPH_NODE_DUPLICATE");
            }
        }
        if (nodes.isEmpty() || keys.size() != nodes.size()
                || !keys.equals(nodes.stream().map(GraphNodeInput::nodeKey)
                        .collect(java.util.stream.Collectors.toSet()))) {
            throw conflict("GRAPH_LAYOUT_KEYS_MISMATCH");
        }
        graphs.positions(plan, request.positions(), request.layout());
        plans.touchOrder(order);
        var result = get(order, plan);

        plans.saveOperation(order, ctx.actor().userId(),
                request.command().operationId(), hash, json.write(result));
        audit.append(order, ActivityCode.UPDATED,
                request.command().operationId(), mapper.valueToTree(
                        Map.of("entityId", plan, "action", "GRAPH_LAYOUT")));
        return result;
    }

    private void validateReferences(long order, GraphDraftRequest request) {
        long revision = request.draft().revisionId();
        var snapshot = json.snapshot(orders.revision(order, revision)
                .orElseThrow(this::missing).snapshot()).request();
        var nodes = request.checkpointNodes();

        if (!nodes.getFirst().countryCode().equals(
                snapshot.getOriginCountryCode())
                || !nodes.getLast().countryCode().equals(
                        snapshot.getDestinationCountryCode())) {
            throw conflict("GRAPH_ROUTE_REVISION_MISMATCH");
        }
        var reservations = nodes.stream().map(
                GraphNodeInput::quarantineReservationId)
                .filter(java.util.Objects::nonNull).distinct().toList();

        if (graphs.quarantineCount(revision, reservations)
                != reservations.size()) {
            throw conflict("GRAPH_QUARANTINE_REVISION_MISMATCH");
        }
        var suppliers = nodes.stream().filter(n -> n.details() != null)
                .flatMap(n -> Stream.of(n.details().facilitySupplierId(),
                        n.details().groundHandlerSupplierId(),
                        n.details().insurerSupplierId()))
                .filter(java.util.Objects::nonNull).distinct().toList();

        // Bounded metadata; batch existence checks keep reads independent of N.
        if (!graphs.activeSuppliers(suppliers)) {
            throw conflict("SUPPLIER_INACTIVE");
        }
    }

    private Context access(long order, boolean write) {
        if (!graphs.ready() || !plans.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GRAPH_SCHEMA_PENDING",
                    "Apply migration 018 on the sandbox before using Studio.");
        }
        var actor = actors.current();
        var parent = cases.order(order, write).orElseThrow(this::missing);
        boolean editor = Set.of("MANAGER", "COORDINATOR")
                .contains(actor.roleCode());

        if ("CUSTOMER".equals(actor.roleCode())) {
            var scope = graphs.scope(order).orElseThrow(this::missing);

            if (write || actor.customerId() == null
                    || actor.customerId() != scope.customerId()) {
                throw missing();
            }
        } else if (actor.staffId() == null
                || !cases.activeStaff(actor.staffId(), actor.roleCode())
                || !graphs.activeProfile(actor.staffId())
                || !editor && !"DRIVER".equals(actor.roleCode())
                || write && !editor) {
            throw new AccessDeniedException("Active Studio role required.");
        } else if ("COORDINATOR".equals(actor.roleCode())
                && !cases.assigned(order, actor.staffId(),
                        CaseRole.COORDINATOR)) {
            throw missing();
        }
        return new Context(actor, parent, editor);
    }

    private void authorizeRead(Context ctx, PlanSummaryView summary) {
        if (ctx.editor()) {
            return;
        }
        var scope = graphs.scope(ctx.parent().orderId())
                .orElseThrow(this::missing);

        if (scope.currentPlanId() == null
                || scope.currentPlanId() != summary.planId()
                || !Set.of(PlanState.CONFIRMED, PlanState.ACTIVE,
                        PlanState.IN_TRANSIT, PlanState.COMPLETED)
                        .contains(summary.planStatus())
                || "DRIVER".equals(ctx.actor().roleCode())
                    && !graphs.assignedDriver(summary.planId(),
                            ctx.actor().staffId())) {
            throw missing();
        }
    }

    private JourneyGraphView view(Context ctx, PlanSummaryView summary) {
        var nodes = graphs.nodes(summary.planId());
        var wires = nodes.isEmpty() ? List.<GraphWireView>of()
                : graphs.wires(summary.planId(), ctx.actor().staffId());

        if (nodes.size() > 21 || wires.size() > 20) {
            throw conflict("GRAPH_DATA_UNSUPPORTED");
        }
        if (!ctx.editor()) {
            nodes = nodes.stream().map(n -> new GraphNodeInput(n.nodeKey(),
                    n.nodeType(), n.sequenceOrder(), n.locationName(),
                    n.countryCode(), n.plannedArrivalAt(),
                    n.plannedDepartureAt(), n.dwellMinutes(), null, null,
                    null, null, n.posX(), n.posY(), null)).toList();
            wires = wires.stream().map(w -> new GraphWireView(w.legId(),
                    w.sequenceNo(), w.fromNodeKey(), w.toNodeKey(),
                    w.transportMode(), w.legStatus(), w.startPoint(),
                    w.endPoint(), w.distanceKm(), null, w.plannedDeparture(),
                    w.plannedArrival(), w.actualDeparture(), w.actualArrival(),
                    "DRIVER".equals(ctx.actor().roleCode())
                            ? w.vehicleId() : null,
                    null, w.flightNumber(), null,
                    w.assignedToCurrentDriver())).toList();
        }
        return new JourneyGraphView(ctx.parent().orderId(), summary.planId(),
                summary.revisionId(), summary.planStatus(),
                ctx.parent().version(), ctx.editor()
                        && OperationalPlanPolicy.editable(summary.planStatus())
                        && ctx.parent().revisionId() != null
                        && ctx.parent().revisionId() == summary.revisionId()
                        && Set.of("APPROVED", "IN_PREPARATION", "CONFIRMED")
                            .contains(ctx.parent().status()),
                nodes, wires, graphs.layout(summary.planId()));
    }

    private OrderException conflict(String code) {
        return new OrderException(HttpStatus.CONFLICT, code,
                "Journey graph changed or cannot be modified; reload case.");
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND, "GRAPH_NOT_FOUND",
                "The requested journey graph or case was not found.");
    }

    private record Context(CurrentActor actor, CaseOrder parent,
            boolean editor) { }
}
