package com.equitransit.backend.dto.response;


import java.time.LocalDate;
import java.time.OffsetDateTime;


/**
 * Public F08 result without storage keys.
 * @param documentVersionId the documentVersionId value
 * @param documentId the documentId value
 * @param versionNo the versionNo value
 * @param documentNumber the documentNumber value
 * @param issuedDate the issuedDate value
 * @param expiryDate the expiryDate value
 * @param uploadedAt the uploadedAt value
 * @param originalFileName the originalFileName value
 * @param contentType the contentType value
 * @param fileSizeBytes the fileSizeBytes value
 */
public record DocumentVersionResponse(
        long documentVersionId,
        long documentId,
        int versionNo,
        String documentNumber,
        LocalDate issuedDate,
        LocalDate expiryDate,
        OffsetDateTime uploadedAt,
        String originalFileName,
        String contentType,
        Long fileSizeBytes) {
}
