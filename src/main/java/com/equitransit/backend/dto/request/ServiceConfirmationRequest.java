/*
 * ServiceConfirmationRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.dto.request;


import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


/**
 * Full preference replacement; cannot supply prices or compulsory quantities.
 * @param recordVersion observed order rowversion
 * @param operationId retry UUID reused with exactly the same input
 * @param horseRecordVersion measurement version shown in this booking
 * @param services explicit bounded preferences, never a new horse selection
 * @param changeReason required for submitted replacements, optional for drafts
 */
public record ServiceConfirmationRequest(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{16}") String recordVersion,
        @NotNull UUID operationId,
        @NotBlank @Pattern(regexp =
                "[0-9a-fA-F]{16}") String horseRecordVersion,
        @NotNull @Valid OrderServicePreferences services,
        @Size(max = 500) String changeReason) {
}
