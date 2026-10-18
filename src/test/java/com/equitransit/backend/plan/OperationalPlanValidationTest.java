package com.equitransit.backend.plan;

import com.equitransit.backend.dto.plan.PlanModels.PlanLegInput;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.service.plan.OperationalPlanPolicy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Concrete invalid-input and UTC-equivalent itinerary boundaries. */
public class OperationalPlanValidationTest {

    private final OffsetDateTime start =
            OffsetDateTime.parse("2027-01-01T06:00:00Z");

    /** A numeric zone offset is not a named local timezone. */
    @Test
    public void namedTimezoneIsRequired() {
        assertThatThrownBy(() -> OperationalPlanPolicy.validateLegs(List.of(
                input(1, "A", "B", "source", "+07:00", start))))
                .hasMessageContaining("IANA timezone");
    }

    /** Unknown timezone spellings cannot silently fall back to UTC. */
    @Test
    public void unknownTimezoneIsRejected() {
        assertThatThrownBy(() -> OperationalPlanPolicy.validateLegs(List.of(
                input(1, "A", "B", "source", "Asia/Hanoi", start))))
                .hasMessageContaining("IANA timezone");
    }

    /** An unverified distance is never accepted as a sourced itinerary. */
    @Test
    public void sourceIsMandatory() {
        assertThatThrownBy(() -> OperationalPlanPolicy.validateLegs(List.of(
                input(1, "A", "B", null, "UTC", start))))
                .hasMessageContaining("source");
    }

    /** UTC-equivalent offsets do not create a fake connection gap. */
    @Test
    public void equivalentOffsetsAndWhitespaceEndpointsConnect() {
        OperationalPlanPolicy.validateLegs(List.of(
                input(1, "A", " Noi  Bai ", "source", "UTC", start),
                input(2, "noi bai", "C", "source", "Asia/Singapore",
                        OffsetDateTime.parse("2027-01-01T16:00:00+08:00"))));
    }

    /** A zero distance never feeds a charge calculation. */
    @Test
    public void zeroDistanceIsRejected() {
        var leg = new PlanLegInput(1, PlanLegMode.ROAD, "A", "B",
                BigDecimal.ZERO, "source", start, start.plusHours(2),
                "UTC", "UTC", null, null, 0, 0, null);

        assertThatThrownBy(() -> OperationalPlanPolicy.validateLegs(
                List.of(leg))).hasMessageContaining("positive");
    }

    /** AIR cannot be recorded with no flight number or carrier. */
    @Test
    public void airRequiresAFlightAndCarrier() {
        var leg = new PlanLegInput(1, PlanLegMode.AIR, "A", "B",
                BigDecimal.TEN, "source", start, start.plusHours(2),
                "UTC", "UTC", null, null, 0, 0, null);

        assertThatThrownBy(() -> OperationalPlanPolicy.validateLegs(
                List.of(leg))).hasMessageContaining("flight number");
    }

    /** A connected ROAD, AIR, ROAD chain respects the transfer window. */
    @Test
    public void completeMultimodalChainIsValid() {
        var first = input(1, "Stable", "SGN", "maps", "Asia/Ho_Chi_Minh",
                start);
        var flight = new PlanLegInput(2, PlanLegMode.AIR, "SGN", "SIN",
                BigDecimal.valueOf(1100), "carrier route",
                start.plusHours(3), start.plusHours(5), "Asia/Ho_Chi_Minh",
                "Asia/Singapore", 21L, "VN51", 60, 0, null);
        var last = input(3, "SIN", "Facility", "maps", "Asia/Singapore",
                start.plusHours(6));

        OperationalPlanPolicy.validateLegs(List.of(first, flight, last));
    }

    private PlanLegInput input(int sequence, String from, String to,
            String source, String zone, OffsetDateTime departure) {
        return new PlanLegInput(sequence, PlanLegMode.ROAD, from, to,
                BigDecimal.TEN, source, departure, departure.plusHours(2),
                zone, zone, null, null, 0, 0, null);
    }
}
