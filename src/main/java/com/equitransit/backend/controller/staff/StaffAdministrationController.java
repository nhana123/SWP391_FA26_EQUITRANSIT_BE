/*
 * StaffAdministrationController
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose active-Manager-only internal staff administration.
 */
package com.equitransit.backend.controller.staff;

import com.equitransit.backend.dto.staff.StaffModels.StaffCreateRequest;
import com.equitransit.backend.dto.staff.StaffModels.StaffPage;
import com.equitransit.backend.dto.staff.StaffModels.StaffRole;
import com.equitransit.backend.dto.staff.StaffModels.StaffStatus;
import com.equitransit.backend.dto.staff.StaffModels.StaffStatusRequest;
import com.equitransit.backend.dto.staff.StaffModels.StaffUpdateRequest;
import com.equitransit.backend.dto.staff.StaffModels.StaffView;
import com.equitransit.backend.service.staff.StaffAdministrationService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin adapter; service owns live authorization, locks and transactions. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/manager/staff",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffAdministrationController {
    private final StaffAdministrationService service;

    /** @param service transactional staff administration */
    public StaffAdministrationController(StaffAdministrationService service) {
        this.service = service;
    }

    /** @param role optional internal role @param status account state
     * @param page zero based @param size bounded @return Manager-only page */
    @GetMapping
    public StaffPage list(@Parameter(example = "SPECIALIST")
            @RequestParam(required = false) StaffRole role,
            @Parameter(example = "ACTIVE")
            @RequestParam(required = false) StaffStatus status,
            @Parameter(example = "0") @Min(0) @Max(100000)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(example = "20") @Min(1) @Max(100)
            @RequestParam(defaultValue = "20") int size) {
        return service.list(role, status, page, size);
    }

    /** @param staffId staff key, never UserID @return private staff view */
    @GetMapping("/{staffId}")
    public StaffView get(@PathVariable @Positive long staffId) {
        return service.get(staffId);
    }

    /** @param body initial credentials and Manager CAS @return new staff */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public StaffView create(@RequestBody @Valid StaffCreateRequest body) {
        return service.create(body);
    }

    /** @param staffId staff key @param body full profile @return new version */
    @PutMapping(value = "/{staffId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public StaffView update(@PathVariable @Positive long staffId,
            @RequestBody @Valid StaffUpdateRequest body) {
        return service.update(staffId, body);
    }

    /** @param staffId staff key @param body reasoned state @return receipt */
    @PostMapping(value = "/{staffId}/status",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public StaffView status(@PathVariable @Positive long staffId,
            @RequestBody @Valid StaffStatusRequest body) {
        return service.status(staffId, body);
    }
}
