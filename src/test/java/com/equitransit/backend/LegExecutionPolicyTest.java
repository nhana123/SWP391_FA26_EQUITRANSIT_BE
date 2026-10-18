package com.equitransit.backend;


import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import com.equitransit.backend.dto.execution.ExecutionModels.SourceCode;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.service.LegExecutionPolicy;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;


/** Actual timestamps and externally sourced AIR observations are mandatory. */
public class LegExecutionPolicyTest {
    private final OffsetDateTime start = OffsetDateTime.parse(
            "2027-02-01T08:00:00Z");

    /** Equivalent offsets and late recording preserve actual instants. */
    @Test
    public void acceptsActualTimeInsideLegWithDifferentOffset() {
        assertThatCode(() -> LegExecutionPolicy.time(
                OffsetDateTime.parse("2027-02-01T15:05:00+07:00"),
                start, start.plusHours(1), start.plusHours(2)))
                .doesNotThrowAnyException();
    }

    /** Future events, pre-departure facts and post-arrival facts fail. */
    @Test
    public void rejectsInvalidChronology() {
        for (var event : new OffsetDateTime[] {start.minusSeconds(1),
                start.plusHours(3), start.plusHours(1).plusSeconds(1)}) {
            assertThatThrownBy(() -> LegExecutionPolicy.time(event, start,
                    start.plusHours(1), start.plusHours(2)))
                    .isInstanceOf(OrderException.class);
        }
    }

    /** AIR movements must never be represented as driver GPS. */
    @Test
    public void rejectsDriverAsAirSource() {
        assertThatThrownBy(() -> LegExecutionPolicy.source("AIR",
                SourceCode.DRIVER_REPORT)).isInstanceOf(OrderException.class);
        assertThatCode(() -> LegExecutionPolicy.source("AIR",
                SourceCode.CARRIER)).doesNotThrowAnyException();
    }

    /** Geography has inclusive bounds and requires a coordinate pair. */
    @Test
    public void validatesCoordinateBoundsAndPair() {
        assertThatCode(() -> LegExecutionPolicy.coordinates("-90", "180"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> LegExecutionPolicy.coordinates("90.000001",
                "180")).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> LegExecutionPolicy.coordinates("10", null))
                .isInstanceOf(OrderException.class);
    }
}
