/*
 * OrderRevisionRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.order.request;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.common.ApiExamples;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/** New submitted version or unapplied change request, never an overwrite. */
@Schema(example = ApiExamples.REVISION_COMMAND)
public class OrderRevisionRequest extends OrderDraftUpdateRequest {

    @NotNull
    @Schema(
            example = "fa2c8d7b-6c51-4976-a28d-613c4199cfd6",
            format = "uuid")
    private UUID operationId;
    @NotBlank
    @Size(min = 1, max = 500)
    @Schema(
            example = "Update delivery schedule.",
            nullable = true)
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
