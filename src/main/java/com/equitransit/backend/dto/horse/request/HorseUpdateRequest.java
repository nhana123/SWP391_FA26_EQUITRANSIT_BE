/*
 * HorseUpdateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement customer-owned horse profiles.
 */
package com.equitransit.backend.dto.horse.request;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.common.ApiExamples;
import com.equitransit.backend.dto.common.ApiPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;


/** Replacement profile carrying the last version returned by the server. */
@Schema(example = ApiExamples.PROFILE_UPDATE)
public class HorseUpdateRequest extends HorseProfileRequest {

    @NotBlank
    @Pattern(regexp = ApiPatterns.VERSION)
    @Schema(
            example = "000000000001A2B3",
            pattern = ApiPatterns.VERSION)
    private String recordVersion;

    /**
     * Returns recordVersion.
     * @return the recordVersion
     */
    public String getRecordVersion() {
        return recordVersion;
    }

    /**
     * Sets recordVersion.
     * @param recordVersion the value to store
     */
    public void setRecordVersion(String recordVersion) {
        this.recordVersion = recordVersion;
    }
}
