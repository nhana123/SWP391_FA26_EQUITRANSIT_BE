/*
 * MilestoneSequenceValidatorTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Unit tests verifying road and air milestone sequence state machines.
 */
package com.equitransit.backend.execution;

import com.equitransit.backend.dto.execution.AirMilestoneType;
import com.equitransit.backend.dto.execution.RoadMilestoneType;
import com.equitransit.backend.entity.execution.LegMilestoneLog;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.service.execution.validation.MilestoneSequenceValidator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for milestone progression constraints and state transitions. */
public class MilestoneSequenceValidatorTest {

    private MilestoneSequenceValidator validator;
    private OffsetDateTime baseTime;

    /** Initializes validator and baseline test timestamps. */
    @BeforeEach
    public void setup() {
        validator = new MilestoneSequenceValidator();
        baseTime = OffsetDateTime.of(2026, 10, 18, 8, 0, 0, 0, ZoneOffset.UTC);
    }

    /** First road milestone must be DEPARTED. */
    @Test
    public void roadInitialMilestoneMustBeDeparted() {
        var existing = new ArrayList<LegMilestoneLog>();

        assertThatCode(() -> validator.validateRoadMilestone(
                existing, RoadMilestoneType.DEPARTED, baseTime, baseTime))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateRoadMilestone(
                existing, RoadMilestoneType.ARRIVED, baseTime, baseTime))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("DEPARTED");

        assertThatThrownBy(() -> validator.validateRoadMilestone(
                existing, RoadMilestoneType.REST_STOP, baseTime, baseTime))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("DEPARTED");
    }

    /** Road milestone after ARRIVED is rejected. */
    @Test
    public void roadMilestoneAfterArrivedIsRejected() {
        var existing = List.of(
                createMilestone(1L, "DEPARTED", baseTime),
                createMilestone(2L, "ARRIVED", baseTime.plusHours(4)));

        assertThatThrownBy(() -> validator.validateRoadMilestone(
                existing, RoadMilestoneType.REST_STOP, baseTime.plusHours(5),
                baseTime.plusHours(5)))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("after ARRIVED");
    }

    /** Air initial milestone must be CARGO_TENDERED or CUSTOMS_CLEARED_OUT. */
    @Test
    public void airInitialMilestoneValidation() {
        var existing = new ArrayList<LegMilestoneLog>();

        assertThatCode(() -> validator.validateAirMilestone(
                existing, AirMilestoneType.CARGO_TENDERED, baseTime, baseTime))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateAirMilestone(
                existing, AirMilestoneType.WHEELS_DOWN, baseTime, baseTime))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("CARGO_TENDERED");
    }

    /** Air progression must follow rank order. */
    @Test
    public void airProgressionMustFollowRankOrder() {
        var existing = List.of(
                createMilestone(1L, "CARGO_TENDERED", baseTime),
                createMilestone(2L, "WHEELS_UP", baseTime.plusHours(2)));

        assertThatCode(() -> validator.validateAirMilestone(
                existing, AirMilestoneType.WHEELS_DOWN, baseTime.plusHours(4),
                baseTime.plusHours(4)))
                .doesNotThrowAnyException();

        // Cannot regress to CARGO_TENDERED after WHEELS_UP
        assertThatThrownBy(() -> validator.validateAirMilestone(
                existing, AirMilestoneType.CARGO_TENDERED,
                baseTime.plusHours(3), baseTime.plusHours(3)))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("violates progression");
    }

    /** Non-monotonic or backwards time is rejected. */
    @Test
    public void backwardsTimeIsRejected() {
        var existing = List.of(
                createMilestone(1L, "DEPARTED", baseTime.plusHours(2)));

        assertThatThrownBy(() -> validator.validateRoadMilestone(
                existing, RoadMilestoneType.REST_STOP, baseTime.plusHours(1),
                baseTime.plusHours(3)))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("cannot precede previous milestone");
    }

    /** Future timestamps beyond clock skew tolerance are rejected. */
    @Test
    public void futureTimestampsAreRejected() {
        var existing = new ArrayList<LegMilestoneLog>();

        assertThatThrownBy(() -> validator.validateRoadMilestone(
                existing, RoadMilestoneType.DEPARTED, baseTime.plusDays(1),
                baseTime))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("cannot be in the future");
    }

    private LegMilestoneLog createMilestone(long id, String type,
            OffsetDateTime occurred) {
        var m = new LegMilestoneLog();
        m.setMilestoneId(id);
        m.setMilestoneType(type);
        m.setOccurredAt(occurred);
        return m;
    }
}
