/* F14 closure receipt. */
package com.equitransit.backend.dto.handover.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @param orderId closed order
 * @param orderStatus CLOSED
 * @param recordVersion resulting SQL rowversion in hexadecimal
 * @param operationId stable retry identifier
 * @param updatedAt persisted UTC update time
 */
public record OrderClosureResponse(long orderId, String orderStatus,
        String recordVersion, UUID operationId, OffsetDateTime updatedAt) {
}
