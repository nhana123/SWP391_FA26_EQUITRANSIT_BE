/*
 * OperationalPlanService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Build and confirm a multimodal operational plan.
 */
package com.equitransit.backend.service.plan;

import com.equitransit.backend.dto.plan.PlanModels.PlanCommand;
import com.equitransit.backend.dto.plan.PlanModels.PlanConfirmRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanDetailResponse;
import com.equitransit.backend.dto.plan.PlanModels.PlanDraftRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegInput;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegView;
import com.equitransit.backend.dto.plan.PlanModels.PlanPageResponse;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanRoadAssignmentRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.PlanSummaryView;
import com.equitransit.backend.dto.plan.PlanModels.SharedStallCandidateView;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAvailabilityRequest;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceMode;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.activity.ActivityCode;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.plan.PlanRepository;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.equitransit.backend.service.resource.ResourceAvailabilityService;
import com.equitransit.backend.service.quote.QuotationCommitmentFacade;
import com.equitransit.backend.service.pricing.ServiceAssessmentFacade;
import com.equitransit.backend.service.activity.AuditWriter;

/** Transactional F10 planning facade; parent-first lock order is enforced. */
@Service
@Validated
@PreAuthorize("hasAnyRole('MANAGER','COORDINATOR')")
@Transactional(timeout = 15)
public class OperationalPlanService {

    private final PlanRepository plans;
    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final ResourceAvailabilityService availability;
    private final ResourceRepository resources;
    private final QuotationCommitmentFacade quotes;
    private final ServiceAssessmentFacade assessments;
    private final AuditWriter audit;
    private final CurrentActorService actors;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * @param plans SQL planning adapter @param cases parent lock and roles
     * @param orders revision snapshots @param availability F09 lock consumer
     * @param resources resource lock @param quotes F06 accepted quote boundary
     * @param assessments F08 quarantine assessment reader
     * @param audit F16 append-only audit @param actors live identity
     * @param json canonical hash codec @param mapper receipt serializer
     * @param clock server UTC
     */
    public OperationalPlanService(PlanRepository plans,
            AssessmentRepository cases, OrderRepository orders,
            ResourceAvailabilityService availability,
            ResourceRepository resources, QuotationCommitmentFacade quotes,
            ServiceAssessmentFacade assessments, AuditWriter audit,
            CurrentActorService actors, OrderJsonCodec json,
            ObjectMapper mapper, Clock clock) {
        this.plans = plans;
        this.cases = cases;
        this.orders = orders;
        this.availability = availability;
        this.resources = resources;
        this.quotes = quotes;
        this.assessments = assessments;
        this.audit = audit;
        this.actors = actors;
        this.json = json;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** @param order parent @param page zero-based @param size bounded
     * @return paginated plan summaries */
    public PlanPageResponse list(@Positive long order, @Min(0) int page,
            @Min(1) @Max(100) int size) {
        var parent = access(order, false).parent();

        return new PlanPageResponse(plans.listPlans(parent.orderId(), page,
                size), page, size, plans.countPlans(parent.orderId()));
    }

    /** @param order parent @param plan plan ID @return full plan detail */
    public PlanDetailResponse get(@Positive long order, @Positive long plan) {
        var ctx = access(order, false);
        var summary = plans.findPlan(order, plan).orElseThrow(this::missing);

        return detail(ctx.parent(), summary);
    }

    /** @param order parent order ID
     * @return compatible shared air stall candidate orders */
    public List<SharedStallCandidateView> sharedStallCandidates(
            @Positive long order) {
        var parent = access(order, false).parent();

        if (parent.revisionId() == null) {
            return List.of();
        }
        return plans.sharedStallCandidates(parent.orderId(),
                parent.revisionId());
    }

    /** @param order parent @param req draft legs command
     * @return created versioned draft plan */
    public PlanDetailResponse createDraft(@Positive long order,
            @Valid PlanDraftRequest req) {
        var ctx = access(order, true);
        String hash = fingerprint("CREATE_DRAFT", req);
        var replay = replay(order, ctx.actor(), req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        requireVersion(ctx.parent(), req.command(), req.revisionId());
        OperationalPlanPolicy.validateLegs(req.legs());
        Long confirmer = distanceConfirmer(order, ctx.actor());

        for (PlanLegInput leg : req.legs()) {
            if (leg.supplierId() != null
                    && !resources.activeSupplier(leg.supplierId())) {
                throw conflict("SUPPLIER_INACTIVE",
                        "Leg supplier is inactive or missing.");
            }
            if (leg.distanceSource() != null && confirmer == null) {
                throw conflict("COORDINATOR_REQUIRED_FOR_DISTANCE",
                        "An active assigned Coordinator is required to "
                                + "confirm sourced leg distances.");
            }
        }
        var now = now();
        int version = plans.nextVersion(req.revisionId());
        var start = req.legs().getFirst().plannedDeparture().withNano(0);
        var end = req.legs().getLast().plannedArrival().withNano(0);
        long planId = plans.insertPlan(order, req.revisionId(),
                ctx.actor().staffId(), version, start, end);

        for (PlanLegInput leg : req.legs()) {
            plans.insertLeg(planId, leg, confirmer, now);
        }
        if (req.setAsCurrentDraft()) {
            Long curId = plans.currentPlanId(order);

            if (curId != null) {
                var cur = plans.findPlan(order, curId).orElse(null);

                if (cur != null && !OperationalPlanPolicy.editable(
                        cur.planStatus())) {
                    throw conflict("CURRENT_PLAN_PROTECTED",
                            "Cannot overwrite a confirmed or active current "
                                    + "plan with a draft.");
                }
            }
            plans.setCurrentPlan(order, planId);
        } else {
            plans.touchOrder(order);
        }
        return finish(order, planId, ctx.actor(), req.command(),
                "CREATE_DRAFT", hash);
    }

    /** @param order parent @param plan plan ID @param leg leg ID
     * @param req road vehicle and driver command @return updated plan */
    public PlanDetailResponse assignRoad(@Positive long order,
            @Positive long plan, @Positive long leg,
            @Valid PlanRoadAssignmentRequest req) {
        var ctx = access(order, true);
        String hash = fingerprint("ASSIGN_ROAD:" + plan + ":" + leg, req);
        var replay = replay(order, ctx.actor(), req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        var summary = editablePlan(ctx.parent(), plan, req.command());
        var targetLeg = findLeg(plan, leg, PlanLegMode.ROAD);

        availability.requireAvailable(ResourceKind.VEHICLE, req.vehicleId(),
                new ResourceAvailabilityRequest(order, summary.revisionId(),
                        ResourceMode.ROAD, targetLeg.plannedDeparture(),
                        targetLeg.plannedArrival(), targetLeg.supplierId(),
                        leg), req.vehicleRecordVersion());
        if (!cases.activeStaff(req.driverStaffId(), "DRIVER")) {
            throw conflict("DRIVER_NOT_ELIGIBLE",
                    "Assigned staff must be an active DRIVER.");
        }
        if (plans.driverOverlaps(req.driverStaffId(),
                targetLeg.plannedDeparture(), targetLeg.plannedArrival(),
                req.driverBufferMinutes(), leg)) {
            throw conflict("DRIVER_OVERLAP",
                    "Driver has an overlapping trip assignment.");
        }
        try {
            plans.assignRoad(leg, req, targetLeg.plannedDeparture(),
                    targetLeg.plannedArrival());
        } catch (DataAccessException error) {
            throw translateSql(error);
        }
        plans.touchOrder(order);
        return finish(order, plan, ctx.actor(), req.command(),
                "ASSIGN_ROAD", hash);
    }

    /** @param order parent @param plan plan ID @param leg leg ID
     * @param req air stall equipment command @return updated plan */
    public PlanDetailResponse assignStall(@Positive long order,
            @Positive long plan, @Positive long leg,
            @Valid PlanStallAssignmentRequest req) {
        var ctx = access(order, true);
        String hash = fingerprint("ASSIGN_STALL:" + plan + ":" + leg, req);
        var replay = replay(order, ctx.actor(), req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        var summary = editablePlan(ctx.parent(), plan, req.command());
        var targetLeg = findLeg(plan, leg, PlanLegMode.AIR);

        OperationalPlanPolicy.validateStallRequest(req);
        availability.requireAvailable(ResourceKind.STALL, req.stallId(),
                new ResourceAvailabilityRequest(order, summary.revisionId(),
                        ResourceMode.AIR, targetLeg.plannedDeparture(),
                        targetLeg.plannedArrival(), targetLeg.supplierId(),
                        leg), req.stallRecordVersion());
        var snapshot = json.snapshot(orders.revision(order,
                summary.revisionId()).orElseThrow(this::missing).snapshot());
        var airStall = snapshot.request().getServices().getAirStall();
        BigDecimal slots = StallFitEvaluator.slots(airStall);
        String sharing = airStall.name().equals("PRIVATE") ? "PRIVATE"
                : "SHARED";

        try {
            plans.assignStall(leg, req, slots, sharing, "SUITABLE",
                    targetLeg.plannedDeparture(), targetLeg.plannedArrival(),
                    now());
        } catch (DataAccessException error) {
            throw translateSql(error);
        }
        plans.touchOrder(order);
        return finish(order, plan, ctx.actor(), req.command(),
                "ASSIGN_STALL", hash);
    }

    /** @param order parent @param plan plan ID @param leg leg ID
     * @param req flight reservation command @return updated plan */
    public PlanDetailResponse reserveFlight(@Positive long order,
            @Positive long plan, @Positive long leg,
            @Valid PlanFlightReservationRequest req) {
        var ctx = access(order, true);
        String hash = fingerprint("RESERVE_FLIGHT:" + plan + ":" + leg, req);
        var replay = replay(order, ctx.actor(), req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        var summary = editablePlan(ctx.parent(), plan, req.command());
        findLeg(plan, leg, PlanLegMode.AIR);
        var now = now();
        var snapshot = json.snapshot(orders.revision(order,
                summary.revisionId()).orElseThrow(this::missing).snapshot());
        var airStall = snapshot.request().getServices() == null ? null
                : snapshot.request().getServices().getAirStall();
        BigDecimal requiredSlots = airStall == null ? null
                : StallFitEvaluator.slots(airStall);

        OperationalPlanPolicy.validateFlightRequest(req, requiredSlots, now);
        var offers = plans.airfareOffer(summary.revisionId(), req.offerId(),
                now);

        if (offers.isEmpty()) {
            throw conflict("OFFER_REVISION_MISMATCH",
                    "Supplier offer does not belong to this plan revision.");
        }
        var offer = offers.getFirst();
        var validUntil = ((Timestamp) offer.get("ValidUntil"))
                .toLocalDateTime().atOffset(ZoneOffset.UTC);

        if (!"AIRFARE".equals(offer.get("ServiceCode"))
                || !List.of("RECEIVED", "SELECTED").contains(
                        String.valueOf(offer.get("Status")))
                || !validUntil.isAfter(now)
                || offer.get("EvidenceFileKey") == null
                || offer.get("EvidenceFileKey").toString().isBlank()) {
            throw conflict("OFFER_INVALID_OR_EXPIRED",
                    "Flight reservation requires an active AIRFARE offer.");
        }
        plans.reserveFlight(leg, req, ctx.actor().staffId(), now);
        plans.touchOrder(order);
        return finish(order, plan, ctx.actor(), req.command(),
                "RESERVE_FLIGHT", hash);
    }

    /** @param order parent @param plan plan ID @param req quarantine command
     * @return updated plan */
    public PlanDetailResponse reserveQuarantine(@Positive long order,
            @Positive long plan,
            @Valid PlanQuarantineReservationRequest req) {
        var ctx = access(order, true);
        String hash = fingerprint("RESERVE_QUARANTINE:" + plan, req);
        var replay = replay(order, ctx.actor(), req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        var summary = editablePlan(ctx.parent(), plan, req.command());

        if (summary.revisionId() != req.revisionId()) {
            throw conflict("PLAN_REVISION_MISMATCH",
                    "Quarantine reservation must match the plan revision.");
        }
        if (req.supplierId() != null
                && !resources.activeSupplier(req.supplierId())) {
            throw conflict("SUPPLIER_INACTIVE", "Supplier is inactive.");
        }
        var choices = plans.quarantineChoice(req.revisionId());

        if (choices.isEmpty()) {
            throw conflict("QUARANTINE_CHOICE_MISSING",
                    "Customer revision has no quarantine package choice.");
        }
        var choice = choices.getFirst();
        long choiceId = ((Number) choice.get("QuarantineChoiceID")).longValue();
        Integer choiceDays = (Integer) choice.get("RequiredDays");
        var now = now();
        var snapshot = json.snapshot(orders.revision(order, req.revisionId())
                .orElseThrow(this::missing).snapshot());
        var horizon = snapshot.request().getDeliveryDeadline() == null ? now
                : snapshot.request().getDeliveryDeadline();
        var assessed = assessments.quarantine(order, req.revisionId(), now,
                horizon);
        Integer requiredDays = assessed.requiredDays() != null
                ? assessed.requiredDays() : choiceDays;

        OperationalPlanPolicy.validateQuarantineRequest(req, requiredDays, now);
        plans.reserveQuarantine(choiceId, req, ctx.actor().staffId(), now);
        plans.touchOrder(order);
        return finish(order, plan, ctx.actor(), req.command(),
                "RESERVE_QUARANTINE", hash);
    }

    /** @param order parent @param plan plan ID @param req insurance command
     * @return updated plan */
    public PlanDetailResponse verifyInsurance(@Positive long order,
            @Positive long plan, @Valid PlanInsurancePolicyRequest req) {
        var ctx = access(order, true);
        String hash = fingerprint("VERIFY_INSURANCE:" + plan, req);
        var replay = replay(order, ctx.actor(), req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        var summary = editablePlan(ctx.parent(), plan, req.command());

        if (summary.revisionId() != req.revisionId()) {
            throw conflict("PLAN_REVISION_MISMATCH",
                    "Insurance verification must match the plan revision.");
        }
        var choices = plans.insuranceChoice(req.revisionId());

        if (choices.isEmpty() || "NONE".equals(choices.getFirst().get(
                "CoverageCode"))) {
            throw conflict("INSURANCE_CHOICE_MISSING",
                    "Customer revision has no active insurance choice.");
        }
        long choiceId = ((Number) choices.getFirst().get("InsuranceChoiceID"))
                .longValue();

        OperationalPlanPolicy.validateInsuranceRequest(req,
                summary.plannedStartTime(), summary.plannedEndTime());
        plans.verifyInsurance(choiceId, req, ctx.actor().staffId(), now());
        plans.touchOrder(order);
        return finish(order, plan, ctx.actor(), req.command(),
                "VERIFY_INSURANCE", hash);
    }

    /** @param order parent @param plan plan ID @param req confirm command
     * @return confirmed operational plan receipt */
    public PlanDetailResponse confirm(@Positive long order,
            @Positive long plan, @Valid PlanConfirmRequest req) {
        var ctx = access(order, true);
        String hash = fingerprint("CONFIRM_PLAN:" + plan, req);
        var replay = replay(order, ctx.actor(), req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        var summary = editablePlan(ctx.parent(), plan, req.command());

        quotes.requireAccepted(order, summary.revisionId(),
                req.acceptedQuoteId());
        var now = now();
        var legs = plans.legs(plan);
        var quarantine = plans.quarantineReservations(summary.revisionId());
        var insurance = plans.insurancePolicies(summary.revisionId());
        var choices = plans.serviceChoices(summary.revisionId());
        var blockers = OperationalPlanPolicy.confirmationBlockers(legs,
                quarantine, insurance, choices,
                quarantineDays(order, summary.revisionId(), now), now);

        if (!blockers.isEmpty()) {
            throw conflict("PLAN_CONFIRMATION_BLOCKED",
                    "Plan has unresolved blockers: "
                            + String.join(", ", blockers));
        }
        if (plans.lockHorseAndCheckOverlap(order, summary.plannedStartTime(),
                summary.plannedEndTime())) {
            throw conflict("HORSE_SCHEDULE_OVERLAP",
                    "Horse has an overlapping active operational plan on "
                            + "another order.");
        }
        resources.lock();
        try {
            plans.confirmPlan(order, plan, req.acceptedQuoteId(), now);
        } catch (DataAccessException error) {
            throw translateSql(error);
        }
        return finish(order, plan, ctx.actor(), req.command(),
                "CONFIRM_PLAN", hash);
    }

    private PlanDetailResponse finish(long order, long plan,
            CurrentActor actor, PlanCommand cmd, String action, String hash) {
        var updatedOrder = cases.order(order, false).orElseThrow();
        var summary = plans.findPlan(order, plan).orElseThrow();
        var response = detail(updatedOrder, summary);

        plans.saveOperation(order, actor.userId(), cmd.operationId(), hash,
                json.write(response));
        audit.append(order, ActivityCode.UPDATED, cmd.operationId(),
                mapper.valueToTree(Map.of("entityId", plan, "action", action,
                        "versionNo", summary.versionNo())));
        return response;
    }

    private PlanDetailResponse detail(CaseOrder parent,
            PlanSummaryView summary) {
        var now = now();
        var legs = plans.legs(summary.planId());
        var quarantine = plans.quarantineReservations(summary.revisionId());
        var insurance = plans.insurancePolicies(summary.revisionId());
        var choices = plans.serviceChoices(summary.revisionId());
        var blockers = OperationalPlanPolicy.confirmationBlockers(legs,
                quarantine, insurance, choices,
                quarantineDays(parent.orderId(), summary.revisionId(), now),
                now);

        return new PlanDetailResponse(parent.orderId(), parent.version(),
                summary.planId(), summary.revisionId(), summary.versionNo(),
                summary.planStatus(), summary.currentPlan(),
                summary.acceptedQuoteId(), summary.createdBy(),
                summary.plannedStartTime(), summary.plannedEndTime(),
                summary.financialGateStatus(), summary.operationalGateStatus(),
                summary.docGateStatus(),
                summary.planStatus() == PlanState.CONFIRMED ? now : null,
                legs, quarantine, insurance, blockers);
    }

    private Integer quarantineDays(long order, long revision,
            OffsetDateTime now) {
        var snapshot = json.snapshot(orders.revision(order, revision)
                .orElseThrow(this::missing).snapshot());
        var horizon = snapshot.request().getDeliveryDeadline() == null ? now
                : snapshot.request().getDeliveryDeadline();
        var assessed = assessments.quarantine(order, revision, now, horizon);

        if (assessed.requiredDays() != null) {
            return assessed.requiredDays();
        }
        var choices = plans.quarantineChoice(revision);
        return choices.isEmpty() ? null
                : (Integer) choices.getFirst().get("RequiredDays");
    }

    private PlanSummaryView editablePlan(CaseOrder parent, long plan,
            PlanCommand cmd) {
        var summary = plans.findPlan(parent.orderId(), plan)
                .orElseThrow(this::missing);

        requireVersion(parent, cmd, summary.revisionId());
        if (!OperationalPlanPolicy.editable(summary.planStatus())) {
            throw conflict("PLAN_IMMUTABLE",
                    "Confirmed or superseded plans cannot be modified; "
                            + "create a new plan version.");
        }
        return summary;
    }

    private PlanLegView findLeg(long plan, long legId, PlanLegMode mode) {
        var leg = plans.legs(plan).stream().filter(l -> l.legId() == legId)
                .findFirst().orElseThrow(this::missing);

        if (leg.transportMode() != mode) {
            throw conflict("LEG_MODE_MISMATCH",
                    "Resource mode does not match the leg transport mode.");
        }
        return leg;
    }

    private void requireVersion(CaseOrder parent, PlanCommand cmd,
            long revision) {
        if (!parent.version().equalsIgnoreCase(cmd.recordVersion())) {
            throw conflict("ORDER_VERSION_CONFLICT",
                    "Order changed; reload and retry with current version.");
        }
        if (parent.revisionId() == null || parent.revisionId() != revision
                || !Set.of("APPROVED", "IN_PREPARATION", "CONFIRMED")
                        .contains(parent.status())) {
            throw conflict("PLAN_REVISION_CONFLICT",
                    "Plan must target the current approved order revision.");
        }
    }

    private Long distanceConfirmer(long order, CurrentActor actor) {
        if ("COORDINATOR".equals(actor.roleCode())) {
            return actor.staffId();
        }
        return cases.assignments(order, true, 0, 20).stream()
                .filter(a -> "COORDINATOR".equals(a.role())
                        && cases.activeStaff(a.staffId(), "COORDINATOR"))
                .map(a -> a.staffId()).findFirst().orElse(null);
    }

    private AccessContext access(long order, boolean write) {
        if (!plans.ready() || !resources.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PLAN_SCHEMA_PENDING",
                    "Deploy F09/F10 migrations before operational planning.");
        }
        var actor = actors.current();
        var parent = cases.order(order, write).orElseThrow(this::missing);
        String role = actor.roleCode();

        if (actor.staffId() == null || !List.of("MANAGER", "COORDINATOR")
                .contains(role) || !cases.activeStaff(actor.staffId(), role)) {
            throw new AccessDeniedException(
                    "An active Manager or Coordinator is required.");
        }
        if ("COORDINATOR".equals(role) && !cases.assigned(order,
                actor.staffId(), CaseRole.COORDINATOR)) {
            throw missing();
        }
        return new AccessContext(actor, parent);
    }

    private Optional<PlanDetailResponse> replay(long order, CurrentActor actor,
            PlanCommand cmd, String hash) {
        var rows = plans.operation(order, actor.userId(), cmd.operationId());

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var row = rows.getFirst();

        if (!hash.equals(row.get("RequestHash"))) {
            throw conflict("PLAN_RETRY_CONFLICT",
                    "Operation ID was already used with a different payload.");
        }
        try {
            return Optional.of(mapper.readValue(
                    row.get("ReceiptJSON").toString(),
                    PlanDetailResponse.class));
        } catch (JsonProcessingException error) {
            throw conflict("PLAN_DATA_UNSUPPORTED",
                    "Stored operation receipt cannot be decoded.");
        }
    }

    private OrderException translateSql(DataAccessException error) {
        String msg = error.getMostSpecificCause() == null ? ""
                : error.getMostSpecificCause().getMessage();

        if (msg.contains("overlapping") || msg.contains("51003")
                || msg.contains("51005") || msg.contains("51006")
                || msg.contains("51007") || msg.contains("51008")
                || msg.contains("capacity")) {
            return conflict("RESOURCE_SCHEDULE_OVERLAP",
                    "Resource, driver or horse has an overlapping schedule.");
        }
        if (msg.contains("51001") || msg.contains("busy")) {
            return conflict("RESOURCE_BUSY",
                    "Resource scheduling lock is busy; retry.");
        }
        return conflict("PLAN_SCHEDULE_CONFLICT",
                "Operational schedule validation failed.");
    }

    private String fingerprint(String action, Object body) {
        return TokenTools.hash(action + ":" + json.canonical(body));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withNano(0);
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
                "The requested operational plan or case was not found.");
    }

    private OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }

    private record AccessContext(CurrentActor actor, CaseOrder parent) { }
}
