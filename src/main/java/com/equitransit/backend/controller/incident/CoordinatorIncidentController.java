/*
 * CoordinatorIncidentController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller for operations coordinator incident management.
 */
package com.equitransit.backend.controller.incident;

import com.equitransit.backend.dto.incident.request.IncidentAcknowledgeRequest;
import com.equitransit.backend.dto.incident.request.IncidentReportCreateRequest;
import com.equitransit.backend.dto.incident.request.IncidentResolveRequest;
import com.equitransit.backend.dto.incident.response.IncidentReportResponse;
import com.equitransit.backend.service.incident.IncidentManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for coordinators and managers to oversee incidents.
 */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Coordinator incident coordination and resolution")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class CoordinatorIncidentController {

    private final IncidentManagementService incidents;

    /**
     * Creates coordinator incident controller.
     * @param incidents incident management service
     */
    public CoordinatorIncidentController(
            IncidentManagementService incidents) {
        this.incidents = incidents;
    }

    /**
     * Reports an incident on a leg by coordinator or manager.
     * @param legId transport leg identifier
     * @param request incident report payload
     * @return created incident report
     */
    @PostMapping(value = "/api/coordinator/legs/{legId}/incidents",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Coordinator reports an incident on a leg")
    public ResponseEntity<IncidentReportResponse> reportIncident(
            @PathVariable @Positive long legId,
            @RequestBody @Valid IncidentReportCreateRequest request) {
        var response = incidents.reportByCoordinator(legId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all incidents recorded for a specific leg.
     * @param legId transport leg identifier
     * @return list of incident reports
     */
    @GetMapping(value = "/api/coordinator/legs/{legId}/incidents")
    @Operation(summary = "List incidents for a leg")
    public List<IncidentReportResponse> listLegIncidents(
            @PathVariable @Positive long legId) {
        return incidents.listByLeg(legId);
    }

    /**
     * Reads a specific incident report.
     * @param incidentId incident primary key
     * @return incident report details
     */
    @GetMapping(value = "/api/coordinator/incidents/{incidentId}")
    @Operation(summary = "Read incident report details")
    public IncidentReportResponse getIncident(
            @PathVariable @Positive long incidentId) {
        return incidents.getById(incidentId);
    }

    /**
     * Acknowledges an incident by operations staff.
     * @param incidentId incident primary key
     * @param request acknowledgment remarks
     * @return updated incident report
     */
    @PostMapping(value = "/api/coordinator/incidents/{incidentId}/acknowledge",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Acknowledge an incident report")
    public IncidentReportResponse acknowledgeIncident(
            @PathVariable @Positive long incidentId,
            @RequestBody(required = false) @Valid
            IncidentAcknowledgeRequest request) {
        var req = request != null ? request
                : new IncidentAcknowledgeRequest(null);

        return incidents.acknowledge(incidentId, req);
    }

    /**
     * Resolves an incident with resolution notes.
     * @param incidentId incident primary key
     * @param request resolution notes payload
     * @return resolved incident report
     */
    @PostMapping(value = "/api/coordinator/incidents/{incidentId}/resolve",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Resolve an incident report")
    public IncidentReportResponse resolveIncident(
            @PathVariable @Positive long incidentId,
            @RequestBody @Valid IncidentResolveRequest request) {
        return incidents.resolve(incidentId, request);
    }
}
