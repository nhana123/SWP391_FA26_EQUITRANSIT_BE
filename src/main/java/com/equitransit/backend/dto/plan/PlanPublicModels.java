package com.equitransit.backend.dto.plan;

import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Public role projections keep internal proof private. */
public final class PlanPublicModels {

    private PlanPublicModels() { }

    /** Public stop without prices or internal identities. */
    public record PlanPublicLeg(
            @Schema(example = "1") int sequenceNo,
            @Schema(example = "ROAD") PlanLegMode transportMode,
            @Schema(example = "Hanoi Stable") String startPoint,
            @Schema(example = "Noi Bai Airport") String endPoint,
            @Schema(example = "2027-02-01T06:00:00Z")
            OffsetDateTime departureAt,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime arrivalAt,
            @Schema(example = "Asia/Ho_Chi_Minh") String startTimezone,
            @Schema(example = "Asia/Ho_Chi_Minh") String endTimezone,
            @Schema(example = "VN0051") String flightNumber,
            @Schema(example = "Vietnam Airlines") String airlineName,
            @Schema(example = "SHARED") String stallSharing) { }

    /** Current operational itinerary; creating a draft never replaces it. */
    public record PlanPublicItinerary(
            @Schema(example = "101") long orderId,
            @Schema(example = "2") int versionNo,
            @Schema(example = "45.500") BigDecimal roadKm,
            @Schema(example = "2100.000") BigDecimal airKm,
            @Schema(example = "[]") List<PlanPublicLeg> legs) { }

    /** Assigned Coordinator contact for operational emergencies. */
    public record PlanEmergencyContact(
            @Schema(example = "Nguyen Van An") String name,
            @Schema(example = "+84901234567") String phone) { }

    /** Driver's own ROAD task and its designated emergency contact. */
    public record PlanDriverLeg(
            @Schema(example = "401") long legId,
            @Schema(example = "1") int sequenceNo,
            @Schema(example = "Hanoi Stable") String startPoint,
            @Schema(example = "Noi Bai Airport") String endPoint,
            @Schema(example = "2027-02-01T06:00:00Z")
            OffsetDateTime departureAt,
            @Schema(example = "2027-02-01T08:00:00Z")
            OffsetDateTime arrivalAt,
            @Schema(example = "Asia/Ho_Chi_Minh") String startTimezone,
            @Schema(example = "Asia/Ho_Chi_Minh") String endTimezone,
            @Schema(example = "Provide water before boarding.")
            String careInstructions,
            @Schema(example = "{\"name\":\"Nguyen Van An\","
                    + "\"phone\":\"+84901234567\"}",
                    description = "Active assigned Coordinator; null when "
                            + "no contact phone is available.")
            PlanEmergencyContact emergencyContact) { }
}
