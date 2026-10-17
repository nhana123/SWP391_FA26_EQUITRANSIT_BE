/*
 * ResourceAvailabilityService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Maintain sourced resources and transactional availability.
 */
package com.equitransit.backend.service;

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
import com.equitransit.backend.dto.resource.ResourceModels.ResourceCandidate;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAvailabilityPage;
import com.equitransit.backend.dto.response.OrderSnapshot;
import com.equitransit.backend.dto.response.HorseResponse;
import com.equitransit.backend.entity.CaseRole;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.ResourceRepository;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.repository.OrderRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.json.OrderJsonCodec;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        return evaluate(kind, id, request);
    }

    /** @param kind physical type @param request exact assigned case/window
     * @param page zero-based @param size bounded @return assessed candidates */
    @Transactional(timeout = 15)
    public ResourceAvailabilityPage search(ResourceKind kind,
            ResourceAvailabilityRequest request, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "RESOURCE_PAGE_INVALID", "Use page>=0 and size1..50.");
        }
        var snapshot = prepare(kind, request);
        var now = OffsetDateTime.now(clock);
        long total = resources.count(kind);
        var items = resources.candidateIds(kind, page, size).stream()
                .map(id -> candidate(kind, id, request, snapshot, now))
                .toList();
        return new ResourceAvailabilityPage(items, page, size, total,
                total / size + (total % size == 0 ? 0 : 1));
    }

    /** F10 must retain this transaction through its assignment write.
     * @param kind physical type @param id ID @param request case/window
     * @return current evidence under parent, scheduling and resource locks */
    @Transactional(propagation = Propagation.MANDATORY)
    public ResourceAvailability lockAndCheck(ResourceKind kind, long id,
            ResourceAvailabilityRequest request) {
        return evaluate(kind, id, request);
    }

    /** Strict F10 write boundary; caller retains this transaction to commit.
     * @param kind physical type @param id key @param request exact case/window
     * @param expectedVersion observed resource version @return current proof */
    @Transactional(propagation = Propagation.MANDATORY)
    public ResourceAvailability requireAvailable(ResourceKind kind, long id,
            ResourceAvailabilityRequest request, String expectedVersion) {
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
        var snapshot = prepare(kind, request);
        return candidate(kind, id, request, snapshot,
                OffsetDateTime.now(clock)).availability();
    }

    private OrderSnapshot prepare(ResourceKind kind,
            ResourceAvailabilityRequest request) {
        if (request == null || request.start() == null || request.end() == null
                || request.orderId() <= 0 || request.revisionId() <= 0) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "RESOURCE_INPUT_INVALID", "Use an exact case and window.");
        }
        if ((kind != ResourceKind.VEHICLE && kind != ResourceKind.STALL)
                || (kind == ResourceKind.VEHICLE
                        && request.mode() != ResourceMode.ROAD)
                || (kind == ResourceKind.STALL
                        && request.mode() != ResourceMode.AIR)) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "RESOURCE_MODE_INVALID", "Use ROAD vehicles or AIR stalls");
        }
        var start = utc(request.start());
        var end = utc(request.end());

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
                    "RESOURCE_SCHEMA_PENDING", "Deploy resource migration007.");
        }
        resources.lock();
        if (request.legId() != null && !resources.legBelongs(request.legId(),
                request.orderId(), request.revisionId())) {
            throw missing();
        }
        if (request.legId() != null && !resources.legContext(request.legId(),
                request.mode().name(), request.carrierSupplierId())) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "RESOURCE_LEG_CONTEXT_CONFLICT",
                    "Use the existing leg's mode and carrier.");
        }
        var snapshot = json.snapshot(orders.revision(request.orderId(),
                request.revisionId()).orElseThrow(this::missing).snapshot());
        if (snapshot.orderId() != request.orderId()
                || snapshot.request() == null) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "RESOURCE_SNAPSHOT_INVALID", "Review the saved revision.");
        }
        return snapshot;
    }

    private ResourceCandidate candidate(ResourceKind kind, long id,
            ResourceAvailabilityRequest request, OrderSnapshot snapshot,
            OffsetDateTime now) {
        var start = utc(request.start());
        var end = utc(request.end());
        var blockers = new ArrayList<ResourceBlocker>();
        ResourceWindow window;
        AssetState state;
        Long owner;
        String version;
        String code;
        Long policyId = null;

        if (kind == ResourceKind.VEHICLE) {
            var vehicle = resources.get(kind, id, ResourceVehicleView.class);

            window = vehicle.data().window();
            state = vehicle.data().status();
            owner = vehicle.data().ownerSupplierId();
            version = vehicle.recordVersion();
            code = vehicle.data().vehicleCode();
            if (vehicle.data().horseCapacity() < 1) {
                blockers.add(ResourceBlocker.CAPACITY);
            }
        } else {
            var stall = resources.get(kind, id, ResourceStallView.class);
            var data = stall.data();
            var horse = snapshot.horse();
            var choice = snapshot.request().getServices();

            window = data.window();
            state = data.status();
            owner = data.ownerSupplierId();
            version = stall.recordVersion();
            code = data.stallCode();
            if (!measured(horse, now)) {
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
            }
            if (choice == null || choice.getAirStall() == null) {
                blockers.add(ResourceBlocker.SLOT_SELECTION_PENDING);
            } else {
                var slots = StallFitEvaluator.slots(choice.getAirStall());
                if (slots.compareTo(data.capacitySlots()) > 0) {
                    blockers.add(ResourceBlocker.CAPACITY);
                }
                if (measured(horse, now)) {
                    var policies = resources.policies(id,
                            request.carrierSupplierId(), slots, now, start,
                            end);
                    var result = fit.evaluate(choice.getAirStall(), horse,
                            policies, now);

                    policyId = result.policyId();
                    if ("PENDING".equals(result.status())) {
                        blockers.add(policies.size() > 1
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
        if (!resources.activeSupplier(owner)
                || !resources.activeSupplier(request.carrierSupplierId())) {
            blockers.add(ResourceBlocker.SUPPLIER_INACTIVE);
        }
        if (window.bufferMinutes() == null || window.bufferMinutes() < 0
                || window.bufferMinutes() > 525600) {
            blockers.add(ResourceBlocker.BUFFER_PENDING);
        } else {
            int buffer = window.bufferMinutes();

            if (ResourceAvailabilityPolicy.maintenance(window, start, end)) {
                blockers.add(ResourceBlocker.MAINTENANCE);
            }
            if (window.documentExpiresAt() == null) {
                blockers.add(ResourceBlocker.DOCUMENT_PENDING);
            } else if (!window.documentExpiresAt().isAfter(
                    end.plusMinutes(buffer))) {
                blockers.add(ResourceBlocker.DOCUMENT_EXPIRED);
            }
            blockers.addAll(resources.scheduleBlockers(kind, id, start, end,
                    buffer, request.legId() == null ? 0 : request.legId()));
        }
        AvailabilityState status = blockers.isEmpty()
                ? AvailabilityState.AVAILABLE : blockers.stream()
                        .allMatch(ResourceAvailabilityPolicy::pending)
                                ? AvailabilityState.PENDING
                                : AvailabilityState.UNAVAILABLE;

        return new ResourceCandidate(code, new ResourceAvailability(kind, id,
                version, status, now, policyId, false,
                blockers.stream().distinct().toList()));
    }

    private boolean measured(HorseResponse horse, OffsetDateTime now) {
        return horse != null && horse.measurementsComplete()
                && horse.weightKg() != null && horse.weightKg().signum() > 0
                && horse.heightCm() != null && horse.heightCm().signum() > 0
                && horse.bodyWidthCm() != null
                && horse.bodyWidthCm().signum() > 0
                && horse.measuredAt() != null
                && !horse.measuredAt().isAfter(now);
    }

    private OffsetDateTime utc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).withNano(0);
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND",
                "The assigned current case was not found.");
    }
}
