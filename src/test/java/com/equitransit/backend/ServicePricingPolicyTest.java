/*
 * ServicePricingPolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify measurements, units, decimal rounding and honest pending.
 */
package com.equitransit.backend;


import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.equitransit.backend.dto.response.HorseResponse;
import com.equitransit.backend.dto.response.ServiceChargeResponse;
import com.equitransit.backend.entity.AirStallPreference;
import com.equitransit.backend.entity.FitPolicyRecord;
import com.equitransit.backend.entity.TariffRecord;
import com.equitransit.backend.service.ChargeCalculator;
import com.equitransit.backend.service.StallFitEvaluator;
import static org.assertj.core.api.Assertions.assertThat;


/** Artificial unit fixtures are never installed as production price/policy. */
public class ServicePricingPolicyTest {

    private final StallFitEvaluator fit = new StallFitEvaluator();
    private final ChargeCalculator calculator = new ChargeCalculator();
    private final OffsetDateTime now =
            OffsetDateTime.parse("2026-10-14T00:00:00Z");
    private final FitPolicyRecord policy = new FitPolicyRecord(1,
                "TEST_ONLY", 1,
            BigDecimal.ONE, new BigDecimal("600"), new BigDecimal("190"),
            new BigDecimal("90"));
    private final TariffRecord rate = new TariffRecord(1, 1, "SLOT_KM",
            "HORSE_ACCOMMODATION", new BigDecimal("0.6"));

    /** Inclusive approved limits permit a measured one-slot request. */
    @Test
    public void inclusiveLimitsDoNotInventCarrierConfirmation()
            throws Exception {
        var result = fit.evaluate(AirStallPreference.SHARED_ONE,
                horse("600", "190", "90"), List.of(policy), now);

        assertThat(result.status()).isEqualTo("ELIGIBLE");
        assertThat(result.policyId()).isEqualTo(1);
        assertThat(result.slots()).isEqualByComparingTo("1");
    }

    /** Exceeding any independent dimension is unsuitable. */
    @Test
    public void everyDimensionIsEnforced() throws Exception {
        for (HorseResponse horse : List.of(horse("601", "190", "90"),
                horse("600", "191", "90"), horse("600", "190", "91"))) {
            assertThat(fit.evaluate(AirStallPreference.SHARED_ONE, horse,
                    List.of(policy), now).status()).isEqualTo("UNSUITABLE");
        }
    }

    /** Missing/invalid measurements remain pending even with a policy. */
    @Test
    public void missingAndInvalidMeasurementsAreNotEligible() throws Exception {
        assertThat(fit.evaluate(AirStallPreference.SHARED_ONE,
                horse(null, "190", "90"), List.of(policy), now).status())
                .isEqualTo("PENDING");
        assertThat(fit.evaluate(AirStallPreference.SHARED_ONE,
                horse("0", "190", "90"), List.of(policy), now).status())
                .isEqualTo("PENDING");
    }

    /** Conflicting source policies are not resolved by guessing a version. */
    @Test
    public void absentAndAmbiguousPoliciesStayPending() throws Exception {
        HorseResponse horse = horse("500", "160", "70");

        assertThat(fit.evaluate(AirStallPreference.SHARED_ONE, horse,
                List.of(), now).reasons()).contains("FIT_POLICY_PENDING");
        assertThat(fit.evaluate(AirStallPreference.SHARED_ONE, horse,
                List.of(policy, policy), now).reasons())
                .contains("FIT_POLICY_AMBIGUOUS");
    }

    /** Incomplete source limits cannot crash or approve the fit evaluator. */
    @Test
    public void incompletePolicyLimitsRemainPending() throws Exception {
        FitPolicyRecord incomplete = new FitPolicyRecord(1, "TEST_ONLY", 1,
                BigDecimal.ONE, null, new BigDecimal("190"),
                new BigDecimal("90"));

        assertThat(fit.evaluate(AirStallPreference.SHARED_ONE,
                horse("500", "160", "70"), List.of(incomplete), now).reasons())
                .contains("FIT_POLICY_CONFIGURATION_INVALID");
    }

    /**
     * Wide/private quantities are 1.5/3 configuration slots, not seat counts.
     */
    @Test
    public void configurationsUseExactDecimalSlots() {
        assertThat(StallFitEvaluator.slots(AirStallPreference.WIDE))
                .isEqualByComparingTo("1.5");
        assertThat(StallFitEvaluator.slots(AirStallPreference.PRIVATE))
                .isEqualByComparingTo("3");
    }

    /**
     * Exact decimal multiplication includes slot quantity before one rounding.
     */
    @Test
    public void slotAirKilometresAreMultipliedBeforeRounding() {
        var result = line(new BigDecimal("1.5").multiply(
                new BigDecimal("1000")), List.of(rate), null);

        assertThat(new BigDecimal(result.amount()))
                .isEqualByComparingTo("900.00");
        assertThat(result.pricingStatus()).isEqualTo("ESTIMATED");
    }

    /**
     * A known published rate is retained when quantity has not been assessed.
     */
    @Test
    public void missingQuantityIsNotZero() {
        var result = line(null, List.of(rate), null);

        assertThat(result.quantity()).isNull();
        assertThat(new BigDecimal(result.unitRate()))
                .isEqualByComparingTo("0.6");
        assertThat(result.amount()).isNull();
        assertThat(result.pricingStatus()).isEqualTo("QUANTITY_PENDING");
    }

    /** Missing or overlapping tariffs never become a default zero price. */
    @Test
    public void missingAndAmbiguousRatesAreExplicit() {
        assertThat(line(BigDecimal.TEN, List.of(), null).amount()).isNull();
        assertThat(line(BigDecimal.TEN, List.of(rate, rate), null).blockers())
                .contains("RATE_AMBIGUOUS");
        assertThat(line(null, List.of(), null).pricingStatus())
                .isEqualTo("RATE_AND_QUANTITY_PENDING");
    }

    /**
     * A wrong unit is invalid configuration, not a numeric conversion guess.
     */
    @Test
    public void wrongUnitCannotProduceAnEstimate() {
        TariffRecord invalid = new TariffRecord(2, 1, "HORSE_DAY",
                "HORSE_ACCOMMODATION", BigDecimal.TEN);

        assertThat(line(BigDecimal.TEN, List.of(invalid), null).blockers())
                .contains("RATE_CONFIGURATION_INVALID");
        assertThat(line(BigDecimal.TEN, List.of(invalid), null).amount())
                .isNull();
    }

    /** Inclusion does not charge the stall again despite a published rate. */
    @Test
    public void bundledAndEventOnlyRowsAreNotPayableItems() {
        assertThat(line(BigDecimal.TEN, List.of(rate), "INCLUDED").amount())
                .isNull();
        assertThat(line(null, List.of(rate), "EVENT_ONLY").amount()).isNull();
    }

    /** A genuine published zero differs from an unknown price. */
    @Test
    public void explicitZeroRateIsKnownAndRoundingIsHalfUp() {
        TariffRecord zero = new TariffRecord(2, 1, rate.unit(),
                rate.costGroup(),
                BigDecimal.ZERO);
        TariffRecord fraction = new TariffRecord(3, 1, rate.unit(),
                rate.costGroup(), new BigDecimal("1.005"));

        assertThat(new BigDecimal(line(BigDecimal.TEN, List.of(zero), null)
                .amount()))
                .isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(line(BigDecimal.ONE, List.of(fraction), null)
                .amount()))
                .isEqualByComparingTo("1.01");
    }

    private ServiceChargeResponse line(
            BigDecimal quantity, List<TariffRecord> rates, String override) {
        return calculator.line("AIR_STALL", "AIR_STALL", "HORSE_ACCOMMODATION",
                "Air stall", "SLOT_KM", "slots × AIR km × rate", true, true,
                quantity, rates, override);
    }

    private HorseResponse horse(String weight, String height, String width)
            throws Exception {
        String json = "{\"weightKg\":" + weight + ",\"heightCm\":" + height
                + ",\"bodyWidthCm\":" + width
                + ",\"measuredAt\":\"2026-10-13T00:00:00Z\","
                + "\"measurementsComplete\":true}";

        return new ObjectMapper().findAndRegisterModules().readValue(json,
                HorseResponse.class);
    }
}
