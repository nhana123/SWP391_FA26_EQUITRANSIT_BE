/*
 * DomainAccessPolicy
 * Author: EquiTransit development team
 * Date: 2026-10-13
 * Copyright: EquiTransit project contributors.
 * Purpose: Apply shared role, ownership and staff identity checks.
 */
package com.equitransit.backend.security;


import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;


/** Checks persisted domain keys, never assignment claims from clients. */
@Component
public class DomainAccessPolicy {

    private final CurrentActorService actors;

    /**
     * Creates the reusable access policy.
     * @param actors the trusted actor resolver
     */
    public DomainAccessPolicy(CurrentActorService actors) {
        this.actors = actors;
    }

    /**
     * Checks the current database role and customer profile.
     * @return whether the actor is a customer
     */
    public boolean isCustomer() {
        return "CUSTOMER".equals(actors.current().roleCode());
    }

    /** @return whether the live actor is a profiled active Manager */
    public boolean isManager() {
        return "MANAGER".equals(actors.current().roleCode());
    }

    /** @return whether the live actor has a case preparation role */
    public boolean isCaseStaff() {
        return java.util.Set.of("SPECIALIST", "COORDINATOR")
                .contains(actors.current().roleCode());
    }

    /**
     * Requires ownership of a resource's persisted customer key.
     * @param ownerCustomerId the owner key read from the resource by service
     */
    public void requireCustomerOwner(Long ownerCustomerId) {
        CurrentActor actor = actors.current();

        if (!"CUSTOMER".equals(actor.roleCode()) || ownerCustomerId == null
                || !ownerCustomerId.equals(actor.customerId())) {
            throw denied();
        }
    }

    /** Requires an active manager with a linked staff profile. */
    public void requireManager() {
        if (!"MANAGER".equals(actors.current().roleCode())) {
            throw denied();
        }
    }

    /**
     * Requires both the expected role and a persisted assigned staff key.
     * Future flows must first resolve current assignment from their tables.
     * @param roleCode the role required by the service, not by client input
     * @param assignedStaffId the active assignment key read from database
     */
    public void requireAssignedStaff(String roleCode, Long assignedStaffId) {
        CurrentActor actor = actors.current();

        if (actor.staffId() == null || assignedStaffId == null
                || !actor.roleCode().equals(roleCode)
                || !actor.staffId().equals(assignedStaffId)) {
            throw denied();
        }
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Access is denied.");
    }
}
