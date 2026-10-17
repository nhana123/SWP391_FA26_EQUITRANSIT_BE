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
import com.equitransit.backend.service.ServiceChargeBuilder;
import com.equitransit.backend.dto.request.OrderDraftRequest;
import com.equitransit.backend.dto.request.OrderServicePreferences;
import com.equitransit.backend.entity.BundleResult;
import com.equitransit.backend.entity.PublishedTariffRecord;
import com.equitransit.backend.entity.ItineraryPricingContext;
import com.equitransit.backend.entity.QuarantineAssessmentResult;
import com.equitransit.backend.entity.VeterinaryAssessmentResult;
import com.equitransit.backend.repository.ServiceSourceRepository;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;


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
                rate.costGroup(), new BigDecimal("1.00"));

        assertThat(new BigDecimal(line(BigDecimal.TEN, List.of(zero), null)
                .amount()))
                .isEqualByComparingTo("0.00");
        var rounded = line(new BigDecimal("1.005"), List.of(fraction), null);

        assertThat(new BigDecimal(rounded.amount()))
                .isEqualByComparingTo("1.01");
    }

    /** SQL DECIMAL(18,4) padding must not break the HTTP decimal contract. */
    @Test
    public void paddedDatabaseRateUsesTwoDecimalsAndExactQuantity() {
        var padded = new TariffRecord(2, 1, rate.unit(), rate.costGroup(),
                new BigDecimal("2.5000"));
        var result = line(new BigDecimal("0.0150"), List.of(padded), null);

        assertThat(result.unitRate()).isEqualTo("2.50");
        assertThat(result.quantity()).isEqualTo("0.015");
        assertThat(result.amount()).isEqualTo("0.04");
        assertThat(line(null, List.of(padded), null).unitRate())
                .isEqualTo("2.50");
    }

    /** Unsupported legacy fractions are pending, never silently repriced. */
    @Test
    public void subCentRateRequiresReviewInsteadOfRoundingThePrice() {
        var unsupported = new TariffRecord(2, 1, rate.unit(), rate.costGroup(),
                new BigDecimal("2.5001"));
        var result = line(BigDecimal.ONE, List.of(unsupported), null);

        assertThat(result.unitRate()).isNull();
        assertThat(result.amount()).isNull();
        assertThat(result.blockers()).contains("RATE_CONFIGURATION_INVALID");
    }

    private ServiceChargeResponse line(
            BigDecimal quantity, List<TariffRecord> rates, String override) {
        return calculator.line("AIR_STALL", "AIR_STALL", "HORSE_ACCOMMODATION",
                "Air stall", "SLOT_KM", "slots × AIR km × rate", true, true,
                quantity, rates, override);
    }

    /** A recommendation needs a proven exclusion, never missing dimensions. */
    @Test
    public void wideDefaultRequiresProvenSharedExclusion() throws Exception {
        var large = horse("650", "180", "80");
        var wide = new FitPolicyRecord(2, "WIDE_TEST", 1,
                new BigDecimal("1.5"), new BigDecimal("700"),
                new BigDecimal("200"), new BigDecimal("100"));

        assertThat(fit.defaultOption(large, List.of(policy, wide), now))
                .isEqualTo(AirStallPreference.WIDE);
        assertThat(fit.defaultOption(large, List.of(policy), now))
                .isEqualTo(AirStallPreference.WIDE);
        assertThat(fit.defaultOption(horse("750", "180", "80"),
                List.of(policy, wide), now)).isNull();
        assertThat(fit.defaultOption(horse(null, "180", "80"),
                List.of(policy, wide), now)).isNull();
        assertThat(fit.defaultOption(large, List.of(), now)).isNull();
    }

    /** Shipment prices expose slot multipliers without guessed distances. */
    @Test
    public void shipmentStallAndFixedFeesNeedNoDistances() {
        var rates = List.of(published("AIR_STALL", "DEFAULT", "SHIPMENT",
                "HORSE_ACCOMMODATION", "100"),
                published("HORSE_CARE", "ENHANCED", "SHIPMENT",
                        "HORSE_CARE", "25"),
                published("DOCUMENT_COORDINATION", "DEFAULT", "SHIPMENT",
                        "DOCUMENTATION_QUARANTINE", "50"));
        var preferences = new OrderServicePreferences();

        preferences.setEnhancedCare(true);
        for (var option : AirStallPreference.values()) {
            preferences.setAirStall(option);
            var rows = build(preferences, new OrderDraftRequest(), null,
                    rates, Set.of());
            var stall = charge(rows, "AIR_STALL");

            assertThat(stall.unit()).isEqualTo("SHIPMENT");
            assertThat(new BigDecimal(stall.amount()))
                    .isEqualByComparingTo(StallFitEvaluator.slots(option)
                            .multiply(new BigDecimal("100")));
            assertThat(charge(rows, "HORSE_CARE").amount())
                    .isEqualTo("25.00");
            assertThat(charge(rows, "DOCUMENT_COORDINATION").amount())
                    .isEqualTo("50.00");
        }
    }

    /** Country/transit inputs give estimates, not authority decisions. */
    @Test
    public void routeCountsPriceHandlingCustomsAndRequestedEscort() {
        var request = new OrderDraftRequest();
        var preferences = new OrderServicePreferences();
        var rates = List.of(published("AIRPORT_HANDLING", "DEFAULT",
                "HORSE_AIRPORT", "AIRPORT_CUSTOMS", "10"),
                published("TERMINAL_HANDLING", "DEFAULT", "HORSE_AIRPORT",
                        "AIRPORT_CUSTOMS", "20"),
                published("CUSTOMS_DECLARATION", "DEFAULT", "DECLARATION",
                        "AIRPORT_CUSTOMS", "30"),
                published("ESCORT", "REQUESTED", "PERSON_LEG", "HORSE_CARE",
                        "40"));

        request.setOriginCountryCode("VN");
        request.setDestinationCountryCode("GB");
        request.setTransitAirports(List.of("BKK", "DOH"));
        preferences.setEscortNotes("Requested groom");
        var rows = build(preferences, request, null, rates, Set.of());

        assertThat(charge(rows, "AIRPORT_HANDLING").quantity())
                .isEqualTo("4");
        assertThat(charge(rows, "AIRPORT_HANDLING").amount())
                .isEqualTo("40.00");
        assertThat(charge(rows, "TERMINAL_HANDLING").amount())
                .isEqualTo("80.00");
        assertThat(charge(rows, "CUSTOMS_DECLARATION").amount())
                .isEqualTo("60.00");
        assertThat(charge(rows, "ESCORT").amount()).isEqualTo("120.00");
        assertThat(charge(rows, "GOVERNMENT_DUTY").amount()).isNull();
        var bundled = build(preferences, request, null, rates,
                Set.of("AIRPORT_HANDLING", "TERMINAL_HANDLING"));

        assertThat(charge(bundled, "AIRPORT_HANDLING").pricingStatus())
                .isEqualTo("INCLUDED");
        assertThat(charge(bundled, "TERMINAL_HANDLING").amount()).isNull();
        assertThat(charge(build(preferences, request, null, List.of(),
                Set.of()), "CUSTOMS_DECLARATION").amount()).isNull();
    }

    /** Unknown routes and explicit road-only plans do not invent airports. */
    @Test
    public void noAirCountsWithoutARouteOrForSourcedRoadOnly() {
        var preferences = new OrderServicePreferences();
        var request = new OrderDraftRequest();
        var rates = List.of(published("AIRPORT_HANDLING", "DEFAULT",
                "HORSE_AIRPORT", "AIRPORT_CUSTOMS", "10"));

        assertThat(charge(build(preferences, request, null, rates, Set.of()),
                "AIRPORT_HANDLING").quantity()).isNull();
        request.setOriginCountryCode("VN");
        request.setDestinationCountryCode("KH");
        assertThat(charge(build(preferences, request, BigDecimal.ZERO,
                rates, Set.of()), "AIRPORT_HANDLING").amount()).isNull();
    }

    /** Sex requires compatibility review without inventing a fit rejection. */
    @Test
    public void stallionSharingCarriesAWarning() throws Exception {
        var profile = new ObjectMapper().findAndRegisterModules().readValue(
                "{\"weightKg\":500,\"heightCm\":160,\"bodyWidthCm\":70,"
                        + "\"measuredAt\":\"2026-10-13T00:00:00Z\","
                        + "\"sexCode\":\"STALLION\"}", HorseResponse.class);
        var result = fit.evaluate(AirStallPreference.SHARED_ONE, profile,
                List.of(policy), now);

        assertThat(result.status()).isEqualTo("ELIGIBLE");
        assertThat(result.reasons())
                .contains("STALLION_COMPATIBILITY_REVIEW_REQUIRED");
        assertThat(fit.defaultOption(profile, List.of(policy), now)).isNull();
    }

    /** Invalid quantities stay unknown instead of violating the API format. */
    @Test
    public void quantityPrecisionIsNeverSilentlyRounded() {
        var result = line(new BigDecimal("0.0155"), List.of(rate), null);

        assertThat(result.quantity()).isNull();
        assertThat(result.amount()).isNull();
        assertThat(result.blockers())
                .contains("QUANTITY_CONFIGURATION_INVALID");
    }

    private PublishedTariffRecord published(String code, String option,
            String unit, String group, String price) {
        var row = mock(PublishedTariffRecord.class);

        when(row.service()).thenReturn(code);
        when(row.option()).thenReturn(option);
        when(row.tariff()).thenReturn(new TariffRecord(1, 1, unit, group,
                new BigDecimal(price)));
        return row;
    }

    private List<ServiceChargeResponse> build(
            OrderServicePreferences preferences, OrderDraftRequest request,
            BigDecimal airKm, List<PublishedTariffRecord> rates,
            Set<String> inclusions) {
        var sources = mock(ServiceSourceRepository.class);
        var quarantine = mock(QuarantineAssessmentResult.class);

        when(sources.tariffs(anyList(), any())).thenReturn(rates);
        when(quarantine.applicability()).thenReturn("UNKNOWN");
        return new ServiceChargeBuilder(sources, calculator).build(
                preferences, new ItineraryPricingContext(null, null,
                        airKm, "PENDING"), quarantine,
                mock(VeterinaryAssessmentResult.class),
                new BundleResult(inclusions, false), now, request);
    }

    private ServiceChargeResponse charge(List<ServiceChargeResponse> rows,
            String code) {
        return rows.stream().filter(row -> row.code().equals(code))
                .findFirst().orElseThrow();
    }

    private HorseResponse horse(String weight, String height, String width)
            throws Exception {
        String json = "{\"weightKg\":" + weight + ",\"heightCm\":" + height
                + ",\"bodyWidthCm\":" + width
                + ",\"measuredAt\":\"2026-10-13T00:00:00Z\"}";

        return new ObjectMapper().findAndRegisterModules().readValue(json,
                HorseResponse.class);
    }
}
