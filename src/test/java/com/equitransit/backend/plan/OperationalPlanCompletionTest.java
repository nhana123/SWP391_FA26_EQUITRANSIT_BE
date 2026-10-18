package com.equitransit.backend.plan;

import com.equitransit.backend.dto.plan.PlanModels.AssignmentState;
import com.equitransit.backend.dto.plan.PlanModels.CarrierAcceptanceState;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationView;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentView;
import com.equitransit.backend.dto.plan.PlanModels.ReservationState;
import com.equitransit.backend.dto.plan.PlanModels.StallSharing;
import com.equitransit.backend.dto.plan.PlanModels.GateState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanCommand;
import com.equitransit.backend.dto.plan.PlanModels.PlanConfirmRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanDraftRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegInput;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegView;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegsRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.PlanSummaryView;
import com.equitransit.backend.dto.plan.PlanModels.PlanTripAssignmentView;
import com.equitransit.backend.dto.plan.PlanModels.TripRole;
import com.equitransit.backend.dto.order.request.OrderDraftRequest;
import com.equitransit.backend.dto.resource.ResourceModels.AvailabilityState;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceAvailability;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.order.response.OrderSnapshot;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.order.OrderRevisionRecord;
import com.equitransit.backend.entity.pricing.QuarantineAssessmentResult;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.plan.PlanEvidenceRepository;
import com.equitransit.backend.repository.plan.PlanRepository;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.plan.OperationalPlanPolicy;
import com.equitransit.backend.service.plan.OperationalPlanService;
import com.equitransit.backend.service.resource.ResourceAvailabilityService;
import com.equitransit.backend.service.pricing.ServiceAssessmentFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Service regression tests use real policies and explicit boundary doubles. */
public class OperationalPlanCompletionTest {

    private static final String VERSION = "0000000000000001";
    private final OffsetDateTime now =
            OffsetDateTime.parse("2026-10-18T00:00:00Z");
    private final OffsetDateTime departure = now.plusDays(30);
    private PlanRepository plans;
    private PlanEvidenceRepository evidence;
    private AssessmentRepository cases;
    private ResourceRepository resources;
    private ResourceAvailabilityService availability;
    private ServiceAssessmentFacade assessments;
    private CurrentActorService actors;
    private OperationalPlanService service;
    private PlanCommand command;

    /** Configure only explicit actual contracts; no database is contacted. */
    @BeforeEach
    public void setup() {
        plans = mock(PlanRepository.class);
        evidence = mock(PlanEvidenceRepository.class);
        cases = mock(AssessmentRepository.class);
        resources = mock(ResourceRepository.class);
        availability = mock(ResourceAvailabilityService.class);
        assessments = mock(ServiceAssessmentFacade.class);
        actors = mock(CurrentActorService.class);
        var orders = mock(OrderRepository.class);
        var json = mock(OrderJsonCodec.class);
        var snapshot = mock(OrderSnapshot.class);
        var request = mock(OrderDraftRequest.class);
        var revision = mock(OrderRevisionRecord.class);

        command = new PlanCommand(UUID.randomUUID(), VERSION);
        when(plans.ready()).thenReturn(true);
        when(plans.currentPlanId(101)).thenReturn(311L);
        when(resources.ready()).thenReturn(true);
        when(resources.activeSupplier(any())).thenReturn(true);
        when(cases.order(eq(101L), anyBoolean())).thenReturn(Optional.of(
                new CaseOrder(101, "EQ-101", "APPROVED", VERSION, 201L)));
        when(cases.activeStaff(anyLong(), anyString())).thenReturn(true);
        when(cases.assigned(anyLong(), anyLong(), any())).thenReturn(true);
        when(actors.current()).thenReturn(
                new CurrentActor(41L, "COORDINATOR", null, 31L));
        when(orders.revision(101, 201)).thenReturn(Optional.of(revision));
        when(revision.snapshot()).thenReturn("snapshot");
        when(json.snapshot("snapshot")).thenReturn(snapshot);
        when(snapshot.request()).thenReturn(request);
        when(request.getDeliveryDeadline()).thenReturn(departure.plusDays(2));
        when(assessments.quarantine(anyLong(), any(), any(), any()))
                .thenReturn(new QuarantineAssessmentResult("NOT_REQUIRED",
                        null, 9L));
        when(plans.findPlan(101, 311)).thenReturn(Optional.of(summary(
                311, PlanState.DRAFT, true)));
        when(plans.legs(311)).thenReturn(List.of(road()));
        when(plans.distanceSourceValid(101, 201, 31)).thenReturn(true);
        when(availability.lockAndCheck(any(), anyLong(), any())).thenReturn(
                new ResourceAvailability(ResourceKind.VEHICLE, 11, VERSION,
                        AvailabilityState.AVAILABLE, now, null, false,
                        List.of()));
        service = new OperationalPlanService(plans, cases, orders,
                availability, resources, evidence, assessments, actors,
                json, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(now.toInstant(), ZoneOffset.UTC));
    }

    /** Sourced ROAD confirmation consumes the commercial boundary. */
    @Test
    public void confirmConsumesQuoteAndAppendsAudit() {
        service.confirm(101, 311, new PlanConfirmRequest(command, 501));
        verify(evidence).requireAccepted(101, 201, 501, now);
        verify(plans).confirmPlan(101, 311, 501, now);
        verify(evidence).append(101, 41, 311, "CONFIRM_PLAN",
                command.operationId(), now);
    }

    /** Unknown assessment cannot fall back to customer-selected days. */
    @Test
    public void pendingQuarantineBlocksConfirmation() {
        when(assessments.quarantine(anyLong(), any(), any(), any()))
                .thenReturn(new QuarantineAssessmentResult("UNKNOWN",
                        null, null));
        when(plans.quarantineChoice(201)).thenReturn(
                List.of(Map.of("RequiredDays", 14)));
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("QUARANTINE_ASSESSMENT_PENDING");
        verify(plans, never()).confirmPlan(anyLong(), anyLong(), anyLong(),
                any());
    }

    /** An unaccepted or cross-order quote prevents any plan transition. */
    @Test
    public void rejectedCommercialEvidenceStopsConfirmation() {
        doThrow(new OrderException(HttpStatus.CONFLICT,
                "ACCEPTED_QUOTE_REQUIRED", "Quote belongs to another order"))
                .when(evidence).requireAccepted(101, 201, 501, now);
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("another order");
        verify(plans, never()).confirmPlan(anyLong(), anyLong(), anyLong(),
                any());
    }

    /** Infrastructure failures are never converted into readiness. */
    @Test
    public void failedEvidenceQueryCannotConfirm() {
        doThrow(new DataAccessResourceFailureException("offline"))
                .when(evidence).requireAccepted(101, 201, 501, now);
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    /** An infrastructure write error is not reported as a schedule conflict. */
    @Test
    public void databaseWriteFailurePropagates() {
        doThrow(new DataAccessResourceFailureException("database offline"))
                .when(plans).confirmPlan(101, 311, 501, now);
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(plans, never()).saveOperation(anyLong(), anyLong(), any(),
                anyString(), anyString());
    }

    /** Ending the case assignment removes Coordinator access. */
    @Test
    public void endedCoordinatorCannotReadOrWrite() {
        when(cases.assigned(anyLong(), anyLong(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.get(101, 311))
                .hasMessageContaining("not found");
        verify(evidence, never()).requireAccepted(anyLong(), anyLong(),
                anyLong(), any());
    }

    /** Customer tokens have no direct staff plan access. */
    @Test
    public void customerCannotUseStaffService() {
        when(actors.current()).thenReturn(
                new CurrentActor(51L, "CUSTOMER", 61L, null));
        assertThatThrownBy(() -> service.get(101, 311))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Resource availability is recalculated at confirmation. */
    @Test
    public void withdrawnResourceBlocksPreviouslyAssignedPlan() {
        when(availability.lockAndCheck(any(), anyLong(), any())).thenReturn(
                new ResourceAvailability(ResourceKind.VEHICLE, 11, VERSION,
                        AvailabilityState.UNAVAILABLE, now, null, false,
                        List.of()));
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("RESOURCE_VEHICLE_UNAVAILABLE");
    }

    /** Deactivated drivers and revoked distance reviewers block the gate. */
    @Test
    public void inactiveDistanceSourceBlocksPlan() {
        when(plans.distanceSourceValid(101, 201, 31)).thenReturn(false);
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("LEG_DISTANCE_SOURCE_INACTIVE");
    }

    /** A horse already on another order cannot be booked twice. */
    @Test
    public void horseOverlapBlocksConfirmation() {
        when(plans.lockHorseAndCheckOverlap(anyLong(), any(), any()))
                .thenReturn(true);
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("overlapping active");
    }

    /** Coordinator cannot silently replace an approved operating plan. */
    @Test
    public void replacementRequiresManager() {
        when(plans.currentPlanId(101)).thenReturn(310L);
        when(plans.findPlan(101, 310)).thenReturn(Optional.of(summary(
                310, PlanState.CONFIRMED, true)));
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("Only Manager");
    }

    /** Even Manager leaves in-flight changes to the operational change flow. */
    @Test
    public void activePlanIsProtected() {
        when(actors.current()).thenReturn(
                new CurrentActor(41L, "MANAGER", null, 31L));
        when(plans.currentPlanId(101)).thenReturn(310L);
        when(plans.findPlan(101, 310)).thenReturn(Optional.of(summary(
                310, PlanState.IN_TRANSIT, true)));
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("operational change");
    }

    /** A stale parent rowversion cannot edit or confirm any draft. */
    @Test
    public void staleVersionRejectsMutation() {
        var stale = new PlanCommand(UUID.randomUUID(), "0000000000000002");

        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(stale, 501)))
                .hasMessageContaining("Order changed");
    }

    /** A retry key with a changed request never executes a second write. */
    @Test
    public void changedRetryPayloadIsRejected() {
        when(plans.operation(101, 41, command.operationId())).thenReturn(
                List.of(Map.of("RequestHash", "different",
                        "ReceiptJSON", "{}")));
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("different payload");
    }

    /** Draft edits cancel only their own proof and retain history. */
    @Test
    public void draftChainCanBeEditedAtomically() {
        service.replaceLegs(101, 311,
                new PlanLegsRequest(command, List.of(input(1, "A", "B"))));
        verify(plans).clearDraft(311, departure, departure.plusHours(2));
        verify(plans).insertLeg(eq(311L), any(), eq(31L), eq(now));
        verify(plans, never()).confirmPlan(anyLong(), anyLong(), anyLong(),
                any());
    }

    /** Confirmed versions remain immutable. */
    @Test
    public void confirmedVersionCannotBeEdited() {
        when(plans.findPlan(101, 311)).thenReturn(Optional.of(summary(
                311, PlanState.CONFIRMED, true)));
        assertThatThrownBy(() -> service.replaceLegs(101, 311,
                new PlanLegsRequest(command, List.of(input(1, "A", "B")))))
                .hasMessageContaining("cannot be modified");
    }

    /** A newer draft can coexist with the old operating pointer. */
    @Test
    public void creatingUnselectedDraftPreservesCurrentPointer() {
        when(plans.insertPlan(anyLong(), anyLong(), anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                .thenReturn(311L);
        service.createDraft(101, new PlanDraftRequest(command, 201, false,
                List.of(input(1, "A", "B"))));
        verify(plans, never()).setCurrentPlan(anyLong(), anyLong());
    }

    /** F11 cannot reuse a confirmed clearance for an old revision. */
    @Test
    public void readinessRequiresExactRevisionAndCurrentPlan() {
        assertThat(service.operationalReady(101, 202, 311)).isFalse();
        verify(evidence, never()).requireAccepted(anyLong(), anyLong(),
                anyLong(), any());
    }

    /** A draft that aged past departure cannot be newly confirmed. */
    @Test
    public void elapsedDraftMustBeRescheduled() {
        var past = new PlanSummaryView(311, 101, 201, 1, PlanState.DRAFT,
                true, null, 31, now.minusHours(1), now.plusHours(1),
                GateState.PENDING, GateState.PENDING, GateState.PENDING);

        when(plans.findPlan(101, 311)).thenReturn(Optional.of(past));
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("Reschedule");
        verify(plans, never()).confirmPlan(anyLong(), anyLong(), anyLong(),
                any());
    }

    /** Cancelling an order closes readiness despite a stored PASS. */
    @Test
    public void cancelledOrderCannotPassOperationalGate() {
        when(plans.findPlan(101, 311)).thenReturn(Optional.of(summary(
                311, PlanState.CONFIRMED, true)));
        when(cases.order(eq(101L), anyBoolean())).thenReturn(Optional.of(
                new CaseOrder(101, "EQ-101", "CANCELLED", VERSION, 201L)));
        assertThat(service.operationalReady(101, 201, 311)).isFalse();
        verify(evidence, never()).requireAccepted(anyLong(), anyLong(),
                anyLong(), any());
    }

    /** A positive live path requires the exact current operating plan. */
    @Test
    public void currentConfirmedPlanPassesWithLiveEvidence() {
        when(plans.findPlan(101, 311)).thenReturn(Optional.of(summary(
                311, PlanState.CONFIRMED, true)));
        assertThat(service.operationalReady(101, 201, 311)).isTrue();
    }

    /** Horse conflicts must still be evaluated when F11 reads readiness. */
    @Test
    public void laterHorseConflictClosesReadiness() {
        when(plans.findPlan(101, 311)).thenReturn(Optional.of(summary(
                311, PlanState.CONFIRMED, true)));
        when(plans.lockHorseAndCheckOverlap(anyLong(), any(), any()))
                .thenReturn(true);
        assertThat(service.operationalReady(101, 201, 311)).isFalse();
    }

    /** A plan window cannot hide time outside its actual leg chain. */
    @Test
    public void inconsistentPlanWindowBlocksConfirmation() {
        var shifted = new PlanSummaryView(311, 101, 201, 1, PlanState.DRAFT,
                true, null, 31, departure, departure.plusHours(1),
                GateState.PENDING, GateState.PENDING, GateState.PENDING);

        when(plans.findPlan(101, 311)).thenReturn(Optional.of(shifted));
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("PLAN_WINDOW_MISMATCH");
    }

    /** A gate database error never falls back to the persisted PASS. */
    @Test
    public void horseReadFailureIsNotReadiness() {
        when(plans.findPlan(101, 311)).thenReturn(Optional.of(summary(
                311, PlanState.CONFIRMED, true)));
        when(plans.lockHorseAndCheckOverlap(anyLong(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> service.operationalReady(101, 201, 311))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    /** A CONFIRMED label cannot bypass a missing source offer. */
    @Test
    public void unprovenFlightCannotConfirm() {
        when(plans.legs(311)).thenReturn(List.of(air()));
        assertThatThrownBy(() -> service.confirm(101, 311,
                new PlanConfirmRequest(command, 501)))
                .hasMessageContaining("AIR_FLIGHT_SOURCE_INVALID");
        verify(plans, never()).confirmPlan(anyLong(), anyLong(), anyLong(),
                any());
    }

    /** The valid AIR path uses a real scoped provenance contract. */
    @Test
    public void flightProvenancePermitsConfirmation() {
        when(plans.legs(311)).thenReturn(List.of(air()));
        when(plans.flightSourceValid(311, 201, 402, 901)).thenReturn(true);
        service.confirm(101, 311, new PlanConfirmRequest(command, 501));
        verify(plans).confirmPlan(101, 311, 501, now);
    }

    /** Times representing different local hours still compare as instants. */
    @Test
    public void disconnectedChainIsRejected() {
        assertThatThrownBy(() -> OperationalPlanPolicy.validateLegs(List.of(
                input(1, "A", "B"), input(2, "C", "D"))))
                .hasMessageContaining("same endpoint");
    }

    private PlanLegInput input(int sequence, String start, String end) {
        return new PlanLegInput(sequence, PlanLegMode.ROAD, start, end,
                BigDecimal.TEN, "verified route", departure,
                departure.plusHours(2), "Asia/Ho_Chi_Minh",
                "Asia/Ho_Chi_Minh", 21L, null, 0, 0, null);
    }

    private PlanSummaryView summary(long id, PlanState state,
            boolean current) {
        return new PlanSummaryView(id, 101, 201, 1, state, current,
                state == PlanState.DRAFT ? null : 501L, 31, departure,
                departure.plusHours(2), GateState.PENDING,
                GateState.PENDING, GateState.PENDING);
    }

    private PlanLegView road() {
        var staff = new PlanTripAssignmentView(701, 401, 32,
                TripRole.DRIVER, departure, departure.plusHours(2),
                AssignmentState.CONFIRMED, 0, "private internal note");

        return new PlanLegView(401, 311, 11L, 1, PlanLegMode.ROAD,
                "A", "B", BigDecimal.TEN, departure,
                departure.plusHours(2), LegState.PLANNED,
                "Asia/Ho_Chi_Minh", "Asia/Ho_Chi_Minh", 21L,
                null, 0, 0, "water", "verified route", 31L, now,
                List.of(staff), List.of(), List.of());
    }

    private PlanLegView air() {
        var arrival = departure.plusHours(2);
        var stall = new PlanStallAssignmentView(801, 402, 12,
                BigDecimal.valueOf(3), StallSharing.PRIVATE, "SUITABLE",
                CarrierAcceptanceState.ACCEPTED, departure, arrival,
                AssignmentState.CONFIRMED, null, "carrier.pdf", now, null);
        var flight = new PlanFlightReservationView(901, 402, 601, "Airline",
                "VN51", "REF-1", ReservationState.CONFIRMED, now,
                "ticket.pdf", departure.plusDays(1), 31L, null,
                BigDecimal.valueOf(3));

        return new PlanLegView(402, 311, null, 1, PlanLegMode.AIR, "A", "B",
                BigDecimal.valueOf(1000), departure, arrival, LegState.PLANNED,
                "Asia/Ho_Chi_Minh", "Asia/Singapore", 21L, "VN51", 0, 0,
                null, "verified route", 31L, now, List.of(), List.of(stall),
                List.of(flight));
    }
}
