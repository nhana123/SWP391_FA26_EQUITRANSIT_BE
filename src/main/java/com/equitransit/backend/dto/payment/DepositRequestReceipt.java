package com.equitransit.backend.dto.payment;

import com.equitransit.backend.dto.ApiPatterns;
import io.swagger.v3.oas.annotations.media.Schema;

/** @param orderId parent @param recordVersion refreshed version
 * @param depositRequested durable request and live payment state */
public record DepositRequestReceipt(
        @Schema(example = "101", minimum = "1") long orderId,
        @Schema(example = "000000000001A2B3", pattern = ApiPatterns.VERSION)
        String recordVersion,
        DepositRequested depositRequested) {
}
