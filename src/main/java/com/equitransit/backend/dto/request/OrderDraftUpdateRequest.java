/*
 * OrderDraftUpdateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiExamples;
import com.equitransit.backend.dto.ApiPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;


/** Full draft replacement with the last observed database version. */
@Schema(example = ApiExamples.DRAFT_UPDATE)
public class OrderDraftUpdateRequest extends OrderDraftRequest {

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
