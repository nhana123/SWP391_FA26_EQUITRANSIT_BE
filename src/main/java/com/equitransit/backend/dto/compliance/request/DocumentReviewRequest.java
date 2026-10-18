package com.equitransit.backend.dto.compliance.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/**
 * Bounded F08 command.
 * @param reviewState the reviewState value
 * @param publicNote the publicNote value
 */
public record DocumentReviewRequest(
        @NotBlank @Size(max = 50) String reviewState,
        @Size(max = 2000) String publicNote) {
}
