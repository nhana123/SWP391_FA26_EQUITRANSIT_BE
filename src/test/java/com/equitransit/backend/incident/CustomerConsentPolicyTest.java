/*
 * CustomerConsentPolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Business rule & commercial consent test suite for adjustments.
 */
package com.equitransit.backend.incident;

import com.equitransit.backend.dto.incident.AdjustmentApprovalStatus;
import com.equitransit.backend.dto.incident.CustomerDecision;
import com.equitransit.backend.dto.incident.request.AdjustmentApprovalRequest;
import com.equitransit.backend.dto.incident.request.CustomerAdjustmentDecisionRequest;
import com.equitransit.backend.dto.incident.request.OperationalAdjustmentCreateRequest;
import com.equitransit.backend.dto.incident.response.CustomerAdjustmentResponse;
import com.equitransit.backend.dto.incident.response.OperationalAdjustmentResponse;
import com.equitransit.backend.dto.incident.response.PlanEvolutionReceipt;
import com.equitransit.backend.entity.incident.OperationalAdjustment;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.execution.LegExecutionRepository.OrderScopeRow;
import com.equitransit.backend.repository.execution.LegExecutionRepository.StaffProfileRow;
import com.equitransit.backend.repository.incident.IncidentReportRepository;
import com.equitransit.backend.repository.incident.OperationalAdjustmentRepository;
import com.equitransit.backend.repository.incident.OperationalAdjustmentRepository.PlanRow;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.incident.OperationalAdjustmentService;
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
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validates commercial consent boundary, manager approvals and plan evolution.
 */
@ExtendWith(MockitoExtension.class)
public class CustomerConsentPolicyTest {

    private static final long ORDER_ID = 101L;
    private static final long CUSTOMER_ID = 51L;
    private static final long PLAN_ID = 311L;
    private static final long NEW_PLAN_ID = 312L;
    private static final long ADJUSTMENT_ID = 601L;
    private static final long COORD_STAFF_ID = 3L;
    private static final long MANAGER_STAFF_ID = 2L;

    @Mock
    private OperationalAdjustmentRepository adjustmentRepository;
    @Mock
    private IncidentReportRepository incidentRepository;
    @Mock
    private LegExecutionRepository legRepository;
    @Mock
    private CurrentActorService actorService;

    private ObjectMapper mapper;
    private Clock fixedClock;
    private OperationalAdjustmentService service;

    /** Sets up mock dependencies before each test. */
    @BeforeEach
    public void setUp() {
        mapper = new ObjectMapper();
        fixedClock = Clock.fixed(Instant.parse("2026-10-18T15:00:00Z"),
                ZoneOffset.UTC);
        service = new OperationalAdjustmentService(adjustmentRepository,
                incidentRepository, legRepository, actorService, mapper,
                fixedClock);
    }

    /** Proposing adjustment with cost requires customer consent (PENDING). */
    @Test
    public void proposalWithAdditionalCostRequiresCustomerConsent() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var plan = new PlanRow(PLAN_ID, ORDER_ID, 201L, 1, "ACTIVE", 401L,
                OffsetDateTime.now(fixedClock), null);

        when(adjustmentRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));
        when(legRepository.findStaffProfile(COORD_STAFF_ID)).thenReturn(
                Optional.of(new StaffProfileRow(COORD_STAFF_ID, 103L,
                        "Claire Martin", "+336111111", "LIC-COORD")));
        when(adjustmentRepository.save(any(OperationalAdjustment.class)))
                .thenReturn(ADJUSTMENT_ID);

        var request = new OperationalAdjustmentCreateRequest(
                null, null,
                "{\"reason\":\"Reroute around road blockage\","
                        + "\"summary\":\"Detour via Lyon rest station\","
                        + "\"additionalKm\":120}",
                new BigDecimal("250.00"),
                "Detour via Lyon rest station due to road closure."
        );

        OperationalAdjustmentResponse response = service.proposeAdjustment(
                PLAN_ID, request);

        assertThat(response.adjustmentId()).isEqualTo(ADJUSTMENT_ID);
        assertThat(response.planId()).isEqualTo(PLAN_ID);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.additionalCostEstimate())
                .isEqualByComparingTo("250.00");
        assertThat(response.customerDecision()).isEqualTo("PENDING");
        assertThat(response.approvalStatus()).isEqualTo("CUSTOMER_PENDING");
        verify(adjustmentRepository).save(any(OperationalAdjustment.class));
    }

    /** Proposing adjustment with zero cost is automatically ACCEPTED. */
    @Test
    public void proposalWithZeroCostIsPreAccepted() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var plan = new PlanRow(PLAN_ID, ORDER_ID, 201L, 1, "ACTIVE", 401L,
                OffsetDateTime.now(fixedClock), null);

        when(adjustmentRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));
        when(legRepository.findStaffProfile(COORD_STAFF_ID)).thenReturn(
                Optional.of(new StaffProfileRow(COORD_STAFF_ID, 103L,
                        "Claire Martin", "+336111111", "LIC-COORD")));
        when(adjustmentRepository.save(any(OperationalAdjustment.class)))
                .thenReturn(ADJUSTMENT_ID);

        var request = new OperationalAdjustmentCreateRequest(
                null, null,
                "{\"reason\":\"Rest stop extension by 30 mins\"}",
                BigDecimal.ZERO,
                "Minor rest stop extension; no additional charge."
        );

        OperationalAdjustmentResponse response = service.proposeAdjustment(
                PLAN_ID, request);

        assertThat(response.customerDecision()).isEqualTo("ACCEPTED");
        assertThat(response.approvalStatus()).isEqualTo("PROPOSED");
    }

    /** Proposed changes with invalid JSON is rejected with 400 Bad Request. */
    @Test
    public void invalidJsonProposedChangesThrowsBadRequest() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var plan = new PlanRow(PLAN_ID, ORDER_ID, 201L, 1, "ACTIVE", 401L,
                null, null);

        when(adjustmentRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));

        var request = new OperationalAdjustmentCreateRequest(
                null, null, "not-json", BigDecimal.ZERO, null);

        assertThatThrownBy(() -> service.proposeAdjustment(PLAN_ID, request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("ProposedChanges must be valid JSON");
    }

    /** Customer accepts adjustment additional charge. */
    @Test
    public void customerAcceptsAdjustmentCost() {
        var actor = new CurrentActor(101L, "CUSTOMER", CUSTOMER_ID, null);

        when(actorService.current()).thenReturn(actor);

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                PLAN_ID, 201L);

        when(legRepository.findOrderByOrderId(ORDER_ID))
                .thenReturn(Optional.of(order));

        var adj = new OperationalAdjustment();

        adj.setAdjustmentId(ADJUSTMENT_ID);
        adj.setPlanId(PLAN_ID);
        adj.setAdditionalCostEstimate(new BigDecimal("250.00"));
        adj.setCustomerDecision(CustomerDecision.PENDING.name());
        adj.setApprovalStatus(AdjustmentApprovalStatus.CUSTOMER_PENDING.name());
        adj.setProposedChanges("{\"summary\":\"Detour via Lyon\"}");

        when(adjustmentRepository.findById(ADJUSTMENT_ID))
                .thenReturn(Optional.of(adj));

        var plan = new PlanRow(PLAN_ID, ORDER_ID, 201L, 1, "ACTIVE", null,
                null, null);

        when(adjustmentRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));

        var request = new CustomerAdjustmentDecisionRequest(
                CustomerDecision.ACCEPTED,
                "Agreed to extra fee to ensure horse welfare."
        );

        CustomerAdjustmentResponse response = service.recordCustomerDecision(
                ORDER_ID, ADJUSTMENT_ID, request);

        assertThat(response.customerDecision()).isEqualTo("ACCEPTED");
        assertThat(response.approvalStatus()).isEqualTo("PROPOSED");
        assertThat(response.customerResponseNotes())
                .contains("Agreed to extra fee");
        verify(adjustmentRepository).recordCustomerDecision(eq(ADJUSTMENT_ID),
                eq("ACCEPTED"), eq(CUSTOMER_ID), any(OffsetDateTime.class),
                eq("Agreed to extra fee to ensure horse welfare."),
                eq("PROPOSED"));
    }

    /** Customer rejects adjustment additional charge. */
    @Test
    public void customerRejectsAdjustmentCost() {
        var actor = new CurrentActor(101L, "CUSTOMER", CUSTOMER_ID, null);

        when(actorService.current()).thenReturn(actor);

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                PLAN_ID, 201L);

        when(legRepository.findOrderByOrderId(ORDER_ID))
                .thenReturn(Optional.of(order));

        var adj = new OperationalAdjustment();

        adj.setAdjustmentId(ADJUSTMENT_ID);
        adj.setPlanId(PLAN_ID);
        adj.setAdditionalCostEstimate(new BigDecimal("500.00"));
        adj.setCustomerDecision(CustomerDecision.PENDING.name());
        adj.setApprovalStatus(AdjustmentApprovalStatus.CUSTOMER_PENDING.name());
        adj.setProposedChanges("{\"summary\":\"Charter replacement\"}");

        when(adjustmentRepository.findById(ADJUSTMENT_ID))
                .thenReturn(Optional.of(adj));

        var plan = new PlanRow(PLAN_ID, ORDER_ID, 201L, 1, "ACTIVE", null,
                null, null);

        when(adjustmentRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));

        var request = new CustomerAdjustmentDecisionRequest(
                CustomerDecision.REJECTED,
                "Cannot accept additional charter cost."
        );

        CustomerAdjustmentResponse response = service.recordCustomerDecision(
                ORDER_ID, ADJUSTMENT_ID, request);

        assertThat(response.customerDecision()).isEqualTo("REJECTED");
        assertThat(response.approvalStatus()).isEqualTo("REJECTED");
        verify(adjustmentRepository).recordCustomerDecision(eq(ADJUSTMENT_ID),
                eq("REJECTED"), eq(CUSTOMER_ID), any(OffsetDateTime.class),
                eq("Cannot accept additional charter cost."),
                eq("REJECTED"));
    }

    /** Customer cannot decide twice on already decided adjustment. */
    @Test
    public void cannotDecideAlreadyDecidedAdjustment() {
        var actor = new CurrentActor(101L, "CUSTOMER", CUSTOMER_ID, null);

        when(actorService.current()).thenReturn(actor);

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                PLAN_ID, 201L);

        when(legRepository.findOrderByOrderId(ORDER_ID))
                .thenReturn(Optional.of(order));

        var adj = new OperationalAdjustment();

        adj.setAdjustmentId(ADJUSTMENT_ID);
        adj.setPlanId(PLAN_ID);
        adj.setCustomerDecision(CustomerDecision.ACCEPTED.name());

        when(adjustmentRepository.findById(ADJUSTMENT_ID))
                .thenReturn(Optional.of(adj));

        var plan = new PlanRow(PLAN_ID, ORDER_ID, 201L, 1, "ACTIVE", null,
                null, null);

        when(adjustmentRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));

        var request = new CustomerAdjustmentDecisionRequest(
                CustomerDecision.REJECTED, "Change mind.");

        assertThatThrownBy(() -> service.recordCustomerDecision(ORDER_ID,
                ADJUSTMENT_ID, request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("already ACCEPTED");
    }

    /** Customer cannot view or decide on another customer's order. */
    @Test
    public void customerCannotAccessAnotherCustomerOrder() {
        var actor = new CurrentActor(101L, "CUSTOMER", 999L, null);

        when(actorService.current()).thenReturn(actor);

        var order = new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                PLAN_ID, 201L);

        when(legRepository.findOrderByOrderId(ORDER_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.listCustomerAdjustments(ORDER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("forbidden");
    }

    /** Manager cannot approve adjustment if customer still PENDING. */
    @Test
    public void managerCannotApprovePendingCustomerConsent() {
        var actor = new CurrentActor(104L, "MANAGER", null, MANAGER_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var adj = new OperationalAdjustment();

        adj.setAdjustmentId(ADJUSTMENT_ID);
        adj.setPlanId(PLAN_ID);
        adj.setAdditionalCostEstimate(new BigDecimal("250.00"));
        adj.setCustomerDecision(CustomerDecision.PENDING.name());
        adj.setApprovalStatus(AdjustmentApprovalStatus.CUSTOMER_PENDING.name());

        when(adjustmentRepository.findById(ADJUSTMENT_ID))
                .thenReturn(Optional.of(adj));

        var request = new AdjustmentApprovalRequest("Approved.");

        assertThatThrownBy(() -> service.approveAdjustment(ADJUSTMENT_ID,
                request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("Customer consent is required");
    }

    /** Manager cannot approve adjustment if customer REJECTED. */
    @Test
    public void managerCannotApproveRejectedCustomerAdjustment() {
        var actor = new CurrentActor(104L, "MANAGER", null, MANAGER_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var adj = new OperationalAdjustment();

        adj.setAdjustmentId(ADJUSTMENT_ID);
        adj.setPlanId(PLAN_ID);
        adj.setAdditionalCostEstimate(new BigDecimal("250.00"));
        adj.setCustomerDecision(CustomerDecision.REJECTED.name());
        adj.setApprovalStatus(AdjustmentApprovalStatus.REJECTED.name());

        when(adjustmentRepository.findById(ADJUSTMENT_ID))
                .thenReturn(Optional.of(adj));

        var request = new AdjustmentApprovalRequest("Force approve.");

        assertThatThrownBy(() -> service.approveAdjustment(ADJUSTMENT_ID,
                request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("Adjustment was rejected.");
    }

    /** Manager approves adjustment with ACCEPTED customer consent -> BR-11. */
    @Test
    public void managerApprovalTriggersPlanEvolution() {
        var actor = new CurrentActor(104L, "MANAGER", null, MANAGER_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var adj = new OperationalAdjustment();

        adj.setAdjustmentId(ADJUSTMENT_ID);
        adj.setPlanId(PLAN_ID);
        adj.setChangeRequestId(31L);
        adj.setAdditionalCostEstimate(new BigDecimal("250.00"));
        adj.setCustomerDecision(CustomerDecision.ACCEPTED.name());
        adj.setApprovalStatus(AdjustmentApprovalStatus.PROPOSED.name());

        when(adjustmentRepository.findById(ADJUSTMENT_ID))
                .thenReturn(Optional.of(adj));

        var oldPlan = new PlanRow(PLAN_ID, ORDER_ID, 201L, 1, "ACTIVE", 401L,
                OffsetDateTime.now(fixedClock), null);

        when(adjustmentRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(oldPlan));
        when(adjustmentRepository.insertEvolvedPlan(eq(ORDER_ID), eq(201L),
                eq(MANAGER_STAFF_ID), eq(2), eq(401L),
                any(OffsetDateTime.class), any(), any(OffsetDateTime.class)))
                .thenReturn(NEW_PLAN_ID);
        when(adjustmentRepository.cloneLegsToNewPlan(PLAN_ID, NEW_PLAN_ID))
                .thenReturn(3);

        var request = new AdjustmentApprovalRequest("Approved reroute.");
        PlanEvolutionReceipt receipt = service.approveAdjustment(ADJUSTMENT_ID,
                request);

        assertThat(receipt.adjustmentId()).isEqualTo(ADJUSTMENT_ID);
        assertThat(receipt.orderId()).isEqualTo(ORDER_ID);
        assertThat(receipt.previousPlanId()).isEqualTo(PLAN_ID);
        assertThat(receipt.newPlanId()).isEqualTo(NEW_PLAN_ID);
        assertThat(receipt.newVersionNo()).isEqualTo(2);
        assertThat(receipt.evolvedLegCount()).isEqualTo(3);
        assertThat(receipt.approvalStatus()).isEqualTo("APPLIED");

        verify(adjustmentRepository).supersedePlan(PLAN_ID);
        verify(adjustmentRepository).updateOrderCurrentPlan(ORDER_ID,
                NEW_PLAN_ID);
        verify(adjustmentRepository).updateOrderChangeRequest(eq(31L),
                eq("APPLIED"), eq(MANAGER_STAFF_ID),
                any(OffsetDateTime.class), any(String.class));
        verify(adjustmentRepository).recordManagerDecision(eq(ADJUSTMENT_ID),
                eq("APPLIED"), eq(MANAGER_STAFF_ID),
                any(OffsetDateTime.class));
    }

    /** Manager can reject an adjustment proposal. */
    @Test
    public void managerCanRejectAdjustment() {
        var actor = new CurrentActor(104L, "MANAGER", null, MANAGER_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var adj = new OperationalAdjustment();

        adj.setAdjustmentId(ADJUSTMENT_ID);
        adj.setPlanId(PLAN_ID);
        adj.setChangeRequestId(31L);
        adj.setAdditionalCostEstimate(BigDecimal.ZERO);
        adj.setCustomerDecision(CustomerDecision.ACCEPTED.name());
        adj.setApprovalStatus(AdjustmentApprovalStatus.PROPOSED.name());

        when(adjustmentRepository.findById(ADJUSTMENT_ID))
                .thenReturn(Optional.of(adj));

        var plan = new PlanRow(PLAN_ID, ORDER_ID, 201L, 1, "ACTIVE", null,
                null, null);

        when(adjustmentRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));

        var request = new AdjustmentApprovalRequest("Alternative found.");
        OperationalAdjustmentResponse response = service.rejectAdjustment(
                ADJUSTMENT_ID, request);

        assertThat(response.approvalStatus()).isEqualTo("REJECTED");
        verify(adjustmentRepository).recordManagerDecision(eq(ADJUSTMENT_ID),
                eq("REJECTED"), eq(MANAGER_STAFF_ID),
                any(OffsetDateTime.class));
        verify(adjustmentRepository).updateOrderChangeRequest(eq(31L),
                eq("REJECTED"), eq(MANAGER_STAFF_ID),
                any(OffsetDateTime.class), eq("Alternative found."));
    }
}
