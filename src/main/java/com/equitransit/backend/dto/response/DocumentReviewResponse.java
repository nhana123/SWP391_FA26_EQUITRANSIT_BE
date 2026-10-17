package com.equitransit.backend.dto.response;


import java.time.OffsetDateTime;


/**
 * Public F08 result without storage keys.
 * @param reviewId the reviewId value
 * @param submissionId the submissionId value
 * @param reviewedBy the reviewedBy value
 * @param reviewState the reviewState value
 * @param publicNote the publicNote value
 * @param reviewedAt the reviewedAt value
 */
public record DocumentReviewResponse(
        long reviewId,
        long submissionId,
        Long reviewedBy,
        String reviewState,
        String publicNote,
        OffsetDateTime reviewedAt) {
}
