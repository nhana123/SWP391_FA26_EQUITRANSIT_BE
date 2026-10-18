/*
 * ResourceAvailabilityServiceTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Complete scoped resource and fit workflows.
 */
package com.equitransit.backend.resource;

import com.equitransit.backend.dto.order.request.OrderDraftRequest;
import com.equitransit.backend.dto.order.request.OrderServicePreferences;
import com.equitransit.backend.dto.resource.ResourceModels.AssetState;
import com.equitransit.backend.dto.resource.ResourceModels.AvailabilityState;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceBlocker;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceMode;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceWindow;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAvailabilityRequest;
import com.equitransit.backend.dto.horse.response.HorseResponse;
import com.equitransit.backend.dto.order.response.OrderSnapshot;
import com.equitransit.backend.entity.order.AirStallPreference;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.pricing.FitPolicyRecord;
import com.equitransit.backend.entity.order.OrderRevisionRecord;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.resource.ResourceAvailabilityService;
import com.equitransit.backend.service.pricing.StallFitEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises real fit/probe orchestration with explicit boundary adapters. */
class ResourceAvailabilityServiceTest {
    private final OffsetDateTime now =
            OffsetDateTime.parse("2027-01-01T08:00:00Z");
    private final OffsetDateTime start = now.plusDays(7);
    private final ResourceRepository resources = mock(ResourceRepository.class);
    private final AssessmentRepository cases = mock(AssessmentRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final OrderJsonCodec codec = mock(OrderJsonCodec.class);
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules();
    private ResourceAvailabilityService service;
    private OrderDraftRequest draft;

    @BeforeEach
    void setup() throws Exception {
        service = new ResourceAvailabilityService(resources, cases, orders,
                actors, codec, new StallFitEvaluator(), Clock.fixed(
                        now.toInstant(), ZoneOffset.UTC));
        draft = new OrderDraftRequest();
        var selection = new OrderServicePreferences();
        selection.setAirStall(AirStallPreference.SHARED_ONE);
        draft.setServices(selection);
        when(actors.current()).thenReturn(new CurrentActor(1L,
                "COORDINATOR", null, 7L));
        when(cases.activeStaff(7, "COORDINATOR")).thenReturn(true);
        when(cases.assigned(10, 7, CaseRole.COORDINATOR)).thenReturn(true);
        when(cases.order(10, true)).thenReturn(Optional.of(new CaseOrder(
                10, "SYNTHETIC", "APPROVED", "0000000000000001", 20L)));
        when(orders.revision(10, 20)).thenReturn(Optional.of(
                new OrderRevisionRecord(20, 10, 1, "snapshot", null, now)));
        when(resources.ready()).thenReturn(true);
        when(resources.activeSupplier(any())).thenReturn(true);
        when(resources.scheduleBlockers(any(), anyLong(), any(), any(),
                anyInt(), anyLong())).thenReturn(List.of());
        when(resources.policies(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(policy("700", "1")));
        when(resources.get(ResourceKind.STALL, 11, ResourceStallView.class))
                .thenReturn(stall(new ResourceWindow(null, null,
                        now.plusYears(2), 30, "INTERNAL NOTE")));
        horse(true, "550", now.minusDays(1));
    }

    @Test
    void rejectsUnassignedCoordinatorBeforeReadingCatalogue() {
        when(cases.assigned(10, 7, CaseRole.COORDINATOR)).thenReturn(false);
        assertThatThrownBy(() -> service.inspect(ResourceKind.STALL, 11,
                request())).isInstanceOf(OrderException.class)
                .hasMessageContaining("assigned current case");
        verify(resources, never()).lock();
    }

    @Test
    void staleRevisionAndWrongModeCannotProbeResources() {
        var stale = new ResourceAvailabilityRequest(10, 21, ResourceMode.AIR,
                start, start.plusHours(2), null, null);
        assertThatThrownBy(() -> service.inspect(ResourceKind.STALL, 11,
                stale)).hasMessageContaining("current revision");
        assertThatThrownBy(() -> service.inspect(ResourceKind.VEHICLE, 11,
                request())).hasMessageContaining("ROAD vehicles");
    }

    @Test
    void existingLegCannotUseAnotherCarriersFitPolicy() {
        when(resources.legBelongs(30, 10, 20)).thenReturn(true);
        var changed = new ResourceAvailabilityRequest(10, 20,
                ResourceMode.AIR, start, start.plusHours(2), 99L, 30L);
        assertThatThrownBy(() -> service.inspect(ResourceKind.STALL, 11,
                changed)).hasMessageContaining(
                        "existing leg's mode and carrier");
    }

    @Test
    void completeFitIsAvailableAndUsesJourneyStart() {
        var result = service.inspect(ResourceKind.STALL, 11, request());
        assertThat(result.status()).isEqualTo(AvailabilityState.AVAILABLE);
        assertThat(result.carrierConfirmed()).isFalse();
        verify(resources).policies(11, null, BigDecimal.ONE, now, start,
                start.plusHours(2));
    }

    @Test
    void falseCompletenessFutureAndNegativeMeasurementsStayPending()
            throws Exception {
        for (var horse : List.of(
                new Object[] {false, "550", now.minusDays(1)},
                new Object[] {true, "550", now.plusDays(1)},
                new Object[] {true, "-1", now.minusDays(1)})) {
            horse((boolean) horse[0], (String) horse[1],
                    (OffsetDateTime) horse[2]);
            var result = service.inspect(ResourceKind.STALL, 11, request());
            assertThat(result.status()).isEqualTo(AvailabilityState.PENDING);
            assertThat(result.blockers()).contains(
                    ResourceBlocker.MEASUREMENTS_PENDING);
        }
    }

    @Test
    void oneSlotRejectsHeavyHorseWhileWideAndPrivateRemainIndependent() {
        for (var option : List.of(AirStallPreference.SHARED_ONE,
                AirStallPreference.WIDE, AirStallPreference.PRIVATE)) {
            draft.getServices().setAirStall(option);
            var slots = StallFitEvaluator.slots(option);
            when(resources.policies(eq(11L), any(), eq(slots), any(), any(),
                    any())).thenReturn(List.of(policy(
                            option == AirStallPreference.SHARED_ONE
                                    ? "480" : "700", slots.toPlainString())));
            var result = service.inspect(ResourceKind.STALL, 11, request());
            assertThat(result.status()).isEqualTo(
                    option == AirStallPreference.SHARED_ONE
                            ? AvailabilityState.UNAVAILABLE
                            : AvailabilityState.AVAILABLE);
        }
    }

    @Test
    void absentAndAmbiguousPoliciesStayPending() {
        when(resources.policies(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        assertThat(service.inspect(ResourceKind.STALL, 11, request())
                .blockers()).contains(ResourceBlocker.FIT_POLICY_PENDING);
        when(resources.policies(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(policy("700", "1"), policy("800", "1")));
        assertThat(service.inspect(ResourceKind.STALL, 11, request())
                .blockers()).contains(ResourceBlocker.FIT_POLICY_AMBIGUOUS);
    }

    @Test
    void maintenanceExpiryAndOverlapCannotBeAvailable() {
        when(resources.get(ResourceKind.STALL, 11, ResourceStallView.class))
                .thenReturn(stall(new ResourceWindow(start, null,
                        start.plusHours(2), 30, null)));
        when(resources.scheduleBlockers(any(), anyLong(), any(), any(),
                anyInt(), anyLong())).thenReturn(List.of(
                        ResourceBlocker.OVERLAP));
        var result = service.inspect(ResourceKind.STALL, 11, request());
        assertThat(result.status()).isEqualTo(AvailabilityState.UNAVAILABLE);
        assertThat(result.blockers()).contains(ResourceBlocker.MAINTENANCE,
                ResourceBlocker.DOCUMENT_EXPIRED, ResourceBlocker.OVERLAP);
    }

    @Test
    void missingBufferRemainsPendingAndSkipsUnsafeDateArithmetic() {
        when(resources.get(ResourceKind.STALL, 11, ResourceStallView.class))
                .thenReturn(stall(new ResourceWindow(null, null,
                        now.plusYears(2), null, null)));
        assertThat(service.inspect(ResourceKind.STALL, 11, request()).status())
                .isEqualTo(AvailabilityState.PENDING);
        verify(resources, never()).scheduleBlockers(any(), anyLong(), any(),
                any(), anyInt(), anyLong());
    }

    @Test
    void discoveryIsPagedAndDoesNotExposeInternalNotes() throws Exception {
        when(resources.count(ResourceKind.STALL)).thenReturn(3L);
        when(resources.candidateIds(ResourceKind.STALL, 0, 2))
                .thenReturn(List.of(11L));
        var result = service.search(ResourceKind.STALL, request(), 0, 2);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.items().getFirst().resourceCode()).isEqualTo("S-11");
        assertThat(mapper.writeValueAsString(result))
                .doesNotContain("INTERNAL NOTE", "ownerSupplierId");
        verify(resources).lock();
    }

    @Test
    void strictConsumerRejectsVersionDriftAndOversizedPages() {
        assertThatThrownBy(() -> service.requireAvailable(ResourceKind.STALL,
                11, request(), "0000000000000000"))
                .hasMessageContaining("evidence/version");
        assertThatThrownBy(() -> service.search(ResourceKind.STALL,
                request(), 0, 51)).hasMessageContaining("size1..50");
    }

    @Test
    void strictConsumerRequiresAndJoinsTheCallersTransaction() {
        var source = new org.springframework.jdbc.datasource.
                DriverManagerDataSource("jdbc:h2:mem:f09tx_"
                        + UUID.randomUUID(), "sa", "");
        var manager = new org.springframework.jdbc.datasource.
                DataSourceTransactionManager(source);
        var advice = new org.springframework.transaction.interceptor.
                TransactionInterceptor();
        advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new org.springframework.
                transaction.annotation.AnnotationTransactionAttributeSource());
        advice.afterPropertiesSet();
        var proxy = new org.springframework.aop.framework.ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(advice);
        var boundary = (ResourceAvailabilityService) proxy.getProxy();
        assertThatThrownBy(() -> boundary.requireAvailable(ResourceKind.STALL,
                11, request(), "0000000000000001"))
                .isInstanceOf(org.springframework.transaction.
                        IllegalTransactionStateException.class);
        var transaction = new org.springframework.transaction.support.
                TransactionTemplate(manager);
        assertThat(transaction.execute(state -> boundary.requireAvailable(
                ResourceKind.STALL, 11, request(), "0000000000000001"))
                .status()).isEqualTo(AvailabilityState.AVAILABLE);
    }

    private ResourceAvailabilityRequest request() {
        return new ResourceAvailabilityRequest(10, 20, ResourceMode.AIR,
                start, start.plusHours(2), null, null);
    }

    private ResourceStallView stall(ResourceWindow window) {
        return new ResourceStallView(11, "0000000000000001",
                new ResourceStallData("S-11", new BigDecimal("3"),
                        new BigDecimal("240"), new BigDecimal("240"),
                        new BigDecimal("1800"), AssetState.ACTIVE, null,
                        window));
    }

    private FitPolicyRecord policy(String weight, String slots) {
        return new FitPolicyRecord(1, "SYNTHETIC", 1, new BigDecimal(slots),
                new BigDecimal(weight), new BigDecimal("190"),
                new BigDecimal("90"));
    }

    private void horse(boolean complete, String weight, OffsetDateTime measured)
            throws Exception {
        var horse = mapper.readValue("{\"horseId\":12,\"weightKg\":" + weight
                + ",\"heightCm\":170,\"bodyWidthCm\":70,\"measuredAt\":\""
                + measured + "\",\"measurementsComplete\":" + complete + "}",
                HorseResponse.class);
        when(codec.snapshot("snapshot")).thenReturn(new OrderSnapshot(1,
                UUID.randomUUID(), "SUBMIT", "synthetic", null, 10, 1,
                now.minusDays(1), draft, horse, List.of()));
    }
}
