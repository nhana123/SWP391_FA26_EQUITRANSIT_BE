/*
 * DriverExecutionController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: REST controller for assigned road driver milestone & welfare logs.
 */
package com.equitransit.backend.controller.execution;

import com.equitransit.backend.dto.execution.request.HorseWelfareLogCorrectionRequest;
import com.equitransit.backend.dto.execution.request.HorseWelfareLogCreateRequest;
import com.equitransit.backend.dto.execution.request.RoadMilestoneCreateRequest;
import com.equitransit.backend.dto.execution.response.HorseWelfareLogResponse;
import com.equitransit.backend.dto.execution.response.LegMilestoneResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for assigned drivers to log road milestones and welfare checks.
 */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Driver leg execution and horse welfare")
@RequestMapping(value = "/api/driver/legs/{legId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class DriverExecutionController {

    private final LegMilestoneService milestones;
    private final HorseWelfareService welfare;

    /**
     * Creates driver execution controller.
     *
     * @param milestones milestone execution service
     * @param welfare horse welfare service
     */
    public DriverExecutionController(LegMilestoneService milestones,
            HorseWelfareService welfare) {
        this.milestones = milestones;
        this.welfare = welfare;
    }

    /**
     * Driver records a road transport milestone event.
     *
     * @param legId target road leg ID
     * @param req road milestone payload
     * @return 201 Created with milestone response
     */
    @Operation(summary = "Record road milestone event with GPS")
    @PostMapping(value = "/milestones",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LegMilestoneResponse> recordMilestone(
            @PathVariable @Positive long legId,
            @Valid @RequestBody RoadMilestoneCreateRequest req) {
        var res = milestones.recordRoadMilestone(legId, req);

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * Driver records an immutable horse welfare inspection check.
     *
     * @param legId target road leg ID
     * @param req welfare check payload
     * @return 201 Created with welfare check response
     */
    @Operation(summary = "Record live horse vital assessment")
    @PostMapping(value = "/welfare",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HorseWelfareLogResponse> recordWelfare(
            @PathVariable @Positive long legId,
            @Valid @RequestBody HorseWelfareLogCreateRequest req) {
        var res = welfare.recordWelfareLog(legId, req);

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * Driver records a superseding correction to a previous welfare log.
     *
     * @param legId target road leg ID
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

    /**
     * Driver retrieves all milestones logged on this leg.
     *
     * @param legId target road leg ID
     * @return list of milestones
     */
    @Operation(summary = "List all milestone events on road leg")
    @GetMapping("/milestones")
    public List<LegMilestoneResponse> getMilestones(
            @PathVariable @Positive long legId) {
        return milestones.getLegMilestones(legId);
    }

    /**
     * Driver retrieves active welfare checks on this leg.
     *
     * @param legId target road leg ID
     * @return list of active welfare checks
     */
    @Operation(summary = "List active welfare assessments on road leg")
    @GetMapping("/welfare")
    public List<HorseWelfareLogResponse> getWelfare(
            @PathVariable @Positive long legId) {
        return welfare.getActiveWelfareLogs(legId);
    }
}
