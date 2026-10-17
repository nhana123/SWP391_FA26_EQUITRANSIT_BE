/*
 * ComplianceReceipt
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceReceipt(
        @Schema(example = "101")
        long entityId,
        @Schema(example = "d86b4f63-5acc-4f20-8da3-e24323268814")
        UUID operationId,
        @Schema(example = "000000000001A2B3",
                pattern = "^[0-9a-fA-F]{16}$",
                minLength = 16, maxLength = 16)
        String recordVersion,
        @Schema(example = "84032724-eac7-45df-8d04-e54f092279d0",
                nullable = true)
        UUID fileKey) {
}
