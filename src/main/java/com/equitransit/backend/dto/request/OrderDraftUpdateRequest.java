/*
 * OrderDraftUpdateRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;


/** Full draft replacement with the last observed database version. */
public class OrderDraftUpdateRequest extends OrderDraftRequest {

    @NotBlank
    @Pattern(regexp = "[0-9A-Fa-f]{16}")
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
