/*
 * HealthResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Document minimal liveness without arbitrary JSON properties.
 */
package com.equitransit.backend.dto.system.response;


import io.swagger.v3.oas.annotations.media.Schema;


/**
 * Application liveness only; not database readiness or dependency health.
 * @param status application liveness
 * @param application public application name
 */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record HealthResponse(
        @Schema(example = "UP", allowableValues = {"UP"}) String status,
        @Schema(example = "EquiTransit") String application) {
}
