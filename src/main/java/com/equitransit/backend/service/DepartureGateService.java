/*
 * DepartureGateService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Orchestrate F11 departure gate evaluation, auth & horse receipt.
 */
package com.equitransit.backend.service;

import com.equitransit.backend.dto.departure.DepartureModels.
        DepartureAuthorizeRequest;
import com.equitransit.backend.dto.departure.DepartureModels.DepartureCommand;
import com.equitransit.backend.dto.departure.DepartureModels.
        DepartureGateEvaluateRequest;
import com.equitransit.backend.dto.departure.DepartureModels.
        DepartureReadinessResponse;
import com.equitransit.backend.dto.departure.DepartureModels.GateDetailView;
import com.equitransit.backend.dto.departure.DepartureModels.
        HorseReceiptRequest;
import com.equitransit.backend.dto.departure.DepartureModels.IdentityResult;
import com.equitransit.backend.dto.departure.DepartureModels.LegDepartRequest;
import com.equitransit.backend.dto.departure.DepartureModels.ReceiptState;
import com.equitransit.backend.dto.plan.PlanModels.AssignmentState;
import com.equitransit.backend.dto.plan.PlanModels.GateState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegView;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.TripRole;
import com.equitransit.backend.dto.resource.ResourceModels.AssetState;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceVehicleView;
import com.equitransit.backend.entity.CaseOrder;
import com.equitransit.backend.entity.CaseRole;
import com.equitransit.backend.entity.activity.ActivityCode;
import com.equitransit.backend.entity.compliance.CompliancePhase;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.json.OrderJsonCodec;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.repository.DepartureRepository;
import com.equitransit.backend.repository.DepartureRepository.FirstLegRow;
import com.equitransit.backend.repository.DepartureRepository.PlanGateRow;
import com.equitransit.backend.repository.OrderRepository;
import com.equitransit.backend.repository.PlanRepository;
import com.equitransit.backend.repository.ResourceRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Transactional service for F11 departure gates, authorization & receipt. */
@Service
@Validated
@PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','COORDINATOR','DRIVER')")
public class DepartureGateService {

    private static final List<String> ALLOWED_ORDER_STATUSES = List.of(
            "APPROVED", "IN_PREPARATION", "CONFIRMED", "IN_TRANSIT",
            "DELIVERED");

    private final DepartureRepository departures;
    private final PlanRepository plans;
    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final ResourceRepository resources;
    private final QuotationCommitmentFacade quotes;
    private final ComplianceReadinessFacade compliance;
    private final ServiceAssessmentFacade assessments;
    private final CurrentActorService actors;
    private final AuditWriter audit;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * @param departures F11 repository
     * @param plans F10 plan repository
     * @param cases case and staff locks
     * @param orders revision lookup
     * @param resources physical vehicle/stall repository
     * @param quotes F07 commercial readiness facade
     * @param compliance F08 legal readiness facade
     * @param assessments F08 quarantine assessment facade
     * @param actors authenticated actor resolver
     * @param audit F16 audit writer
     * @param json canonical JSON codec
     * @param mapper Jackson mapper
     * @param clock UTC clock
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public DepartureGateService(DepartureRepository departures,
            PlanRepository plans, AssessmentRepository cases,
            OrderRepository orders, ResourceRepository resources,
            QuotationCommitmentFacade quotes,
            ComplianceReadinessFacade compliance,
            ServiceAssessmentFacade assessments, CurrentActorService actors,
            AuditWriter audit, OrderJsonCodec json, ObjectMapper mapper,
            Clock clock) {
        this.departures = departures;
        this.plans = plans;
        this.cases = cases;
        this.orders = orders;
        this.resources = resources;
        this.quotes = quotes;
        this.compliance = compliance;
        this.assessments = assessments;
        this.actors = actors;
        this.audit = audit;
        this.json = json;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Evaluates and returns current departure gate and receipt state.
     *
     * @param order transport order ID
     * @return live readiness response (redacted when caller is CUSTOMER)
     */
    @Transactional(timeout = 15)
    public DepartureReadinessResponse getReadiness(@Positive long order) {
        ensureReady();
        var actor = actors.current();
        var parent = cases.order(order, true).orElseThrow(this::missing);

        authorizeRead(actor, parent);
        var plan = currentPlan(parent, true);
        var response = buildReadiness(parent, plan, now());

        return "CUSTOMER".equals(actor.roleCode())
                ? DepartureGatePolicy.redactForCustomer(response) : response;
    }

    /**
     * Evaluates all 3 gates in real time and persists statuses on current plan.
     *
     * @param order transport order ID
     * @param req evaluation command
     * @return updated readiness response
     */
    @Transactional(timeout = 15)
    public DepartureReadinessResponse evaluateGates(@Positive long order,
            @Valid DepartureGateEvaluateRequest req) {
        ensureReady();
        var actor = actors.current();
        var parent = cases.order(order, true).orElseThrow(this::missing);

        authorizeCoordinatorOrManager(actor, order);
        String hash = fingerprint("EVALUATE_GATES", req);
        var replay = replay(order, actor, req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        requireOrderVersion(parent, req.command());
        var plan = currentPlan(parent, true);
        var now = now();
        var eval = evaluateAllGates(parent, plan, now);
        boolean allPass = eval.allPass();

        departures.updateGates(order, plan.planId(),
                eval.financial().status(), eval.legal().status(),
                eval.operational().status(), now, !allPass);
        plans.touchOrder(order);
        return finish(order, plan.planId(), actor, req.command(),
                "EVALUATE_DEPARTURE_GATES", ActivityCode.UPDATED, hash, now);
    }

    /**
     * Authorizes departure when caller is active MANAGER and all 3 gates PASS.
     *
     * @param order transport order ID
     * @param req authorization command
     * @return updated readiness response
     */
    @Transactional(timeout = 15)
    public DepartureReadinessResponse authorize(@Positive long order,
            @Valid DepartureAuthorizeRequest req) {
        ensureReady();
        var actor = actors.current();
        var parent = cases.order(order, true).orElseThrow(this::missing);

        authorizeManagerOnly(actor);
        String hash = fingerprint("AUTHORIZE_DEPARTURE:" + req.planId(), req);
        var replay = replay(order, actor, req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        requireOrderVersion(parent, req.command());
        var plan = matchingCurrentPlan(parent, req.planId());

        if (!List.of(PlanState.CONFIRMED, PlanState.ACTIVE)
                .contains(plan.planStatus())) {
            throw conflict("PLAN_NOT_CONFIRMED",
                    "Only a CONFIRMED operational plan can be authorized.");
        }
        var now = now();
        var eval = evaluateAllGates(parent, plan, now);

        if (!eval.allPass()) {
            departures.updateGates(order, plan.planId(),
                    eval.financial().status(), eval.legal().status(),
                    eval.operational().status(), now, true);
            throw conflict("DEPARTURE_GATE_BLOCKED",
                    "Cannot authorize departure while gates are blocked: "
                            + String.join(", ", eval.allBlockers()));
        }
        departures.authorizeDeparture(order, plan.planId(),
                actor.staffId(), now);
        plans.touchOrder(order);
        return finish(order, plan.planId(), actor, req.command(),
                "AUTHORIZE_DEPARTURE", ActivityCode.UPDATED, hash, now);
    }

    /**
     * Records physical horse inspection and microchip/passport check by driver.
     *
     * @param order transport order ID
     * @param req horse receipt command
     * @return updated readiness response including the recorded receipt
     */
    @Transactional(timeout = 15)
    public DepartureReadinessResponse recordHorseReceipt(@Positive long order,
            @Valid HorseReceiptRequest req) {
        ensureReady();
        var actor = actors.current();
        var parent = cases.order(order, true).orElseThrow(this::missing);

        authorizeDriverOnLeg(actor, req.planId(), req.legId());
        String hash = fingerprint(
                "RECORD_HORSE_RECEIPT:" + req.planId() + ":" + req.legId(),
                req);
        var replay = replay(order, actor, req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        requireOrderVersion(parent, req.command());
        var plan = matchingCurrentPlan(parent, req.planId());

        if (!List.of(PlanState.CONFIRMED, PlanState.ACTIVE)
                .contains(plan.planStatus())) {
            throw conflict("PLAN_NOT_CONFIRMED",
                    "Horse receipt requires a CONFIRMED operational plan.");
        }
        var firstLeg = requireFirstRoadLeg(plan.planId(), req.legId());

        if (firstLeg.legStatus() != LegState.CONFIRMED) {
            throw conflict("LEG_NOT_READY_FOR_RECEIPT",
                    "First road leg must be in CONFIRMED state.");
        }
        var expected = expectedHorseIdentity(order, parent.revisionId(), true);
        var identity = DepartureGatePolicy.verifyIdentity(
                expected.microchip(), expected.passport(),
                req.observedMicrochip(), req.observedPassportNumber());
        var status = DepartureGatePolicy.receiptStatus(
                identity, Boolean.TRUE.equals(req.fitToTravel()));
        var now = now();

        departures.insertHorseReceipt(plan.planId(), firstLeg.legId(),
                actor.staffId(), req, identity, status, now);
        plans.touchOrder(order);
        return finish(order, plan.planId(), actor, req.command(),
                "RECORD_HORSE_RECEIPT", ActivityCode.UPDATED, hash, now);
    }

    /**
     * Executes atomic first-leg departure when 3 gates, manager auth and
     * matched horse receipt are all valid as of now.
     *
     * @param order transport order ID
     * @param req departure command
     * @return updated readiness response in IN_TRANSIT status
     */
    @Transactional(timeout = 15)
    public DepartureReadinessResponse depart(@Positive long order,
            @Valid LegDepartRequest req) {
        ensureReady();
        var actor = actors.current();
        var parent = cases.order(order, true).orElseThrow(this::missing);

        authorizeDriverOnLeg(actor, req.planId(), req.legId());
        String hash = fingerprint(
                "DEPART_LEG:" + req.planId() + ":" + req.legId(), req);
        var replay = replay(order, actor, req.command(), hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        requireOrderVersion(parent, req.command());
        var plan = matchingCurrentPlan(parent, req.planId());

        if (!List.of(PlanState.CONFIRMED, PlanState.ACTIVE)
                .contains(plan.planStatus())) {
            throw conflict("PLAN_NOT_CONFIRMED",
                    "Departure requires a CONFIRMED operational plan.");
        }
        var firstLeg = requireFirstRoadLeg(plan.planId(), req.legId());

        if (firstLeg.legStatus() != LegState.CONFIRMED
                || firstLeg.actualDeparture() != null) {
            throw conflict("LEG_ALREADY_DEPARTED_OR_INVALID",
                    "First road leg is not in CONFIRMED pre-departure state.");
        }
        var now = now();
        var eval = evaluateAllGates(parent, plan, now);

        if (!eval.allPass()) {
            throw conflict("DEPARTURE_GATE_BLOCKED",
                    "Departure blocked by live gate re-evaluation: "
                            + String.join(", ", eval.allBlockers()));
        }
        if (!isAuthorized(plan, now, true)) {
            throw conflict("DEPARTURE_NOT_AUTHORIZED",
                    "Departure requires active MANAGER authorization.");
        }
        var receipts = departures.receipts(plan.planId()).stream()
                .filter(r -> firstLeg.legId() == (r.legId() == null
                        ? -1L : r.legId()))
                .toList();

        if (receipts.isEmpty()
                || receipts.getFirst().receiptId() != req.receiptId()) {
            throw conflict("HORSE_RECEIPT_INVALID",
                    "Departure requires the latest horse receipt on leg.");
        }
        var receipt = receipts.getFirst();
        var expected = expectedHorseIdentity(order, parent.revisionId(), true);
        var rechecked = DepartureGatePolicy.verifyIdentity(
                expected.microchip(), expected.passport(),
                receipt.observedMicrochip(), receipt.observedPassportNumber());

        if (receipt.identityCheckResult() != IdentityResult.MATCHED
                || rechecked != IdentityResult.MATCHED
                || receipt.receiptStatus() != ReceiptState.ACCEPTED
                || receipt.receivedAt().isAfter(now)) {
            throw conflict("HORSE_RECEIPT_REJECTED_OR_MISMATCHED",
                    "Departure requires an ACCEPTED and MATCHED horse "
                            + "receipt.");
        }
        resources.lock();
        try {
            departures.departFirstLeg(order, plan.planId(),
                    firstLeg.legId(), now);
        } catch (DataAccessException error) {
            throw translateSql(error);
        }
        return finish(order, plan.planId(), actor, req.command(),
                "DEPART_FIRST_LEG", ActivityCode.DEPARTED, hash, now);
    }

    private DepartureReadinessResponse finish(long order, long planId,
            CurrentActor actor, DepartureCommand cmd, String action,
            ActivityCode code, String hash, OffsetDateTime now) {
        var updatedOrder = cases.order(order, false).orElseThrow();
        var updatedPlan = departures.plan(order, planId, false).orElseThrow();
        var response = buildReadiness(updatedOrder, updatedPlan, now);

        plans.saveOperation(order, actor.userId(), cmd.operationId(), hash,
                json.write(response));
        audit.append(order, code, cmd.operationId(),
                mapper.valueToTree(Map.of("entityId", planId,
                        "action", action,
                        "versionNo", updatedPlan.versionNo())));
        return response;
    }

    private DepartureReadinessResponse buildReadiness(CaseOrder parent,
            PlanGateRow plan, OffsetDateTime now) {
        var eval = evaluateAllGates(parent, plan, now);
        var firstLeg = departures.firstLeg(plan.planId()).orElse(null);
        var receipts = departures.receipts(plan.planId());
        var latestFirstReceipt = firstLeg == null ? null
                : receipts.stream()
                        .filter(r -> r.legId() != null
                                && r.legId() == firstLeg.legId())
                        .findFirst().orElse(null);
        var expected = expectedHorseIdentity(
                parent.orderId(), parent.revisionId(), false);
        var rechecked = latestFirstReceipt == null ? IdentityResult.PENDING
                : DepartureGatePolicy.verifyIdentity(
                        expected.microchip(), expected.passport(),
                        latestFirstReceipt.observedMicrochip(),
                        latestFirstReceipt.observedPassportNumber());
        boolean authValid = isAuthorized(plan, now, eval.allPass());
        var depBlockers = DepartureGatePolicy.departureBlockers(
                eval.financial(), eval.legal(), eval.operational(),
                authValid, latestFirstReceipt, rechecked,
                eval.horseOverlap());

        return new DepartureReadinessResponse(parent.orderId(),
                parent.version(), parent.status(), parent.revisionId(),
                plan.planId(), plan.versionNo(), plan.planStatus(),
                plan.acceptedQuoteId(),
                firstLeg == null ? null : firstLeg.legId(),
                firstLeg == null ? null : firstLeg.legStatus(),
                firstLeg == null ? null : firstLeg.actualDeparture(),
                now, plan.gatesCheckedAt(), eval.financial(), eval.legal(),
                eval.operational(), eval.allPass(),
                authValid ? plan.departureAuthorizedBy() : null,
                authValid ? plan.departureAuthorizedAt() : null,
                depBlockers.isEmpty(), depBlockers, receipts);
    }

    private GateEvaluation evaluateAllGates(CaseOrder parent, PlanGateRow plan,
            OffsetDateTime now) {
        long order = parent.orderId();
        Long revision = parent.revisionId();
        var finBlockers = new ArrayList<String>();
        var legalBlockers = new ArrayList<String>();
        var opsBlockers = new ArrayList<String>();

        if (revision == null || revision != plan.revisionId()) {
            finBlockers.add("REVISION_MISMATCH");
            legalBlockers.add("REVISION_MISMATCH");
            opsBlockers.add("REVISION_MISMATCH");
        } else {
            if (plan.acceptedQuoteId() == null) {
                finBlockers.add("ACCEPTED_QUOTE_MISSING");
            } else {
                var comm = quotes.readiness(order, revision,
                        plan.acceptedQuoteId(), now);
                finBlockers.addAll(comm.departureBlockers());
            }
            var comp = compliance.readiness(order, revision,
                    CompliancePhase.PRE_EXPORT, now);
            legalBlockers.addAll(comp.blockers());
            if (departures.complianceHoldActive(revision)) {
                legalBlockers.add("COMPLIANCE_HOLD_ACTIVE");
            }
            opsBlockers.addAll(evaluateOperationalBlockers(
                    parent, plan, now));
        }
        boolean overlap = opsBlockers.contains("HORSE_SCHEDULE_OVERLAP");

        return new GateEvaluation(
                DepartureGatePolicy.gateView(finBlockers),
                DepartureGatePolicy.gateView(legalBlockers),
                DepartureGatePolicy.gateView(opsBlockers), overlap);
    }

    private List<String> evaluateOperationalBlockers(CaseOrder parent,
            PlanGateRow plan, OffsetDateTime now) {
        var blockers = new ArrayList<String>();
        long order = parent.orderId();
        long revision = plan.revisionId();

        if (plan.acceptedQuoteId() == null) {
            blockers.add("ACCEPTED_QUOTE_MISSING");
        }
        if (!List.of(PlanState.CONFIRMED, PlanState.ACTIVE,
                PlanState.IN_TRANSIT).contains(plan.planStatus())) {
            blockers.add("PLAN_NOT_CONFIRMED");
        }
        var legs = plans.legs(plan.planId());
        var quarantine = plans.quarantineReservations(revision);
        var insurance = plans.insurancePolicies(revision);
        var choices = plans.serviceChoices(revision);

        blockers.addAll(OperationalPlanPolicy.confirmationBlockers(
                legs, quarantine, insurance, choices,
                quarantineDays(order, revision, now), now));
        for (var leg : legs) {
            if (leg.transportMode() == PlanLegMode.ROAD) {
                checkRoadLegResources(leg, blockers);
            } else if (leg.transportMode() == PlanLegMode.AIR) {
                checkAirLegResources(leg, blockers);
            }
        }
        if (plan.plannedStartTime() == null || plan.plannedEndTime() == null
                || plans.lockHorseAndCheckOverlap(order,
                        plan.plannedStartTime(), plan.plannedEndTime())) {
            blockers.add("HORSE_SCHEDULE_OVERLAP");
        }
        return blockers;
    }

    private void checkRoadLegResources(PlanLegView leg,
            List<String> blockers) {
        if (leg.vehicleId() == null) {
            blockers.add("ROAD_VEHICLE_MISSING");
        } else {
            var vehicle = resources.get(ResourceKind.VEHICLE,
                    leg.vehicleId(), ResourceVehicleView.class).data();
            var win = vehicle.window();

            if (vehicle.status() != AssetState.ACTIVE
                    || vehicle.horseCapacity() < 1
                    || !resources.activeSupplier(vehicle.ownerSupplierId())
                    || win.bufferMinutes() == null
                    || ResourceAvailabilityPolicy.maintenance(
                            win, leg.plannedDeparture(), leg.plannedArrival())
                    || win.documentExpiresAt() == null
                    || !win.documentExpiresAt().isAfter(
                            leg.plannedArrival().plusMinutes(
                                    win.bufferMinutes()))
                    || !resources.scheduleBlockers(ResourceKind.VEHICLE,
                            leg.vehicleId(), leg.plannedDeparture(),
                            leg.plannedArrival(), win.bufferMinutes(),
                            leg.legId()).isEmpty()) {
                blockers.add("ROAD_VEHICLE_UNAVAILABLE");
            }
        }
        if (!departures.legDriversActive(leg.legId())) {
            blockers.add("ROAD_DRIVER_INACTIVE");
        }
        for (var staff : leg.staffAssignments()) {
            if (staff.roleInTrip() == TripRole.DRIVER
                    && (staff.status() == AssignmentState.CONFIRMED
                            || staff.status() == AssignmentState.ACTIVE)
                    && plans.driverOverlaps(staff.staffId(),
                            leg.plannedDeparture(), leg.plannedArrival(),
                            staff.bufferMinutes(), leg.legId())) {
                blockers.add("ROAD_DRIVER_OVERLAP");
            }
        }
    }

    private void checkAirLegResources(PlanLegView leg, List<String> blockers) {
        for (var stallAssign : leg.stallAssignments()) {
            if (stallAssign.status() != AssignmentState.CONFIRMED
                    && stallAssign.status() != AssignmentState.ACTIVE) {
                continue;
            }
            var stall = resources.get(ResourceKind.STALL,
                    stallAssign.stallId(), ResourceStallView.class).data();
            var win = stall.window();

            if (stall.status() != AssetState.ACTIVE
                    || !resources.activeSupplier(stall.ownerSupplierId())
                    || !resources.activeSupplier(leg.supplierId())
                    || win.bufferMinutes() == null
                    || ResourceAvailabilityPolicy.maintenance(
                            win, leg.plannedDeparture(), leg.plannedArrival())
                    || win.documentExpiresAt() == null
                    || !win.documentExpiresAt().isAfter(
                            leg.plannedArrival().plusMinutes(
                                    win.bufferMinutes()))
                    || !resources.scheduleBlockers(ResourceKind.STALL,
                            stallAssign.stallId(), leg.plannedDeparture(),
                            leg.plannedArrival(), win.bufferMinutes(),
                            leg.legId()).isEmpty()) {
                blockers.add("AIR_STALL_UNAVAILABLE");
            }
        }
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

    private ExpectedIdentity expectedHorseIdentity(long order, Long revision,
            boolean lock) {
        var row = departures.horseIdentity(order, lock).orElse(null);
        String chip = row == null ? null : row.snapshotMicrochip();
        String pass = row == null ? null : row.passportNumber();

        if ((chip == null || pass == null) && revision != null) {
            var rev = orders.revision(order, revision).orElse(null);
            if (rev != null) {
                var horse = json.snapshot(rev.snapshot()).horse();
                if (horse != null) {
                    if (chip == null) {
                        chip = horse.microchipId();
                    }
                    if (pass == null) {
                        pass = horse.passportNumber();
                    }
                }
            }
        }
        if (chip == null && row != null) {
            chip = row.horseMicrochip();
        }
        return new ExpectedIdentity(chip, pass);
    }

    private boolean isAuthorized(PlanGateRow plan, OffsetDateTime now,
            boolean allGatesPass) {
        return allGatesPass
                && plan.departureAuthorizedBy() != null
                && plan.departureAuthorizedAt() != null
                && !plan.departureAuthorizedAt().isAfter(now)
                && cases.activeStaff(plan.departureAuthorizedBy(), "MANAGER");
    }

    private PlanGateRow currentPlan(CaseOrder parent, boolean lock) {
        var scope = departures.orderScope(parent.orderId())
                .orElseThrow(this::missing);

        if (parent.revisionId() == null || scope.currentPlanId() == null
                || !ALLOWED_ORDER_STATUSES.contains(parent.status())) {
            throw conflict("CURRENT_PLAN_MISSING",
                    "Order does not have an active confirmed transport plan.");
        }
        return departures.plan(parent.orderId(), scope.currentPlanId(), lock)
                .orElseThrow(this::missing);
    }

    private PlanGateRow matchingCurrentPlan(CaseOrder parent, long planId) {
        var plan = currentPlan(parent, true);

        if (plan.planId() != planId
                || !parent.revisionId().equals(plan.revisionId())) {
            throw conflict("PLAN_MISMATCH",
                    "Target plan does not match current order plan/revision.");
        }
        return plan;
    }

    private FirstLegRow requireFirstRoadLeg(long planId, long legId) {
        var first = departures.firstLeg(planId).orElseThrow(this::missing);

        if (first.legId() != legId
                || first.transportMode() != PlanLegMode.ROAD) {
            throw conflict("FIRST_ROAD_LEG_REQUIRED",
                    "Horse receipt and departure apply to first ROAD leg.");
        }
        return first;
    }

    private void authorizeRead(CurrentActor actor, CaseOrder parent) {
        String role = actor.roleCode();
        var scope = departures.orderScope(parent.orderId())
                .orElseThrow(this::missing);

        if ("CUSTOMER".equals(role)) {
            if (actor.customerId() == null
                    || actor.customerId() != scope.customerId()) {
                throw missing();
            }
            return;
        }
        if (actor.staffId() == null
                || !cases.activeStaff(actor.staffId(), role)) {
            throw new AccessDeniedException("An active actor is required.");
        }
        if ("MANAGER".equals(role)) {
            return;
        }
        if ("COORDINATOR".equals(role)) {
            if (!cases.assigned(parent.orderId(), actor.staffId(),
                    CaseRole.COORDINATOR)) {
                throw missing();
            }
            return;
        }
        if ("DRIVER".equals(role)) {
            if (scope.currentPlanId() == null
                    || !departures.assignedDriverOnPlan(
                            scope.currentPlanId(), actor.staffId())) {
                throw missing();
            }
            return;
        }
        throw new AccessDeniedException("Role cannot read departure gates.");
    }

    private void authorizeCoordinatorOrManager(CurrentActor actor, long order) {
        String role = actor.roleCode();

        if (actor.staffId() == null
                || !List.of("MANAGER", "COORDINATOR").contains(role)
                || !cases.activeStaff(actor.staffId(), role)) {
            throw new AccessDeniedException(
                    "Active MANAGER or COORDINATOR is required.");
        }
        if ("COORDINATOR".equals(role)
                && !cases.assigned(order, actor.staffId(),
                        CaseRole.COORDINATOR)) {
            throw missing();
        }
    }

    private void authorizeManagerOnly(CurrentActor actor) {
        if (actor.staffId() == null || !"MANAGER".equals(actor.roleCode())
                || !cases.activeStaff(actor.staffId(), "MANAGER")) {
            throw new AccessDeniedException(
                    "Only an active MANAGER can authorize departure.");
        }
    }

    private void authorizeDriverOnLeg(CurrentActor actor, long planId,
            long legId) {
        if (actor.staffId() == null || !"DRIVER".equals(actor.roleCode())
                || !cases.activeStaff(actor.staffId(), "DRIVER")
                || !departures.assignedDriverOnLeg(
                        planId, legId, actor.staffId())) {
            throw new AccessDeniedException(
                    "Only the active DRIVER assigned to this ROAD leg may "
                            + "receive the horse or depart.");
        }
    }

    private void requireOrderVersion(CaseOrder parent, DepartureCommand cmd) {
        if (!parent.version().equalsIgnoreCase(cmd.recordVersion())) {
            throw conflict("ORDER_VERSION_MISMATCH",
                    "Refresh the transport order and retry.");
        }
    }

    private Optional<DepartureReadinessResponse> replay(long order,
            CurrentActor actor, DepartureCommand cmd, String hash) {
        var rows = plans.operation(order, actor.userId(), cmd.operationId());

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var row = rows.getFirst();

        if (!hash.equals(row.get("RequestHash"))) {
            throw conflict("IDEMPOTENCY_CONFLICT",
                    "operationId was already used with a different payload.");
        }
        try {
            return Optional.of(mapper.readValue(
                    row.get("ReceiptJSON").toString(),
                    DepartureReadinessResponse.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw conflict("DEPARTURE_DATA_UNSUPPORTED",
                    "Stored operation receipt cannot be decoded.");
        }
    }

    private String fingerprint(String action, Object body) {
        return TokenTools.hash(action + ":" + json.canonical(body));
    }

    private void ensureReady() {
        if (!departures.ready() || !plans.ready() || !resources.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DEPARTURE_SCHEMA_PENDING",
                    "Deploy migration 012 before using F11 endpoints.");
        }
    }

    private OrderException translateSql(DataAccessException error) {
        String msg = error.getMostSpecificCause() == null ? ""
                : error.getMostSpecificCause().getMessage();

        if (msg.contains("51001")) {
            return new OrderException(HttpStatus.CONFLICT,
                    "RESOURCE_LOCK_BUSY", "Resource scheduling lock is busy.");
        }
        return new OrderException(HttpStatus.CONFLICT,
                "DEPARTURE_CONSTRAINT_VIOLATION",
                "Departure violates database operational or gate constraints.");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND",
                "The target transport order or operational plan was not "
                        + "found.");
    }

    private OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }

    private record ExpectedIdentity(String microchip, String passport) { }

    private record GateEvaluation(
            GateDetailView financial, GateDetailView legal,
            GateDetailView operational, boolean horseOverlap) {

        boolean allPass() {
            return financial.status() == GateState.PASS
                    && legal.status() == GateState.PASS
                    && operational.status() == GateState.PASS;
        }

        List<String> allBlockers() {
            var all = new ArrayList<String>();
            all.addAll(financial.blockers());
            all.addAll(legal.blockers());
            all.addAll(operational.blockers());
            return all.stream().distinct().toList();
        }
    }
}
