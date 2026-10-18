package com.equitransit.backend.plan;

import com.equitransit.backend.dto.plan.PlanModels.AssignmentState;
import com.equitransit.backend.dto.plan.PlanModels.CarrierAcceptanceState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanCommand;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationView;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegView;
import com.equitransit.backend.dto.plan.PlanModels.
        PlanQuarantineReservationRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentView;
import com.equitransit.backend.dto.plan.PlanModels.ReservationState;
import com.equitransit.backend.dto.plan.PlanModels.StallSharing;
import com.equitransit.backend.service.plan.OperationalPlanPolicy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real confirmation policy tests for independent evidence and capacity. */
public class OperationalPlanGatePolicyTest {

    private final OffsetDateTime now =
            OffsetDateTime.parse("2026-10-18T00:00:00Z");
    private final OffsetDateTime start = now.plusDays(30);

    /** A smaller HELD stall cannot satisfy a larger confirmed stall's load. */
    @Test
    public void heldStallCannotLaunderInsufficientFlightCapacity() {
        var leg = air(List.of(stall(1, 3, AssignmentState.CONFIRMED),
                stall(2, 1, AssignmentState.HELD)), 1);

        assertThat(OperationalPlanPolicy.confirmationBlockers(List.of(leg),
                List.of(), List.of(), List.of(), 0, now))
                .contains("AIR_FLIGHT_NOT_CONFIRMED");
    }

    /** All eligible active equipment must fit in the reserved flight space. */
    @Test
    public void totalConfirmedStallCapacityIsRequired() {
        var stalls = List.of(stall(1, 3, AssignmentState.CONFIRMED),
                stall(2, 3, AssignmentState.CONFIRMED));

        assertThat(OperationalPlanPolicy.confirmationBlockers(
                List.of(air(stalls, 3)), List.of(), List.of(), List.of(), 0,
                now)).contains("AIR_FLIGHT_NOT_CONFIRMED");
        assertThat(OperationalPlanPolicy.confirmationBlockers(
                List.of(air(stalls, 6)), List.of(), List.of(), List.of(), 0,
                now)).isEmpty();
    }

    /** A valid ticket and confirmed carrier-approved stall pass together. */
    @Test
    public void confirmedFlightHasSufficientCapacity() {
        assertThat(OperationalPlanPolicy.confirmationBlockers(List.of(air(
                List.of(stall(1, 3, AssignmentState.CONFIRMED)), 3)),
                List.of(), List.of(), List.of(), 0, now)).isEmpty();
    }

    /** A booking expiring before facility arrival is invalid immediately. */
    @Test
    public void quarantineExpiryMustCoverBookingStart() {
        var request = new PlanQuarantineReservationRequest(new PlanCommand(
                UUID.randomUUID(), "0000000000000001"), 201, "Facility",
                "Q-REF", start, start.plusDays(14),
                ReservationState.CONFIRMED, null, "booking.pdf",
                start.minusHours(1), null);

        assertThatThrownBy(() -> OperationalPlanPolicy
                .validateQuarantineRequest(request, 14, now))
                .hasMessageContaining("booking start");
    }

    private PlanLegView air(List<PlanStallAssignmentView> stalls, int slots) {
        var flight = new PlanFlightReservationView(901, 402, 601, "Airline",
                "VN51", "BOOK-1", ReservationState.CONFIRMED, now,
                "ticket.pdf", start.plusDays(1), 31L, null,
                BigDecimal.valueOf(slots));
        return new PlanLegView(402, 311, null, 1, PlanLegMode.AIR, "A", "B",
                BigDecimal.valueOf(1000), start, start.plusHours(2),
                LegState.PLANNED, "Asia/Ho_Chi_Minh", "Asia/Singapore", 21L,
                "VN51", 0, 0, null, "verified route", 31L, now, List.of(),
                stalls, List.of(flight));
    }

    private PlanStallAssignmentView stall(long id, int slots,
            AssignmentState status) {
        return new PlanStallAssignmentView(id, 402, 12,
                BigDecimal.valueOf(slots), StallSharing.PRIVATE, "SUITABLE",
                CarrierAcceptanceState.ACCEPTED, start, start.plusHours(2),
                status, null, "carrier.pdf", now, null);
    }
}
