/*
 * CasePageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned request assessment and case responsibility.
 */
package com.equitransit.backend.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;


/**
 * Typed case contract without account credentials or supplier finance.
 * @param items visible queue page
 * @param page zero-based offset
 * @param size bounded page size
 * @param totalElements visible count
 * @param totalPages page count
 */
public record CasePageResponse(
        @Schema(
                example = "[{\"orderId\":101,\"orderNumber\":\"ET-20" +
                        "26-000101\",\"orderStatus\":\"SUBMITTED\"" +
                        ",\"recordVersion\":\"000000000001A2B3\"," +
                        "\"currentRevisionId\":201,\"originCount" +
                        "ryCode\":\"VN\",\"destinationCountryCode" +
                        "\":\"SG\",\"deliveryDeadline\":\"2026-11-0" +
                        "3T18:00:00Z\"}]")
        List<CaseSummaryResponse> items,
        @Schema(
                example = "0",
                minimum = "0")
        int page,
        @Schema(
                example = "20",
                minimum = "1",
                maximum = "100")
        int size,
        @Schema(
                example = "1",
                minimum = "0")
        long totalElements,
        @Schema(
                example = "1",
                minimum = "0")
        long totalPages) {
}
