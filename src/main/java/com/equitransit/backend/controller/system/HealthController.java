/*
 * HealthController
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Report application liveness.
 */
package com.equitransit.backend.controller.system;


import com.equitransit.backend.dto.system.response.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


/** Exposes a minimal application liveness endpoint. */
@RestController
@Tag(name = "Application health")
public class HealthController {

    /**
     * Reports that the application has started.
     * @return the liveness status, not database credentials
     */
    @GetMapping(value = "/api/health",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Check application liveness")
    public HealthResponse health() {
        return new HealthResponse("UP", "EquiTransit");
    }
}
