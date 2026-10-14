/*
 * HorseResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement customer-owned horse profiles.
 */
package com.equitransit.backend.dto.response;


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
public record HorseResponse(
        Long horseId,
        String name,
        String microchipId,
        String passportNumber,
        String breed,
        LocalDate dateOfBirth,
        HorseSex sexCode,
        BigDecimal weightKg,
        BigDecimal heightCm,
        BigDecimal bodyWidthCm,
        OffsetDateTime measuredAt,
        String specialCareNotes,
        String handlingNotes,
        String feedWaterRequirements,
        String stallPreferences,
        String veterinaryInstructions,
        String veterinaryInstructionSource,
        String recordVersion,
        boolean measurementsComplete) {
}
