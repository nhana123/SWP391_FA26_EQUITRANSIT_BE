package com.equitransit.backend.departure;

import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.departure.DepartureRepository;
import com.equitransit.backend.repository.plan.PlanRepository;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.
        SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;

/** Real security chain and F11 service with isolated repository boundaries. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DepartureHttpAuthorizationTest {

    private static final String ROOT =
            "/api/transport-orders/101/departure";
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DepartureRepository departures;
    @MockitoBean
    private AssessmentRepository cases;
    @MockitoBean
    private PlanRepository plans;
    @MockitoBean
    private ResourceRepository resources;
    @MockitoBean
    private CurrentActorService actors;

    /** Let authorized roles reach scoped lookup without connecting to SQL. */
    @BeforeEach
    public void setup() {
        when(departures.ready()).thenReturn(true);
        when(plans.ready()).thenReturn(true);
        when(resources.ready()).thenReturn(true);
        when(cases.order(101, true)).thenReturn(Optional.of(new CaseOrder(
                101, "SYNTHETIC", "APPROVED", "0000000000000001", 201L)));
    }

    /** The generic CUSTOMER order matcher must not swallow staff F11 access. */
    @Test
    public void intendedRolesReachScopedLookup() throws Exception {
        for (String role : new String[] {"MANAGER", "COORDINATOR", "DRIVER",
                "CUSTOMER"}) {
            when(actors.current()).thenReturn(new CurrentActor(41L, role,
                    "CUSTOMER".equals(role) ? 51L : null,
                    "CUSTOMER".equals(role) ? null : 31L));
            mvc.perform(get(ROOT).with(jwt().authorities(
                    new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isNotFound());
        }
    }

    /** F11 privileges do not extend to Specialist. */
    @Test
    public void specialistCannotReadDeparture() throws Exception {
        mvc.perform(get(ROOT).with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_SPECIALIST"))))
                .andExpect(status().isForbidden());
    }

    /** Customer visibility never permits gate mutation. */
    @Test
    public void customerCannotEvaluateGates() throws Exception {
        when(actors.current()).thenReturn(
                new CurrentActor(41L, "CUSTOMER", 51L, null));
        mvc.perform(post(ROOT + "/gates/evaluate")
                .contentType("application/json")
                .content(com.equitransit.backend.dto.departure.
                        DepartureExamples.EVALUATE)
                .with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }
    /** Multipart upload and owner download preserve case scope. */
    @Test
    public void realPhotoUploadAndOwnerDownload() throws Exception {
        var now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        when(actors.current()).thenReturn(
                new CurrentActor(41L, "DRIVER", null, 31L));
        when(departures.assignedDriverOnLeg(311, 401, 31)).thenReturn(true);
        when(cases.activeStaff(31, "DRIVER")).thenReturn(true);
        when(departures.orderScope(101)).thenReturn(Optional.of(
                new com.equitransit.backend.repository.departure.
                        DepartureRepository.OrderScopeRow(
                                101, 51, 201L, 311L)));
        var plan = org.mockito.Mockito.mock(
                com.equitransit.backend.repository.departure.
                        DepartureRepository.PlanGateRow.class);
        when(plan.planId()).thenReturn(311L);
        when(plan.revisionId()).thenReturn(201L);
        when(plan.planStatus()).thenReturn(com.equitransit.backend.dto.plan.
                PlanModels.PlanState.CONFIRMED);
        when(departures.plan(101, 311, true))
                .thenReturn(Optional.of(plan));
        when(departures.firstLeg(311)).thenReturn(Optional.of(
                new com.equitransit.backend.repository.departure.
                        DepartureRepository.FirstLegRow(401, 311, 1,
                        com.equitransit.backend.dto.plan.PlanModels.
                                PlanLegMode.ROAD,
                        com.equitransit.backend.dto.plan.PlanModels.
                                LegState.CONFIRMED,
                        now, now.plusHours(2), null)));
        byte[] image = DepartureCompletionTest.image();
        var upload = mvc.perform(org.springframework.test.web.servlet.request.
                MockMvcRequestBuilders.multipart(ROOT + "/evidence")
                .file(new org.springframework.mock.web.MockMultipartFile(
                        "file", "pickup.png", "image/png", image))
                .param("planId", "311").param("legId", "401")
                .with(jwt().authorities(new SimpleGrantedAuthority(
                        "ROLE_DRIVER"))))
                .andExpect(status().isOk()).andReturn();
        var descriptor = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(upload.getResponse().getContentAsString());
        var receipt = org.mockito.Mockito.mock(
                com.equitransit.backend.dto.departure.DepartureModels.
                        HorseReceiptView.class);
        when(receipt.receiptId()).thenReturn(701L);
        when(receipt.legId()).thenReturn(401L);
        when(receipt.evidenceFileKey()).thenReturn(
                descriptor.path("fileKey").asText());
        when(departures.receipts(311)).thenReturn(java.util.List.of(receipt));
        when(actors.current()).thenReturn(
                new CurrentActor(43L, "CUSTOMER", 51L, null));
        var download = mvc.perform(get(ROOT + "/horse-receipts/701/file")
                .with(jwt().authorities(new SimpleGrantedAuthority(
                        "ROLE_CUSTOMER"))))
                .andExpect(status().isOk()).andReturn();
        org.assertj.core.api.Assertions.assertThat(download.getResponse()
                .getContentAsByteArray()).isEqualTo(image);
        when(actors.current()).thenReturn(
                new CurrentActor(44L, "CUSTOMER", 52L, null));
        mvc.perform(get(ROOT + "/horse-receipts/701/file")
                .with(jwt().authorities(new SimpleGrantedAuthority(
                        "ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());
    }
}


