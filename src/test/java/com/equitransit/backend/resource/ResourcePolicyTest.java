/*
 * ResourcePolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Maintain sourced resources and transactional availability.
 */
package com.equitransit.backend.resource;

import com.equitransit.backend.dto.resource.ResourceModels.ResourceWindow;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceBlocker;
import com.equitransit.backend.service.resource.ResourceAvailabilityPolicy;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Boundary cases keep the SQL predicate and documented meaning aligned. */
public class ResourcePolicyTest {

    private final OffsetDateTime start =
            OffsetDateTime.parse("2027-01-01T08:00:00Z");

    /** Touching half-open windows can reuse an asset only without a buffer. */
    @Test
    public void adjacencyAndBufferAreSymmetric() {
        var end = start.plusHours(2);

        assertThat(ResourceAvailabilityPolicy.overlaps(start, end,
                end, end.plusHours(2), 0)).isFalse();
        assertThat(ResourceAvailabilityPolicy.overlaps(start, end,
                end, end.plusHours(2), 1)).isTrue();
        assertThat(ResourceAvailabilityPolicy.overlaps(end, end.plusHours(2),
                start, end, 1)).isTrue();
        assertThat(ResourceAvailabilityPolicy.overlaps(start, end,
                end.plusMinutes(30), end.plusHours(2), 30)).isFalse();
        assertThat(ResourceAvailabilityPolicy.overlaps(start, end,
                end.minusSeconds(1), end.plusHours(2), 0)).isTrue();
    }

    /** UTC-equivalent offsets and nesting are true intersections. */
    @Test
    public void offsetsAndContainedWindowsDoNotBypassOverlap() {
        assertThat(ResourceAvailabilityPolicy.overlaps(start,
                start.plusHours(2), start.withOffsetSameInstant(
                        java.time.ZoneOffset.ofHours(7)).plusMinutes(30),
                start.plusHours(1), 0)).isTrue();
        assertThat(ResourceAvailabilityPolicy.overlaps(start,
                start.plusHours(2), start.minusMinutes(1),
                start.plusHours(3), 0)).isTrue();
    }

    /** Open-ended maintenance is unavailable after it starts. */
    @Test
    public void maintenanceIncludesOngoingAndClearanceTime() {
        var ongoing = new ResourceWindow(start, null, null, 30, null);
        var bounded = new ResourceWindow(start, start.plusHours(2),
                null, 30, null);

        assertThat(ResourceAvailabilityPolicy.maintenance(ongoing,
                start.plusDays(1), start.plusDays(2))).isTrue();
        assertThat(ResourceAvailabilityPolicy.maintenance(bounded,
                start.plusHours(2).plusMinutes(29), start.plusHours(3)))
                .isTrue();
        assertThat(ResourceAvailabilityPolicy.maintenance(bounded,
                start.plusHours(2).plusMinutes(30), start.plusHours(3)))
                .isFalse();
    }

    /** Missing evidence never means that equipment is confirmed available. */
    @Test
    public void pendingIsSeparateFromProvenRejection() {
        assertThat(ResourceAvailabilityPolicy.pending(
                ResourceBlocker.FIT_POLICY_PENDING)).isTrue();
        assertThat(ResourceAvailabilityPolicy.pending(
                ResourceBlocker.BUFFER_PENDING)).isTrue();
        assertThat(ResourceAvailabilityPolicy.pending(
                ResourceBlocker.OVERLAP)).isFalse();
        assertThat(ResourceAvailabilityPolicy.pending(
                ResourceBlocker.PHYSICAL_FIT)).isFalse();
    }
}
