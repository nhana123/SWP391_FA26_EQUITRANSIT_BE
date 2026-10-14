/*
 * OrderRevisionResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiExamples;
import java.time.OffsetDateTime;


/**
 * Immutable revision, also returned identically for safe retries.
 * @param revisionId the revisionId
 * @param orderId the orderId
 * @param revisionNo the revisionNo
 * @param changeReason the changeReason
 * @param submittedAt the submittedAt
 * @param snapshot the snapshot
 */
@Schema(example = ApiExamples.REVISION)
public record OrderRevisionResponse(
        @Schema(
                example = "201",
                nullable = true,
                minimum = "1")
        long revisionId,
        @Schema(
                example = "101",
                minimum = "1")
        long orderId,
        @Schema(
                example = "1",
                minimum = "1")
        int revisionNo,
        @Schema(
                example = "Update delivery schedule.",
                nullable = true)
        String changeReason,
        @Schema(
                example = "2026-10-14T12:00:00Z",
                format = "date-time")
        OffsetDateTime submittedAt,
        @Schema(
                example = ApiExamples.SNAPSHOT)
        OrderSnapshot snapshot) {
}
