/*
 * TripHandoverCompletionTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Verification of handover lifecycle, gates and customer confirmation.
 */
package com.equitransit.backend.handover;

import com.equitransit.backend.dto.handover.HandoverCustomerDecision;
import com.equitransit.backend.dto.handover.HandoverOutcome;
import com.equitransit.backend.dto.handover.HandoverType;
import com.equitransit.backend.dto.handover.HorseConditionStatus;
import com.equitransit.backend.dto.handover.request.CustomerHandoverDecisionRequest;
import com.equitransit.backend.dto.handover.request.FinalDeliveryHandoverCreateRequest;
import com.equitransit.backend.dto.handover.request.IntermediateHandoverCreateRequest;
import com.equitransit.backend.dto.handover.response.CustomerHandoverResponse;
import com.equitransit.backend.dto.handover.response.OperationalCompletionReceipt;
import com.equitransit.backend.dto.handover.response.TripHandoverResponse;
import com.equitransit.backend.entity.handover.TripHandover;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.execution.LegExecutionRepository.LegRow;
import com.equitransit.backend.repository.execution.LegExecutionRepository.OrderScopeRow;
import com.equitransit.backend.repository.execution.LegExecutionRepository.TripAssignmentRow;
import com.equitransit.backend.repository.handover.TripHandoverRepository;
import com.equitransit.backend.repository.handover.TripHandoverRepository.OrderReceiverRow;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.compliance.ComplianceService;
import com.equitransit.backend.service.handover.FinalDeliveryValidator;
import com.equitransit.backend.service.handover.TripHandoverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit & business scenario test suite for Trip Handover operations.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
public class TripHandoverCompletionTest {

    private static final long ORDER_ID = 101L;
    private static final long PLAN_ID = 311L;
    private static final long LEG_ID_1 = 401L;
    private static final long LEG_ID_2 = 402L;
    private static final long HANDOVER_ID = 701L;
    private static final long DRIVER_STAFF_ID = 102L;
    private static final long CUSTOMER_ID = 501L;

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 18, 16, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private TripHandoverRepository handoverRepository;

    @Mock
    private LegExecutionRepository legRepository;

    @Mock
    private CurrentActorService actorService;

    @Mock
    private ComplianceService complianceService;

    private FinalDeliveryValidator validator;
    private TripHandoverService service;
    private Clock fixedClock;
    private ObjectMapper mapper;

    /** Sets up service test fixture with mocked dependencies. */
    @BeforeEach
    public void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-18T16:00:00Z"),
                ZoneOffset.UTC);
        mapper = new ObjectMapper();

        var beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("complianceService", complianceService);
        var provider = beanFactory.getBeanProvider(ComplianceService.class);

        validator = new FinalDeliveryValidator(provider);
        service = new TripHandoverService(handoverRepository, legRepository,
                validator, actorService, mapper, fixedClock);
    }

    /** Assigned driver records intermediate handover at cargo airport. */
    @Test
    public void driverCanRecordIntermediateHandoverOnAssignedLeg() {
        var actor = new CurrentActor(101L, "DRIVER", null, DRIVER_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var assignment = new TripAssignmentRow(11L, LEG_ID_1, DRIVER_STAFF_ID,
                "DRIVER", "CONFIRMED", NOW.minusHours(4), NOW.plusHours(4));
        when(legRepository.findDriverAssignment(LEG_ID_1, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(assignment));

        var leg = new LegRow(LEG_ID_1, PLAN_ID, 1, "ROAD", "Stables", "CDG",
                BigDecimal.valueOf(85), NOW.minusHours(3), NOW.minusHours(1),
                NOW.minusHours(3), null, "IN_TRANSIT", null, null);
        when(legRepository.findLegById(LEG_ID_1)).thenReturn(Optional.of(leg));

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                PLAN_ID, 201L);
        when(legRepository.findOrderByLegId(LEG_ID_1))
                .thenReturn(Optional.of(order));
        when(legRepository.findStaffName(DRIVER_STAFF_ID))
                .thenReturn("Antoine Dupont");

        when(handoverRepository.insert(any(TripHandover.class)))
                .thenReturn(HANDOVER_ID);

        var request = new IntermediateHandoverCreateRequest(
                HandoverType.INTERMEDIATE_AIRPORT,
                NOW,
                "Markus Vance",
                "RAMP_SUPERVISOR",
                "sig/cdg.png",
                HorseConditionStatus.EXCELLENT,
                HandoverOutcome.SUCCESSFUL,
                null,
                "photo/cdg.jpg",
                "CDG Cargo Gate 4",
                null
        );

        TripHandoverResponse response = service.recordIntermediateByDriver(
                LEG_ID_1, request);

        assertThat(response.handoverId()).isEqualTo(HANDOVER_ID);
        assertThat(response.handoverType()).isEqualTo("INTERMEDIATE_AIRPORT");
        assertThat(response.horseConditionStatus()).isEqualTo("EXCELLENT");

        verify(handoverRepository).completeLeg(eq(LEG_ID_1), eq(NOW));
        verify(handoverRepository).activateNextLeg(eq(PLAN_ID), eq(2));
        verify(handoverRepository, never()).completePlan(any(Long.class));
        verify(handoverRepository, never()).deliverOrder(any(Long.class),
                any(OffsetDateTime.class));
    }

    /** Unassigned driver cannot record intermediate handover. */
    @Test
    public void unassignedDriverCannotRecordHandover() {
        var actor = new CurrentActor(101L, "DRIVER", null, DRIVER_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        when(legRepository.findDriverAssignment(LEG_ID_1, DRIVER_STAFF_ID))
                .thenReturn(Optional.empty());

        var request = new IntermediateHandoverCreateRequest(
                HandoverType.INTERMEDIATE_AIRPORT,
                NOW,
                "Markus Vance",
                "RAMP_SUPERVISOR",
                null,
                HorseConditionStatus.GOOD,
                HandoverOutcome.SUCCESSFUL,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.recordIntermediateByDriver(
                LEG_ID_1, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Driver is not actively assigned");
    }

    /** Intermediate endpoint rejects final delivery handover type. */
    @Test
    public void intermediateHandoverRejectsFinalReceiverType() {
        var actor = new CurrentActor(101L, "DRIVER", null, DRIVER_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var assignment = new TripAssignmentRow(11L, LEG_ID_1, DRIVER_STAFF_ID,
                "DRIVER", "CONFIRMED", NOW.minusHours(4), NOW.plusHours(4));
        when(legRepository.findDriverAssignment(LEG_ID_1, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(assignment));

        var request = new IntermediateHandoverCreateRequest(
                HandoverType.FINAL_RECEIVER,
                NOW,
                "Jean-Pierre Dubois",
                null,
                null,
                HorseConditionStatus.EXCELLENT,
                HandoverOutcome.SUCCESSFUL,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.recordIntermediateByDriver(
                LEG_ID_1, request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("Expected intermediate handover type");
    }

    /** Final delivery completes leg, plan and moves order to DELIVERED. */
    @Test
    public void finalDeliveryCompletesLegPlanAndOrderWhenPrerequisitesPass() {
        var actor = new CurrentActor(101L, "DRIVER", null, DRIVER_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var assignment = new TripAssignmentRow(12L, LEG_ID_2, DRIVER_STAFF_ID,
                "DRIVER", "CONFIRMED", NOW.minusHours(4), NOW.plusHours(4));
        when(legRepository.findDriverAssignment(LEG_ID_2, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(assignment));

        var leg = new LegRow(LEG_ID_2, PLAN_ID, 2, "ROAD", "Airport",
                "Deauville Stables", BigDecimal.valueOf(200), NOW.minusHours(4),
                NOW, NOW.minusHours(4), null, "IN_TRANSIT", null, null);
        when(legRepository.findLegById(LEG_ID_2)).thenReturn(Optional.of(leg));

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                PLAN_ID, 201L);
        when(legRepository.findOrderByLegId(LEG_ID_2))
                .thenReturn(Optional.of(order));

        when(handoverRepository.isLastLegOfPlan(LEG_ID_2, PLAN_ID))
                .thenReturn(true);

        var receiver = new OrderReceiverRow("Jean-Pierre Dubois",
                "+33612345678", "jp@dubois.fr", "OWNER");
        when(handoverRepository.findOrderReceiver(ORDER_ID))
                .thenReturn(Optional.of(receiver));

        when(complianceService.quarantineReleasePassed(ORDER_ID))
                .thenReturn(true);

        when(handoverRepository.insert(any(TripHandover.class)))
                .thenReturn(HANDOVER_ID);

        var request = new FinalDeliveryHandoverCreateRequest(
                NOW,
                "Jean-Pierre Dubois",
                "OWNER",
                "sig/dubois.png",
                HorseConditionStatus.EXCELLENT,
                HandoverOutcome.SUCCESSFUL,
                null,
                "photo/delivery.jpg",
                "Ecurie Dubois, Box 14",
                null
        );

        OperationalCompletionReceipt receipt = service
                .recordFinalDeliveryByDriver(LEG_ID_2, request);

        assertThat(receipt.handoverId()).isEqualTo(HANDOVER_ID);
        assertThat(receipt.orderStatus()).isEqualTo("DELIVERED");
        assertThat(receipt.planStatus()).isEqualTo("COMPLETED");
        assertThat(receipt.quarantineReleaseVerified()).isTrue();

        verify(handoverRepository).completeLeg(eq(LEG_ID_2), eq(NOW));
        verify(handoverRepository).completePlan(eq(PLAN_ID));
        verify(handoverRepository).deliverOrder(eq(ORDER_ID), any());
    }

    /** Final delivery blocked when quarantine release has not passed. */
    @Test
    public void finalDeliveryBlockedWhenQuarantineReleaseFails() {
        var actor = new CurrentActor(101L, "DRIVER", null, DRIVER_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var assignment = new TripAssignmentRow(12L, LEG_ID_2, DRIVER_STAFF_ID,
                "DRIVER", "CONFIRMED", NOW.minusHours(4), NOW.plusHours(4));
        when(legRepository.findDriverAssignment(LEG_ID_2, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(assignment));

        var leg = new LegRow(LEG_ID_2, PLAN_ID, 2, "ROAD", "Airport",
                "Deauville Stables", BigDecimal.valueOf(200), NOW.minusHours(4),
                NOW, NOW.minusHours(4), null, "IN_TRANSIT", null, null);
        when(legRepository.findLegById(LEG_ID_2)).thenReturn(Optional.of(leg));

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                PLAN_ID, 201L);
        when(legRepository.findOrderByLegId(LEG_ID_2))
                .thenReturn(Optional.of(order));

        when(handoverRepository.isLastLegOfPlan(LEG_ID_2, PLAN_ID))
                .thenReturn(true);

        var receiver = new OrderReceiverRow("Jean-Pierre Dubois",
                "+33612345678", "jp@dubois.fr", "OWNER");
        when(handoverRepository.findOrderReceiver(ORDER_ID))
                .thenReturn(Optional.of(receiver));

        when(complianceService.quarantineReleasePassed(ORDER_ID))
                .thenReturn(false);

        var request = new FinalDeliveryHandoverCreateRequest(
                NOW,
                "Jean-Pierre Dubois",
                "OWNER",
                "sig/dubois.png",
                HorseConditionStatus.EXCELLENT,
                HandoverOutcome.SUCCESSFUL,
                null,
                "photo/delivery.jpg",
                "Ecurie Dubois, Box 14",
                null
        );

        assertThatThrownBy(() -> service.recordFinalDeliveryByDriver(
                LEG_ID_2, request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("QUARANTINE_RELEASE");

        verify(handoverRepository, never()).completePlan(any(Long.class));
        verify(handoverRepository, never()).deliverOrder(any(Long.class),
                any(OffsetDateTime.class));
    }

    /** Final delivery blocked when recipient name mismatches without proxy. */
    @Test
    public void finalDeliveryBlockedWhenReceiverMismatchedWithoutProxy() {
        var actor = new CurrentActor(101L, "DRIVER", null, DRIVER_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var assignment = new TripAssignmentRow(12L, LEG_ID_2, DRIVER_STAFF_ID,
                "DRIVER", "CONFIRMED", NOW.minusHours(4), NOW.plusHours(4));
        when(legRepository.findDriverAssignment(LEG_ID_2, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(assignment));

        var leg = new LegRow(LEG_ID_2, PLAN_ID, 2, "ROAD", "Airport",
                "Deauville Stables", BigDecimal.valueOf(200), NOW.minusHours(4),
                NOW, NOW.minusHours(4), null, "IN_TRANSIT", null, null);
        when(legRepository.findLegById(LEG_ID_2)).thenReturn(Optional.of(leg));

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                PLAN_ID, 201L);
        when(legRepository.findOrderByLegId(LEG_ID_2))
                .thenReturn(Optional.of(order));

        when(handoverRepository.isLastLegOfPlan(LEG_ID_2, PLAN_ID))
                .thenReturn(true);

        var receiver = new OrderReceiverRow("Jean-Pierre Dubois",
                "+33612345678", "jp@dubois.fr", "OWNER");
        when(handoverRepository.findOrderReceiver(ORDER_ID))
                .thenReturn(Optional.of(receiver));

        when(complianceService.quarantineReleasePassed(ORDER_ID))
                .thenReturn(true);

        var request = new FinalDeliveryHandoverCreateRequest(
                NOW,
                "Unknown Stranger",
                null,
                "sig/stranger.png",
                HorseConditionStatus.EXCELLENT,
                HandoverOutcome.SUCCESSFUL,
                null,
                "photo/delivery.jpg",
                "Ecurie Dubois, Box 14",
                null
        );

        assertThatThrownBy(() -> service.recordFinalDeliveryByDriver(
                LEG_ID_2, request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("does not match authorized consignee");
    }

    /** Final delivery allowed when authorized proxy authority is given. */
    @Test
    public void finalDeliveryAllowedWhenProxyAuthorityRecognized() {
        var actor = new CurrentActor(101L, "DRIVER", null, DRIVER_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var assignment = new TripAssignmentRow(12L, LEG_ID_2, DRIVER_STAFF_ID,
                "DRIVER", "CONFIRMED", NOW.minusHours(4), NOW.plusHours(4));
        when(legRepository.findDriverAssignment(LEG_ID_2, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(assignment));

        var leg = new LegRow(LEG_ID_2, PLAN_ID, 2, "ROAD", "Airport",
                "Deauville Stables", BigDecimal.valueOf(200), NOW.minusHours(4),
                NOW, NOW.minusHours(4), null, "IN_TRANSIT", null, null);
        when(legRepository.findLegById(LEG_ID_2)).thenReturn(Optional.of(leg));

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                PLAN_ID, 201L);
        when(legRepository.findOrderByLegId(LEG_ID_2))
                .thenReturn(Optional.of(order));

        when(handoverRepository.isLastLegOfPlan(LEG_ID_2, PLAN_ID))
                .thenReturn(true);

        var receiver = new OrderReceiverRow("Jean-Pierre Dubois",
                "+33612345678", "jp@dubois.fr", "OWNER");
        when(handoverRepository.findOrderReceiver(ORDER_ID))
                .thenReturn(Optional.of(receiver));

        when(complianceService.quarantineReleasePassed(ORDER_ID))
                .thenReturn(true);

        when(handoverRepository.insert(any(TripHandover.class)))
                .thenReturn(HANDOVER_ID);

        var request = new FinalDeliveryHandoverCreateRequest(
                NOW,
                "Marc Lefevre",
                "STABLE_MANAGER",
                "sig/lefevre.png",
                HorseConditionStatus.GOOD,
                HandoverOutcome.SUCCESSFUL,
                null,
                "photo/delivery.jpg",
                "Ecurie Dubois, Box 14",
                null
        );

        OperationalCompletionReceipt receipt = service
                .recordFinalDeliveryByDriver(LEG_ID_2, request);

        assertThat(receipt.orderStatus()).isEqualTo("DELIVERED");
        assertThat(receipt.receivedByName()).isEqualTo("Marc Lefevre");
    }

    /** Final delivery cannot be executed on non-final leg. */
    @Test
    public void finalDeliveryRejectedIfNotLastLeg() {
        var actor = new CurrentActor(101L, "DRIVER", null, DRIVER_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var assignment = new TripAssignmentRow(11L, LEG_ID_1, DRIVER_STAFF_ID,
                "DRIVER", "CONFIRMED", NOW.minusHours(4), NOW.plusHours(4));
        when(legRepository.findDriverAssignment(LEG_ID_1, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(assignment));

        var leg = new LegRow(LEG_ID_1, PLAN_ID, 1, "ROAD", "Origin", "Hub",
                BigDecimal.valueOf(80), NOW.minusHours(4), NOW,
                NOW.minusHours(4), null, "IN_TRANSIT", null, null);
        when(legRepository.findLegById(LEG_ID_1)).thenReturn(Optional.of(leg));

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                PLAN_ID, 201L);
        when(legRepository.findOrderByLegId(LEG_ID_1))
                .thenReturn(Optional.of(order));

        when(handoverRepository.isLastLegOfPlan(LEG_ID_1, PLAN_ID))
                .thenReturn(false);

        var request = new FinalDeliveryHandoverCreateRequest(
                NOW,
                "Jean-Pierre Dubois",
                "OWNER",
                "sig/dubois.png",
                HorseConditionStatus.EXCELLENT,
                HandoverOutcome.SUCCESSFUL,
                null,
                "photo/delivery.jpg",
                "Destination",
                null
        );

        assertThatThrownBy(() -> service.recordFinalDeliveryByDriver(
                LEG_ID_1, request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("final destination leg");
    }

    /** Customer submits confirmation feedback on delivery. */
    @Test
    public void customerCanSubmitDeliveryFeedbackAndDecision() {
        var actor = new CurrentActor(105L, "CUSTOMER", CUSTOMER_ID, null);
        when(actorService.current()).thenReturn(actor);

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "DELIVERED",
                PLAN_ID, 201L);
        when(legRepository.findOrderByOrderId(ORDER_ID))
                .thenReturn(Optional.of(order));

        var h = new TripHandover();
        h.setHandoverId(HANDOVER_ID);
        h.setLegId(LEG_ID_2);
        h.setHandoverType(HandoverType.FINAL_RECEIVER.name());
        h.setHandoverTime(NOW);
        h.setReceivedByName("Jean-Pierre Dubois");
        h.setHorseConditionStatus("EXCELLENT");
        h.setOutcome("SUCCESSFUL");
        when(handoverRepository.findById(HANDOVER_ID))
                .thenReturn(Optional.of(h));
        when(legRepository.findOrderByLegId(LEG_ID_2))
                .thenReturn(Optional.of(order));

        var request = new CustomerHandoverDecisionRequest(
                HandoverCustomerDecision.ACCEPTED,
                "Horse arrived in peak health."
        );

        CustomerHandoverResponse response = service.recordCustomerDecision(
                ORDER_ID, HANDOVER_ID, request);

        assertThat(response.customerDecision()).isEqualTo("ACCEPTED");
        assertThat(response.customerFeedback())
                .isEqualTo("Horse arrived in peak health.");

        verify(handoverRepository).updateCustomerDecision(
                eq(HANDOVER_ID), eq("ACCEPTED"),
                eq("Horse arrived in peak health."), eq(NOW), eq(CUSTOMER_ID));
    }
}
