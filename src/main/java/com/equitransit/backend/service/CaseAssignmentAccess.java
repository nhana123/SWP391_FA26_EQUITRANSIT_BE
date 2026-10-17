/*
 * CaseAssignmentAccess
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Enforce current case responsibility for staff consumers.
 */
package com.equitransit.backend.service;


import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.entity.CaseOrder;
import com.equitransit.backend.entity.CaseRole;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;


/** BE2 consumes this facade inside its write transaction before case work. */
@Service
@PreAuthorize("@domainAccessPolicy.isCaseStaff()")
public class CaseAssignmentAccess {

    private final AssessmentRepository cases;
    private final CurrentActorService actors;

    /**
     * Creates the persisted assignment boundary.
     * @param cases current responsibility adapter
     * @param actors trusted live actor resolver
     */
    public CaseAssignmentAccess(AssessmentRepository cases,
            CurrentActorService actors) {
        this.cases = cases;
        this.actors = actors;
    }

    /**
     * Locks the parent and verifies role, active account and current
     * assignment.
     * A consumer transaction retains the lock until its own work commits.
     * @param order order being processed
     * @param role service-owned required role, never from request body
     * @return authorized exact current revision
     */
    @Transactional
    public long lockRevision(long order, CaseRole role) {
        return requireRevision(order, role, false);
    }

    /**
     * Legal review continues at arrival/release during active operations.
     * @param order current case, never a caller-selected staff identity
     * @return exact current revision, retaining the shared parent lock
     */
    @Transactional
    public long lockLegalRevision(long order) {
        return requireRevision(order, CaseRole.SPECIALIST, true);
    }

    private long requireRevision(long order, CaseRole role, boolean legal) {
        CurrentActor actor = actors.current();
        CaseOrder parent = cases.order(order, true)
                .orElseThrow(this::notFound);

        if (!role.name().equals(actor.roleCode())
                || !cases.activeStaff(actor.staffId(), role.name())
                || !(legal ? java.util.Set.of("APPROVED", "IN_PREPARATION",
                        "CONFIRMED", "IN_TRANSIT", "DELIVERED")
                        .contains(parent.status())
                        : "APPROVED".equals(parent.status()))
                || parent.revisionId() == null
                || !cases.assigned(order, actor.staffId(), role)) {
            throw notFound();
        }
        return parent.revisionId();
    }

    private OrderException notFound() {
        return new OrderException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND",
                "The assigned case was not found.");
    }
}
