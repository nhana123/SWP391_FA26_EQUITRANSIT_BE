package com.equitransit.backend.dto.response;


import java.time.OffsetDateTime;


/**
 * Public F08 result without storage keys.
 * @param procedureId the procedureId value
 * @param revisionId the revisionId value
 * @param procedureCode the procedureCode value
 * @param authorityName the authorityName value
 * @param applicationReference the applicationReference value
 * @param approvalStatus the approvalStatus value
 * @param phaseCode the phaseCode value
 * @param authorityContact the authorityContact value
 * @param holdReason the holdReason value
 * @param decidedAt the decidedAt value
 * @param expiresAt the expiresAt value
 */
public record AuthorityProcedureResponse(
        long procedureId,
        long revisionId,
        String procedureCode,
        String authorityName,
        String applicationReference,
        String approvalStatus,
        String phaseCode,
        String authorityContact,
        String holdReason,
        OffsetDateTime decidedAt,
        OffsetDateTime expiresAt) {
}
