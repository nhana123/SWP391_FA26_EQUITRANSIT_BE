/*
 * ResourceAvailabilityPolicy
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Maintain sourced resources and transactional availability.
 */
package com.equitransit.backend.service.resource;

import com.equitransit.backend.dto.resource.ResourceModels.ResourceWindow;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceBlocker;
import java.time.OffsetDateTime;

/** Pure interval arithmetic shared by availability and its tests. */
public final class ResourceAvailabilityPolicy {

    private ResourceAvailabilityPolicy() { }

    /** Buffer is a minimum gap, applied once between adjacent uses.
     * @param startA first start @param endA first end
     * @param startB second start @param endB second end
     * @param buffer clearance minutes @return half-open buffered overlap */
    public static boolean overlaps(OffsetDateTime startA, OffsetDateTime endA,
            OffsetDateTime startB, OffsetDateTime endB, int buffer) {
        return startA.isBefore(endB.plusMinutes(buffer))
                && startB.isBefore(endA.plusMinutes(buffer));
    }

    /** @param window configured maintenance @param start proposed start
     * @param end proposed end @return buffered maintenance overlap */
    public static boolean maintenance(ResourceWindow window,
            OffsetDateTime start, OffsetDateTime end) {
        return window.maintenanceFrom() != null
                && window.maintenanceFrom().isBefore(
                        end.plusMinutes(window.bufferMinutes()))
                && (window.maintenanceTo() == null
                        || start.isBefore(window.maintenanceTo().plusMinutes(
                                window.bufferMinutes())));
    }

    /** @param blocker reason @return whether missing evidence can resolve it */
    public static boolean pending(ResourceBlocker blocker) {
        return blocker.name().endsWith("PENDING")
                || blocker == ResourceBlocker.FIT_POLICY_AMBIGUOUS;
    }
}
