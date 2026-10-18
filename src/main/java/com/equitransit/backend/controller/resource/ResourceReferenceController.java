/*
 * ResourceReferenceController
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Complete scoped resource and fit workflows.
 */
package com.equitransit.backend.controller.resource;

import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyPage;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyView;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAssignedVehicle;
import com.equitransit.backend.service.resource.ResourceReferenceService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Narrow staff reads; supplier contacts and asset notes remain private. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Resource source review and assigned vehicles")
@RequestMapping(value = "/api/resources",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class ResourceReferenceController {
    private final ResourceReferenceService service;

    /** @param service role-scoped source/assignment reads */
    public ResourceReferenceController(ResourceReferenceService service) {
        this.service = service;
    }

    /** @param page zero-based @param size bounded @return source review page */
    @GetMapping("/fit-policies")
    public ResourcePolicyPage policies(
            @Parameter(example = "0") @Min(0)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(example = "20") @Min(1) @Max(100)
            @RequestParam(defaultValue = "20") int size) {
        return service.policies(page, size);
    }

    /** @param id source version @return read-only policy for review */
    @GetMapping("/fit-policies/{id}")
    public ResourcePolicyView policy(
            @Parameter(example = "14") @PathVariable @Positive long id) {
        return service.policy(id);
    }

    /** @param leg current assigned leg @return driver-safe technical data */
    @GetMapping("/legs/{leg}/vehicle")
    public ResourceAssignedVehicle vehicle(
            @Parameter(example = "301") @PathVariable @Positive long leg) {
        return service.vehicle(leg);
    }
}
