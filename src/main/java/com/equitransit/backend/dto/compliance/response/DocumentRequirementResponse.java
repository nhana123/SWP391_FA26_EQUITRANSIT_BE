package com.equitransit.backend.dto.compliance.response;


import java.time.OffsetDateTime;


/**
 * Public F08 result without storage keys.
 * @param requirementId the requirementId value
 * @param revisionId the revisionId value
 * @param ruleId the ruleId value
 * @param documentType the documentType value
 * @param phaseCode the phaseCode value
 * @param mandatory the mandatory value
 * @param status the status value
 * @param instructions the instructions value
 * @param dueAt the dueAt value
 */
public record DocumentRequirementResponse(
        long requirementId,
        long revisionId,
        long ruleId,
        String documentType,
        String phaseCode,
        boolean mandatory,
        String status,
        String instructions,
        OffsetDateTime dueAt) {
}
