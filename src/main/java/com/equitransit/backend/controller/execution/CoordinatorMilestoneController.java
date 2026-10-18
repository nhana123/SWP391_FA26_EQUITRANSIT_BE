/*
 * CoordinatorMilestoneController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller for coordinator air milestones and welfare audit.
 */
package com.equitransit.backend.controller.execution;

import com.equitransit.backend.dto.execution.request.AirMilestoneCreateRequest;
import com.equitransit.backend.dto.execution.request.EstimatedArrivalUpdateRequest;
import com.equitransit.backend.dto.execution.request.HorseWelfareLogCorrectionRequest;
import com.equitransit.backend.dto.execution.request.HorseWelfareLogCreateRequest;
import com.equitransit.backend.dto.execution.response.HorseWelfareLogResponse;
import com.equitransit.backend.dto.execution.response.LegMilestoneResponse;
import com.equitransit.backend.dto.execution.response.WelfareAuditHistoryResponse;
import com.equitransit.backend.service.execution.HorseWelfareService;
import com.equitransit.backend.service.execution.LegMilestoneService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for coordinators to log air milestones and manage welfare. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Coordinator air execution and welfare audit")
@RequestMapping(value = "/api/coordinator/legs/{legId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class CoordinatorMilestoneController {

    private final LegMilestoneService milestones;
    private final HorseWelfareService welfare;

    /**
     * Creates coordinator milestone controller.
     *
     * @param milestones milestone execution service
     * @param welfare horse welfare service
     */
    public CoordinatorMilestoneController(LegMilestoneService milestones,
            HorseWelfareService welfare) {
        this.milestones = milestones;
        this.welfare = welfare;
    }

    /**
     * Coordinator records an air transport milestone with partner citation.
     *
     * @param legId target air leg ID
     * @param req air milestone payload
     * @return 201 Created with milestone response
     */
    @Operation(summary = "Record sourced air transport milestone")
    @PostMapping(value = "/air-milestones",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LegMilestoneResponse> recordAirMilestone(
            @PathVariable @Positive long legId,
            @Valid @RequestBody AirMilestoneCreateRequest req) {
        var res = milestones.recordAirMilestone(legId, req);

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * Coordinator updates estimated arrival time and delay reason on a leg.
     *
     * @param legId target trip leg ID
     * @param req estimated arrival payload
     * @return 200 OK with updated milestone response
     */
    @Operation(summary = "Update estimated arrival time and delay reason")
    @PutMapping(value = "/estimated-arrival",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LegMilestoneResponse> updateEstimatedArrival(
            @PathVariable @Positive long legId,
            @Valid @RequestBody EstimatedArrivalUpdateRequest req) {
        var res = milestones.updateEstimatedArrival(legId, req);

        return ResponseEntity.ok(res);
    }

    /**
     * Coordinator retrieves all milestones on a leg.
     *
     * @param legId target trip leg ID
     * @return list of milestones
     */
    @Operation(summary = "List all milestone events on leg")
    @GetMapping("/milestones")
    public List<LegMilestoneResponse> getMilestones(
            @PathVariable @Positive long legId) {
        return milestones.getLegMilestones(legId);
    }

    /**
     * Coordinator retrieves active welfare checks on a leg.
     *
     * @param legId target trip leg ID
     * @return list of active welfare checks
     */
    @Operation(summary = "List active welfare assessments on leg")
    @GetMapping("/welfare")
    public List<HorseWelfareLogResponse> getWelfare(
            @PathVariable @Positive long legId) {
        return welfare.getActiveWelfareLogs(legId);
    }

    /**
     * Coordinator retrieves complete audit history for a welfare log.
     *
     * @param legId target trip leg ID
     * @param logId target welfare log ID
     * @return audit history with active version and full chain
     */
    @Operation(summary = "View full immutable audit chain of welfare checks")
    @GetMapping("/welfare/{logId}/audit")
    public WelfareAuditHistoryResponse getWelfareAudit(
            @PathVariable @Positive long legId,
            @PathVariable @Positive long logId) {
        return welfare.getWelfareAuditHistory(legId, logId);
    }

    /**
     * Coordinator records a horse welfare assessment (e.g. from groom/vet).
     *
     * @param legId target trip leg ID
     * @param req welfare check payload
     * @return 201 Created with welfare check response
     */
    @Operation(summary = "Record horse vital assessment on behalf of flight")
    @PostMapping(value = "/welfare",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HorseWelfareLogResponse> recordWelfare(
            @PathVariable @Positive long legId,
            @Valid @RequestBody HorseWelfareLogCreateRequest req) {
        var res = welfare.recordWelfareLog(legId, req);

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * Coordinator records a superseding correction to a previous welfare log.
     *
     * @param legId target trip leg ID
     * @param logId prior welfare log ID to supersede
     * @param req correction payload
     * @return 201 Created with new superseding welfare response
     */
    @Operation(summary = "Record superseding correction for welfare log")
    @PostMapping(value = "/welfare/{logId}/supersede",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HorseWelfareLogResponse> supersedeWelfare(
            @PathVariable @Positive long legId,
            @PathVariable @Positive long logId,
            @Valid @RequestBody HorseWelfareLogCorrectionRequest req) {
        var res = welfare.supersedeWelfareLog(legId, logId, req);

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }
}
