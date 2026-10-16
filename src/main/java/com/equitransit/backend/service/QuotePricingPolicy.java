/*
 * QuotePricingPolicy
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.service;


import com.equitransit.backend.dto.quote.QuoteEvidence;
import com.equitransit.backend.dto.quote.QuoteLine;
import com.equitransit.backend.dto.quote.QuotePrepareRequest;
import com.equitransit.backend.dto.response.OrderSnapshot;
import com.equitransit.backend.dto.response.ServiceChargeResponse;
import com.equitransit.backend.entity.BundleResult;
import com.equitransit.backend.entity.quote.QuoteEvidenceStatus;
import com.equitransit.backend.entity.quote.QuotePriceSource;
import com.equitransit.backend.entity.quote.QuoteSnapshot;
import com.equitransit.backend.entity.quote.QuoteCalculationContext;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.QuoteSourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


/** Pricing consumes F05 and evidenced commercial gaps, not fake readiness. */
@Service
public class QuotePricingPolicy {

    private static final Set<String> FREE = Set.of("INCLUDED",
            "NOT_REQUIRED", "NOT_SELECTED", "EXTERNAL_PROVIDER", "EVENT_ONLY");
    private static final Set<String> BUNDLED = Set.of("AIRPORT_HANDLING",
            "TERMINAL_HANDLING", "CUSTOMS_DECLARATION");
    private final ServiceAssessmentFacade assessments;
    private final ServiceChargeBuilder builder;
    private final ServicePreferencePolicy preferences;
    private final QuoteSourceRepository sources;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * @param assessments F08/F10 sourced read contracts
     * @param builder shared F05 charge catalogue
     * @param preferences measurement fit and eligibility
     * @param sources bounded retail rate snapshots
     * @param mapper strict internal inclusion decoder
     * @param clock UTC clock
     */
    public QuotePricingPolicy(ServiceAssessmentFacade assessments,
            ServiceChargeBuilder builder, ServicePreferencePolicy preferences,
            QuoteSourceRepository sources, ObjectMapper mapper, Clock clock) {
        this.assessments = assessments;
        this.builder = builder;
        this.preferences = preferences;
        this.sources = sources;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Captures prices and blockers for the exact current revision.
     * @param order authorized locked order
     * @param snapshot immutable submitted request
     * @param request validated commercial preparation
     * @return private snapshot; unresolved lines have null amounts
     */
    public QuoteSnapshot prepare(long order, OrderSnapshot snapshot,
            QuotePrepareRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        var route = assessments.quotationItinerary(order,
                request.revisionId(), now);
        var horizon = preferences.horizon(
                snapshot.request().getDeliveryDeadline(), now);
        var chosen = snapshot.request().getServices();
        var sourceRows = sources.read(request.revisionId(),
                request.assessments().stream().map(QuoteEvidence::tariffId)
                        .filter(java.util.Objects::nonNull).distinct().toList(),
                request.assessments().stream().map(QuoteEvidence::offerId)
                        .filter(java.util.Objects::nonNull).distinct().toList(),
                now);
        List<String> blockers = new ArrayList<>();
        Set<String> codes = new HashSet<>();

        for (var evidence : request.assessments()) {
            if (!codes.add(evidence.code().name())) {
                throw invalid("Duplicate assessed charge.");
            }
            if (!evidence.validUntil().isAfter(now)
                    || evidence.validUntil().isBefore(request.validUntil())) {
                blockers.add(evidence.code() + "_EVIDENCE_EXPIRED");
            }
        }
        BundleResult bundle = bundle(request, sourceRows);
        var quarantine = assessments.quarantine(order, request.revisionId(),
                now, horizon);
        var veterinary = assessments.veterinary(order, request.revisionId(),
                now, horizon);
        var rows = builder.build(chosen, route,
                quarantine, veterinary, bundle, now);
        com.equitransit.backend.entity.StallFitResult fit = null;

        if (route.airKm() == null) {
            blockers.add("ITINERARY_PENDING");
        } else if (route.airKm().signum() > 0) {
            fit = preferences.validate(chosen, snapshot.horse(),
                    snapshot.request().getDeliveryDeadline());

            if (fit == null || !"ELIGIBLE".equals(fit.status())) {
                blockers.add("FIT_POLICY_PENDING");
            }
        }
        List<Long> tariffIds = rows.stream()
                .filter(row -> "ESTIMATED".equals(row.pricingStatus()))
                .map(ServiceChargeResponse::tariffId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        List<QuotePriceSource> retained = new ArrayList<>(sourceRows);

        retained.addAll(sources.read(request.revisionId(), tariffIds,
                List.of(), now));
        for (var rate : retained) {
            if (rate.validUntil() != null
                    && rate.validUntil().isBefore(request.validUntil())) {
                blockers.add("PRICING_SOURCE_EXPIRES_BEFORE_QUOTE");
            }
        }
        List<QuoteLine> lines = new ArrayList<>();

        for (var row : rows) {
            QuoteEvidence evidence = request.assessments().stream()
                    .filter(e -> e.code().name().equals(row.code()))
                    .findFirst().orElse(null);
            QuoteLine line = line(row, evidence, retained,
                    request.validUntil());

            lines.add(line);
            if ("PENDING".equals(line.pricingStatus())
                    && (row.mandatory() || row.selected())) {
                blockers.add(row.code() + "_PENDING");
            }
        }
        BigDecimal total = lines.stream().filter(l -> l.amount() != null)
                .map(l -> new BigDecimal(l.amount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);

        if (total.precision() > 18) {
            blockers.add("TOTAL_AMOUNT_REVIEW_REQUIRED");
        }
        var context = new QuoteCalculationContext(route.planId(),
                route.roadKm(), route.airKm(), quarantine == null ? null
                        : quarantine.requiredDays(),
                quarantine == null ? null : quarantine.assessmentId(),
                veterinary, fit == null ? null : fit.policyId(),
                fit == null ? null : fit.policyVersion(),
                bundle.included().stream().sorted().toList());

        return new QuoteSnapshot(1, request, List.copyOf(lines),
                retained.stream().distinct().toList(),
                blockers.stream().distinct().toList(), context);
    }

    /**
     * Rechecks current sources without rewriting captured rates or amounts.
     * @param snapshot stored preparation snapshot
     */
    public void requireSources(QuoteSnapshot snapshot) {
        var current = sources.read(snapshot.request().revisionId(),
                snapshot.sources().stream().filter(s -> !s.offer())
                        .map(QuotePriceSource::id).distinct().toList(),
                snapshot.sources().stream().filter(QuotePriceSource::offer)
                        .map(QuotePriceSource::id).distinct().toList(),
                OffsetDateTime.now(clock));

        for (var saved : snapshot.sources()) {
            if (current.stream().noneMatch(s -> s.id() == saved.id()
                    && s.offer() == saved.offer()
                    && s.rate().compareTo(saved.rate()) == 0
                    && java.util.Objects.equals(s.version(), saved.version())
                    && java.util.Objects.equals(s.validUntil(),
                            saved.validUntil()))) {
                throw invalid("Pricing source changed; prepare a new version.");
            }
        }
    }

    private QuoteLine line(ServiceChargeResponse row, QuoteEvidence evidence,
            List<QuotePriceSource> rates, OffsetDateTime expiry) {
        if (FREE.contains(row.pricingStatus())) {
            if (evidence != null) {
                throw invalid("Do not price included or unselected services.");
            }
            return view(row, null, null, null, row.pricingStatus());
        }
        if (evidence != null) {
            if (evidence.status() == QuoteEvidenceStatus.NOT_REQUIRED) {
                if (evidence.quantity() != null || evidence.tariffId() != null
                        || evidence.offerId() != null) {
                    throw invalid("Not-required evidence cannot carry prices.");
                }
                return view(row, null, null, null, "NOT_REQUIRED");
            }
            if ((evidence.tariffId() == null) == (evidence.offerId() == null)
                    || evidence.quantity() == null) {
                throw invalid("Choose one retail source and a quantity.");
            }
            var source = rates.stream().filter(s ->
                    s.offer() ? evidence.offerId() != null
                            && s.id() == evidence.offerId()
                            : evidence.tariffId() != null
                                    && s.id() == evidence.tariffId())
                    .findFirst().orElse(null);
            if (source == null || !source.code().equals(row.code())
                    || !source.unit().equals(row.unit())
                    || (!source.offer()
                            && !row.costGroup().equals(source.group()))
                    || (source.validUntil() != null
                            && source.validUntil().isBefore(expiry))) {
                return view(row, null, null, null, "PENDING");
            }
            BigDecimal quantity = new BigDecimal(evidence.quantity());

            if (source.offer() && quantity.compareTo(source.quantity()) != 0) {
                throw invalid("Offer quantity does not match its scope.");
            }
            return priced(row, quantity, source.rate());
        }
        if (!"ESTIMATED".equals(row.pricingStatus())) {
            return view(row, null, null, null, "PENDING");
        }
        if (row.tariffId() == null || rates.stream().noneMatch(s ->
                !s.offer() && s.id() == row.tariffId()
                        && s.unit().equals(row.unit())
                        && s.rate().compareTo(new BigDecimal(row.unitRate()))
                                == 0)) {
            return view(row, null, null, null, "PENDING");
        }
        return priced(row, new BigDecimal(row.quantity()),
                new BigDecimal(row.unitRate()));
    }

    private QuoteLine priced(ServiceChargeResponse row, BigDecimal quantity,
            BigDecimal rate) {
        if (quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 3
                || quantity.setScale(4).precision() > 18
                || rate.signum() < 0 || rate.stripTrailingZeros().scale() > 2
                || rate.setScale(4).precision() > 18) {
            throw invalid("Positive quantity and exact units are required.");
        }
        BigDecimal amount = quantity.multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);

        if (amount.precision() > 18) {
            throw invalid("Pricing exceeds the supported monetary range.");
        }
        return view(row, quantity.stripTrailingZeros().toPlainString(),
                rate.setScale(2).toPlainString(),
                amount.toPlainString(), "PRICED");
    }

    private QuoteLine view(ServiceChargeResponse row, String quantity,
            String rate, String amount, String status) {
        return new QuoteLine(row.code(), row.costGroup(), row.label(),
                row.unit(), quantity, rate, amount, status,
                "PENDING".equals(status) ? List.of(row.code() + "_PENDING")
                        : List.of());
    }

    private BundleResult bundle(QuotePrepareRequest request,
            List<QuotePriceSource> rates) {
        Set<String> included = new HashSet<>();

        for (var source : rates) {
            if (!source.offer() || !"AIRFARE".equals(source.code())) {
                continue;
            }
            if (source.includesStall()) {
                included.add("AIR_STALL");
            }
            if (source.inclusions() == null) {
                continue;
            }
            try {
                var node = mapper.readTree(source.inclusions());

                if (node.path("schemaVersion").asInt() != 1
                        || !node.path("services").isArray()) {
                    return new BundleResult(Set.of(), true);
                }
                for (var code : node.path("services")) {
                    if (!BUNDLED.contains(code.asText())) {
                        return new BundleResult(Set.of(), true);
                    }
                    included.add(code.asText());
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                return new BundleResult(Set.of(), true);
            }
        }
        return new BundleResult(Set.copyOf(included), false);
    }

    private OrderException invalid(String message) {
        return new OrderException(HttpStatus.CONFLICT,
                "QUOTE_PRICING_INVALID", message);
    }
}
