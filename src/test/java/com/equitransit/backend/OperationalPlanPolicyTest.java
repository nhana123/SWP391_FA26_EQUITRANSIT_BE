/*
 * OperationalPlanPolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Unit test F10 pure domain invariants and confirmation blockers.
 */
package com.equitransit.backend;

import com.equitransit.backend.dto.plan.PlanModels.AssignmentState;
import com.equitransit.backend.dto.plan.PlanModels.CarrierAcceptanceState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanCommand;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationView;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyView;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegInput;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegView;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationView;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentView;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.PlanTripAssignmentView;
import com.equitransit.backend.dto.plan.PlanModels.ReservationState;
import com.equitransit.backend.dto.plan.PlanModels.StallSharing;
import com.equitransit.backend.dto.plan.PlanModels.TripRole;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.service.plan.OperationalPlanPolicy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests for F10 multimodal operational planning rules. */
public class OperationalPlanPolicyTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2027, 1, 20, 10, 0, 0, 0, ZoneOffset.UTC);

    /** Verifies sequence, distance, mode and connection buffer checks. */
    @Test
    public void legValidationEnforcesSequenceAndConnectionBuffer() {
        var dep1 = NOW.plusDays(5);
        var arr1 = dep1.plusHours(2);
        var leg1 = new PlanLegInput(1, PlanLegMode.ROAD, "A", "B",
                new BigDecimal("40.000"), "Maps", dep1, arr1, "UTC", "UTC",
                null, null, 0, 0, null);
        var leg2Valid = new PlanLegInput(2, PlanLegMode.AIR, "B", "C",
                new BigDecimal("1200.000"), "IATA", arr1.plusMinutes(90),
                arr1.plusHours(6), "UTC", "UTC", 21L, "VN51", 60, 30, null);

        OperationalPlanPolicy.validateLegs(List.of(leg1, leg2Valid));
        var leg2Overlap = new PlanLegInput(2, PlanLegMode.AIR, "B", "C",
                new BigDecimal("1200.000"), "IATA", arr1.plusMinutes(89),
                arr1.plusHours(6), "UTC", "UTC", 21L, "VN51", 60, 30, null);

        assertThatThrownBy(() -> OperationalPlanPolicy.validateLegs(
                List.of(leg1, leg2Overlap)))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("connection/rest");
        var roadWithFlight = new PlanLegInput(1, PlanLegMode.ROAD, "A", "B",
                new BigDecimal("40.000"), "Maps", dep1, arr1, "UTC", "UTC",
                null, "VN51", 0, 0, null);

        assertThatThrownBy(() -> OperationalPlanPolicy.validateLegs(
                List.of(roadWithFlight)))
                .isInstanceOf(OrderException.class);
    }

    /** HELD flight or stall without carrier proof never counts as CONFIRMED. */
    @Test
    public void heldIsNeverConfirmedAndCompulsoryDaysAreEnforced() {
        var cmd = new PlanCommand(UUID.randomUUID(), "000000000001A2B3");

        assertThatThrownBy(() -> OperationalPlanPolicy.validateStallRequest(
                new PlanStallAssignmentRequest(cmd, 12L, "000000000001A2B3",
                        CarrierAcceptanceState.PENDING,
                        AssignmentState.CONFIRMED, null, null, null)))
                .isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> OperationalPlanPolicy.validateFlightRequest(
                new PlanFlightReservationRequest(cmd, 501L, "VN Cargo", "VN51",
                        null, ReservationState.CONFIRMED, "proof.pdf",
                        NOW.plusDays(10), null, new BigDecimal("3.0")),
                new BigDecimal("1.5"), NOW))
                .isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> OperationalPlanPolicy
                .validateQuarantineRequest(
                        new PlanQuarantineReservationRequest(cmd, 201L,
                                "Station", "Q-1", NOW.plusDays(5),
                                NOW.plusDays(18), ReservationState.CONFIRMED,
                                null, "q.pdf", NOW.plusDays(25), null),
                        14, NOW))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("reduce compulsory days");
        assertThatThrownBy(() -> OperationalPlanPolicy.validateInsuranceRequest(
                new PlanInsurancePolicyRequest(cmd, 201L, "Insurer", "POL-1",
                        "pol.pdf", NOW.plusDays(6), NOW.plusDays(20),
                        ReservationState.CONFIRMED, null),
                NOW.plusDays(5), NOW.plusDays(10)))
                .isInstanceOf(OrderException.class);
    }

    /** Confirmation blockers detect HELD reservations and missing resources. */
    @Test
    public void confirmationBlockersRequireConfirmedEvidenceAcrossAllLegs() {
        var start = NOW.plusDays(5);
        var mid = start.plusHours(2);
        var end = mid.plusHours(8);
        var roadLeg = new PlanLegView(1L, 10L, 11L, 1, PlanLegMode.ROAD,
                "A", "B", new BigDecimal("50.000"), start, mid,
                LegState.PLANNED, "UTC", "UTC", null, null, 0, 0, null,
                "Maps", 32L, NOW,
                List.of(new PlanTripAssignmentView(1L, 1L, 41L,
                        TripRole.DRIVER, start, mid, AssignmentState.CONFIRMED,
                        60, null)),
                List.of(), List.of());
        var airLegHeld = new PlanLegView(2L, 10L, null, 2, PlanLegMode.AIR,
                "B", "C", new BigDecimal("1000.000"), mid, end,
                LegState.PLANNED, "UTC", "UTC", 21L, "VN51", 60, 0, null,
                "IATA", 32L, NOW, List.of(),
                List.of(new PlanStallAssignmentView(1L, 2L, 12L,
                        new BigDecimal("1.5"), StallSharing.WIDE, "SUITABLE",
                        CarrierAcceptanceState.ACCEPTED, mid, end,
                        AssignmentState.CONFIRMED, null, "proof.pdf", NOW,
                        null)),
                List.of(new PlanFlightReservationView(1L, 2L, 501L, "VN",
                        "VN51", "REF-1", ReservationState.HELD, null,
                        "flight.pdf", NOW.plusDays(10), null, null,
                        new BigDecimal("3.0"))));
        List<Map<String, Object>> choices = List.of(
                Map.of("ServiceCode", "ROAD_TRANSPORT",
                        "OptionCode", "EQUITRANSIT",
                        "ArrangedBy", "EQUITRANSIT"),
                Map.of("ServiceCode", "QUARANTINE",
                        "OptionCode", "STANDARD",
                        "ArrangedBy", "EQUITRANSIT"),
                Map.of("ServiceCode", "INSURANCE",
                        "OptionCode", "COMBINED",
                        "ArrangedBy", "EQUITRANSIT"));

        var blockers = OperationalPlanPolicy.confirmationBlockers(
                List.of(roadLeg, airLegHeld), List.of(), List.of(), choices,
                14, NOW);

        assertThat(blockers).containsExactlyInAnyOrder(
                "AIR_FLIGHT_NOT_CONFIRMED", "QUARANTINE_NOT_CONFIRMED",
                "INSURANCE_NOT_VERIFIED");
        var airLegConfirmed = new PlanLegView(2L, 10L, null, 2,
                PlanLegMode.AIR, "B", "C", new BigDecimal("1000.000"), mid,
                end, LegState.PLANNED, "UTC", "UTC", 21L, "VN51", 60, 0, null,
                "IATA", 32L, NOW, List.of(), airLegHeld.stallAssignments(),
                List.of(new PlanFlightReservationView(1L, 2L, 501L, "VN",
                        "VN51", "REF-1", ReservationState.CONFIRMED, NOW,
                        "flight.pdf", NOW.plusDays(10), 32L, null,
                        new BigDecimal("3.0"))));
        var qRes = List.of(new PlanQuarantineReservationView(1L, 1L, "Facility",
                "Q-REF", end, end.plusDays(14), ReservationState.CONFIRMED,
                21L, "q.pdf", 32L, NOW, end.plusDays(20), null));
        var ins = List.of(new PlanInsurancePolicyView(1L, 1L, "Insurer",
                "POL-1", "i.pdf", start.minusHours(1), end.plusDays(15),
                ReservationState.CONFIRMED, 32L, NOW, "Terms"));

        assertThat(OperationalPlanPolicy.confirmationBlockers(
                List.of(roadLeg, airLegConfirmed), qRes, ins, choices, 14,
                NOW)).isEmpty();
        assertThat(OperationalPlanPolicy.editable(PlanState.DRAFT)).isTrue();
        assertThat(OperationalPlanPolicy.editable(PlanState.CONFIRMED))
                .isFalse();
    }
}
