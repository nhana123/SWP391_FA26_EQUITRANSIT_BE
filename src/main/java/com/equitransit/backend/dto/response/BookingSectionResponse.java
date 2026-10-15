/*
 * BookingSectionResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Publish the seven booking sections without default selections.
 */
package com.equitransit.backend.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;


/**
 * Stable section display contract; option codes are not selected implicitly.
 * @param code stable section key
 * @param title customer display heading
 * @param options allowed/requestable preference codes, not reservations
 */
public record BookingSectionResponse(
        @Schema(
                example = "AIR_STALL",
                allowableValues = {
                        "TRANSPORTATION",
                        "AIR_STALL",
                        "HORSE_CARE",
                        "VETERINARY",
                        "QUARANTINE",
                        "INSURANCE",
                        "ADDITIONAL_CHARGES"})
        String code,
        @Schema(
                example = "Available Air Stalls for Your Horse")
        String title,
        @Schema(
                example = "[\"WIDE\",\"PRIVATE\"]")
        List<String> options) {
}
