/*
 * OrderSnapshot
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.response;


import java.util.UUID;
import java.util.List;
import java.time.OffsetDateTime;
import com.equitransit.backend.dto.request.OrderDraftRequest;
import com.equitransit.backend.entity.OrderServiceIntent;


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
public record OrderSnapshot(
        int schemaVersion,
        UUID operationId,
        String operationType,
        String requestHash,
        String sourceOrderVersion,
        long orderId,
        int revisionNo,
        OffsetDateTime submittedAt,
        OrderDraftRequest request,
        HorseResponse horse,
        List<OrderServiceIntent> services) {
}
