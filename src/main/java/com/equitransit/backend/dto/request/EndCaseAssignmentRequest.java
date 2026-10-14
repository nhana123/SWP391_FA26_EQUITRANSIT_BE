/*
 * EndCaseAssignmentRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.request;


import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


/**
 * Explicit revocation command without destructive assignment deletion.
 * @param recordVersion the observed order version
 * @param operationId stable retry UUID
 * @param reason required revocation explanation
 */
public record EndCaseAssignmentRequest(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{16}") String recordVersion,
        @NotNull UUID operationId,
        @NotBlank @Size(max = 1000) String reason) {
}
