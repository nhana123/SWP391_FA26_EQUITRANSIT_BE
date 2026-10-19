/*
 * ActualCostAndProfitabilityTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Verification of actual supplier costs & profitability analysis.
 */
package com.equitransit.backend.handover;

import com.equitransit.backend.dto.handover.CostCode;
import com.equitransit.backend.dto.handover.request.ActualCostCreateRequest;
import com.equitransit.backend.dto.handover.response.ActualCostResponse;
import com.equitransit.backend.dto.handover.response.CustomerHandoverResponse;
import com.equitransit.backend.dto.handover.response.PlanProfitabilityResponse;
import com.equitransit.backend.entity.handover.ActualCost;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.handover.ActualCostRepository;
import com.equitransit.backend.repository.handover.TripHandoverRepository;
import com.equitransit.backend.repository.handover.TripHandoverRepository.PlanRow;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.handover.ActualCostTrackingService;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit & scenario test suite for Actual Cost and Profitability analysis.
 */
@ExtendWith(MockitoExtension.class)
public class ActualCostAndProfitabilityTest {

    private static final long ORDER_ID = 101L;
    private static final long PLAN_ID = 311L;
    private static final long LEG_ID = 401L;
    private static final long COST_ID = 801L;
    private static final long COORD_STAFF_ID = 103L;
    private static final long MANAGER_STAFF_ID = 104L;

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 18, 16, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private ActualCostRepository costRepository;

    @Mock
    private TripHandoverRepository handoverRepository;

    @Mock
    private LegExecutionRepository legRepository;

    @Mock
    private CurrentActorService actorService;

    private ActualCostTrackingService service;
    private Clock fixedClock;

    /** Sets up test fixture with mocked dependencies. */
    @BeforeEach
    public void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-18T16:00:00Z"),
                ZoneOffset.UTC);
        service = new ActualCostTrackingService(costRepository,
                handoverRepository, legRepository, actorService, fixedClock);
    }

    /** Coordinator records actual supplier expenditure allocated to a leg. */
    @Test
    public void coordinatorCanRecordActualCostOnPlan() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var plan = new PlanRow(PLAN_ID, ORDER_ID, "ACTIVE", 1);
        when(handoverRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));

        when(costRepository.legBelongsToPlan(LEG_ID, PLAN_ID)).thenReturn(true);
        when(costRepository.insert(any(ActualCost.class))).thenReturn(COST_ID);
        when(legRepository.findStaffName(COORD_STAFF_ID))
                .thenReturn("Claire Martin");

        var request = new ActualCostCreateRequest(
                LEG_ID,
                CostCode.AIR_FREIGHT,
                BigDecimal.valueOf(1850.00),
                "EUR",
                NOW,
                "Air France Cargo airway bill #618-91234567",
                "invoices/af-618.pdf"
        );

        ActualCostResponse response = service
                .recordActualCost(PLAN_ID, request);

        assertThat(response.costId()).isEqualTo(COST_ID);
        assertThat(response.planId()).isEqualTo(PLAN_ID);
        assertThat(response.legId()).isEqualTo(LEG_ID);
        assertThat(response.costCode()).isEqualTo("AIR_FREIGHT");
        assertThat(response.amount()).isEqualByComparingTo("1850.00");
        assertThat(response.recordedByName()).isEqualTo("Claire Martin");

        verify(costRepository).insert(any(ActualCost.class));
    }

    /** Recording actual cost rejects negative amount. */
    @Test
    public void actualCostRejectsNegativeAmount() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var plan = new PlanRow(PLAN_ID, ORDER_ID, "ACTIVE", 1);
        when(handoverRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));

        var request = new ActualCostCreateRequest(
                null,
                CostCode.FUEL,
                BigDecimal.valueOf(-50.00),
                "EUR",
                NOW,
                "Invalid credit",
                null
        );

        assertThatThrownBy(() -> service.recordActualCost(PLAN_ID, request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("cannot be negative")
                .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                        .isEqualTo("NEGATIVE_COST_AMOUNT"));
    }

    /** Enforces composite FK: leg must belong to plan. */
    @Test
    public void actualCostRejectsLegNotBelongingToPlan() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var plan = new PlanRow(PLAN_ID, ORDER_ID, "ACTIVE", 1);
        when(handoverRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));

        when(costRepository.legBelongsToPlan(999L, PLAN_ID)).thenReturn(false);

        var request = new ActualCostCreateRequest(
                999L,
                CostCode.ROAD_TOLL,
                BigDecimal.valueOf(45.00),
                "EUR",
                NOW,
                "Toll tag",
                null
        );

        assertThatThrownBy(() -> service.recordActualCost(PLAN_ID, request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("does not belong to plan")
                .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                        .isEqualTo("LEG_NOT_IN_PLAN"));
    }

    /** Plan-wide actual costs without specific leg allocation are permitted. */
    @Test
    public void actualCostWithoutLegIdAllowedForPlanWideCost() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var plan = new PlanRow(PLAN_ID, ORDER_ID, "ACTIVE", 1);
        when(handoverRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));

        when(costRepository.insert(any(ActualCost.class))).thenReturn(COST_ID);

        var request = new ActualCostCreateRequest(
                null,
                CostCode.QUARANTINE_LODGING,
                BigDecimal.valueOf(2500.00),
                "EUR",
                NOW,
                "National Quarantine Station 14 days",
                "invoices/quarantine.pdf"
        );

        ActualCostResponse response = service
                .recordActualCost(PLAN_ID, request);

        assertThat(response.legId()).isNull();
        assertThat(response.costCode()).isEqualTo("QUARANTINE_LODGING");
    }

    /** Manager can view executive plan profitability report. */
    @Test
    public void managerCanViewPlanProfitabilitySummary() {
        var actor = new CurrentActor(104L, "MANAGER", null, MANAGER_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        var plan = new PlanRow(PLAN_ID, ORDER_ID, "ACTIVE", 1);
        when(handoverRepository.findPlan(PLAN_ID))
                .thenReturn(Optional.of(plan));

        when(costRepository.findOrderTotalQuotedAmount(ORDER_ID))
                .thenReturn(BigDecimal.valueOf(14500.00));
        when(costRepository.findTotalActualCost(PLAN_ID))
                .thenReturn(BigDecimal.valueOf(10300.00));
        when(costRepository.countByPlanId(PLAN_ID)).thenReturn(4);

        PlanProfitabilityResponse report = service
                .getPlanProfitability(PLAN_ID);

        assertThat(report.totalQuotedAmount())
                .isEqualByComparingTo("14500.00");
        assertThat(report.totalActualCost())
                .isEqualByComparingTo("10300.00");
        assertThat(report.grossProfit())
                .isEqualByComparingTo("4200.00");
        assertThat(report.profitMarginPercent())
                .isEqualByComparingTo("28.97");
        assertThat(report.costCount()).isEqualTo(4);
    }

    /** Non-manager roles cannot access profitability analysis. */
    @Test
    public void nonManagerCannotViewPlanProfitability() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);
        when(actorService.current()).thenReturn(actor);

        assertThatThrownBy(() -> service.getPlanProfitability(PLAN_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Manager role required");
    }

    /** Verifies cost privacy isolation: leaks no costs. */
    @Test
    public void customerViewsNeverExposeActualCosts() {
        List<String> fieldNames = Arrays.stream(
                CustomerHandoverResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fieldNames).noneMatch(name ->
                name.toLowerCase().contains("cost")
                || name.toLowerCase().contains("amount")
                || name.toLowerCase().contains("price")
                || name.toLowerCase().contains("profit")
                || name.toLowerCase().contains("margin")
                || name.toLowerCase().contains("staff")
        );
    }
}
