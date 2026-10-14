/*
 * OrderRevisionRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.request;


import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/** New submitted version or unapplied change request, never an overwrite. */
public class OrderRevisionRequest extends OrderDraftUpdateRequest {

    @NotNull
    private UUID operationId;
    @NotBlank
    @Size(max = 500)
    private String changeReason;

    /**
     * Returns operationId.
     * @return the operationId
     */
    public UUID getOperationId() {
        return operationId;
    }

    /**
     * Sets operationId.
     * @param operationId the value to store
     */
    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    /**
     * Returns changeReason.
     * @return the changeReason
     */
    public String getChangeReason() {
        return changeReason;
    }

    /**
     * Sets changeReason.
     * @param changeReason the value to store
     */
    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }
}
