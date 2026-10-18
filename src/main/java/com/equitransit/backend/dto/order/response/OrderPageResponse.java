/*
 * OrderPageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.order.response;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;


/**
 * Explicit owner-filtered pagination response.
 * @param items the items
 * @param page the page
 * @param size the size
 * @param totalElements the totalElements
 * @param totalPages the totalPages
 */
public record OrderPageResponse(
        @Schema(
                example = "[{\"orderId\":101,\"orderNumber\":\"ET-20" +
                        "26-000101\",\"orderStatus\":\"SUBMITTED\"" +
                        ",\"currency\":\"USD\",\"recordVersion\":\"0" +
                        "00000000001A2B4\",\"currentRevisionId\"" +
                        ":201,\"currentPlanId\":null,\"createdAt" +
                        "\":\"2026-10-14T10:00:00Z\",\"updatedAt\"" +
                        ":\"2026-10-14T12:05:00Z\",\"draft\":{\"ho" +
                        "rseId\":12,\"originCountryCode\":\"VN\",\"" +
                        "destinationCountryCode\":\"SG\",\"origin" +
                        "Address\":\"12 Nguyen Hue, Ho Chi Minh" +
                        " City\",\"destinationAddress\":\"1 Turf " +
                        "Club Avenue, Singapore\",\"originTimez" +
                        "one\":\"Asia/Ho_Chi_Minh\",\"destination" +
                        "Timezone\":\"Asia/Singapore\",\"preferre" +
                        "dDepartureAt\":\"2026-11-01T06:00:00Z\"" +
                        ",\"deliveryDeadline\":\"2026-11-03T18:0" +
                        "0:00Z\",\"importType\":\"TEMPORARY\",\"rec" +
                        "eiverName\":\"Alex Nguyen\",\"receiverPh" +
                        "one\":\"+6590123456\",\"receiverEmail\":\"" +
                        "receiver@equitransit.com\",\"receiverA" +
                        "uthority\":\"Authorized horse receiver" +
                        "\",\"eventStartAt\":\"2026-11-10T08:00:0" +
                        "0Z\",\"eventName\":\"Autumn Racing Event" +
                        "\",\"eventVenue\":\"Singapore\",\"eventTim" +
                        "ezone\":\"Asia/Singapore\",\"transitAirp" +
                        "orts\":[\"BKK\"],\"specialNeeds\":\"Calm h" +
                        "andling required.\",\"services\":{\"road" +
                        "Arrangement\":\"CUSTOMER\",\"veterinaryA" +
                        "rrangement\":\"CUSTOMER\",\"airStall\":\"W" +
                        "IDE\",\"enhancedCare\":false,\"groomingD" +
                        "ays\":2,\"boardingNights\":1,\"quarantin" +
                        "ePackage\":\"STANDARD\",\"insurance\":\"EX" +
                        "ISTING\",\"declaredValue\":\"50000.00\",\"" +
                        "existingPolicyReference\":\"POL-2026-0" +
                        "147\",\"escortNotes\":\"Escort support r" +
                        "equested for airport handover.\",\"roa" +
                        "dProviderName\":\"Customer road provid" +
                        "er\",\"roadProviderContact\":\"+84901234" +
                        "567\",\"veterinaryProviderName\":\"Custo" +
                        "mer veterinary provider\",\"veterinary" +
                        "ProviderContact\":\"+84909876543\"}}}]")
        List<OrderResponse> items,
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
