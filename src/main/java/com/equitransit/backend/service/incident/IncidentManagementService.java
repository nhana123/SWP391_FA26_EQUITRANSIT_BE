/*
 * IncidentManagementService
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Business engine for incident reporting and resolution.
 */
package com.equitransit.backend.service.incident;

import com.equitransit.backend.dto.incident.IncidentStatus;
import com.equitransit.backend.dto.incident.request.IncidentAcknowledgeRequest;
import com.equitransit.backend.dto.incident.request.IncidentReportCreateRequest;
import com.equitransit.backend.dto.incident.request.IncidentResolveRequest;
import com.equitransit.backend.dto.incident.response.IncidentReportResponse;
import com.equitransit.backend.entity.incident.IncidentReport;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.incident.IncidentReportRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing lifecycle of incidents and welfare emergencies.
 */
@Service
public class IncidentManagementService {

    private final IncidentReportRepository incidents;
    private final LegExecutionRepository legs;
    private final CurrentActorService actors;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * Creates incident management service.
     * @param incidents incident repository
     * @param legs leg execution repository
     * @param actors actor identity service
     * @param mapper json object mapper
     * @param clock system clock
     */
    public IncidentManagementService(IncidentReportRepository incidents,
            LegExecutionRepository legs,
            CurrentActorService actors,
            ObjectMapper mapper,
            Clock clock) {
        this.incidents = incidents;
        this.legs = legs;
        this.actors = actors;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Logs an incident report by an assigned driver.
     * @param legId trip leg identifier
     * @param request incident report payload
     * @return created incident response
     */
    @Transactional
    public IncidentReportResponse reportByDriver(long legId,
            IncidentReportCreateRequest request) {
        CurrentActor actor = actors.current();

        if (actor.staffId() == null) {
            throw new AccessDeniedException("Staff profile required.");
        }
        var assignment = legs.findDriverAssignment(legId, actor.staffId());

        if (assignment.isEmpty()) {
            throw new AccessDeniedException(
                    "Driver is not assigned to this transport leg.");
        }
        return createIncident(legId, actor.staffId(), request);
    }

    /**
     * Logs an incident report by a coordinator or manager.
     * @param legId trip leg identifier
     * @param request incident report payload
     * @return created incident response
     */
    @Transactional
    public IncidentReportResponse reportByCoordinator(long legId,
            IncidentReportCreateRequest request) {
        CurrentActor actor = actors.current();

        if (actor.staffId() == null) {
            throw new AccessDeniedException("Staff profile required.");
        }
        var leg = legs.findLegById(legId);

        if (leg.isEmpty()) {
            throw new OrderException(HttpStatus.NOT_FOUND, "LEG_NOT_FOUND",
                    "Transport leg not found: " + legId);
        }
        return createIncident(legId, actor.staffId(), request);
    }

    /**
     * Lists all incidents logged for a leg.
     * @param legId trip leg identifier
     * @return list of incident responses
     */
    @Transactional(readOnly = true)
    public List<IncidentReportResponse> listByLeg(long legId) {
        var leg = legs.findLegById(legId);

        if (leg.isEmpty()) {
            throw new OrderException(HttpStatus.NOT_FOUND, "LEG_NOT_FOUND",
                    "Transport leg not found: " + legId);
        }
        return incidents.findByLegId(legId).stream()
                .map(this::toResponse).toList();
    }

    /**
     * Reads a specific incident report.
     * @param incidentId incident primary key
     * @return incident response
     */
    @Transactional(readOnly = true)
    public IncidentReportResponse getById(long incidentId) {
        var report = incidents.findById(incidentId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND",
                        "Incident report not found: " + incidentId));

        return toResponse(report);
    }

    /**
     * Acknowledges an active incident report.
     * @param incidentId incident primary key
     * @param request optional acknowledgment remarks
     * @return updated incident response
     */
    @Transactional
    public IncidentReportResponse acknowledge(long incidentId,
            IncidentAcknowledgeRequest request) {
        CurrentActor actor = actors.current();

        if (actor.staffId() == null) {
            throw new AccessDeniedException("Staff profile required.");
        }
        var report = incidents.findById(incidentId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND",
                        "Incident report not found: " + incidentId));

        if (!IncidentStatus.REPORTED.name().equals(report.getStatus())) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "INVALID_INCIDENT_STATUS",
                    "Incident is already " + report.getStatus());
        }
        OffsetDateTime now = OffsetDateTime.now(clock);

        incidents.acknowledge(incidentId, actor.staffId(), now);
        report.setStatus(IncidentStatus.ACKNOWLEDGED.name());
        report.setAcknowledgedBy(actor.staffId());
        report.setAcknowledgedAt(now);
        return toResponse(report);
    }

    /**
     * Resolves an incident with mandatory resolution notes.
     * @param incidentId incident primary key
     * @param request resolution notes payload
     * @return updated incident response
     */
    @Transactional
    public IncidentReportResponse resolve(long incidentId,
            IncidentResolveRequest request) {
        CurrentActor actor = actors.current();

        if (actor.staffId() == null) {
            throw new AccessDeniedException("Staff profile required.");
        }
        var report = incidents.findById(incidentId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND",
                        "Incident report not found: " + incidentId));

        if (IncidentStatus.RESOLVED.name().equals(report.getStatus())
                || IncidentStatus.CANCELLED.name().equals(report.getStatus())) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "INVALID_INCIDENT_STATUS",
                    "Incident cannot be resolved from status: "
                            + report.getStatus());
        }
        OffsetDateTime now = OffsetDateTime.now(clock);

        incidents.resolve(incidentId, actor.staffId(), now,
                request.resolutionNotes());
        report.setStatus(IncidentStatus.RESOLVED.name());
        report.setResolvedBy(actor.staffId());
        report.setResolvedAt(now);
        report.setResolutionNotes(request.resolutionNotes());
        return toResponse(report);
    }

    private IncidentReportResponse createIncident(long legId, long staffId,
            IncidentReportCreateRequest request) {
        validateEvidenceJson(request.evidenceFilesJson());

        var report = new IncidentReport();

        report.setLegId(legId);
        report.setReportedBy(staffId);
        report.setIncidentType(request.incidentType().name());
        report.setSeverityLevel(request.severityLevel().name());
        report.setIncidentTime(request.incidentTime() != null
                ? request.incidentTime() : OffsetDateTime.now(clock));
        report.setDescription(request.description());
        report.setStatus(IncidentStatus.REPORTED.name());
        report.setImmediateActionNotes(request.immediateActionNotes());
        report.setEmergencyContact(request.emergencyContact());
        report.setEvidenceFilesJson(request.evidenceFilesJson());

        long id = incidents.save(report);

        report.setIncidentId(id);
        return toResponse(report);
    }

    private void validateEvidenceJson(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            mapper.readTree(json);
        } catch (Exception e) {
            throw new OrderException(HttpStatus.BAD_REQUEST, "INVALID_JSON",
                    "EvidenceFilesJSON must be valid JSON: " + e.getMessage());
        }
    }

    private IncidentReportResponse toResponse(IncidentReport r) {
        String reporterName = lookupStaffName(r.getReportedBy());
        String ackName = r.getAcknowledgedBy() != null
                ? lookupStaffName(r.getAcknowledgedBy()) : null;
        String resName = r.getResolvedBy() != null
                ? lookupStaffName(r.getResolvedBy()) : null;

        return new IncidentReportResponse(
                r.getIncidentId() == null ? 0L : r.getIncidentId(),
                r.getLegId() == null ? 0L : r.getLegId(),
                r.getReportedBy() == null ? 0L : r.getReportedBy(),
                reporterName,
                r.getIncidentType(),
                r.getSeverityLevel(),
                r.getIncidentTime(),
                r.getDescription(),
                r.getStatus(),
                r.getImmediateActionNotes(),
                r.getEmergencyContact(),
                r.getAcknowledgedBy(),
                ackName,
                r.getAcknowledgedAt(),
                r.getResolvedBy(),
                resName,
                r.getResolvedAt(),
                r.getResolutionNotes(),
                r.getEvidenceFilesJson()
        );
    }

    private String lookupStaffName(Long staffId) {
        if (staffId == null) {
            return null;
        }
        return legs.findStaffProfile(staffId)
                .map(s -> s.fullName())
                .orElse("Staff #" + staffId);
    }
}
