/*
 * DriverIncidentController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller for assigned driver incident reporting on road legs.
 */
package com.equitransit.backend.controller.incident;

import com.equitransit.backend.dto.incident.request.IncidentReportCreateRequest;
import com.equitransit.backend.dto.incident.response.IncidentReportResponse;
import com.equitransit.backend.service.incident.IncidentManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller allowing assigned road drivers to report disruptions & incidents.
 */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Driver incident reporting")
@RequestMapping(value = "/api/driver/legs/{legId}/incidents",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class DriverIncidentController {

    private final IncidentManagementService incidents;

    /**
     * Creates driver incident controller.
     * @param incidents incident management service
     */
    public DriverIncidentController(IncidentManagementService incidents) {
        this.incidents = incidents;
    }

    /**
     * Logs an incident report on an assigned transport leg.
     * @param legId transport leg identifier
     * @param request incident report payload
     * @return created incident report
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Report an incident on an assigned leg",
            description = "Driver must be actively assigned to this leg. "
                    + "Welfare care takes immediate priority.")
    public ResponseEntity<IncidentReportResponse> reportIncident(
            @PathVariable @Positive long legId,
            @RequestBody @Valid IncidentReportCreateRequest request) {
        var response = incidents.reportByDriver(legId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
