/*
 * OrderSubmitRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.request;


import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;


/**
 * Idempotent submit command for a saved draft.
 * @param recordVersion the last observed eight-byte version
 * @param operationId the client-generated retry identifier
 */
public record OrderSubmitRequest(
        @NotBlank @Pattern(regexp = "[0-9A-Fa-f]{16}") String recordVersion,
        @NotNull UUID operationId) {
}
