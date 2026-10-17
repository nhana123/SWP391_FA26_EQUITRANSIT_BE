package com.equitransit.backend.dto.request;


import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/**
 * Bounded F08 command.
 * @param phaseCode the phaseCode value
 * @param status the status value
 * @param expiresAt the expiresAt value
 * @param assessmentNotes the assessmentNotes value
 */
public record ComplianceClearanceRequest(
        @NotBlank @Size(max = 50) String phaseCode,
        @NotBlank @Size(max = 50) String status,
        OffsetDateTime expiresAt,
        @Size(max = 2000) String assessmentNotes) {
}
