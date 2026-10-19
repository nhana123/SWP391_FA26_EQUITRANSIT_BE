/*
 * IncidentHttpAuthorizationTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Integration test verifying Spring Security chain for F13 routes.
 */
package com.equitransit.backend.incident;

import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.incident.IncidentReportRepository;
import com.equitransit.backend.repository.incident.OperationalAdjustmentRepository;
import com.equitransit.backend.repository.incident.OperationalAdjustmentRepository.PlanRow;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
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

/** Tests HTTP authorization, roles & security filter chain for F13. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class IncidentHttpAuthorizationTest {

    private static final String DRIVER_INCIDENT =
            "/api/driver/legs/901/incidents";
    private static final String COORD_INCIDENT =
            "/api/coordinator/legs/901/incidents";
    private static final String COORD_ADJUSTMENT =
            "/api/coordinator/plans/801/adjustments";
    private static final String MANAGER_APPROVE =
            "/api/manager/adjustments/601/approve";
    private static final String CUSTOMER_ADJUSTMENTS =
            "/api/customer/transport-orders/101/adjustments";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LegExecutionRepository legs;
    @MockitoBean
    private IncidentReportRepository incidents;
    @MockitoBean
    private OperationalAdjustmentRepository adjustments;
    @MockitoBean
    private CurrentActorService actors;

    /** Mock entity setup before each test. */
    @BeforeEach
    public void setup() {
        when(legs.findLegById(901L)).thenReturn(Optional.of(
                new LegExecutionRepository.LegRow(901L, 801L, 1, "ROAD",
                        "A", "B", null, null, null, null, null,
                        "IN_PROGRESS", null, null)));
        when(legs.findOrderByOrderId(101L)).thenReturn(Optional.of(
                new LegExecutionRepository.OrderScopeRow(101L, 201L,
                        "IN_TRANSIT", 801L, 1L)));
    }

    /** Unauthenticated calls return 401 Unauthorized. */
    @Test
    public void unauthenticatedRequestsReturn401() throws Exception {
        mvc.perform(post(DRIVER_INCIDENT).contentType("application/json")
                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(COORD_INCIDENT).contentType("application/json")
                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(COORD_ADJUSTMENT))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(MANAGER_APPROVE).contentType("application/json")
                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(CUSTOMER_ADJUSTMENTS))
                .andExpect(status().isUnauthorized());
    }

    /** Customer role cannot access driver, coordinator or manager routes. */
    @Test
    public void customerForbiddenOnStaffRoutes() throws Exception {
        var customerAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        mvc.perform(post(DRIVER_INCIDENT).with(customerAuth)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(COORD_INCIDENT).with(customerAuth)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(MANAGER_APPROVE).with(customerAuth)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    /** Driver role cannot access manager or customer routes. */
    @Test
    public void driverForbiddenOnManagerAndCustomerRoutes() throws Exception {
        var driverAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_DRIVER"));

        mvc.perform(post(MANAGER_APPROVE).with(driverAuth)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(CUSTOMER_ADJUSTMENTS).with(driverAuth))
                .andExpect(status().isForbidden());
    }

    /** Coordinator role is authorized on coordinator endpoints. */
    @Test
    public void coordinatorAuthorizedOnCoordinatorEndpoints() throws Exception {
        var coordAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_COORDINATOR"));

        when(actors.current()).thenReturn(new CurrentActor(
                12L, "COORDINATOR", null, 51L));
        when(adjustments.findPlan(801L)).thenReturn(Optional.of(
                new PlanRow(801L, 101L, 1L, 1, "ACTIVE", null, null, null)));
        when(adjustments.findByPlanId(801L)).thenReturn(List.of());

        mvc.perform(get(COORD_ADJUSTMENT).with(coordAuth))
                .andExpect(status().isOk());
    }

    /** Customer role is authorized on customer adjustments. */
    @Test
    public void customerAuthorizedOnCustomerAdjustmentEndpoint()
            throws Exception {
        var customerAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        when(actors.current()).thenReturn(new CurrentActor(
                13L, "CUSTOMER", 201L, null));
        when(adjustments.findByOrderId(101L)).thenReturn(List.of());

        mvc.perform(get(CUSTOMER_ADJUSTMENTS).with(customerAuth))
                .andExpect(status().isOk());
    }
}
