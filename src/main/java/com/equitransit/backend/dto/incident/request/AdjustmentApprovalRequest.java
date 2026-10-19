/*
 * AdjustmentApprovalRequest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Request DTO for manager approval of an operational adjustment.
 */
package com.equitransit.backend.dto.incident.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Payload submitted by managers when approving or rejecting an adjustment.
 */
public record AdjustmentApprovalRequest(
        @Schema(example = "Approved reroute with confirmed customer consent.")
        @Size(max = 2000) String approvalNotes) { }
