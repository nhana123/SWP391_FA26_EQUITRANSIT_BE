/*
 * HandoverHttpAuthorizationTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Integration test verifying Spring Security chain for F14 routes.
 */
package com.equitransit.backend.handover;

import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.handover.ActualCostRepository;
import com.equitransit.backend.repository.handover.TripHandoverRepository;
import com.equitransit.backend.repository.handover.TripHandoverRepository.PlanRow;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests HTTP authorization, roles & security filter chain for F14. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SuppressWarnings("null")
public class HandoverHttpAuthorizationTest {

    private static final String DRIVER_INTERMEDIATE =
            "/api/driver/legs/901/intermediate-handover";
    private static final String DRIVER_FINAL =
            "/api/driver/legs/901/final-delivery";
    private static final String DRIVER_HANDOVERS =
            "/api/driver/legs/901/handovers";
    private static final String COORD_HANDOVERS =
            "/api/coordinator/legs/901/handovers";
    private static final String COORD_ACTUAL_COSTS =
            "/api/coordinator/plans/801/actual-costs";
    private static final String MANAGER_PROFITABILITY =
            "/api/manager/plans/801/profitability";
    private static final String CUSTOMER_HANDOVERS =
            "/api/customer/transport-orders/101/handovers";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LegExecutionRepository legs;
    @MockitoBean
    private TripHandoverRepository handovers;
    @MockitoBean
    private ActualCostRepository costs;
    @MockitoBean
    private CurrentActorService actors;

    /** Mock entity setup before each test. */
    @BeforeEach
    public void setup() {
        when(legs.findLegById(901L)).thenReturn(Optional.of(
                new LegExecutionRepository.LegRow(901L, 801L, 1, "ROAD",
                        "A", "B", null, null, null, null, null,
                        "IN_TRANSIT", null, null)));
        when(legs.findOrderByLegId(901L)).thenReturn(Optional.of(
                new LegExecutionRepository.OrderScopeRow(101L, 201L,
                        "IN_TRANSIT", 801L, 1L)));
        when(legs.findOrderByOrderId(101L)).thenReturn(Optional.of(
                new LegExecutionRepository.OrderScopeRow(101L, 201L,
                        "IN_TRANSIT", 801L, 1L)));
    }

    /** Unauthenticated calls return 401 Unauthorized. */
    @Test
    public void unauthenticatedRequestsReturn401() throws Exception {
        mvc.perform(post(DRIVER_INTERMEDIATE).contentType("application/json")
                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(DRIVER_FINAL).contentType("application/json")
                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(DRIVER_HANDOVERS))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(COORD_HANDOVERS))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(COORD_ACTUAL_COSTS))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(MANAGER_PROFITABILITY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(CUSTOMER_HANDOVERS))
                .andExpect(status().isUnauthorized());
    }

    /** Customer role cannot access driver, coordinator or manager routes. */
    @Test
    public void customerForbiddenOnStaffRoutes() throws Exception {
        var customerAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        mvc.perform(post(DRIVER_INTERMEDIATE).with(customerAuth)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(COORD_HANDOVERS).with(customerAuth))
                .andExpect(status().isForbidden());
        mvc.perform(get(COORD_ACTUAL_COSTS).with(customerAuth))
                .andExpect(status().isForbidden());
        mvc.perform(get(MANAGER_PROFITABILITY).with(customerAuth))
                .andExpect(status().isForbidden());
    }

    /** Driver role cannot access manager or customer routes. */
    @Test
    public void driverForbiddenOnManagerAndCustomerRoutes() throws Exception {
        var driverAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_DRIVER"));

        mvc.perform(get(MANAGER_PROFITABILITY).with(driverAuth))
                .andExpect(status().isForbidden());
        mvc.perform(get(CUSTOMER_HANDOVERS).with(driverAuth))
                .andExpect(status().isForbidden());
    }

    /** Coordinator role is authorized on actual costs. */
    @Test
    public void coordinatorAuthorizedOnActualCostEndpoints() throws Exception {
        var coordAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_COORDINATOR"));

        when(actors.current()).thenReturn(new CurrentActor(
                12L, "COORDINATOR", null, 51L));
        when(handovers.findPlan(801L)).thenReturn(Optional.of(
                new PlanRow(801L, 101L, "ACTIVE", 1)));
        when(costs.findByPlanId(801L)).thenReturn(List.of());

        mvc.perform(get(COORD_ACTUAL_COSTS).with(coordAuth))
                .andExpect(status().isOk());
    }

    /** Customer role is authorized on customer handovers. */
    @Test
    public void customerAuthorizedOnCustomerHandoverEndpoint()
            throws Exception {
        var customerAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        when(actors.current()).thenReturn(new CurrentActor(
                13L, "CUSTOMER", 201L, null));
        when(handovers.findByOrderId(101L)).thenReturn(List.of());

        mvc.perform(get(CUSTOMER_HANDOVERS).with(customerAuth))
                .andExpect(status().isOk());
    }

    /** Manager role is authorized on profitability analysis. */
    @Test
    public void managerAuthorizedOnProfitabilityEndpoint() throws Exception {
        var managerAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_MANAGER"));

        when(actors.current()).thenReturn(new CurrentActor(
                14L, "MANAGER", null, 52L));
        when(handovers.findPlan(801L)).thenReturn(Optional.of(
                new PlanRow(801L, 101L, "ACTIVE", 1)));
        when(costs.findOrderTotalQuotedAmount(101L))
                .thenReturn(BigDecimal.valueOf(10000));
        when(costs.findTotalActualCost(801L))
                .thenReturn(BigDecimal.valueOf(7000));
        when(costs.countByPlanId(801L)).thenReturn(2);

        mvc.perform(get(MANAGER_PROFITABILITY).with(managerAuth))
                .andExpect(status().isOk());
    }
}
