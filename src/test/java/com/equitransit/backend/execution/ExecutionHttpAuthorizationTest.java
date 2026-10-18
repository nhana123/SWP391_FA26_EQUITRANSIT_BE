/*
 * ExecutionHttpAuthorizationTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Integration test verifying Spring Security chain for F12 routes.
 */
package com.equitransit.backend.execution;

import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.execution.LegMilestoneLogRepository;
import com.equitransit.backend.repository.execution.HorseWelfareLogRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
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

/** Tests HTTP authorization, roles & security filter chain for F12. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ExecutionHttpAuthorizationTest {

    private static final String DRIVER_MILESTONES =
            "/api/driver/legs/901/milestones";
    private static final String DRIVER_WELFARE =
            "/api/driver/legs/901/welfare";
    private static final String COORDINATOR_AIR =
            "/api/coordinator/legs/901/air-milestones";
    private static final String CUSTOMER_TRACKING =
            "/api/customer/transport-orders/101/tracking";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LegExecutionRepository legs;
    @MockitoBean
    private LegMilestoneLogRepository milestones;
    @MockitoBean
    private HorseWelfareLogRepository welfare;
    @MockitoBean
    private CurrentActorService actors;

    /** Mock actor setup before each test. */
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
        mvc.perform(get(DRIVER_MILESTONES))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(DRIVER_WELFARE).contentType("application/json")
                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(COORDINATOR_AIR).contentType("application/json")
                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(CUSTOMER_TRACKING))
                .andExpect(status().isUnauthorized());
    }

    /** Customer role cannot access driver or coordinator routes. */
    @Test
    public void customerForbiddenOnDriverAndCoordinatorRoutes()
            throws Exception {
        var customerAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        mvc.perform(get(DRIVER_MILESTONES).with(customerAuth))
                .andExpect(status().isForbidden());
        mvc.perform(post(DRIVER_WELFARE).with(customerAuth)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(COORDINATOR_AIR).with(customerAuth)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    /** Driver role is authorized on driver endpoints. */
    @Test
    public void driverAuthorizedOnDriverEndpoints() throws Exception {
        var driverAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_DRIVER"));
        when(actors.current()).thenReturn(new CurrentActor(
                11L, "DRIVER", null, 41L));

        mvc.perform(get(DRIVER_MILESTONES).with(driverAuth))
                .andExpect(status().isOk());
        mvc.perform(get(DRIVER_WELFARE).with(driverAuth))
                .andExpect(status().isOk());
    }

    /** Coordinator role is authorized on coordinator endpoints. */
    @Test
    public void coordinatorAuthorizedOnCoordinatorEndpoints()
            throws Exception {
        var coordAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_COORDINATOR"));
        when(actors.current()).thenReturn(new CurrentActor(
                12L, "COORDINATOR", null, 51L));

        mvc.perform(get("/api/coordinator/legs/901/milestones")
                .with(coordAuth))
                .andExpect(status().isOk());
    }

    /** Customer role is authorized on tracking endpoint. */
    @Test
    public void customerAuthorizedOnTrackingEndpoint() throws Exception {
        var customerAuth = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        when(actors.current()).thenReturn(new CurrentActor(
                13L, "CUSTOMER", 201L, null));

        mvc.perform(get(CUSTOMER_TRACKING).with(customerAuth))
                .andExpect(status().isOk());
    }
}
