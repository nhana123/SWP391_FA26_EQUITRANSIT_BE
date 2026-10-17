package com.equitransit.backend.dto.request;


import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


/**
 * Bounded F08 command.
 * @param ruleId the ruleId value
 * @param documentType the documentType value
 * @param phaseCode the phaseCode value
 * @param mandatory the mandatory value
 * @param horseId the horseId value
 * @param instructions the instructions value
 * @param caseJustification the caseJustification value
 * @param dueAt the dueAt value
 */
public record DocumentRequirementRequest(
        @NotNull @Positive Long ruleId,
        @NotBlank @Size(max = 50) String documentType,
        @NotBlank @Size(max = 50) String phaseCode,
        boolean mandatory,
        @Positive Long horseId,
        @Size(max = 2000) String instructions,
        @Size(max = 2000) String caseJustification,
        OffsetDateTime dueAt) {
}
