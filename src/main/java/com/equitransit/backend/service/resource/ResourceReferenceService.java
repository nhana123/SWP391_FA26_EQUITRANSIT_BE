/*
 * ResourceReferenceService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Complete scoped resource and fit workflows.
 */
package com.equitransit.backend.service.resource;

import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyPage;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyView;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAssignedVehicle;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.security.CurrentActorService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Source review and assigned-driver reads do not grant catalogue writes. */
@Service
public class ResourceReferenceService {
    private final ResourceRepository resources;
    private final AssessmentRepository staff;
    private final CurrentActorService actors;
    private final Clock clock;

    /** @param resources persisted resources @param staff live role checks
     * @param actors current identity @param clock server time */
    public ResourceReferenceService(ResourceRepository resources,
            AssessmentRepository staff, CurrentActorService actors,
            Clock clock) {
        this.resources = resources;
        this.staff = staff;
        this.actors = actors;
        this.clock = clock;
    }

    /** @param page zero-based @param size bounded @return reviewed sources */
    @PreAuthorize("hasAnyRole('MANAGER','SPECIALIST')")
    @Transactional(timeout = 15)
    public ResourcePolicyPage policies(int page, int size) {
        requireRole(List.of("MANAGER", "SPECIALIST"));
        if (page < 0 || size < 1 || size > 100) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "RESOURCE_PAGE_INVALID", "Use page>=0 and size1..100.");
        }
        return new ResourcePolicyPage(resources.list(ResourceKind.POLICY,
                page, size, ResourcePolicyView.class), page, size,
                resources.count(ResourceKind.POLICY));
    }

    /** @param id policy version @return source details for specialist review */
    @PreAuthorize("hasAnyRole('MANAGER','SPECIALIST')")
    @Transactional(timeout = 15)
    public ResourcePolicyView policy(long id) {
        requireRole(List.of("MANAGER", "SPECIALIST"));
        return resources.get(ResourceKind.POLICY, id, ResourcePolicyView.class);
    }

    /** @param leg assigned current-plan leg @return technical metadata only */
    @PreAuthorize("hasRole('DRIVER')")
    @Transactional(timeout = 15)
    public ResourceAssignedVehicle vehicle(long leg) {
        long driver = requireRole(List.of("DRIVER"));
        return resources.assignedVehicle(leg, driver,
                OffsetDateTime.now(clock)).orElseThrow(() ->
                    new OrderException(HttpStatus.NOT_FOUND,
                            "RESOURCE_ASSIGNMENT_NOT_FOUND",
                            "The active assigned vehicle was not found."));
    }

    private long requireRole(List<String> roles) {
        var actor = actors.current();
        if (actor.staffId() == null || !roles.contains(actor.roleCode())
                || !staff.activeStaff(actor.staffId(), actor.roleCode())) {
            throw new AccessDeniedException(
                    "An active assigned role is required.");
        }
        if (!resources.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RESOURCE_SCHEMA_PENDING", "Deploy resource migration007.");
        }
        return actor.staffId();
    }
}
