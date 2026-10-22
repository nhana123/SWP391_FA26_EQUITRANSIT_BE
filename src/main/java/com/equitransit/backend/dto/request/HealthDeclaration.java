/*
 * HealthDeclaration
 * Author: EquiTransit development team
 * Date: 2026-10-22
 * Copyright: EquiTransit project contributors.
 * Purpose: Capture the customer's initial health and vaccination statement.
 */
package com.equitransit.backend.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.validation.OptionalText;
import java.time.LocalDate;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;


/**
 * Customer statement captured with the request for the Specialist dossier.
 * It is a declaration, never veterinary or legal evidence: certificates
 * still go through the F08 document checklist.
 * @param influenzaVaccinated whether the horse is vaccinated against equine
 *        influenza, null while the customer has not answered
 * @param lastInfluenzaVaccinationDate the last equine influenza dose
 * @param vaccinationRecordReference the customer's vaccination record or
 *        passport page reference
 * @param healthNotes current health remarks for the Specialist
 * @param declaredAccurate whether the customer confirmed the statement
 */
public record HealthDeclaration(
        @Schema(
                example = "true",
                nullable = true)
        Boolean influenzaVaccinated,
        @PastOrPresent
        @Schema(
                example = "2026-08-20",
                nullable = true,
                format = "date")
        LocalDate lastInfluenzaVaccinationDate,
        @Size(min = 1, max = 100)
        @Schema(
                example = "FEI passport p.12",
                nullable = true)
        @OptionalText
        String vaccinationRecordReference,
        @Size(min = 1, max = 1000)
        @Schema(
                example = "No illness in the last 30 days.",
                nullable = true)
        @OptionalText
        String healthNotes,
        @Schema(
                example = "true",
                nullable = true)
        Boolean declaredAccurate) {
}
