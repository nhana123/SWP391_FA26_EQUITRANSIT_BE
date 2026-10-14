/*
 * OrderHorseData
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Keep horse snapshot SQL data independent from HTTP DTOs.
 */
package com.equitransit.backend.entity;


import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * Current order-horse write model, separate from immutable revision content.
 * @param horseId the selected owned horse
 * @param microchipId the captured chip
 * @param sexCode the captured sex
 * @param weightKg the captured kilograms
 * @param heightCm the captured height
 * @param bodyWidthCm the captured width
 * @param measuredAt the captured UTC measurement time
 * @param specialNeeds the request-specific instructions
 */
public record OrderHorseData(long horseId, String microchipId,
        String sexCode, BigDecimal weightKg, BigDecimal heightCm,
        BigDecimal bodyWidthCm, LocalDateTime measuredAt, String specialNeeds) {
}
