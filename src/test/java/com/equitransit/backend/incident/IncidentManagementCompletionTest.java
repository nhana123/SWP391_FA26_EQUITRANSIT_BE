/*
 * IncidentManagementCompletionTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Unit & workflow test suite for F13 incident lifecycle management.
 */
package com.equitransit.backend.incident;

import com.equitransit.backend.dto.incident.IncidentStatus;
import com.equitransit.backend.dto.incident.IncidentType;
import com.equitransit.backend.dto.incident.SeverityLevel;
import com.equitransit.backend.dto.incident.request.IncidentAcknowledgeRequest;
import com.equitransit.backend.dto.incident.request.IncidentReportCreateRequest;
import com.equitransit.backend.dto.incident.request.IncidentResolveRequest;
import com.equitransit.backend.dto.incident.response.IncidentReportResponse;
import com.equitransit.backend.entity.incident.IncidentReport;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.execution.LegExecutionRepository.LegRow;
import com.equitransit.backend.repository.execution.LegExecutionRepository.StaffProfileRow;
import com.equitransit.backend.repository.execution.LegExecutionRepository.TripAssignmentRow;
import com.equitransit.backend.repository.incident.IncidentReportRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.incident.IncidentManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/**
 * Unit & workflow verification test suite for Incident Management.
 */
@ExtendWith(MockitoExtension.class)
public class IncidentManagementCompletionTest {

    private static final long LEG_ID = 401L;
    private static final long DRIVER_STAFF_ID = 12L;
    private static final long COORD_STAFF_ID = 3L;
    private static final long INCIDENT_ID = 501L;

    @Mock
    private IncidentReportRepository incidentRepository;
    @Mock
    private LegExecutionRepository legRepository;
    @Mock
    private CurrentActorService actorService;

    private ObjectMapper mapper;
    private Clock fixedClock;
    private IncidentManagementService service;

    /** Sets up mock dependencies before each test. */
    @BeforeEach
    public void setUp() {
        mapper = new ObjectMapper();
        fixedClock = Clock.fixed(Instant.parse("2026-10-18T14:30:00Z"),
                ZoneOffset.UTC);
        service = new IncidentManagementService(incidentRepository,
                legRepository, actorService, mapper, fixedClock);
    }

    /** Assigned driver can report incident with emergency welfare care. */
    @Test
    public void driverCanReportWelfareIncidentOnAssignedLeg() {
        var actor = new CurrentActor(102L, "DRIVER", null, DRIVER_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var assignment = new TripAssignmentRow(701L, LEG_ID, DRIVER_STAFF_ID,
                "DRIVER", "CONFIRMED", null, null);

        when(legRepository.findDriverAssignment(LEG_ID, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(assignment));
        when(legRepository.findStaffProfile(DRIVER_STAFF_ID)).thenReturn(
                Optional.of(new StaffProfileRow(DRIVER_STAFF_ID, 102L,
                        "Jean Dupont", "+336000000", "LIC-123")));
        when(incidentRepository.save(any(IncidentReport.class)))
                .thenReturn(INCIDENT_ID);

        var request = new IncidentReportCreateRequest(
                IncidentType.HORSE_WELFARE_EMERGENCY,
                SeverityLevel.CRITICAL,
                OffsetDateTime.now(fixedClock),
                "Horse showing symptoms of severe colic.",
                "Administered tranquilizer; immediate vet dispatch.",
                "Dr. Dupont: +33 4 72 00 00 00",
                "[\"colic-photo.jpg\"]"
        );

        IncidentReportResponse response = service.reportByDriver(LEG_ID,
                request);

        assertThat(response.incidentId()).isEqualTo(INCIDENT_ID);
        assertThat(response.legId()).isEqualTo(LEG_ID);
        assertThat(response.reportedBy()).isEqualTo(DRIVER_STAFF_ID);
        assertThat(response.reporterName()).isEqualTo("Jean Dupont");
        assertThat(response.incidentType())
                .isEqualTo("HORSE_WELFARE_EMERGENCY");
        assertThat(response.severityLevel()).isEqualTo("CRITICAL");
        assertThat(response.status()).isEqualTo("REPORTED");
        assertThat(response.immediateActionNotes())
                .contains("Administered tranquilizer");
        verify(incidentRepository).save(any(IncidentReport.class));
    }

    /** Driver not assigned to the leg cannot report an incident. */
    @Test
    public void unassignedDriverCannotReportIncident() {
        var actor = new CurrentActor(102L, "DRIVER", null, DRIVER_STAFF_ID);

        when(actorService.current()).thenReturn(actor);
        when(legRepository.findDriverAssignment(LEG_ID, DRIVER_STAFF_ID))
                .thenReturn(Optional.empty());

        var request = new IncidentReportCreateRequest(
                IncidentType.VEHICLE_BREAKDOWN,
                SeverityLevel.MEDIUM,
                OffsetDateTime.now(fixedClock),
                "Tire blowout.",
                null, null, null
        );

        assertThatThrownBy(() -> service.reportByDriver(LEG_ID, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not assigned");
    }

    /** Malformed JSON in evidenceFilesJson is rejected. */
    @Test
    public void malformedEvidenceJsonIsRejected() {
        var actor = new CurrentActor(102L, "DRIVER", null, DRIVER_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var assignment = new TripAssignmentRow(701L, LEG_ID, DRIVER_STAFF_ID,
                "DRIVER", "CONFIRMED", null, null);

        when(legRepository.findDriverAssignment(LEG_ID, DRIVER_STAFF_ID))
                .thenReturn(Optional.of(assignment));

        var request = new IncidentReportCreateRequest(
                IncidentType.VEHICLE_BREAKDOWN,
                SeverityLevel.LOW,
                OffsetDateTime.now(fixedClock),
                "Flat tire.",
                null, null,
                "{not-valid-json"
        );

        assertThatThrownBy(() -> service.reportByDriver(LEG_ID, request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("EvidenceFilesJSON must be valid JSON");
    }

    /** Coordinator can report an incident on any leg. */
    @Test
    public void coordinatorCanReportIncidentOnAnyLeg() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var leg = new LegRow(LEG_ID, 311L, 1, "ROAD", "Paris", "Lyon",
                new BigDecimal("465.0"), null, null, null, null,
                "IN_TRANSIT", null, null);

        when(legRepository.findLegById(LEG_ID)).thenReturn(Optional.of(leg));
        when(legRepository.findStaffProfile(COORD_STAFF_ID)).thenReturn(
                Optional.of(new StaffProfileRow(COORD_STAFF_ID, 103L,
                        "Claire Martin", "+336111111", "LIC-COORD")));
        when(incidentRepository.save(any(IncidentReport.class)))
                .thenReturn(INCIDENT_ID);

        var request = new IncidentReportCreateRequest(
                IncidentType.TRAFFIC_DELAY,
                SeverityLevel.MEDIUM,
                OffsetDateTime.now(fixedClock),
                "Highway closure due to accident.",
                "Rerouting via D1083.",
                null, null
        );

        IncidentReportResponse response = service.reportByCoordinator(LEG_ID,
                request);

        assertThat(response.incidentId()).isEqualTo(INCIDENT_ID);
        assertThat(response.reporterName()).isEqualTo("Claire Martin");
        assertThat(response.status()).isEqualTo("REPORTED");
    }

    /** Operations coordinator acknowledges reported incident. */
    @Test
    public void coordinatorCanAcknowledgeReportedIncident() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var incident = new IncidentReport();

        incident.setIncidentId(INCIDENT_ID);
        incident.setLegId(LEG_ID);
        incident.setReportedBy(DRIVER_STAFF_ID);
        incident.setIncidentType("HORSE_WELFARE_EMERGENCY");
        incident.setSeverityLevel("CRITICAL");
        incident.setStatus(IncidentStatus.REPORTED.name());
        incident.setDescription("Horse colic.");

        when(incidentRepository.findById(INCIDENT_ID))
                .thenReturn(Optional.of(incident));
        when(legRepository.findStaffProfile(COORD_STAFF_ID)).thenReturn(
                Optional.of(new StaffProfileRow(COORD_STAFF_ID, 103L,
                        "Claire Martin", "+336111111", "LIC-COORD")));
        when(legRepository.findStaffProfile(DRIVER_STAFF_ID)).thenReturn(
                Optional.of(new StaffProfileRow(DRIVER_STAFF_ID, 102L,
                        "Jean Dupont", "+336000000", "LIC-123")));

        var request = new IncidentAcknowledgeRequest("Monitoring situation.");
        IncidentReportResponse response = service.acknowledge(INCIDENT_ID,
                request);

        assertThat(response.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(response.acknowledgedBy()).isEqualTo(COORD_STAFF_ID);
        assertThat(response.acknowledgedByName()).isEqualTo("Claire Martin");
        verify(incidentRepository).acknowledge(eq(INCIDENT_ID),
                eq(COORD_STAFF_ID), any(OffsetDateTime.class));
    }

    /** Cannot acknowledge an already acknowledged incident. */
    @Test
    public void cannotAcknowledgeAlreadyAcknowledgedIncident() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var incident = new IncidentReport();

        incident.setIncidentId(INCIDENT_ID);
        incident.setStatus(IncidentStatus.ACKNOWLEDGED.name());

        when(incidentRepository.findById(INCIDENT_ID))
                .thenReturn(Optional.of(incident));

        var request = new IncidentAcknowledgeRequest(null);

        assertThatThrownBy(() -> service.acknowledge(INCIDENT_ID, request))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("Incident is already ACKNOWLEDGED");
    }

    /** Operations staff resolves an acknowledged incident with notes. */
    @Test
    public void staffCanResolveAcknowledgedIncidentWithNotes() {
        var actor = new CurrentActor(103L, "COORDINATOR", null, COORD_STAFF_ID);

        when(actorService.current()).thenReturn(actor);

        var incident = new IncidentReport();

        incident.setIncidentId(INCIDENT_ID);
        incident.setLegId(LEG_ID);
        incident.setReportedBy(DRIVER_STAFF_ID);
        incident.setIncidentType("HORSE_WELFARE_EMERGENCY");
        incident.setSeverityLevel("CRITICAL");
        incident.setStatus(IncidentStatus.ACKNOWLEDGED.name());
        incident.setDescription("Colic.");

        when(incidentRepository.findById(INCIDENT_ID))
                .thenReturn(Optional.of(incident));
        when(legRepository.findStaffProfile(COORD_STAFF_ID)).thenReturn(
                Optional.of(new StaffProfileRow(COORD_STAFF_ID, 103L,
                        "Claire Martin", "+336111111", "LIC-COORD")));
        when(legRepository.findStaffProfile(DRIVER_STAFF_ID)).thenReturn(
                Optional.of(new StaffProfileRow(DRIVER_STAFF_ID, 102L,
                        "Jean Dupont", "+336000000", "LIC-123")));

        var request = new IncidentResolveRequest(
                "Veterinarian confirmed horse gut sounds returned; stable.");

        IncidentReportResponse response = service.resolve(INCIDENT_ID, request);

        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.resolvedBy()).isEqualTo(COORD_STAFF_ID);
        assertThat(response.resolvedByName()).isEqualTo("Claire Martin");
        assertThat(response.resolutionNotes()).contains("gut sounds returned");
        verify(incidentRepository).resolve(eq(INCIDENT_ID), eq(COORD_STAFF_ID),
                any(OffsetDateTime.class), any(String.class));
    }

    /** Lists all incidents logged for a leg. */
    @Test
    public void listByLegReturnsAllIncidents() {
        var leg = new LegRow(LEG_ID, 311L, 1, "ROAD", "Paris", "Lyon",
                new BigDecimal("465.0"), null, null, null, null,
                "IN_TRANSIT", null, null);

        when(legRepository.findLegById(LEG_ID)).thenReturn(Optional.of(leg));

        var inc1 = new IncidentReport();

        inc1.setIncidentId(501L);
        inc1.setLegId(LEG_ID);
        inc1.setReportedBy(DRIVER_STAFF_ID);
        inc1.setStatus("RESOLVED");

        var inc2 = new IncidentReport();

        inc2.setIncidentId(502L);
        inc2.setLegId(LEG_ID);
        inc2.setReportedBy(DRIVER_STAFF_ID);
        inc2.setStatus("REPORTED");

        when(legRepository.findStaffProfile(DRIVER_STAFF_ID)).thenReturn(
                Optional.of(new StaffProfileRow(DRIVER_STAFF_ID, 102L,
                        "Jean Dupont", "+336000000", "LIC-123")));
        when(incidentRepository.findByLegId(LEG_ID))
                .thenReturn(List.of(inc1, inc2));

        List<IncidentReportResponse> list = service.listByLeg(LEG_ID);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).incidentId()).isEqualTo(501L);
        assertThat(list.get(1).incidentId()).isEqualTo(502L);
    }
}
