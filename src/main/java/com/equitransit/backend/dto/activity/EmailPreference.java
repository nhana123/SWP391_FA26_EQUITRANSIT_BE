/*
 * EmailPreference
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.dto.activity;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;


/** Safe, bounded actor-facing activity contract. */
public record EmailPreference(
        @Schema(example = "false") @NotNull
        Boolean enabled) {
}
