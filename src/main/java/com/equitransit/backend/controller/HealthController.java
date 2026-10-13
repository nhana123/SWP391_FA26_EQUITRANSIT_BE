/*
 * HealthController
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Report application liveness.
 */
package com.equitransit.backend.controller;


import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


/** Exposes a minimal application liveness endpoint. */
@RestController
public class HealthController {

    /**
     * Reports that the application has started.
     * @return the liveness status, not database credentials
     */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "application", "EquiTransit");
    }
}
