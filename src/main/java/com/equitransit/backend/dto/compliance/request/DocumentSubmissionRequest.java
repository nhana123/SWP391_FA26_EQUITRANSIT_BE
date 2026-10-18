package com.equitransit.backend.dto.compliance.request;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


/**
 * Bounded F08 command.
 * @param documentVersionId the documentVersionId value
 */
public record DocumentSubmissionRequest(
        @NotNull @Positive Long documentVersionId) {
}
