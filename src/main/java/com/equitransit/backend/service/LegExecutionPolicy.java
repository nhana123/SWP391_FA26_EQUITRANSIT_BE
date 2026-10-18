package com.equitransit.backend.service;


import com.equitransit.backend.dto.execution.ExecutionModels.SourceCode;
import com.equitransit.backend.exception.OrderException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;


/** F12 event time/source/quantity rules, independent of persistence. */
public final class LegExecutionPolicy {
    private LegExecutionPolicy() { }

    /** @param mode real transport mode @param source verified source */
    public static void source(String mode, SourceCode source) {
        require(source != null && !("AIR".equals(mode)
                && source == SourceCode.DRIVER_REPORT), "AIR_SOURCE_REQUIRED");
    }

    /** @param event actual fact @param start departure @param end arrival
     * @param now server time; late/out-of-order facts remain append-only */
    public static void time(OffsetDateTime event, OffsetDateTime start,
            OffsetDateTime end, OffsetDateTime now) {
        require(event != null && start != null && !event.isBefore(start)
                && !event.isAfter(now)
                && (end == null || !event.isAfter(end)),
                "EXECUTION_TIME_INVALID");
    }

    /** @param value bounded decimal input @return exact decimal or null */
    public static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    /** @param latitude bounded input @param longitude bounded input */
    public static void coordinates(String latitude, String longitude) {
        require((latitude == null) == (longitude == null),
                "COORDINATE_PAIR_REQUIRED");
        if (latitude != null) {
            require(decimal(latitude).abs().compareTo(
                    BigDecimal.valueOf(90)) <= 0
                    && decimal(longitude).abs().compareTo(
                            BigDecimal.valueOf(180)) <= 0,
                    "COORDINATES_OUT_OF_RANGE");
        }
    }

    /** @param condition invariant @param code stable frontend error */
    public static void require(boolean condition, String code) {
        if (!condition) {
            throw new OrderException(HttpStatus.UNPROCESSABLE_ENTITY,
                    code, "Execution evidence does not satisfy " + code);
        }
    }
}
