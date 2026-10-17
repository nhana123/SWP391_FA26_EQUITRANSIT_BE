/*
 * QuotePricingPolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend;


import com.equitransit.backend.dto.ApiExamples;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.dto.quote.QuoteEvidence;
import com.equitransit.backend.dto.quote.QuotePrepareRequest;
import com.equitransit.backend.dto.quote.QuoteSchedule;
import com.equitransit.backend.dto.response.OrderSnapshot;
import com.equitransit.backend.dto.response.ServiceChargeResponse;
import com.equitransit.backend.entity.ItineraryPricingContext;
import com.equitransit.backend.entity.quote.QuoteChargeCode;
import com.equitransit.backend.entity.quote.QuoteEvidenceStatus;
import com.equitransit.backend.entity.quote.QuotePriceSource;
import com.equitransit.backend.repository.QuoteSourceRepository;
import com.equitransit.backend.service.QuotePricingPolicy;
import com.equitransit.backend.service.ServiceAssessmentFacade;
import com.equitransit.backend.service.ServiceChargeBuilder;
import com.equitransit.backend.service.ServicePreferencePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/** Exact arithmetic, expiry and missing-source semantics in isolation. */
public class QuotePricingPolicyTest {

    private final ServiceAssessmentFacade assessments =
            mock(ServiceAssessmentFacade.class);
    private final ServiceChargeBuilder builder =
            mock(ServiceChargeBuilder.class);
    private final ServicePreferencePolicy preferences =
            mock(ServicePreferencePolicy.class);
    private final QuoteSourceRepository sources =
            mock(QuoteSourceRepository.class);
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final Clock clock = Clock.fixed(Instant.parse(
            "2026-10-16T00:00:00Z"), ZoneOffset.UTC);
    private final QuotePricingPolicy policy = new QuotePricingPolicy(
            assessments, builder, preferences, sources, mapper, clock);
    private OrderSnapshot snapshot;

    /**
     * @throws Exception if the actual versioned example cannot be parsed
     */
    @BeforeEach
    public void setup() throws Exception {
        snapshot = mapper.readValue(ApiExamples.SNAPSHOT, OrderSnapshot.class);
        when(assessments.quotationItinerary(anyLong(), anyLong(), any()))
                .thenReturn(new ItineraryPricingContext(1L,
                        BigDecimal.ONE, BigDecimal.ZERO, "SOURCED"));
        when(sources.read(anyLong(), anyList(), anyList(), any()))
                .thenReturn(List.of(source("1.00", null)));
    }

    /** Quantity preserves three decimals; money rounds once, HALF_UP. */
    @Test
    public void slotQuantityPrecisionDoesNotLoseHalfCent() {
        rows("0.015", "1.00", "ESTIMATED");
        var result = policy.prepare(1, snapshot, request(List.of()));

        assertThat(result.lines().getFirst().quantity()).isEqualTo("0.015");
        assertThat(result.lines().getFirst().amount()).isEqualTo("0.02");
        assertThat(result.blockers()).isEmpty();
    }

    /** Unknown mandatory quantity never becomes a zero-priced item. */
    @Test
    public void unknownQuantityRemainsNullWithBlocker() {
        rows(null, "1.00", "QUANTITY_PENDING");
        var result = policy.prepare(1, snapshot, request(List.of()));

        assertThat(result.lines().getFirst().amount()).isNull();
        assertThat(result.blockers()).contains("ROAD_TRANSPORT_PENDING");
    }

    /** A disappearing tariff blocks preparation instead of guessing. */
    @Test
    public void missingRateSourceRemainsPending() {
        rows("1", "1.00", "ESTIMATED");
        when(sources.read(anyLong(), anyList(), anyList(), any()))
                .thenReturn(List.of());
        var result = policy.prepare(1, snapshot, request(List.of()));

        assertThat(result.lines().getFirst().amount()).isNull();
        assertThat(result.blockers()).isNotEmpty();
    }

    /** Quote expiry cannot exceed its captured source validity. */
    @Test
    public void sourceExpiringBeforeQuoteBlocksPublication() {
        rows("1", "1.00", "ESTIMATED");
        when(sources.read(anyLong(), anyList(), anyList(), any()))
                .thenReturn(List.of(source("1.00",
                        OffsetDateTime.parse("2026-10-17T00:00:00Z"))));
        var result = policy.prepare(1, snapshot, request(List.of()));

        assertThat(result.blockers())
                .contains("PRICING_SOURCE_EXPIRES_BEFORE_QUOTE");
    }

    /** More than three quantity decimals cannot be silently rounded. */
    @Test
    public void overprecisionQuantityIsRejected() {
        rows("0.0155", "1.00", "ESTIMATED");
        assertThatThrownBy(() -> policy.prepare(1, snapshot,
                request(List.of()))).hasMessageContaining("exact units");
    }

    /** Supplier retail price is separate from internal purchase cost. */
    @Test
    public void explicitRetailOfferPricesOnlyTheVerifiedScope() {
        var offer = new QuotePriceSource(5, true, "AIRFARE", "SHIPMENT",
                null, new BigDecimal("1200.00"), BigDecimal.ONE,
                OffsetDateTime.parse("2026-11-01T00:00:00Z"), null, false,
                null);
        var row = new ServiceChargeResponse("AIRFARE", "TRANSPORTATION",
                "TRANSPORTATION", "Air freight", true, true, "SHIPMENT",
                null, null, null, "AIRFARE_PENDING", null, null,
                "Retail supplier quote", List.of("AIRFARE_PENDING"));
        var evidence = new QuoteEvidence(QuoteChargeCode.AIRFARE,
                QuoteEvidenceStatus.PRICED, "1", null, 5L,
                "Artificial commercial evidence",
                OffsetDateTime.parse("2026-11-01T00:00:00Z"),
                "Verified retail offer");

        when(builder.build(any(), any(), any(), any(), any(), any(),
                any()))
                .thenReturn(List.of(row));
        when(sources.read(anyLong(), anyList(), anyList(), any()))
                .thenReturn(List.of(offer));
        var result = policy.prepare(1, snapshot, request(List.of(evidence)));

        assertThat(result.lines().getFirst().amount()).isEqualTo("1200.00");
        assertThat(result.blockers()).isEmpty();
    }

    private void rows(String quantity, String rate, String status) {
        var row = new ServiceChargeResponse("ROAD_TRANSPORT", "TRANSPORTATION",
                "TRANSPORTATION", "Road transport", true, true, "KM",
                quantity, rate, null, status, 1L, 1, "ROAD km x rate",
                List.of());

        when(builder.build(any(), any(), any(), any(), any(), any(),
                any()))
                .thenReturn(List.of(row));
    }

    /** Individually valid lines cannot overflow the SQL quotation total. */
    @Test
    public void totalOverflowRemainsAnUnpublishableDraft() {
        String rate = "90000000000000.00";
        var first = new ServiceChargeResponse("ROAD_TRANSPORT",
                "TRANSPORTATION", "TRANSPORTATION", "Road", true, true,
                "KM", "100", rate, null, "ESTIMATED", 1L, 1,
                "quantity x rate", List.of());
        var second = new ServiceChargeResponse("VETERINARY_EXAM",
                "VETERINARY", "VETERINARY", "Visit", true, true,
                "HORSE_VISIT", "100", rate, null, "ESTIMATED", 2L, 1,
                "quantity x rate", List.of());
        var veterinary = new QuotePriceSource(2, false, "VETERINARY",
                "HORSE_VISIT", "VETERINARY", new BigDecimal(rate), null,
                null, null, false, 1);

        when(builder.build(any(), any(), any(), any(), any(), any(),
                any()))
                .thenReturn(List.of(first, second));
        when(sources.read(anyLong(), anyList(), anyList(), any()))
                .thenReturn(List.of(source(rate, null), veterinary));
        var result = policy.prepare(1, snapshot, request(List.of()));

        assertThat(result.blockers()).contains("TOTAL_AMOUNT_REVIEW_REQUIRED");
    }

    private QuotePriceSource source(String rate, OffsetDateTime expiry) {
        return new QuotePriceSource(1, false, "ROAD_TRANSPORT", "KM",
                "TRANSPORTATION", new BigDecimal(rate), null, expiry,
                null, false, 1);
    }

    /** Inferred route counts can be priced without commercial overrides. */
    @Test
    public void routeEstimateUsesTariffUntilEvidenceOverridesIt() {
        var row = new ServiceChargeResponse("AIRPORT_HANDLING",
                "ADDITIONAL_CHARGES", "AIRPORT_CUSTOMS", "Handling", true,
                true, "HORSE_AIRPORT", "3", "10.00", "30.00", "ESTIMATED",
                1L, 1, "Route estimate", List.of());
        var tariff = new QuotePriceSource(1, false, "AIRPORT_HANDLING",
                "HORSE_AIRPORT", "AIRPORT_CUSTOMS", new BigDecimal("10"),
                null, null, null, false, 1);

        when(builder.build(any(), any(), any(), any(), any(), any(),
                any())).thenReturn(List.of(row));
        when(sources.read(anyLong(), anyList(), anyList(), any()))
                .thenReturn(List.of(tariff));
        var inferred = policy.prepare(1, snapshot, request(List.of()));

        assertThat(inferred.lines().getFirst().amount()).isEqualTo("30.00");
        var evidence = new QuoteEvidence(QuoteChargeCode.AIRPORT_HANDLING,
                QuoteEvidenceStatus.PRICED, "4", 1L, null,
                "Verified handling scope",
                OffsetDateTime.parse("2026-11-01T00:00:00Z"),
                "Actual handling includes an extra airport visit");
        var assessed = policy.prepare(1, snapshot, request(List.of(evidence)));

        assertThat(assessed.lines().getFirst().quantity()).isEqualTo("4");
        assertThat(assessed.lines().getFirst().amount()).isEqualTo("40.00");
        assertThat(assessed.blockers()).isEmpty();
    }

    /** Shipment slot quantities retain exact decimal arithmetic in F06. */
    @Test
    public void shipmentSlotRateCanBeCapturedInQuote() {
        var row = new ServiceChargeResponse("AIR_STALL", "AIR_STALL",
                "HORSE_ACCOMMODATION", "Wide stall", true, true,
                "SHIPMENT", "1.5", "100.00", "150.00", "ESTIMATED",
                1L, 1, "slots x shipment rate", List.of());
        var tariff = new QuotePriceSource(1, false, "AIR_STALL", "SHIPMENT",
                "HORSE_ACCOMMODATION", new BigDecimal("100"), null, null,
                null, false, 1);

        when(builder.build(any(), any(), any(), any(), any(), any(),
                any())).thenReturn(List.of(row));
        when(sources.read(anyLong(), anyList(), anyList(), any()))
                .thenReturn(List.of(tariff));
        var result = policy.prepare(1, snapshot, request(List.of()));

        assertThat(result.lines().getFirst().unit()).isEqualTo("SHIPMENT");
        assertThat(result.lines().getFirst().quantity()).isEqualTo("1.5");
        assertThat(result.lines().getFirst().amount()).isEqualTo("150.00");
    }

    /** An evidenced supplemental lease overrides partial inclusion. */
    @Test
    public void supplementalStallUsesItsOwnSourceUnitAndAmount() {
        for (String status : List.of("INCLUDED", "BUNDLE_REVIEW_REQUIRED")) {
            var row = new ServiceChargeResponse("AIR_STALL", "AIR_STALL",
                    "HORSE_ACCOMMODATION", "Supplemental stall", true, true,
                    "SLOT_KM", null, null, null, status, null, null,
                    "Assess remaining uncovered scope", List.of());
            var source = new QuotePriceSource(7, false, "AIR_STALL",
                    "SHIPMENT", "HORSE_ACCOMMODATION",
                    new BigDecimal("100.00"), null, null, null, false, 1);
            var evidence = new QuoteEvidence(QuoteChargeCode.AIR_STALL,
                    QuoteEvidenceStatus.PRICED, "1.5", 7L, null,
                    "Second flight lease evidence",
                    OffsetDateTime.parse("2026-11-01T00:00:00Z"),
                    "Only the uncovered second leg");

            when(builder.build(any(), any(), any(), any(), any(), any(),
                    any())).thenReturn(List.of(row));
            when(sources.read(anyLong(), anyList(), anyList(), any()))
                    .thenReturn(List.of(source));
            var line = policy.prepare(1, snapshot, request(List.of(evidence)))
                    .lines().getFirst();

            assertThat(line.pricingStatus()).isEqualTo("PRICED");
            assertThat(line.unit()).isEqualTo("SHIPMENT");
            assertThat(line.quantity()).isEqualTo("1.5");
            assertThat(line.amount()).isEqualTo("150.00");
        }
    }

    /** Only selected full-route airfare can waive an entire service charge. */
    @Test
    public void onlyEntireRouteCoverageSuppressesTheFullCharge() {
        for (String scope : List.of("ENTIRE_ROUTE", "PARTIAL_ROUTE",
                "UNKNOWN")) {
            var offer = new QuotePriceSource(5, true, "AIRFARE", "SHIPMENT",
                    null, new BigDecimal("1000"), BigDecimal.ONE,
                    OffsetDateTime.parse("2026-11-01T00:00:00Z"),
                    "{\"schemaVersion\":1,\"coverage\":\"" + scope
                            + "\",\"services\":[\"TERMINAL_HANDLING\"]}",
                    true, null);
            var evidence = new QuoteEvidence(QuoteChargeCode.AIRFARE,
                    QuoteEvidenceStatus.PRICED, "1", null, 5L,
                    "Verified route coverage", offer.validUntil(),
                    "Supplier attests the recorded scope");

            when(builder.build(any(), any(), any(), any(), any(), any(),
                    any())).thenReturn(List.of());
            when(sources.read(anyLong(), anyList(), anyList(), any()))
                    .thenReturn(List.of(offer));
            policy.prepare(1, snapshot, request(List.of(evidence)));
            var capture = org.mockito.ArgumentCaptor.forClass(
                    com.equitransit.backend.entity.BundleResult.class);

            verify(builder, atLeastOnce())
                    .build(any(), any(), any(), any(), capture.capture(), any(),
                            any());
            assertThat(capture.getValue().included())
                    .contains("AIR_STALL", "TERMINAL_HANDLING");
            assertThat(capture.getValue().ambiguous())
                    .isEqualTo(!scope.equals("ENTIRE_ROUTE"));
        }
    }

    private QuotePrepareRequest request(List<QuoteEvidence> evidence) {
        return new QuotePrepareRequest(new QuoteCommand("0000000000000001",
                UUID.randomUUID()), 1,
                OffsetDateTime.parse("2026-10-20T00:00:00Z"), "TEST-TERMS",
                "Artificial payment terms", "Artificial cancellation terms",
                List.of(new QuoteSchedule(1, "FULL", "1200.00",
                        OffsetDateTime.parse("2026-10-21T00:00:00Z"))),
                evidence);
    }
}
