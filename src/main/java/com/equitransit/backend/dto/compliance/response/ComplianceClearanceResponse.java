package com.equitransit.backend.dto.compliance.response;


import java.time.OffsetDateTime;


/**
 * Public F08 result without storage keys.
 * @param clearanceId the clearanceId value
 * @param revisionId the revisionId value
 * @param specialistId the specialistId value
 * @param phaseCode the phaseCode value
 * @param status the status value
 * @param checkedAt the checkedAt value
 * @param expiresAt the expiresAt value
 * @param assessmentNotes the assessmentNotes value
 */
public record ComplianceClearanceResponse(
        long clearanceId,
        long revisionId,
        Long specialistId,
        String phaseCode,
        String status,
        OffsetDateTime checkedAt,
        OffsetDateTime expiresAt,
        String assessmentNotes) {
}
