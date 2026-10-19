package com.equitransit.backend.service.execution;


import com.equitransit.backend.dto.execution.ExecutionModels.ExecutionOverview;
import com.equitransit.backend.dto.execution.ExecutionModels.ExecutionReceipt;
import com.equitransit.backend.dto.execution.ExecutionModels.LegView;
import com.equitransit.backend.dto.execution.ExecutionModels.MilestoneRequest;
import com.equitransit.backend.dto.execution.ExecutionModels.MilestoneView;
import com.equitransit.backend.dto.execution.ExecutionModels.TransitionRequest;
import com.equitransit.backend.dto.execution.ExecutionModels.WelfareRequest;
import com.equitransit.backend.dto.execution.ExecutionModels.WelfareView;
import com.equitransit.backend.dto.execution.ExecutionModels.HealthStatus;
import com.equitransit.backend.dto.execution.ExecutionModels.SourceCode;
import com.equitransit.backend.dto.execution.ExecutionModels.
        RecordedMilestoneType;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.order.OrderStatus;
import com.equitransit.backend.entity.activity.ActivityCode;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.execution.LegExecutionOperationRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.equitransit.backend.service.activity.AuditWriter;


/** Parent-first locks serialize F12 retries, corrections and transitions. */
@Service
@Validated
@PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','COORDINATOR','DRIVER')")
public class LegExecutionService {
    private final LegExecutionOperationRepository data;
    private final AssessmentRepository cases;
    private final CurrentActorService actors;
    private final AuditWriter audit;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** @param data SQL adapter @param cases parent/assignment access
     * @param actors live account @param audit append-only F16 facade
     * @param json strict canonical codec @param mapper typed receipt codec
     * @param clock server UTC clock */
    public LegExecutionService(LegExecutionOperationRepository data,
            AssessmentRepository cases, CurrentActorService actors,
            AuditWriter audit, OrderJsonCodec json, ObjectMapper mapper,
            Clock clock) {
        this.data = data;
        this.cases = cases;
        this.actors = actors;
        this.audit = audit;
        this.json = json;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** @param order parent @param page independent history page @param size
     * bounded rows per history @return current-plan facts in caller scope */
    @Transactional(timeout = 15)
    public ExecutionOverview overview(@Positive long order,
            @Min(0) int page, @Min(1) @Max(100) int size) {
        var ctx = access(order);
        var a = ctx.args();

        a.put("offset", (long) page * size);
        a.put("size", size);
        var legs = data.rows("legs", a).stream().map(r -> new LegView(
                id(r, "LegID"), ((Number) r.get("SequenceNo")).intValue(),
                PlanLegMode.valueOf(text(r, "TransportMode")),
                LegState.valueOf(text(r, "LegStatus")),
                at(r, "PlannedDeparture"), at(r, "PlannedArrival"),
                at(r, "ActualDeparture"), at(r, "ActualArrival"))).toList();
        var milestones = data.rows("milestones", a).stream().map(r ->
                new MilestoneView(id(r, "MilestoneID"), id(r, "LegID"),
                        RecordedMilestoneType.valueOf(text(r, "MilestoneType")),
                        at(r, "OccurredAt"),
                        at(r, "RecordedAt"), source(r),
                        text(r, "SourceReference"), text(r, "Latitude"),
                        text(r, "Longitude"), text(r, "LocationDescription"),
                        at(r, "EstimatedArrivalAt"), text(r, "DelayReason"),
                        text(r, "PublicNote"), text(r, "Notes"),
                        Boolean.TRUE.equals(r.get("IsCustomerVisible"))))
                .toList();
        var welfare = data.rows("welfareLogs", a).stream().map(r ->
                new WelfareView(id(r, "WelfareLogID"), id(r, "LegID"),
                        HealthStatus.valueOf(text(r, "HealthStatus")),
                        at(r, "CheckTime"), at(r, "RecordedAt"),
                        text(r, "TemperatureCelsius"),
                        text(r, "WaterIntakeLiters"), text(r, "FeedAmountKg"),
                        source(r), text(r, "SourceReference"),
                        text(r, "BehaviorNotes"), text(r, "CareNotes"),
                        text(r, "PublicNote"), r.get("SupersedesLogID") == null
                                ? null : id(r, "SupersedesLogID"),
                        Boolean.TRUE.equals(r.get("IsCustomerVisible"))))
                .toList();
        var last = at(data.rows("milestoneLast", a).getFirst(), "LastUpdate");
        var careLast = at(data.rows("welfareLast", a).getFirst(), "LastUpdate");

        if (careLast != null && (last == null || careLast.isAfter(last))) {
            last = careLast;
        }
        return new ExecutionOverview(order, id(a, "plan"),
                ctx.parent().version(), OrderStatus.valueOf(
                        ctx.parent().status()), page, size,
                data.number("legCount", a),
                data.number("milestoneCount", a),
                data.number("welfareCount", a), last, legs, milestones,
                welfare);
    }

    /** @param order parent @param leg target @param body evidence @return
     * retry-stable transition receipt; start=false is completion */
    @Transactional(timeout = 15)
    public ExecutionReceipt transition(@Positive long order,
            @Positive long leg, @Valid TransitionRequest body,
            boolean start) {
        return write(order, leg, body.command(), body,
                start ? "START" : "COMPLETE");
    }

    /** @param order parent @param leg target @param body fact
     * @return receipt */
    @Transactional(timeout = 15)
    public ExecutionReceipt milestone(@Positive long order,
            @Positive long leg, @Valid MilestoneRequest body) {
        return write(order, leg, body.command(), body, "MILESTONE");
    }

    /** @param order parent @param leg target @param body care
     * @return receipt */
    @Transactional(timeout = 15)
    public ExecutionReceipt welfare(@Positive long order,
            @Positive long leg, @Valid WelfareRequest body) {
        return write(order, leg, body.command(), body, "WELFARE");
    }

    private ExecutionReceipt write(long order, long leg, QuoteCommand command,
            Object body, String action) {
        var ctx = access(order);
        var a = ctx.args();

        a.put("leg", leg);
        var target = data.rows("leg", a);

        if (target.isEmpty()) {
            throw missing();
        }
        var row = target.getFirst();
        authorizeLeg(ctx.actor(), row, a);
        a.put("operation", command.operationId());
        String hash = TokenTools.hash(action + ":" + leg + ":"
                + json.canonical(body));
        var prior = data.rows("operation", a);

        if (!prior.isEmpty()) {
            if (!hash.equals(prior.getFirst().get("RequestHash"))) {
                throw conflict("IDEMPOTENCY_CONFLICT");
            }
            try {
                return mapper.readValue(prior.getFirst().get("ReceiptJSON")
                        .toString(), ExecutionReceipt.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw conflict("EXECUTION_RECEIPT_UNSUPPORTED");
            }
        }
        if ("DRIVER".equals(ctx.actor().roleCode())
                && data.number("driver", a) == 0) {
            throw new AccessDeniedException("Active leg assignment required.");
        }
        if (!ctx.parent().version().equalsIgnoreCase(
                command.recordVersion())) {
            throw conflict("ORDER_VERSION_CONFLICT");
        }
        if (!"IN_TRANSIT".equals(ctx.parent().status())
                || !"IN_TRANSIT".equals(a.get("planStatus"))) {
            throw conflict("EXECUTION_NOT_DEPARTED");
        }
        var now = OffsetDateTime.now(clock).withNano(0);

        a.put("now", LegExecutionOperationRepository.utc(now));
        Long incident = null;
        long entity;

        if (body instanceof TransitionRequest request) {
            entity = transition(row, a, request, action.equals("START"), now);
        } else if (body instanceof MilestoneRequest request) {
            fact(row, a, request.occurredAt(), request.sourceCode(),
                    request.sourceReference(), now);
            LegExecutionPolicy.coordinates(request.latitude(),
                    request.longitude());
            LegExecutionPolicy.require(request.estimatedArrivalAt() == null
                    || !request.estimatedArrivalAt().isBefore(
                            request.occurredAt()), "ETA_BEFORE_EVENT");
            a.put("type", request.milestoneType().name());
            a.put("latitude", LegExecutionPolicy.decimal(request.latitude()));
            a.put("longitude", LegExecutionPolicy.decimal(request.longitude()));
            a.put("location", request.locationDescription());
            a.put("eta", LegExecutionOperationRepository.utc(
                    request.estimatedArrivalAt()));
            a.put("delay", request.delayReason());
            a.put("notes", request.internalNotes());
            visible(a, request.customerVisible(), request.publicNote());
            entity = data.number("milestone", a);
        } else {
            var request = (WelfareRequest) body;

            fact(row, a, request.occurredAt(), request.sourceCode(),
                    request.sourceReference(), now);
            a.put("supersedes", request.supersedesLogId());
            if (request.supersedesLogId() != null) {
                var previous = data.rows("supersedes", a);

                LegExecutionPolicy.require(!previous.isEmpty(),
                        "WELFARE_CORRECTION_WRONG_LEG");
                LegExecutionPolicy.require(data.number("correctionExists", a)
                        == 0, "WELFARE_ALREADY_SUPERSEDED");
                LegExecutionPolicy.require(!Boolean.TRUE.equals(previous
                        .getFirst().get("IsCustomerVisible"))
                        || request.customerVisible(),
                        "PUBLIC_CORRECTION_MUST_REMAIN_VISIBLE");
            }
            a.put("health", request.healthStatus().name());
            a.put("temperature", LegExecutionPolicy.decimal(
                    request.temperatureCelsius()));
            a.put("water", LegExecutionPolicy.decimal(
                    request.waterIntakeLiters()));
            a.put("feed", LegExecutionPolicy.decimal(request.feedIntakeKg()));
            a.put("behavior", request.behaviorNotes());
            a.put("care", request.careNotes());
            visible(a, request.customerVisible(), request.publicNote());
            entity = data.number("welfare", a);
            if (request.healthStatus() == HealthStatus.URGENT) {
                incident = data.number("incident", a);
            }
        }
        data.update("touch", a);
        var parent = cases.order(order, false).orElseThrow();
        var receipt = new ExecutionReceipt(order, parent.version(),
                command.operationId(), leg, entity, incident);

        a.put("hash", hash);
        a.put("receipt", json.write(receipt));
        data.update("save", a);
        audit.append(order, ActivityCode.UPDATED, command.operationId(),
                mapper.valueToTree(Map.of("entityId", entity,
                        "orderId", order, "count", 1)));
        return receipt;
    }

    private long transition(Map<String, Object> row, Map<String, Object> a,
            TransitionRequest request, boolean start, OffsetDateTime now) {
        if (start) {
            int sequence = ((Number) row.get("SequenceNo")).intValue();

            LegExecutionPolicy.require(sequence > 1, "FIRST_LEG_REQUIRES_F11");
            a.put("previous", sequence - 1);
            var previous = data.rows("previous", a);

            LegExecutionPolicy.require(!previous.isEmpty()
                    && "COMPLETED".equals(previous.getFirst().get("LegStatus"))
                    && "CONFIRMED".equals(row.get("LegStatus")),
                    "PREVIOUS_LEG_NOT_COMPLETED");
            LegExecutionPolicy.time(request.occurredAt(),
                    at(previous.getFirst(), "ActualArrival"), null, now);
            LegExecutionPolicy.source(text(row, "TransportMode"),
                    request.sourceCode());
            a.put("at",
                    LegExecutionOperationRepository.utc(request.occurredAt()));
            a.put("source", request.sourceCode().name());
            a.put("reference", request.sourceReference());
        } else {
            LegExecutionPolicy.require(
                    "IN_TRANSIT".equals(row.get("LegStatus")),
                    "LEG_NOT_IN_TRANSIT");
            fact(row, a, request.occurredAt(), request.sourceCode(),
                    request.sourceReference(), now);
        }
        LegExecutionPolicy.require(data.update(start ? "start" : "complete", a)
                == 1, "LEG_STATE_CONFLICT");
        data.update(start ? "assignmentStart" : "assignmentEnd", a);
        a.put("type", start ? "LEG_STARTED" : "LEG_COMPLETED");
        for (String key : List.of("latitude", "longitude", "notes", "location",
                "eta", "delay")) {
            a.put(key, null);
        }
        visible(a, true, start ? "Leg started." : "Leg completed.");
        return data.number("milestone", a);
    }

    private void fact(Map<String, Object> row, Map<String, Object> a,
            OffsetDateTime event,
            com.equitransit.backend.dto.execution.ExecutionModels.SourceCode
                    source, String reference, OffsetDateTime now) {
        LegExecutionPolicy.require(List.of("IN_TRANSIT", "COMPLETED")
                .contains(row.get("LegStatus")), "LEG_NOT_DEPARTED");
        LegExecutionPolicy.source(text(row, "TransportMode"), source);
        LegExecutionPolicy.time(event, at(row, "ActualDeparture"),
                at(row, "ActualArrival"), now);
        a.put("at", LegExecutionOperationRepository.utc(event));
        a.put("source", source.name());
        a.put("reference", reference);
    }

    private Context access(long order) {
        var actor = actors.current();
        var parent = cases.order(order, true).orElseThrow(this::missing);

        if (!data.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "EXECUTION_SCHEMA_PENDING", "Deploy migration014.");
        }
        var a = new HashMap<String, Object>();

        a.put("order", order);
        var scopes = data.rows("scope", a);

        if (scopes.isEmpty()) {
            throw missing();
        }
        var scope = scopes.getFirst();
        String role = actor.roleCode();
        boolean customer = "CUSTOMER".equals(role);

        a.put("plan", scope.get("CurrentPlanID"));
        a.put("planStatus", scope.get("PlanStatus"));
        a.put("staff", actor.staffId());
        a.put("user", actor.userId());
        a.put("customer", customer ? 1 : 0);
        a.put("driver", "DRIVER".equals(role) ? 1 : 0);
        if (customer) {
            if (actor.customerId() == null || actor.customerId().longValue()
                    != id(scope, "CustomerID")) {
                throw missing();
            }
        } else if (actor.staffId() == null
                || !cases.activeStaff(actor.staffId(), role)
                || !("MANAGER".equals(role) || "COORDINATOR".equals(role)
                    && cases.assigned(order, actor.staffId(),
                            CaseRole.COORDINATOR)
                    || "DRIVER".equals(role)
                    && data.number("driverRead", a) > 0)) {
            throw new AccessDeniedException("Active assignment required.");
        }
        if (parent.revisionId() == null || parent.revisionId().longValue()
                != id(scope, "RevisionID")) {
            throw conflict("EXECUTION_REVISION_MISMATCH");
        }
        return new Context(parent, actor, a);
    }

    private void authorizeLeg(CurrentActor actor, Map<String, Object> row,
            Map<String, Object> args) {
        if ("CUSTOMER".equals(actor.roleCode())
                || "DRIVER".equals(actor.roleCode())
                && (!"ROAD".equals(row.get("TransportMode"))
                    || data.number("driverHistory", args) == 0)) {
            throw new AccessDeniedException("No write access to this leg.");
        }
    }

    private void visible(Map<String, Object> args, boolean visible,
            String note) {
        args.put("visible", visible);
        args.put("publicNote", note);
    }

    private static SourceCode source(Map<String, Object> row) {
        return row.get("SourceCode") == null ? null
                : SourceCode.valueOf(text(row, "SourceCode"));
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : row.get(key).toString();
    }

    private static long id(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static OffsetDateTime at(Map<String, Object> row, String key) {
        return row.get(key) == null ? null
                : ((Timestamp) row.get(key)).toInstant()
                        .atOffset(ZoneOffset.UTC);
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND",
                "The current execution plan was not found.");
    }

    private OrderException conflict(String code) {
        return new OrderException(HttpStatus.CONFLICT, code,
                "Reload execution data before retrying.");
    }

    private record Context(CaseOrder parent, CurrentActor actor,
            HashMap<String, Object> args) { }
}
