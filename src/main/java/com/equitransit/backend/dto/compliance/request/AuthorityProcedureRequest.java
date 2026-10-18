/*
 * AuthorityProcedureRequest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Capture authority contact and approval decisions for FR-08.
 */
package com.equitransit.backend.dto.compliance.request;


import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/**
 * Bounded F08 authority procedure command.
 * @param procedureCode the procedureCode value
 * @param authorityName the authorityName value
 * @param applicationReference the applicationReference value
 * @param approvalStatus the approvalStatus value
 * @param evidenceFileKey the evidenceFileKey value
 * @param phaseCode the phaseCode value
 * @param authorityContact the authorityContact value
 * @param holdReason the holdReason value
 * @param expiresAt the expiresAt value
 * @param decidedAt timestamp when external authority made decision
 */
public record AuthorityProcedureRequest(
        @NotBlank @Size(max = 50) String procedureCode,
        @NotBlank @Size(max = 300) String authorityName,
        @Size(max = 150) String applicationReference,
        @NotBlank @Size(max = 50) String approvalStatus,
        @Size(max = 1024) String evidenceFileKey,
        @NotBlank @Size(max = 50) String phaseCode,
        @Size(max = 500) String authorityContact,
        @Size(max = 2000) String holdReason,
        OffsetDateTime expiresAt,
        OffsetDateTime decidedAt) {
}
