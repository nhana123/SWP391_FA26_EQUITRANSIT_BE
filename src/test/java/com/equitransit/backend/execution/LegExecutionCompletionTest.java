/*
 * LegExecutionCompletionTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Complete unit & workflow tests for Feature 12 execution & welfare.
 */
package com.equitransit.backend.execution;

import com.equitransit.backend.dto.execution.AirMilestoneType;
import com.equitransit.backend.dto.execution.HealthStatus;
import com.equitransit.backend.dto.execution.RoadMilestoneType;
import com.equitransit.backend.dto.execution.request.AirMilestoneCreateRequest;
import com.equitransit.backend.dto.execution.request.EstimatedArrivalUpdateRequest;
import com.equitransit.backend.dto.execution.request.HorseWelfareLogCorrectionRequest;
import com.equitransit.backend.dto.execution.request.HorseWelfareLogCreateRequest;
import com.equitransit.backend.dto.execution.request.RoadMilestoneCreateRequest;
import com.equitransit.backend.entity.execution.HorseWelfareLog;
import com.equitransit.backend.entity.execution.LegMilestoneLog;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.HorseWelfareLogRepository;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.execution.LegExecutionRepository.LegRow;
import com.equitransit.backend.repository.execution.LegExecutionRepository.OrderScopeRow;
import com.equitransit.backend.repository.execution.LegExecutionRepository.TripAssignmentRow;
import com.equitransit.backend.repository.execution.LegMilestoneLogRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.DomainAccessPolicy;
import com.equitransit.backend.service.execution.CustomerTrackingService;
import com.equitransit.backend.service.execution.HorseWelfareService;
import com.equitransit.backend.service.execution.LegMilestoneService;
import com.equitransit.backend.service.execution.validation.MilestoneSequenceValidator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Business acceptance & workflow validation suite for Feature 12 (F12/BE2). */
@ExtendWith(MockitoExtension.class)
public class LegExecutionCompletionTest {

    private static final long ORDER_ID = 101L;
    private static final long PLAN_ID = 801L;
    private static final long ROAD_LEG_ID = 901L;
    private static final long AIR_LEG_ID = 902L;
    private static final long DRIVER_STAFF_ID = 41L;
    private static final long OTHER_DRIVER_STAFF_ID = 42L;
    private static final long COORDINATOR_STAFF_ID = 51L;
    private static final long CUSTOMER_ID = 201L;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
    private final MilestoneSequenceValidator validator =
            new MilestoneSequenceValidator();

    @Mock
    private LegMilestoneLogRepository milestones;
    @Mock
    private HorseWelfareLogRepository welfare;
    @Mock
    private LegExecutionRepository legs;
    @Mock
    private CurrentActorService actors;
    @Mock
    private DomainAccessPolicy access;

    private LegMilestoneService milestoneService;
    private HorseWelfareService welfareService;
    private CustomerTrackingService trackingService;

    /** Prepares services with mocked dependencies before each test. */
    @BeforeEach
    public void setup() {
        milestoneService = new LegMilestoneService(
                milestones, legs, validator, actors, access, clock);
        welfareService = new HorseWelfareService(
                welfare, legs, actors, clock);
        trackingService = new CustomerTrackingService(
                legs, milestones, welfare, access);
    }

    // -------------------------------------------------------------------------
    // Scenario 1: Happy Path Chặng ROAD
    // -------------------------------------------------------------------------

    /** Assigned driver records DEPARTED, REST_STOP & ARRIVED successfully. */
    @Test
    public void roadHappyPathProgressionAndStatusTransitions() {
        when(actors.current()).thenReturn(new CurrentActor(
                11L, "DRIVER", null, DRIVER_STAFF_ID));
        var leg = createRoadLeg(ROAD_LEG_ID, 1, "CONFIRMED", null, null);
        when(legs.findLegById(ROAD_LEG_ID)).thenReturn(Optional.of(leg));
        when(legs.findDriverAssignment(ROAD_LEG_ID, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(createAssignment(ROAD_LEG_ID,
                        DRIVER_STAFF_ID)));
        when(legs.findOrderByLegId(ROAD_LEG_ID)).thenReturn(Optional.of(
                new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "CONFIRMED",
                        PLAN_ID, 1L)));
        when(milestones.findByLegId(ROAD_LEG_ID))
                .thenReturn(new ArrayList<>());
        when(milestones.save(any())).thenReturn(101L);

        // 1. DEPARTED
        var departReq = new RoadMilestoneCreateRequest(
                RoadMilestoneType.DEPARTED,
                OffsetDateTime.parse("2026-10-18T08:00:00Z"),
                new BigDecimal("21.028511"), new BigDecimal("105.854444"),
                "Departing Hanoi stable", "Hanoi Stable Gate A", null, null);

        var departRes = milestoneService.recordRoadMilestone(
                ROAD_LEG_ID, departReq);
        assertThat(departRes.milestoneType()).isEqualTo("DEPARTED");
        verify(legs).updateLegDeparture(eq(ROAD_LEG_ID), eq("IN_PROGRESS"),
                any());
        verify(legs).updatePlanStatus(eq(PLAN_ID), eq("IN_TRANSIT"));
        verify(legs).updateOrderStatus(eq(ORDER_ID), eq("IN_TRANSIT"));

        // 2. REST_STOP with Welfare check
        var m1 = new LegMilestoneLog();
        m1.setMilestoneId(101L);
        m1.setMilestoneType("DEPARTED");
        m1.setOccurredAt(OffsetDateTime.parse("2026-10-18T08:00:00Z"));
        when(milestones.findByLegId(ROAD_LEG_ID)).thenReturn(List.of(m1));

        var restReq = new RoadMilestoneCreateRequest(
                RoadMilestoneType.REST_STOP,
                OffsetDateTime.parse("2026-10-18T10:00:00Z"),
                new BigDecimal("20.950000"), new BigDecimal("105.900000"),
                "Rest stop on highway", "Highway Rest Area 1", null,
                "evidence.jpg");
        var restRes = milestoneService.recordRoadMilestone(
                ROAD_LEG_ID, restReq);
        assertThat(restRes.milestoneType()).isEqualTo("REST_STOP");

        // Driver records HorseWelfareLog
        when(welfare.save(any())).thenReturn(501L);
        var welfareReq = new HorseWelfareLogCreateRequest(
                OffsetDateTime.parse("2026-10-18T10:05:00Z"),
                HealthStatus.HEALTHY,
                new BigDecimal("37.80"), new BigDecimal("20.00"),
                new BigDecimal("3.00"), "Horse calm, drinking well",
                "Hydration verified", "photo.jpg", null);
        var welfareRes = welfareService.recordWelfareLog(
                ROAD_LEG_ID, welfareReq);
        assertThat(welfareRes.healthStatus()).isEqualTo("HEALTHY");
        assertThat(welfareRes.fever()).isFalse();
        assertThat(welfareRes.emergencyAlert()).isFalse();

        // 3. ARRIVED
        var m2 = new LegMilestoneLog();
        m2.setMilestoneId(102L);
        m2.setMilestoneType("REST_STOP");
        m2.setOccurredAt(OffsetDateTime.parse("2026-10-18T10:00:00Z"));
        when(milestones.findByLegId(ROAD_LEG_ID)).thenReturn(List.of(m1, m2));

        var arrivedReq = new RoadMilestoneCreateRequest(
                RoadMilestoneType.ARRIVED,
                OffsetDateTime.parse("2026-10-18T12:00:00Z"),
                new BigDecimal("21.221200"), new BigDecimal("105.807200"),
                "Arrived at cargo terminal", "Noi Bai Airport Gate B",
                null, null);
        var arrivedRes = milestoneService.recordRoadMilestone(
                ROAD_LEG_ID, arrivedReq);
        assertThat(arrivedRes.milestoneType()).isEqualTo("ARRIVED");
        verify(legs).updateLegArrival(eq(ROAD_LEG_ID), eq("COMPLETED"), any());
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Happy Path Chặng AIR
    // -------------------------------------------------------------------------

    /** Coordinator records air milestones with mandatory partner citations. */
    @Test
    public void airHappyPathSourcedProgression() {
        when(actors.current()).thenReturn(new CurrentActor(
                12L, "COORDINATOR", null, COORDINATOR_STAFF_ID));
        var airClock = Clock.fixed(
                Instant.parse("2026-10-18T20:00:00Z"), ZoneOffset.UTC);
        milestoneService = new LegMilestoneService(
                milestones, legs, validator, actors, access, airClock);

        var airLeg = createAirLeg(AIR_LEG_ID, 2, "SCHEDULED", null, null);
        var roadLeg = createRoadLeg(ROAD_LEG_ID, 1, "COMPLETED",
                OffsetDateTime.parse("2026-10-18T08:00:00Z"),
                OffsetDateTime.parse("2026-10-18T12:00:00Z"));

        when(legs.findLegById(AIR_LEG_ID)).thenReturn(Optional.of(airLeg));
        when(legs.findLegsByPlanId(PLAN_ID)).thenReturn(
                List.of(roadLeg, airLeg));
        when(legs.findOrderByLegId(AIR_LEG_ID)).thenReturn(Optional.of(
                new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                        PLAN_ID, 1L)));
        when(milestones.save(any())).thenReturn(201L);

        // 1. CARGO_TENDERED
        when(milestones.findByLegId(AIR_LEG_ID))
                .thenReturn(new ArrayList<>());
        var tenderReq = new AirMilestoneCreateRequest(
                AirMilestoneType.CARGO_TENDERED,
                OffsetDateTime.parse("2026-10-18T13:00:00Z"),
                "CARRIER_API", "AWB-618-91234567",
                "Cargo Terminal 1", "Cargo tendered to airline",
                new BigDecimal("21.221200"), new BigDecimal("105.807200"),
                "tender.pdf");
        var tenderRes = milestoneService.recordAirMilestone(
                AIR_LEG_ID, tenderReq);
        assertThat(tenderRes.milestoneType()).isEqualTo("CARGO_TENDERED");
        verify(legs).updateLegStatus(AIR_LEG_ID, "IN_PROGRESS");

        // 2. WHEELS_UP
        var mAir1 = new LegMilestoneLog();
        mAir1.setMilestoneId(201L);
        mAir1.setMilestoneType("CARGO_TENDERED");
        mAir1.setOccurredAt(OffsetDateTime.parse("2026-10-18T13:00:00Z"));
        when(milestones.findByLegId(AIR_LEG_ID)).thenReturn(List.of(mAir1));

        var wheelsUpReq = new AirMilestoneCreateRequest(
                AirMilestoneType.WHEELS_UP,
                OffsetDateTime.parse("2026-10-18T14:30:00Z"),
                "AIRPORT_TOWER", "AWB-618-91234567",
                "HAN Runway 11R", "Flight departed",
                new BigDecimal("21.221200"), new BigDecimal("105.807200"),
                null);
        var wheelsUpRes = milestoneService.recordAirMilestone(
                AIR_LEG_ID, wheelsUpReq);
        assertThat(wheelsUpRes.milestoneType()).isEqualTo("WHEELS_UP");
        verify(legs).updateLegDeparture(eq(AIR_LEG_ID), eq("IN_PROGRESS"),
                any());

        // 3. HANDOVER_READY
        var mAir2 = new LegMilestoneLog();
        mAir2.setMilestoneId(202L);
        mAir2.setMilestoneType("WHEELS_UP");
        mAir2.setOccurredAt(OffsetDateTime.parse("2026-10-18T14:30:00Z"));
        when(milestones.findByLegId(AIR_LEG_ID)).thenReturn(
                List.of(mAir1, mAir2));

        var handoverReq = new AirMilestoneCreateRequest(
                AirMilestoneType.HANDOVER_READY,
                OffsetDateTime.parse("2026-10-18T19:00:00Z"),
                "HANDLING_AGENT", "AWB-618-91234567",
                "SIN Changi Cargo Terminal", "Cleared customs and quarantine",
                new BigDecimal("1.364400"), new BigDecimal("103.991500"),
                null);
        var handoverRes = milestoneService.recordAirMilestone(
                AIR_LEG_ID, handoverReq);
        assertThat(handoverRes.milestoneType()).isEqualTo("HANDOVER_READY");
        verify(legs).updateLegArrival(eq(AIR_LEG_ID), eq("COMPLETED"), any());
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Immutability & Welfare Supersedes
    // -------------------------------------------------------------------------

    /** Welfare superseding links prior log and preserves full audit chain. */
    @Test
    public void welfareSupersedingAuditTrail() {
        when(actors.current()).thenReturn(new CurrentActor(
                11L, "DRIVER", null, DRIVER_STAFF_ID));
        var leg = createRoadLeg(ROAD_LEG_ID, 1, "IN_PROGRESS", null, null);
        when(legs.findLegById(ROAD_LEG_ID)).thenReturn(Optional.of(leg));
        when(legs.findDriverAssignment(ROAD_LEG_ID, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(createAssignment(ROAD_LEG_ID,
                        DRIVER_STAFF_ID)));

        // 1. Initial log with mistaken temperature
        var initialEntity = new HorseWelfareLog();
        initialEntity.setWelfareLogId(501L);
        initialEntity.setLegId(ROAD_LEG_ID);
        initialEntity.setRecordedBy(DRIVER_STAFF_ID);
        initialEntity.setCheckTime(
                OffsetDateTime.parse("2026-10-18T08:30:00Z"));
        initialEntity.setHealthStatus("HEALTHY");
        initialEntity.setTemperatureCelsius(new BigDecimal("40.00"));
        when(welfare.findById(501L)).thenReturn(Optional.of(initialEntity));
        when(welfare.save(any())).thenReturn(502L);

        // 2. Correction superseding 501
        var correctionReq = new HorseWelfareLogCorrectionRequest(
                OffsetDateTime.parse("2026-10-18T08:30:00Z"),
                HealthStatus.HEALTHY,
                new BigDecimal("37.80"), new BigDecimal("15.00"),
                new BigDecimal("2.50"), "Calm and responsive",
                "Corrected initial faulty thermometer reading",
                null, null);

        var corrected = welfareService.supersedeWelfareLog(
                ROAD_LEG_ID, 501L, correctionReq);
        assertThat(corrected.welfareLogId()).isEqualTo(502L);
        assertThat(corrected.supersedesLogId()).isEqualTo(501L);
        assertThat(corrected.temperatureCelsius()).isEqualTo(
                new BigDecimal("37.80"));
        assertThat(corrected.fever()).isFalse();

        // 3. Cross-leg superseding is rejected
        var otherLegEntity = new HorseWelfareLog();
        otherLegEntity.setWelfareLogId(601L);
        otherLegEntity.setLegId(999L); // Different leg
        when(welfare.findById(601L)).thenReturn(Optional.of(otherLegEntity));

        assertThatThrownBy(() -> welfareService.supersedeWelfareLog(
                ROAD_LEG_ID, 601L, correctionReq))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("different transport leg");
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Security & RBAC Enforcement
    // -------------------------------------------------------------------------

    /** Unassigned driver and unauthorized roles cannot log milestones. */
    @Test
    public void rbacRestrictionsOnExecution() {
        var leg = createRoadLeg(ROAD_LEG_ID, 1, "IN_PROGRESS", null, null);
        when(legs.findLegById(ROAD_LEG_ID)).thenReturn(Optional.of(leg));

        // Driver 42 is not assigned to ROAD_LEG_ID
        when(actors.current()).thenReturn(new CurrentActor(
                99L, "DRIVER", null, OTHER_DRIVER_STAFF_ID));
        when(legs.findDriverAssignment(ROAD_LEG_ID, OTHER_DRIVER_STAFF_ID))
                .thenReturn(Optional.empty());

        var req = new RoadMilestoneCreateRequest(
                RoadMilestoneType.REST_STOP, null, null, null, null, null,
                null, null);

        assertThatThrownBy(() -> milestoneService.recordRoadMilestone(
                ROAD_LEG_ID, req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Driver is not assigned");

        // Customer cannot call driver road milestone
        when(actors.current()).thenReturn(new CurrentActor(
                100L, "CUSTOMER", CUSTOMER_ID, null));
        assertThatThrownBy(() -> milestoneService.recordRoadMilestone(
                ROAD_LEG_ID, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Customer tracking timeline is redacted of internal notes. */
    @Test
    public void customerTimelineIsRedacted() {
        when(access.isCustomer()).thenReturn(true);
        when(legs.findOrderByOrderId(ORDER_ID)).thenReturn(Optional.of(
                new OrderScopeRow(ORDER_ID, CUSTOMER_ID, "IN_TRANSIT",
                        PLAN_ID, 1L)));
        var leg = createRoadLeg(ROAD_LEG_ID, 1, "IN_PROGRESS",
                OffsetDateTime.parse("2026-10-18T07:00:00Z"), null);
        when(legs.findLegsByPlanId(PLAN_ID)).thenReturn(List.of(leg));

        var internalMilestone = new LegMilestoneLog();
        internalMilestone.setMilestoneId(101L);
        internalMilestone.setMilestoneType("REST_STOP");
        internalMilestone.setOccurredAt(
                OffsetDateTime.parse("2026-10-18T08:30:00Z"));
        internalMilestone.setNotes(
                "Internal driver notes: traffic delay & fuel stop");
        internalMilestone.setSourceReference("CONFIDENTIAL-DISPATCH-999");
        internalMilestone.setLocationDescription("Rest Stop Km 45");
        when(milestones.findByLegId(ROAD_LEG_ID)).thenReturn(
                List.of(internalMilestone));

        var welfareCheck = new HorseWelfareLog();
        welfareCheck.setWelfareLogId(501L);
        welfareCheck.setCheckTime(OffsetDateTime.parse("2026-10-18T08:30:00Z"));
        welfareCheck.setHealthStatus("HEALTHY");
        welfareCheck.setTemperatureCelsius(new BigDecimal("37.80"));
        welfareCheck.setPhotoFileKey("horse-public-photo.jpg");
        when(welfare.findActiveByLegId(ROAD_LEG_ID)).thenReturn(
                List.of(welfareCheck));

        var timeline = trackingService.getTrackingTimeline(ORDER_ID);

        verify(access).requireCustomerOwner(CUSTOMER_ID);
        assertThat(timeline.orderId()).isEqualTo(ORDER_ID);
        assertThat(timeline.legs()).hasSize(1);
        var customerLeg = timeline.legs().getFirst();
        assertThat(customerLeg.milestones()).hasSize(1);
        var pubMilestone = customerLeg.milestones().getFirst();
        assertThat(pubMilestone.milestoneType()).isEqualTo("REST_STOP");
        assertThat(pubMilestone.locationDescription()).isEqualTo(
                "Rest Stop Km 45");

        // Assert welfare check projection contains only public vital summary
        assertThat(customerLeg.welfareChecks()).hasSize(1);
        var pubWelfare = customerLeg.welfareChecks().getFirst();
        assertThat(pubWelfare.healthStatus()).isEqualTo("HEALTHY");
        assertThat(pubWelfare.photoFileKey()).isEqualTo(
                "horse-public-photo.jpg");
    }

    // -------------------------------------------------------------------------
    // Scenario 5: Sequence Anomaly & State Machine Invariants
    // -------------------------------------------------------------------------

    /** Leg cannot progress if previous leg has not completed. */
    @Test
    public void priorLegMustBeCompletedBeforeSubsequentLegCanProgress() {
        when(actors.current()).thenReturn(new CurrentActor(
                12L, "COORDINATOR", null, COORDINATOR_STAFF_ID));
        var leg1Incomplete = createRoadLeg(ROAD_LEG_ID, 1, "IN_PROGRESS",
                OffsetDateTime.parse("2026-10-18T07:00:00Z"), null);
        var leg2Air = createAirLeg(AIR_LEG_ID, 2, "SCHEDULED", null, null);

        when(legs.findLegById(AIR_LEG_ID)).thenReturn(Optional.of(leg2Air));
        when(legs.findLegsByPlanId(PLAN_ID)).thenReturn(
                List.of(leg1Incomplete, leg2Air));

        var req = new AirMilestoneCreateRequest(
                AirMilestoneType.CARGO_TENDERED, null,
                "CARRIER_API", "AWB-123", "Terminal", null, null, null, null);

        assertThatThrownBy(() -> milestoneService.recordAirMilestone(
                AIR_LEG_ID, req))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("must be COMPLETED before progressing");
    }

    /** Fever and emergency indicators are computed accurately. */
    @Test
    public void feverAndEmergencyIndicators() {
        when(actors.current()).thenReturn(new CurrentActor(
                12L, "COORDINATOR", null, COORDINATOR_STAFF_ID));
        var leg = createRoadLeg(ROAD_LEG_ID, 1, "IN_PROGRESS", null, null);
        when(legs.findLegById(ROAD_LEG_ID)).thenReturn(Optional.of(leg));
        when(welfare.save(any())).thenReturn(505L);

        // Fever check: 38.6°C (>= 38.5) and CRITICAL status
        var feverReq = new HorseWelfareLogCreateRequest(
                null, HealthStatus.CRITICAL,
                new BigDecimal("38.60"), BigDecimal.ZERO, BigDecimal.ZERO,
                "Colic signs", "Immediate vet call", null, null);

        var res = welfareService.recordWelfareLog(ROAD_LEG_ID, feverReq);
        assertThat(res.fever()).isTrue();
        assertThat(res.emergencyAlert()).isTrue();
        assertThat(res.alertReason())
                .contains("Immediate veterinary attention");
    }

    /** Coordinator updates estimated arrival with future timestamp. */
    @Test
    public void updateEstimatedArrivalSuccess() {
        when(actors.current()).thenReturn(new CurrentActor(
                12L, "COORDINATOR", null, COORDINATOR_STAFF_ID));
        var leg = createAirLeg(AIR_LEG_ID, 2, "IN_PROGRESS", null, null);
        when(legs.findLegById(AIR_LEG_ID)).thenReturn(Optional.of(leg));
        when(milestones.save(any())).thenReturn(301L);

        var req = new EstimatedArrivalUpdateRequest(
                OffsetDateTime.parse("2026-10-18T22:00:00Z"),
                "Air traffic control hold at destination");

        var res = milestoneService.updateEstimatedArrival(AIR_LEG_ID, req);
        assertThat(res.delayReason()).isEqualTo(
                "Air traffic control hold at destination");
        assertThat(res.estimatedArrivalAt()).isEqualTo(
                OffsetDateTime.parse("2026-10-18T22:00:00Z"));
    }

    // -------------------------------------------------------------------------
    // Helper fixtures
    // -------------------------------------------------------------------------

    private LegRow createRoadLeg(long id, int seq, String status,
            OffsetDateTime dep, OffsetDateTime arr) {
        return new LegRow(id, PLAN_ID, seq, "ROAD", "Hanoi Stable",
                "Noi Bai Airport", new BigDecimal("35.5"),
                OffsetDateTime.parse("2026-10-18T07:00:00Z"),
                OffsetDateTime.parse("2026-10-18T12:00:00Z"),
                dep, arr, status, null, "Provide water hourly");
    }

    private LegRow createAirLeg(long id, int seq, String status,
            OffsetDateTime dep, OffsetDateTime arr) {
        return new LegRow(id, PLAN_ID, seq, "AIR", "Noi Bai Airport",
                "Changi Airport", new BigDecimal("2200.0"),
                OffsetDateTime.parse("2026-10-18T13:00:00Z"),
                OffsetDateTime.parse("2026-10-18T18:00:00Z"),
                dep, arr, status, "VN123", "Groom on board");
    }

    private TripAssignmentRow createAssignment(long legId, long staffId) {
        return new TripAssignmentRow(1L, legId, staffId, "DRIVER", "ACTIVE",
                OffsetDateTime.parse("2026-10-18T00:00:00Z"),
                OffsetDateTime.parse("2026-10-18T23:59:59Z"));
    }
}
