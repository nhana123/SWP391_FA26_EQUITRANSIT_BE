/*
 * HorsePageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement customer-owned horse profiles.
 */
package com.equitransit.backend.dto.horse.response;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;


/**
 * Stable pagination contract, without exposing Spring Page internals.
 * @param items the profiles on this page
 * @param page the zero-based page
 * @param size the requested size
 * @param totalElements the owner's matching record count
 * @param totalPages the number of matching pages
 */
public record HorsePageResponse(
        @Schema(
                example = "[{\"horseId\":12,\"name\":\"Thunder Strik" +
                        "e\",\"microchipId\":\"985112003456789\",\"" +
                        "passportNumber\":\"FEI-10459921\",\"bree" +
                        "d\":\"Thoroughbred\",\"dateOfBirth\":\"201" +
                        "9-04-15\",\"sexCode\":\"GELDING\",\"weight" +
                        "Kg\":520,\"heightCm\":165,\"bodyWidthCm\"" +
                        ":80,\"measuredAt\":\"2026-10-13T08:00:0" +
                        "0Z\",\"recordVersion\":\"000000000001A2B" +
                        "3\",\"measurementsComplete\":true}]")
        List<HorseResponse> items,
        @Schema(
                example = "0",
                minimum = "0")
        int page,
        @Schema(
                example = "20",
                minimum = "1",
                maximum = "100")
        int size,
        @Schema(
                example = "1",
                minimum = "0")
        long totalElements,
        @Schema(
                example = "1",
                minimum = "0")
        long totalPages) {
}
