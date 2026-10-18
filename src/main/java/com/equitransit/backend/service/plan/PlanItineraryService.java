package com.equitransit.backend.service.plan;

import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.PlanSummaryView;
import com.equitransit.backend.dto.plan.PlanPublicModels.PlanDriverLeg;
import com.equitransit.backend.dto.plan.PlanPublicModels.PlanPublicItinerary;
import com.equitransit.backend.dto.plan.PlanPublicModels.PlanPublicLeg;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.pricing.ItineraryPricingContext;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.plan.PlanRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scoped public projections and an explicit sourced-draft feed for BE1. */
@Service
@Transactional(timeout = 15)
public class PlanItineraryService {

    private final PlanRepository plans;
    private final OrderRepository orders;
    private final AssessmentRepository cases;
    private final CurrentActorService actors;
    private final Clock clock;

    /** @param plans plans @param orders ownership @param cases staff scope
     * @param actors live identity @param clock server time */
    public PlanItineraryService(PlanRepository plans, OrderRepository orders,
            AssessmentRepository cases, CurrentActorService actors,
            Clock clock) {
        this.plans = plans;
        this.orders = orders;
        this.cases = cases;
        this.actors = actors;
        this.clock = clock;
    }

    /** @param order owned order @return public current operational itinerary */
    @PreAuthorize("hasRole('CUSTOMER')")
    public PlanPublicItinerary customer(long order) {
        var actor = actors.current();

        customerAccess(order, actor);
        var summary = current(order, true);
        var legs = plans.legs(summary.planId());
        BigDecimal road = BigDecimal.ZERO;
        BigDecimal air = BigDecimal.ZERO;

        for (var leg : legs) {
            if (leg.transportMode() == PlanLegMode.ROAD) {
                road = road.add(leg.distanceKm());
            } else {
                air = air.add(leg.distanceKm());
            }
        }
        var publicLegs = legs.stream().map(leg -> new PlanPublicLeg(
                leg.sequenceNo(), leg.transportMode(), leg.startPoint(),
                leg.endPoint(), leg.plannedDeparture(), leg.plannedArrival(),
                leg.startTimezone(), leg.endTimezone(),
                leg.plannedFlightNumber(),
                leg.flightReservations().stream().filter(f ->
                        "CONFIRMED".equals(f.status().name()))
                        .map(f -> f.airlineName()).findFirst().orElse(null),
                leg.stallAssignments().stream().filter(a -> List.of(
                        "ACTIVE", "CONFIRMED").contains(a.status().name()))
                        .map(a -> a.sharingCode().name()).findFirst()
                        .orElse(null))).toList();

        return new PlanPublicItinerary(order, summary.versionNo(), road, air,
                publicLegs);
    }

    /** @param order assigned order @return driver's own ROAD legs */
    @PreAuthorize("hasRole('DRIVER')")
    public List<PlanDriverLeg> driver(long order) {
        var actor = actors.current();

        if (!"DRIVER".equals(actor.roleCode()) || actor.staffId() == null
                || !cases.activeStaff(actor.staffId(), "DRIVER")) {
            throw new AccessDeniedException("An active Driver is required.");
        }
        var summary = current(order, true);
        var tasks = plans.legs(summary.planId()).stream()
                .filter(leg -> leg.transportMode() == PlanLegMode.ROAD
                        && leg.staffAssignments().stream().anyMatch(a ->
                                a.staffId() == actor.staffId()
                                && "DRIVER".equals(a.roleInTrip().name())
                                && List.of("ACTIVE", "CONFIRMED").contains(
                                        a.status().name()))).toList();

        if (tasks.isEmpty()) {
            throw missing();
        }
        var contact = plans.coordinatorContact(order).orElse(null);
        return tasks.stream().map(leg -> new PlanDriverLeg(leg.legId(),
                leg.sequenceNo(), leg.startPoint(), leg.endPoint(),
                leg.plannedDeparture(), leg.plannedArrival(),
                leg.startTimezone(), leg.endTimezone(),
                leg.careInstructions(), contact)).toList();
    }

    /** BE1 may consume sourced draft kilometres before quote acceptance.
     * This quantity contract does not confirm or authorize a trip.
     * @param order authorized parent @param revision current revision
     * @return actual sourced kilometres or pending, never an invented total */
    @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','COORDINATOR')")
    public ItineraryPricingContext pricing(long order, long revision) {
        var actor = actors.current();

        if ("CUSTOMER".equals(actor.roleCode())) {
            customerAccess(order, actor);
        } else if (actor.staffId() == null
                || !List.of("MANAGER", "COORDINATOR").contains(
                        actor.roleCode())
                || !cases.activeStaff(actor.staffId(), actor.roleCode())
                || ("COORDINATOR".equals(actor.roleCode())
                        && !cases.assigned(order, actor.staffId(),
                                CaseRole.COORDINATOR))) {
            throw missing();
        }
        var parent = cases.order(order, false).orElseThrow(this::missing);
        if (!plans.ready() || parent.revisionId() == null
                || parent.revisionId() != revision) {
            return new ItineraryPricingContext(null, null, null, "PENDING");
        }
        var summary = pricingCandidate(order, revision);
        if (summary == null) {
            return new ItineraryPricingContext(null, null, null, "PENDING");
        }
        var legs = plans.legs(summary.planId());
        var now = OffsetDateTime.now(clock);

        if (parent.revisionId() == null || parent.revisionId() != revision
                || summary.revisionId() != revision || legs.isEmpty()
                || legs.stream().anyMatch(leg -> leg.distanceSource() == null
                        || leg.distanceSource().isBlank()
                        || leg.distanceConfirmedAt() == null
                        || leg.distanceConfirmedAt().isAfter(now)
                        || leg.distanceConfirmedBy() == null
                        || !plans.distanceSourceValid(order, revision,
                                leg.distanceConfirmedBy()))) {
            return new ItineraryPricingContext(summary.planId(), null, null,
                    "PENDING");
        }
        try {
            OperationalPlanPolicy.validateLegs(legs.stream().map(l ->
                    new com.equitransit.backend.dto.plan.PlanModels.
                            PlanLegInput(l.sequenceNo(), l.transportMode(),
                                    l.startPoint(), l.endPoint(),
                                    l.distanceKm(), l.distanceSource(),
                                    l.plannedDeparture(), l.plannedArrival(),
                                    l.startTimezone(), l.endTimezone(),
                                    l.supplierId(), l.plannedFlightNumber(),
                                    l.connectionBufferMinutes(),
                                    l.restDurationMinutes(),
                                    l.careInstructions())).toList());
        } catch (OrderException invalid) {
            return new ItineraryPricingContext(summary.planId(), null, null,
                    "PENDING");
        }
        BigDecimal road = legs.stream().filter(l -> l.transportMode()
                == PlanLegMode.ROAD).map(l -> l.distanceKm())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal air = legs.stream().filter(l -> l.transportMode()
                == PlanLegMode.AIR).map(l -> l.distanceKm())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ItineraryPricingContext(summary.planId(), road, air,
                "SOURCED");
    }

    private PlanSummaryView pricingCandidate(long order, long revision) {
        Long id = plans.currentPlanId(order);
        var selected = id == null ? null
                : plans.findPlan(order, id).orElse(null);
        var eligible = List.of(PlanState.DRAFT, PlanState.PROPOSED,
                PlanState.PLANNED, PlanState.CONFIRMED, PlanState.ACTIVE,
                PlanState.IN_TRANSIT);

        if (selected != null && selected.orderId() == order
                && selected.revisionId() == revision
                && eligible.contains(selected.planStatus())) {
            return selected;
        }
        var drafts = plans.pricingDrafts(order, revision);
        if (drafts.size() != 1) {
            return null;
        }
        var draft = drafts.getFirst();
        return draft.orderId() == order && draft.revisionId() == revision
                && List.of(PlanState.DRAFT, PlanState.PROPOSED,
                        PlanState.PLANNED).contains(draft.planStatus())
                ? draft : null;
    }

    private void customerAccess(long order, CurrentActor actor) {
        if (!"CUSTOMER".equals(actor.roleCode()) || actor.customerId() == null
                || orders.findOwned(order, actor.customerId(), false)
                        .isEmpty()) {
            throw missing();
        }
    }

    private PlanSummaryView current(long order, boolean operational) {
        if (!plans.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PLAN_SCHEMA_PENDING", "Apply migration 008 first.");
        }
        Long id = plans.currentPlanId(order);
        var summary = id == null ? null : plans.findPlan(order, id)
                .orElse(null);

        if (summary == null || List.of(PlanState.CANCELLED,
                PlanState.REJECTED, PlanState.SUPERSEDED).contains(
                        summary.planStatus()) || (operational
                && !List.of(PlanState.CONFIRMED,
                PlanState.ACTIVE, PlanState.IN_TRANSIT).contains(
                        summary.planStatus()))) {
            throw missing();
        }
        return summary;
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND,
                "PLAN_NOT_FOUND", "No accessible current itinerary exists.");
    }
}
