/*
 * ResourceHttpAuthorizationTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Complete scoped resource and fit workflows.
 */
package com.equitransit.backend.resource;

import com.equitransit.backend.dto.order.request.OrderDraftRequest;
import com.equitransit.backend.dto.resource.ResourceModels.AssetState;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAssignedVehicle;
import com.equitransit.backend.dto.order.response.OrderSnapshot;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.order.OrderRevisionRecord;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.
        SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;

/** Uses real MVC and method security with isolated boundary data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceHttpAuthorizationTest {
    private static final String SEARCH = "/api/resources/STALL/availability";
    private static final String BODY = "{\"orderId\":10,\"revisionId\":20,"
            + "\"mode\":\"AIR\",\"start\":\"2027-01-08T08:00:00Z\","
            + "\"end\":\"2027-01-08T10:00:00Z\"}";
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ResourceRepository resources;
    @MockitoBean
    private AssessmentRepository cases;
    @MockitoBean
    private OrderRepository orders;
    @MockitoBean
    private CurrentActorService actors;
    @MockitoBean
    private OrderJsonCodec codec;

    @BeforeEach
    void setup() {
        var now = OffsetDateTime.parse("2027-01-01T00:00:00Z");
        when(resources.ready()).thenReturn(true);
        when(cases.activeStaff(anyLong(), any())).thenReturn(true);
        when(cases.order(10, true)).thenReturn(Optional.of(new CaseOrder(10,
                "SYNTHETIC", "APPROVED", "0000000000000001", 20L)));
        when(cases.assigned(10, 3, CaseRole.COORDINATOR)).thenReturn(true);
        when(orders.revision(10, 20)).thenReturn(Optional.of(
                new OrderRevisionRecord(20, 10, 1, "snapshot", null, now)));
        when(codec.snapshot("snapshot")).thenReturn(new OrderSnapshot(1,
                UUID.randomUUID(), "SUBMIT", "synthetic", null, 10, 1,
                now, new OrderDraftRequest(), null, List.of()));
    }

    @Test
    void anonymousAndCustomerCannotDiscoverInternalResources()
            throws Exception {
        mvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andExpect(status().isUnauthorized());
        role("CUSTOMER");
        mvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON)
                .content(BODY).with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorDiscoveryChecksAssignmentAndPageBounds() throws Exception {
        role("COORDINATOR");
        var authority = new SimpleGrantedAuthority("ROLE_COORDINATOR");
        mvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON)
                .content(BODY).with(jwt().authorities(authority)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalPages").value(0));
        when(cases.assigned(10, 3, CaseRole.COORDINATOR)).thenReturn(false);
        mvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON)
                .content(BODY).with(jwt().authorities(authority)))
                .andExpect(status().isNotFound());
        mvc.perform(post(SEARCH + "?size=51")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)
                .with(jwt().authorities(authority)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void specialistReadsSourcesButCannotUseManagerWrites() throws Exception {
        role("SPECIALIST");
        var authority = new SimpleGrantedAuthority("ROLE_SPECIALIST");
        mvc.perform(get("/api/resources/fit-policies")
                .with(jwt().authorities(authority)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/manager/resources/fit-policies")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
                .with(jwt().authorities(authority)))
                .andExpect(status().isForbidden());
    }

    @Test
    void driverGetsOnlyAssignedTechnicalData() throws Exception {
        role("DRIVER");
        when(resources.assignedVehicle(anyLong(), anyLong(), any()))
                .thenReturn(Optional.of(new ResourceAssignedVehicle(11, 2,
                        "VEH-2", "TEST-2", 2, "VENTILATED",
                        AssetState.ACTIVE)));
        var authority = new SimpleGrantedAuthority("ROLE_DRIVER");
        mvc.perform(get("/api/resources/legs/11/vehicle")
                .with(jwt().authorities(authority)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(2))
                .andExpect(jsonPath("$.ownerSupplierId").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist());
        when(resources.assignedVehicle(anyLong(), anyLong(), any()))
                .thenReturn(Optional.empty());
        mvc.perform(get("/api/resources/legs/11/vehicle")
                .with(jwt().authorities(authority)))
                .andExpect(status().isNotFound());
        role("CUSTOMER");
        mvc.perform(get("/api/resources/legs/11/vehicle")
                .with(jwt().authorities(new SimpleGrantedAuthority(
                        "ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    private void role(String role) {
        when(actors.current()).thenReturn(new CurrentActor(1L, role,
                null, 3L));
    }
}
