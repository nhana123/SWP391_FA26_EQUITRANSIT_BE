/*
 * OrderSnapshot
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.order.response;

import com.equitransit.backend.dto.horse.response.HorseResponse;



import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.common.ApiPatterns;
import com.equitransit.backend.dto.common.ApiExamples;
import java.util.UUID;
import java.util.List;
import java.time.OffsetDateTime;
import com.equitransit.backend.dto.order.request.OrderDraftRequest;
import com.equitransit.backend.entity.order.OrderServiceIntent;


/**
 * Versioned request/horse/services captured atomically at submission.
 * @param schemaVersion the schemaVersion
 * @param operationId the operationId
 * @param operationType the operationType
 * @param requestHash the requestHash
 * @param sourceOrderVersion the sourceOrderVersion
 * @param orderId the orderId
 * @param revisionNo the revisionNo
 * @param submittedAt the submittedAt
 * @param request the request
 * @param horse the horse
 * @param services the services
 */
@Schema(example = ApiExamples.SNAPSHOT)
public record OrderSnapshot(
        @Schema(
                example = "1",
                minimum = "1")
        int schemaVersion,
        @Schema(
                example = "fa2c8d7b-6c51-4976-a28d-613c4199cfd6",
                format = "uuid")
        UUID operationId,
        @Schema(
                example = "SUBMIT",
                allowableValues = {"SUBMIT", "REVISE", "CHANGE"})
        String operationType,
        @Schema(
                example = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                pattern = ApiPatterns.HASH)
        String requestHash,
        @Schema(
                example = "000000000001A2B3",
                pattern = ApiPatterns.VERSION)
        String sourceOrderVersion,
        @Schema(
                example = "101",
                minimum = "1")
        long orderId,
        @Schema(
                example = "1",
                minimum = "1")
        int revisionNo,
        @Schema(
                example = "2026-10-14T12:00:00Z",
                format = "date-time")
        OffsetDateTime submittedAt,
        @Schema(
                example = ApiExamples.DRAFT)
        OrderDraftRequest request,
        @Schema(
                example = ApiExamples.HORSE)
        HorseResponse horse,
        @Schema(
                example = ApiExamples.INTENTS)
        List<OrderServiceIntent> services) {
}
