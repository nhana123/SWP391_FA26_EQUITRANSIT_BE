/*
 * HorseResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement customer-owned horse profiles.
 */
package com.equitransit.backend.dto.response;


import com.equitransit.backend.dto.ApiExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiPatterns;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.equitransit.backend.entity.HorseSex;


/**
 * Safe owner-visible profile; complete measurements do not imply clearance.
 * @param horseId the horseId
 * @param name the name
 * @param microchipId the microchipId
 * @param passportNumber the passportNumber
 * @param breed the breed
 * @param dateOfBirth the dateOfBirth
 * @param sexCode the sexCode
 * @param weightKg the weightKg
 * @param heightCm the heightCm
 * @param bodyWidthCm the bodyWidthCm
 * @param measuredAt the measuredAt
 * @param specialCareNotes the specialCareNotes
 * @param handlingNotes the handlingNotes
 * @param feedWaterRequirements the feedWaterRequirements
 * @param stallPreferences the stallPreferences
 * @param veterinaryInstructions the veterinaryInstructions
 * @param veterinaryInstructionSource the veterinaryInstructionSource
 * @param recordVersion the recordVersion
 * @param measurementsComplete the measurementsComplete
 */
@Schema(example = ApiExamples.HORSE)
public record HorseResponse(
        @Schema(
                example = "12",
                minimum = "1")
        Long horseId,
        @Schema(
                example = "Thunder Strike")
        String name,
        @Schema(
                example = "985112003456789",
                nullable = true,
                pattern = ApiPatterns.MICROCHIP)
        String microchipId,
        @Schema(
                example = "FEI-10459921",
                nullable = true,
                pattern = ApiPatterns.PASSPORT)
        String passportNumber,
        @Schema(
                example = "Thoroughbred",
                nullable = true)
        String breed,
        @Schema(
                example = "2019-04-15",
                nullable = true,
                format = "date")
        LocalDate dateOfBirth,
        @Schema(
                example = "GELDING",
                nullable = true)
        HorseSex sexCode,
        @Schema(
                example = "520.00",
                nullable = true,
                minimum = "0",
                exclusiveMinimum = true)
        BigDecimal weightKg,
        @Schema(
                example = "165.00",
                nullable = true,
                minimum = "0",
                exclusiveMinimum = true)
        BigDecimal heightCm,
        @Schema(
                example = "80.00",
                nullable = true,
                minimum = "0",
                exclusiveMinimum = true)
        BigDecimal bodyWidthCm,
        @Schema(
                example = "2026-10-13T08:00:00Z",
                nullable = true,
                format = "date-time")
        OffsetDateTime measuredAt,
        @Schema(
                example = "Calm handling required.",
                nullable = true)
        String specialCareNotes,
        @Schema(
                example = "Lead slowly.",
                nullable = true)
        String handlingNotes,
        @Schema(
                example = "Provide fresh water during stops.",
                nullable = true)
        String feedWaterRequirements,
        @Schema(
                example = "Wide stall requested.",
                nullable = true)
        String stallPreferences,
        @Schema(
                example = "Follow the supplied veterinary instructions.",
                nullable = true)
        String veterinaryInstructions,
        @Schema(
                example = "Owner-provided veterinary note.",
                nullable = true)
        String veterinaryInstructionSource,
        @Schema(
                example = "000000000001A2B3",
                pattern = ApiPatterns.VERSION)
        String recordVersion,
        @Schema(
                example = "true")
        boolean measurementsComplete) {
}
