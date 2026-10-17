/*
 * ResourceFitEvaluatorTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Complete scoped resource and fit workflows.
 */
package com.equitransit.backend;

import com.equitransit.backend.dto.response.HorseResponse;
import com.equitransit.backend.entity.AirStallPreference;
import com.equitransit.backend.entity.FitPolicyRecord;
import com.equitransit.backend.service.StallFitEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Shared F05/F09 evaluation respects the captured completeness contract. */
class ResourceFitEvaluatorTest {
    private final StallFitEvaluator evaluator = new StallFitEvaluator();
    private final OffsetDateTime now =
            OffsetDateTime.parse("2027-01-01T00:00:00Z");

    @Test
    void absentHorseReturnsPendingInsteadOfThrowing() {
        assertThat(evaluator.evaluate(AirStallPreference.SHARED_ONE, null,
                List.of(), now).reasons())
                .contains("HORSE_MEASUREMENTS_REQUIRED");
    }

    @Test
    void falseCompletenessCannotBecomeEligibleDespiteNumericValues()
            throws Exception {
        var horse = new ObjectMapper().findAndRegisterModules().readValue(
                "{\"weightKg\":550,\"heightCm\":170,\"bodyWidthCm\":70,"
                + "\"measuredAt\":\"2026-12-01T00:00:00Z\","
                + "\"measurementsComplete\":false}", HorseResponse.class);
        var policy = new FitPolicyRecord(1, "SYNTHETIC", 1, BigDecimal.ONE,
                new BigDecimal("700"), new BigDecimal("190"),
                new BigDecimal("90"));
        assertThat(evaluator.evaluate(AirStallPreference.SHARED_ONE, horse,
                List.of(policy), now).status()).isEqualTo("PENDING");
    }
}
