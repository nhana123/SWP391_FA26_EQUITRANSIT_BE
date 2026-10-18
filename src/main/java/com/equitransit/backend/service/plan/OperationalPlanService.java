/*
 * OperationalPlanService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Build and confirm a multimodal operational plan.
 */
package com.equitransit.backend.service.plan;

import com.equitransit.backend.service.pricing.ServiceAssessmentFacade;
import com.equitransit.backend.service.pricing.StallFitEvaluator;
import com.equitransit.backend.service.resource.ResourceAvailabilityService;


import com.equitransit.backend.dto.plan.PlanModels.PlanCommand;
import com.equitransit.backend.dto.plan.PlanModels.PlanConfirmRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanDetailResponse;
import com.equitransit.backend.dto.plan.PlanModels.PlanDraftRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegInput;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegsRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegView;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyView;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationView;
import com.equitransit.backend.dto.plan.PlanModels.PlanPageResponse;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanRoadAssignmentRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.PlanSummaryView;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAvailabilityRequest;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceMode;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.plan.PlanRepository;
import com.equitransit.backend.repository.plan.PlanEvidenceRepository;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

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
    private final PlanEvidenceRepository evidence;
    private final ServiceAssessmentFacade assessments;
    private final CurrentActorService actors;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * @param plans SQL planning adapter @param cases parent lock and roles
     * @param orders revision snapshots @param availability F09 lock consumer
     * @param resources resource lock @param evidence actual BE1 evidence
     * @param assessments F08 quarantine assessment reader
     * @param actors live identity
     * @param json canonical hash codec @param mapper receipt serializer
     * @param clock server UTC
     */
    public OperationalPlanService(PlanRepository plans,
            AssessmentRepository cases, OrderRepository orders,
            ResourceAvailabilityService availability,
            ResourceRepository resources, PlanEvidenceRepository evidence,
            ServiceAssessmentFacade assessments,
            CurrentActorService actors, OrderJsonCodec json,
            ObjectMapper mapper, Clock clock) {
        this.plans = plans;
        this.cases = cases;
        this.orders = orders;
        this.availability = availability;
        this.resources = resources;
        this.evidence = evidence;
        this.assessments = assessments;
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
        var ctx = access(order, true);
        var summary = plans.findPlan(order, plan).orElseThrow(this::missing);

        return detail(ctx.parent(), summary);
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
        validateInputs(req.legs(), now());
        Long confirmer = distanceConfirmer(order, ctx.actor());

        for (PlanLegInput leg : req.legs()) {
            if (leg.supplierId() != null
                    && !resources.activeSupplier(leg.supplierId())) {
                throw conflict("SUPPLIER_INACTIVE",
                        "Leg supplier is inactive or missing.");
            }
            if (confirmer == null) {
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
            plans.supersedeDrafts(order, req.revisionId(), planId);
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
        if (req.driverBufferMinutes() < (targetLeg.restDurationMinutes()
                == null ? 0 : targetLeg.restDurationMinutes())) {
            throw conflict("DRIVER_REST_BUFFER_REQUIRED",
                    "Driver buffer must include the leg rest requirement.");
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
                : airStall.name().equals("WIDE") ? "WIDE" : "SHARED";

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
        var targetLeg = findLeg(plan, leg, PlanLegMode.AIR);
        var now = now();
        resources.lock();
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
        var validUntil = offer.get("ValidUntil") == null ? null
                : ((Timestamp) offer.get("ValidUntil")).toLocalDateTime()
                        .atOffset(ZoneOffset.UTC);

        if (!"AIRFARE".equals(offer.get("ServiceCode"))
                || !List.of("RECEIVED", "SELECTED").contains(
                        String.valueOf(offer.get("Status")))
                || validUntil == null || !validUntil.isAfter(now)
                || targetLeg.supplierId() == null
                || !targetLeg.supplierId().equals(offer.get("SupplierID"))
                || !req.flightNumber().equalsIgnoreCase(
                        targetLeg.plannedFlightNumber())
                || (req.status() == com.equitransit.backend.dto.plan.
                        PlanModels.ReservationState.CONFIRMED
                        && !req.expiresAt().isAfter(
                                targetLeg.plannedDeparture()))
                || offer.get("EvidenceFileKey") == null
                || offer.get("EvidenceFileKey").toString().isBlank()) {
            throw conflict("OFFER_INVALID_OR_EXPIRED",
                    "Flight reservation requires an active AIRFARE offer.");
        }
        plans.reserveFlight(leg, req, ctx.actor().staffId(),
                now);
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
        resources.lock();
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
        var now = now();
        var snapshot = json.snapshot(orders.revision(order, req.revisionId())
                .orElseThrow(this::missing).snapshot());
        var horizon = snapshot.request().getDeliveryDeadline() == null ? now
                : snapshot.request().getDeliveryDeadline();
        if (summary.plannedEndTime().isAfter(horizon)) {
            horizon = summary.plannedEndTime();
        }
        var assessed = assessments.quarantine(order, req.revisionId(), now,
                horizon);
        Integer requiredDays = "REQUIRED".equals(assessed.applicability())
                ? assessed.requiredDays() : null;

        OperationalPlanPolicy.validateQuarantineRequest(req, requiredDays, now);
        if (req.startsAt().isBefore(summary.plannedEndTime())) {
            throw conflict("QUARANTINE_WINDOW_INVALID",
                    "Reserve quarantine after the plan reaches its facility.");
        }
        plans.reserveQuarantine(plan, choiceId, req, ctx.actor().staffId(),
                now);
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
        plans.verifyInsurance(plan, choiceId, req, ctx.actor().staffId(),
                now());
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

        var now = now();
        if (summary.plannedStartTime() == null
                || !summary.plannedStartTime().isAfter(now)) {
            throw conflict("PLAN_DEPARTURE_PAST",
                    "Reschedule an elapsed draft before confirming it.");
        }
        evidence.requireAccepted(order, summary.revisionId(),
                req.acceptedQuoteId(), now);
        protectCurrentPlan(ctx, plan);
        resources.lock();
        var blockers = blockers(ctx.parent(), summary);

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
        evidence.append(order, actor.userId(), plan, action,
                cmd.operationId(), now());
        return response;
    }

    private PlanDetailResponse detail(CaseOrder parent,
            PlanSummaryView summary) {
        var legs = plans.legs(summary.planId());
        var quarantine = plans.quarantineReservations(summary.planId(),
                summary.revisionId());
        var insurance = plans.insurancePolicies(summary.planId(),
                summary.revisionId());
        var blockers = blockers(parent, summary, legs, quarantine, insurance);

        return new PlanDetailResponse(parent.orderId(), parent.version(),
                summary.planId(), summary.revisionId(), summary.versionNo(),
                summary.planStatus(), summary.currentPlan(),
                summary.acceptedQuoteId(), summary.createdBy(),
                summary.plannedStartTime(), summary.plannedEndTime(),
                summary.financialGateStatus(), summary.operationalGateStatus(),
                summary.docGateStatus(),
                plans.checkedAt(summary.planId()),
                legs, quarantine, insurance, blockers);
    }

    private Integer quarantineDays(long order, long revision,
            OffsetDateTime now, OffsetDateTime requiredUntil) {
        var snapshot = json.snapshot(orders.revision(order, revision)
                .orElseThrow(this::missing).snapshot());
        var horizon = snapshot.request().getDeliveryDeadline() == null ? now
                : snapshot.request().getDeliveryDeadline();
        if (requiredUntil != null && requiredUntil.isAfter(horizon)) {
            horizon = requiredUntil;
        }
        var assessed = assessments.quarantine(order, revision, now, horizon);

        if ("NOT_REQUIRED".equals(assessed.applicability())) {
            return 0;
        }
        return "REQUIRED".equals(assessed.applicability())
                ? assessed.requiredDays() : null;
    }

    /** @param order parent @param plan draft @param req complete new chain
     * @return receipt after an atomic edit; old commitments are cancelled */
    public PlanDetailResponse replaceLegs(@Positive long order,
            @Positive long plan, @Valid PlanLegsRequest req) {
        var ctx = access(order, true);
        String hash = fingerprint("REPLACE_LEGS:" + plan, req);
        var prior = replay(order, ctx.actor(), req.command(), hash);

        if (prior.isPresent()) {
            return prior.get();
        }
        editablePlan(ctx.parent(), plan, req.command());
        validateInputs(req.legs(), now());
        Long confirmer = distanceConfirmer(order, ctx.actor());

        if (confirmer == null) {
            throw conflict("COORDINATOR_REQUIRED_FOR_DISTANCE",
                    "Assign an active Coordinator before recording sources.");
        }
        for (var leg : req.legs()) {
            if (leg.supplierId() != null
                    && !resources.activeSupplier(leg.supplierId())) {
                throw conflict("SUPPLIER_INACTIVE", "Supplier is inactive.");
            }
        }
        resources.lock();
        plans.clearDraft(plan, req.legs().getFirst().plannedDeparture(),
                req.legs().getLast().plannedArrival());
        for (var leg : req.legs()) {
            plans.insertLeg(plan, leg, confirmer, now());
        }
        plans.touchOrder(order);
        return finish(order, plan, ctx.actor(), req.command(),
                "REPLACE_LEGS", hash);
    }

    /** F11 reads live operational evidence; no financial or legal PASS.
     * @param order parent @param revision exact revision @param plan plan
     * @return true only for the current confirmed plan with current proof */
    public boolean operationalReady(long order, long revision, long plan) {
        var ctx = access(order, true);
        var summary = plans.findPlan(order, plan).orElseThrow(this::missing);

        if (!Set.of("APPROVED", "IN_PREPARATION", "CONFIRMED", "IN_TRANSIT")
                .contains(ctx.parent().status())
                || !summary.currentPlan() || summary.revisionId() != revision
                || ctx.parent().revisionId() == null
                || ctx.parent().revisionId() != revision
                || !List.of(PlanState.CONFIRMED, PlanState.ACTIVE,
                        PlanState.IN_TRANSIT).contains(summary.planStatus())
                || summary.acceptedQuoteId() == null) {
            return false;
        }
        evidence.requireAccepted(order, revision, summary.acceptedQuoteId(),
                now());
        resources.lock();
        return blockers(ctx.parent(), summary).isEmpty()
                && !plans.lockHorseAndCheckOverlap(order,
                        summary.plannedStartTime(), summary.plannedEndTime());
    }

    private List<String> blockers(CaseOrder parent, PlanSummaryView summary) {
        var legs = plans.legs(summary.planId());
        return blockers(parent, summary, legs,
                plans.quarantineReservations(summary.planId(),
                        summary.revisionId()),
                plans.insurancePolicies(summary.planId(),
                        summary.revisionId()));
    }

    private List<String> blockers(CaseOrder parent, PlanSummaryView summary,
            List<PlanLegView> legs,
            List<PlanQuarantineReservationView> quarantine,
            List<PlanInsurancePolicyView> insurance) {
        var reasons = new ArrayList<>(
                OperationalPlanPolicy.confirmationBlockers(legs,
                        quarantine, insurance,
                        plans.serviceChoices(summary.revisionId()),
                        quarantineDays(parent.orderId(),
                                summary.revisionId(), now(),
                                summary.plannedEndTime()),
                        now()));

        if (legs.isEmpty() || summary.plannedStartTime() == null
                || summary.plannedEndTime() == null
                || !summary.plannedStartTime().isEqual(
                        legs.getFirst().plannedDeparture())
                || !summary.plannedEndTime().isEqual(
                        legs.getLast().plannedArrival())) {
            reasons.add("PLAN_WINDOW_MISMATCH");
        }
        if (parent.revisionId() == null
                || parent.revisionId() != summary.revisionId()) {
            reasons.add("PLAN_REVISION_STALE");
            return reasons;
        }
        for (var leg : legs) {
            if (leg.distanceConfirmedBy() == null
                    || !plans.distanceSourceValid(parent.orderId(),
                            summary.revisionId(), leg.distanceConfirmedBy())) {
                reasons.add("LEG_DISTANCE_SOURCE_INACTIVE");
            }
            if (leg.vehicleId() != null) {
                checkResource(reasons, ResourceKind.VEHICLE,
                        leg.vehicleId(), parent, summary, leg);
            }
            for (var stall : leg.stallAssignments()) {
                checkResource(reasons, ResourceKind.STALL, stall.stallId(),
                        parent, summary, leg);
            }
            if (leg.transportMode() == PlanLegMode.AIR
                    && leg.flightReservations().stream().noneMatch(f ->
                            OperationalPlanPolicy.confirmedFlight(leg, f,
                                    now())
                            && plans.flightSourceValid(summary.planId(),
                                    summary.revisionId(), leg.legId(),
                                    f.reservationId()))) {
                reasons.add("AIR_FLIGHT_SOURCE_INVALID");
            }
            for (var staff : leg.staffAssignments()) {
                if (staff.roleInTrip() == com.equitransit.backend.dto.plan.
                        PlanModels.TripRole.DRIVER
                        && (!cases.activeStaff(staff.staffId(), "DRIVER")
                                || plans.driverOverlaps(staff.staffId(),
                                        leg.plannedDeparture(),
                                        leg.plannedArrival(),
                                        staff.bufferMinutes() == null ? 0
                                                : staff.bufferMinutes(),
                                        leg.legId()))) {
                    reasons.add("ROAD_DRIVER_UNAVAILABLE");
                }
            }
        }
        for (var booking : quarantine) {
            if (booking.status() == com.equitransit.backend.dto.plan.
                    PlanModels.ReservationState.CONFIRMED
                    && !resources.activeSupplier(booking.supplierId())) {
                reasons.add("QUARANTINE_SUPPLIER_INACTIVE");
            }
        }
        return reasons.stream().distinct().toList();
    }

    private void checkResource(List<String> reasons, ResourceKind kind,
            long id, CaseOrder parent, PlanSummaryView summary,
            PlanLegView leg) {
        var result = availability.lockAndCheck(kind, id,
                new ResourceAvailabilityRequest(parent.orderId(),
                        summary.revisionId(),
                        kind == ResourceKind.VEHICLE ? ResourceMode.ROAD
                                : ResourceMode.AIR,
                        leg.plannedDeparture(), leg.plannedArrival(),
                        leg.supplierId(), leg.legId()));

        if (result.status() != com.equitransit.backend.dto.resource.
                ResourceModels.AvailabilityState.AVAILABLE) {
            reasons.add("RESOURCE_" + kind + "_UNAVAILABLE");
        }
    }

    private void protectCurrentPlan(AccessContext ctx, long replacement) {
        Long current = plans.currentPlanId(ctx.parent().orderId());

        if (current == null || current == replacement) {
            return;
        }
        var old = plans.findPlan(ctx.parent().orderId(), current)
                .orElseThrow(this::missing);

        if (List.of(PlanState.ACTIVE, PlanState.IN_TRANSIT).contains(
                old.planStatus()) || (old.planStatus() == PlanState.CONFIRMED
                && !"MANAGER".equals(ctx.actor().roleCode()))) {
            throw conflict("CURRENT_PLAN_PROTECTED",
                    "Only Manager may replace a confirmed plan; an active "
                            + "trip requires the operational change flow.");
        }
    }

    private void validateInputs(List<PlanLegInput> legs,
            OffsetDateTime now) {
        OperationalPlanPolicy.validateLegs(legs);
        if (!legs.getFirst().plannedDeparture().isAfter(now)) {
            throw conflict("PLAN_DEPARTURE_PAST",
                    "A new draft must depart in the future.");
        }
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
        String msg = String.valueOf(error.getMostSpecificCause().getMessage());

        if (msg.contains("overlapping") || msg.contains("51003")
                || msg.contains("51005") || msg.contains("51007")
                || msg.contains("51008")) {
            return conflict("RESOURCE_SCHEDULE_OVERLAP",
                    "Resource, driver or horse has an overlapping schedule.");
        }
        if (msg.contains("51001") || msg.contains("busy")) {
            return conflict("RESOURCE_BUSY",
                    "Resource scheduling lock is busy; retry.");
        }
        throw error;
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
