/*
 * WelfareAuditHistoryResponse
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Response DTO presenting full audit trail of horse welfare checks.
 */
package com.equitransit.backend.dto.execution.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** Audit history view showing full superseding chain. */
public record WelfareAuditHistoryResponse(
        @Schema(example = "501") @Positive long welfareLogId,
        @Schema(example = "901") @Positive long legId,
        @NotNull HorseWelfareLogResponse activeLog,
        @NotNull List<HorseWelfareLogResponse> auditChain) { }
