/*
 * OrderSubmitRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiPatterns;
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
        @Schema(
                example = "000000000001A2B3",
                pattern = ApiPatterns.VERSION)
        @NotBlank @Pattern(regexp = ApiPatterns.VERSION) String recordVersion,
        @Schema(
                example = "fa2c8d7b-6c51-4976-a28d-613c4199cfd6",
                format = "uuid")
        @NotNull UUID operationId) {
}
