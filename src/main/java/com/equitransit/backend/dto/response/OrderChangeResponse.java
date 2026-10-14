/*
 * OrderChangeResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;


/**
 * Unapplied proposal for the controlled F13 workflow.
 * @param changeRequestId the changeRequestId
 * @param orderId the orderId
 * @param baseRevisionId the baseRevisionId
 * @param status the status
 * @param createdAt the createdAt
 */
public record OrderChangeResponse(
        @Schema(
                example = "31",
                minimum = "1")
        long changeRequestId,
        @Schema(
                example = "101",
                minimum = "1")
        long orderId,
        @Schema(
                example = "201",
                nullable = true,
                minimum = "1")
        Long baseRevisionId,
        @Schema(
                example = "PENDING",
                allowableValues = {
                        "PENDING",
                        "APPROVED",
                        "REJECTED",
                        "APPLIED",
                        "CANCELLED"})
        String status,
        @Schema(
                example = "2026-10-14T10:00:00Z",
                format = "date-time")
        OffsetDateTime createdAt) {
}
