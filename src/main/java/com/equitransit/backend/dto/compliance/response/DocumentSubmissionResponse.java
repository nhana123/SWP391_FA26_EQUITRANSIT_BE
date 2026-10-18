package com.equitransit.backend.dto.compliance.response;


import java.time.OffsetDateTime;


/**
 * Public F08 result without storage keys.
 * @param submissionId the submissionId value
 * @param requirementId the requirementId value
 * @param documentVersionId the documentVersionId value
 * @param submittedAt the submittedAt value
 */
public record DocumentSubmissionResponse(
        long submissionId,
        long requirementId,
        long documentVersionId,
        OffsetDateTime submittedAt) {
}
