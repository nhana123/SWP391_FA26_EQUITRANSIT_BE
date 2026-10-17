/*
 * ResourceAvailabilityController
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Maintain sourced resources and transactional availability.
 */
package com.equitransit.backend.controller;

import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceAvailability;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAvailabilityRequest;
import com.equitransit.backend.service.ResourceAvailabilityService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authorized case probe; no reservation or public customer inventory. */
@RestController
@SecurityRequirement(name = "bearerAuth")
@Validated
@Tag(name = "Resource availability and fit")
@RequestMapping(value = "/api/resources",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class ResourceAvailabilityController {

    private final ResourceAvailabilityService service;

    /** @param service scoped current-revision resource assessment */
    public ResourceAvailabilityController(ResourceAvailabilityService service) {
        this.service = service;
    }

    /** @param kind physical type @param id resource ID @param request interval
     * @return observation only; F10 must recheck before writing assignment */
    @PostMapping(value = "/{kind}/{id}/availability",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResourceAvailability inspect(
            @Parameter(example = "STALL") @PathVariable ResourceKind kind,
            @Parameter(example = "11") @PathVariable @Positive long id,
            @Valid @RequestBody ResourceAvailabilityRequest request) {
        return service.inspect(kind, id, request);
    }
}
