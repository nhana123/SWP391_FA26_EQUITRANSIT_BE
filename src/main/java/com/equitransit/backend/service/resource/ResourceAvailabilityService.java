/*
 * ResourceAvailabilityService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Maintain sourced resources and transactional availability.
 */
package com.equitransit.backend.service.resource;

import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceMode;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceWindow;
import com.equitransit.backend.dto.resource.ResourceModels.AssetState;
import com.equitransit.backend.dto.resource.ResourceModels.AvailabilityState;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceBlocker;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceAvailability;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAvailabilityRequest;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceVehicleView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallView;
import com.equitransit.backend.dto.order.response.OrderSnapshot;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceCandidate;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceCandidatePage;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.security.CurrentActorService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.service.pricing.StallFitEvaluator;

/** F09 probe and F10 retained-lock facade; neither creates assignments. */
@Service
@PreAuthorize("hasAnyRole('MANAGER','COORDINATOR')")
public class ResourceAvailabilityService {

    private final ResourceRepository resources;
    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final CurrentActorService actors;
    private final OrderJsonCodec json;
    private final StallFitEvaluator fit;
    private final Clock clock;

    /** @param resources SQL catalogue @param cases parent/access locks
     * @param orders captured revision @param actors trusted identity
     * @param json snapshot codec @param fit sourced measurement evaluator
     * @param clock server UTC */
    public ResourceAvailabilityService(ResourceRepository resources,
            AssessmentRepository cases, OrderRepository orders,
            CurrentActorService actors, OrderJsonCodec json,
            StallFitEvaluator fit, Clock clock) {
        this.resources = resources;
        this.cases = cases;
        this.orders = orders;
        this.actors = actors;
        this.json = json;
        this.fit = fit;
        this.clock = clock;
    }

    /** @param kind physical type @param id ID @param request captured case
     * @return observation only; lock ends with this request transaction */
    @Transactional(timeout = 15)
    public ResourceAvailability inspect(ResourceKind kind, long id,
            ResourceAvailabilityRequest request) {
        validateContext(kind, request);
        return evaluate(kind, id, request);
    }

    /** Advisory options for one authorized case and time window. */
    @Transactional(timeout = 15)
    public ResourceCandidatePage candidates(ResourceKind kind,
            ResourceAvailabilityRequest request, String search,
            int page, int size) {
        if (page < 0 || size < 1 || size > 10
                || (search != null && (search.isBlank()
                        || search.length() > 50))) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "RESOURCE_PAGE_INVALID", "Use page>=0 and size1..10.");
        }
        validateContext(kind, request);
        String prefix = (search == null ? "" : search.trim())
                .replace("[", "[[]")
                .replace("%", "[%]").replace("_", "[_]");
        var options = resources.options(kind, prefix, page, size);
        var observations = observations(kind, options, request);
        var items = new ArrayList<ResourceCandidate>();

        for (int i = 0; i < options.size(); i++) {
            var option = options.get(i);
            var result = observations.get(i);

            items.add(new ResourceCandidate(option.id(), option.code(),
                    result.status(), result.recordVersion(),
                    result.blockers()));
        }
        return new ResourceCandidatePage(List.copyOf(items), page, size,
                resources.optionCount(kind, prefix));
    }

    private List<ResourceAvailability> observations(ResourceKind kind,
            List<ResourceRepository.ResourceOption> options,
            ResourceAvailabilityRequest request) {
        var ids = options.stream().map(ResourceRepository.ResourceOption::id)
                .toList();
        var snapshot = json.snapshot(orders.revision(request.orderId(),
                request.revisionId()).orElseThrow(this::missing).snapshot());
        var now = OffsetDateTime.now(clock);
        var choice = snapshot.request().getServices();
        BigDecimal slots = kind == ResourceKind.STALL && choice != null
                && choice.getAirStall() != null
                        ? StallFitEvaluator.slots(choice.getAirStall())
                        : new BigDecimal("3");
        var suppliers = new HashSet<Long>();

        if (request.carrierSupplierId() != null) {
            suppliers.add(request.carrierSupplierId());
        }
        for (var option : options) {
            Long owner = kind == ResourceKind.STALL
                    ? ((ResourceStallView) option.view()).data()
                            .ownerSupplierId()
                    : ((ResourceVehicleView) option.view()).data()
                            .ownerSupplierId();
            if (owner != null) {
                suppliers.add(owner);
            }
        }
        Set<Long> active = resources.activeSuppliers(suppliers);
        var policies = kind == ResourceKind.STALL
                ? resources.candidatePolicies(ids,
                        request.carrierSupplierId(), slots, now,
                        request.end().withNano(0))
                : List.<ResourceRepository.CandidatePolicy>of();
        var schedules = resources.candidateSchedules(kind, ids,
                request.start().withNano(0), request.end().withNano(0),
                request.legId() == null ? 0 : request.legId());
        return options.stream().map(option -> candidateAvailability(kind,
                option, request, snapshot, policies, schedules, active, now))
                .toList();
    }

    /** F10 must retain this transaction through its assignment write.
     * @param kind physical type @param id ID @param request case/window
     * @return current evidence under parent, scheduling and resource locks */
    @Transactional(propagation = Propagation.MANDATORY)
    public ResourceAvailability lockAndCheck(ResourceKind kind, long id,
            ResourceAvailabilityRequest request) {
        validateContext(kind, request);
        return evaluate(kind, id, request);
    }

    /** Strict F10 write boundary; caller retains this transaction to commit.
     * @param kind physical type @param id key @param request exact case/window
     * @param expectedVersion observed resource version @return current proof */
    @Transactional(propagation = Propagation.MANDATORY)
    public ResourceAvailability requireAvailable(ResourceKind kind, long id,
            ResourceAvailabilityRequest request, String expectedVersion) {
        validateContext(kind, request);
        var result = evaluate(kind, id, request);

        if (result.status() != AvailabilityState.AVAILABLE
                || expectedVersion == null || !result.recordVersion()
                        .equalsIgnoreCase(expectedVersion)) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "RESOURCE_NOT_AVAILABLE",
                    "Review resource evidence/version.");
        }
        return result;
    }

    private ResourceAvailability evaluate(ResourceKind kind, long id,
            ResourceAvailabilityRequest request) {
        Object view;
        String code;

        if (kind == ResourceKind.VEHICLE) {
            var vehicle = resources.get(kind, id, ResourceVehicleView.class);

            view = vehicle;
            code = vehicle.data().vehicleCode();
        } else {
            var stall = resources.get(kind, id, ResourceStallView.class);

            view = stall;
            code = stall.data().stallCode();
        }
        var option = new ResourceRepository.ResourceOption(id, code, view);

        return observations(kind, List.of(option), request).getFirst();
    }

    private ResourceAvailability candidateAvailability(ResourceKind kind,
            ResourceRepository.ResourceOption option,
            ResourceAvailabilityRequest request, OrderSnapshot snapshot,
            List<ResourceRepository.CandidatePolicy> policies,
            Map<Long, List<ResourceBlocker>> schedules, Set<Long> active,
            OffsetDateTime now) {
        var blockers = new ArrayList<ResourceBlocker>();
        ResourceWindow window;
        AssetState state;
        Long owner;
        String version;
        Long policyId = null;

        if (kind == ResourceKind.VEHICLE) {
            var vehicle = (ResourceVehicleView) option.view();

            window = vehicle.data().window();
            state = vehicle.data().status();
            owner = vehicle.data().ownerSupplierId();
            version = vehicle.recordVersion();
            if (vehicle.data().horseCapacity() < 1) {
                blockers.add(ResourceBlocker.CAPACITY);
            }
        } else {
            var stall = (ResourceStallView) option.view();
            var data = stall.data();
            var horse = snapshot.horse();
            var choice = snapshot.request().getServices();

            window = data.window();
            state = data.status();
            owner = data.ownerSupplierId();
            version = stall.recordVersion();
            if (horse == null || horse.weightKg() == null
                    || horse.heightCm() == null || horse.bodyWidthCm() == null
                    || horse.measuredAt() == null) {
                blockers.add(ResourceBlocker.MEASUREMENTS_PENDING);
            } else {
                if (data.weightLimitKg() == null) {
                    blockers.add(ResourceBlocker.WEIGHT_LIMIT_PENDING);
                } else if (horse.weightKg().compareTo(
                        data.weightLimitKg()) > 0) {
                    blockers.add(ResourceBlocker.PHYSICAL_FIT);
                }
                if (horse.heightCm().compareTo(data.internalHeightCm()) > 0
                        || horse.bodyWidthCm().compareTo(
                                data.internalWidthCm()) > 0) {
                    blockers.add(ResourceBlocker.PHYSICAL_FIT);
                }
                if (choice == null || choice.getAirStall() == null) {
                    blockers.add(ResourceBlocker.SLOT_SELECTION_PENDING);
                } else {
                    var slots = StallFitEvaluator.slots(choice.getAirStall());
                    var scoped = policies.stream()
                            .filter(p -> p.stallId() == null
                                    || p.stallId() == option.id())
                            .map(ResourceRepository.CandidatePolicy::policy)
                            .limit(2).toList();
                    var result = fit.evaluate(choice.getAirStall(), horse,
                            scoped, now);

                    policyId = result.policyId();
                    if (slots.compareTo(data.capacitySlots()) > 0) {
                        blockers.add(ResourceBlocker.CAPACITY);
                    }
                    if ("PENDING".equals(result.status())) {
                        blockers.add(scoped.size() > 1
                                ? ResourceBlocker.FIT_POLICY_AMBIGUOUS
                                : ResourceBlocker.FIT_POLICY_PENDING);
                    } else if ("UNSUITABLE".equals(result.status())) {
                        blockers.add(ResourceBlocker.FIT_POLICY_UNSUITABLE);
                    }
                }
            }
        }
        if (state != AssetState.ACTIVE) {
            blockers.add(ResourceBlocker.INACTIVE);
        }
        if ((owner != null && !active.contains(owner))
                || (request.carrierSupplierId() != null
                        && !active.contains(request.carrierSupplierId()))) {
            blockers.add(ResourceBlocker.SUPPLIER_INACTIVE);
        }
        if (window.bufferMinutes() == null) {
            blockers.add(ResourceBlocker.BUFFER_PENDING);
        } else {
            if (ResourceAvailabilityPolicy.maintenance(window,
                    request.start().withNano(0),
                    request.end().withNano(0))) {
                blockers.add(ResourceBlocker.MAINTENANCE);
            }
            if (window.documentExpiresAt() == null) {
                blockers.add(ResourceBlocker.DOCUMENT_PENDING);
            } else if (!window.documentExpiresAt().isAfter(
                    request.end().withNano(0).plusMinutes(
                            window.bufferMinutes()))) {
                blockers.add(ResourceBlocker.DOCUMENT_EXPIRED);
            }
            blockers.addAll(schedules.getOrDefault(option.id(), List.of()));
        }
        AvailabilityState status = blockers.isEmpty()
                ? AvailabilityState.AVAILABLE : blockers.stream()
                        .allMatch(ResourceAvailabilityPolicy::pending)
                                ? AvailabilityState.PENDING
                                : AvailabilityState.UNAVAILABLE;
        return new ResourceAvailability(kind, option.id(), version,
                status, now, policyId, false,
                blockers.stream().distinct().toList());
    }

    private void validateContext(ResourceKind kind,
            ResourceAvailabilityRequest request) {
        if ((kind != ResourceKind.VEHICLE && kind != ResourceKind.STALL)
                || (kind == ResourceKind.VEHICLE
                        && request.mode() != ResourceMode.ROAD)
                || (kind == ResourceKind.STALL
                        && request.mode() != ResourceMode.AIR)) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "RESOURCE_MODE_INVALID", "Use ROAD vehicles or AIR stalls");
        }
        var start = request.start().withNano(0);
        var end = request.end().withNano(0);

        if (!start.isBefore(end) || start.getYear() < 1900
                || end.getYear() > 9997) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "RESOURCE_WINDOW_INVALID", "Use a valid bounded interval.");
        }
        var actor = actors.current();
        var parent = cases.order(request.orderId(), true)
                .orElseThrow(this::missing);
        String role = actor.roleCode();

        if (actor.staffId() == null || !List.of("MANAGER", "COORDINATOR")
                .contains(role) || !cases.activeStaff(actor.staffId(), role)) {
            throw new AccessDeniedException(
                    "An active scheduling actor is required.");
        }
        if ("COORDINATOR".equals(role) && !cases.assigned(request.orderId(),
                actor.staffId(), CaseRole.COORDINATOR)) {
            throw missing();
        }
        if (parent.revisionId() == null
                || parent.revisionId() != request.revisionId()
                || !List.of("APPROVED", "IN_PREPARATION", "CONFIRMED",
                        "IN_TRANSIT", "DELIVERED").contains(parent.status())) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "RESOURCE_REVISION_CONFLICT",
                    "Review the current revision.");
        }
        if (!resources.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RESOURCE_SCHEMA_PENDING", "Deploy resource migration010.");
        }
        resources.lock();
        if (request.legId() != null && !resources.legBelongs(request.legId(),
                request.orderId(), request.revisionId())) {
            throw missing();
        }
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND",
                "The assigned current case was not found.");
    }
}
