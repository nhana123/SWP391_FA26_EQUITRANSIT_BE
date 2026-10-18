package com.equitransit.backend.plan;

import com.equitransit.backend.dto.plan.PlanModels.AssignmentState;
import com.equitransit.backend.dto.plan.PlanModels.GateState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegView;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.PlanSummaryView;
import com.equitransit.backend.dto.plan.PlanModels.PlanTripAssignmentView;
import com.equitransit.backend.dto.plan.PlanModels.TripRole;
import com.equitransit.backend.dto.plan.PlanPublicModels.PlanEmergencyContact;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.order.TransportOrderRecord;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.plan.PlanRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.plan.PlanItineraryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Ownership, assignment and explicit public DTO projection regressions. */
public class PlanItineraryTest {

    private final OffsetDateTime now =
            OffsetDateTime.parse("2026-10-18T00:00:00Z");
    private PlanRepository plans;
    private OrderRepository orders;
    private AssessmentRepository cases;
    private CurrentActorService actors;
    private PlanItineraryService service;

    /** Prepare a current plan with two different drivers. */
    @BeforeEach
    public void setup() {
        plans = mock(PlanRepository.class);
        orders = mock(OrderRepository.class);
        cases = mock(AssessmentRepository.class);
        actors = mock(CurrentActorService.class);
        when(plans.ready()).thenReturn(true);
        when(plans.currentPlanId(101)).thenReturn(311L);
        when(plans.findPlan(101, 311)).thenReturn(Optional.of(
                summary(PlanState.CONFIRMED)));
        when(plans.legs(311)).thenReturn(List.of(leg(401, 32), leg(402, 33)));
        when(actors.current()).thenReturn(
                new CurrentActor(41L, "CUSTOMER", 51L, null));
        when(orders.findOwned(101, 51, false)).thenReturn(Optional.of(
                mock(TransportOrderRecord.class)));
        when(cases.activeStaff(anyLong(), anyString())).thenReturn(true);
        when(cases.order(101, false)).thenReturn(Optional.of(new CaseOrder(
                101, "EQ-101", "APPROVED", "0000000000000001", 201L)));
        when(plans.distanceSourceValid(101, 201, 31)).thenReturn(true);
        service = new PlanItineraryService(plans, orders, cases, actors,
                Clock.fixed(now.toInstant(), ZoneOffset.UTC));
    }

    /** Only the order's live owner may receive its customer projection. */
    @Test
    public void customerOwnershipIsMandatory() {
        when(orders.findOwned(101, 51, false)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.customer(101))
                .hasMessageContaining("accessible current itinerary");
    }

    /** Draft creation never presents that draft as an operational booking. */
    @Test
    public void draftIsHiddenFromCustomer() {
        when(plans.findPlan(101, 311)).thenReturn(Optional.of(
                summary(PlanState.DRAFT)));
        assertThatThrownBy(() -> service.customer(101))
                .hasMessageContaining("accessible current itinerary");
    }

    /** Public DTOs exclude private commercial and staff fields. */
    @Test
    public void customerResponseHasNoInternalEvidence() throws Exception {
        String json = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(service.customer(101));

        assertThat(json).contains("startPoint", "roadKm", "versionNo")
                .doesNotContain("secret notes", "staffId", "conflictNotes",
                        "vehicleId", "evidenceFileKey", "quoteId",
                        "supplierContact", "acceptedQuoteId");
    }

    /** Each driver only sees the leg with their own assignment. */
    @Test
    public void driverSeesOnlyTheirOwnRoadLegs() {
        when(actors.current()).thenReturn(
                new CurrentActor(42L, "DRIVER", null, 32L));

        assertThat(service.driver(101)).hasSize(1)
                .allSatisfy(leg -> assertThat(leg.legId()).isEqualTo(401));
    }

    /** An active driver without a matching trip assignment gets no data. */
    @Test
    public void unrelatedDriverCannotReadOrder() {
        when(actors.current()).thenReturn(
                new CurrentActor(42L, "DRIVER", null, 99L));
        assertThatThrownBy(() -> service.driver(101))
                .hasMessageContaining("accessible current itinerary");
        verify(plans, never()).coordinatorContact(anyLong());
    }

    /** A driver's own task includes only the designated emergency contact. */
    @Test
    public void assignedDriverReceivesCoordinatorPhone() {
        when(actors.current()).thenReturn(
                new CurrentActor(42L, "DRIVER", null, 32L));
        when(plans.coordinatorContact(101)).thenReturn(Optional.of(
                new PlanEmergencyContact("Coordinator", "+84901234567")));

        assertThat(service.driver(101).getFirst().emergencyContact().phone())
                .isEqualTo("+84901234567");
    }

    /** The Customer projection does not include staff contact data. */
    @Test
    public void customerNeverReadsCoordinatorPhone() throws Exception {
        when(plans.coordinatorContact(101)).thenReturn(Optional.of(
                new PlanEmergencyContact("Coordinator", "+84901234567")));
        String encoded = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(service.customer(101));

        assertThat(encoded).doesNotContain("emergencyContact", "+84901234567");
        verify(plans, never()).coordinatorContact(anyLong());
    }

    /** A current sourced draft can feed pricing before quote acceptance. */
    @Test
    public void sourcedDraftPricingDoesNotRequireConfirmedPlan() {
        when(plans.findPlan(101, 311)).thenReturn(Optional.of(
                summary(PlanState.DRAFT)));

        assertThat(service.pricing(101, 201).status()).isEqualTo("SOURCED");
        assertThat(service.pricing(101, 201).roadKm())
                .isEqualByComparingTo("20");
    }

    /** Revoking the distance source produces pending quantities. */
    @Test
    public void staleSourceCannotProducePricingQuantities() {
        when(plans.distanceSourceValid(101, 201, 31)).thenReturn(false);

        assertThat(service.pricing(101, 201).status()).isEqualTo("PENDING");
        assertThat(service.pricing(101, 201).roadKm()).isNull();
    }

    /** A different revision never reuses the operating plan's kilometres. */
    @Test
    public void pricingRequiresExactRevision() {
        assertThat(service.pricing(101, 202).status()).isEqualTo("PENDING");
    }

    private PlanSummaryView summary(PlanState state) {
        return new PlanSummaryView(311, 101, 201, 1, state, true,
                501L, 31, now.plusDays(30), now.plusDays(31),
                GateState.PENDING, GateState.PENDING, GateState.PENDING);
    }

    private PlanLegView leg(long id, long driver) {
        var from = now.plusDays(30).plusHours((id - 401) * 2);
        var to = from.plusHours(2);
        var staff = new PlanTripAssignmentView(id, id, driver,
                TripRole.DRIVER, from, to, AssignmentState.CONFIRMED, 0,
                "secret notes");

        return new PlanLegView(id, 311, 11L, (int) (id - 400),
                PlanLegMode.ROAD, id == 401 ? "A" : "B",
                id == 401 ? "B" : "C", BigDecimal.TEN, from, to,
                LegState.CONFIRMED, "Asia/Ho_Chi_Minh",
                "Asia/Ho_Chi_Minh", 21L, null, 0, 0, "water",
                "verified source", 31L, now, List.of(staff), List.of(),
                List.of());
    }
}
