package com.equitransit.backend.dto.response;


import java.time.OffsetDateTime;


/**
 * Public F08 result without storage keys.
 * @param documentId the documentId value
 * @param horseId the horseId value
 * @param documentType the documentType value
 * @param createdAt the createdAt value
 */
public record HorseDocumentResponse(
        long documentId,
        long horseId,
        String documentType,
        OffsetDateTime createdAt) {
}
