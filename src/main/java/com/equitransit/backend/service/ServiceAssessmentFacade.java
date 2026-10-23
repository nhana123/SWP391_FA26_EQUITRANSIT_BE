/*
 * ServiceAssessmentFacade
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose sourced F08/F09/F10 contracts with honest pending results.
 */
package com.equitransit.backend.service;


import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import com.equitransit.backend.entity.PublishedTariffRecord;
import com.equitransit.backend.dto.pricing.ServiceRateResponse;
import com.equitransit.backend.entity.pricing.PricingMode;
import com.equitransit.backend.entity.pricing.ServiceProduct;
import com.equitransit.backend.entity.quote.QuoteSnapshot;
import com.equitransit.backend.exception.OrderException;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import com.equitransit.backend.entity.BundleResult;
import com.equitransit.backend.entity.BundleSourceRecord;
import com.equitransit.backend.entity.ItineraryPricingContext;
import com.equitransit.backend.entity.QuarantineAssessmentResult;
import com.equitransit.backend.entity.VeterinaryAssessmentResult;
import com.equitransit.backend.repository.ServiceSourceRepository;


/**
 * Only owned-parent services consume this facade; it is not an HTTP endpoint.
 */
@Service
public class ServiceAssessmentFacade {

    private static final Set<String> BUNDLE_CODES = Set.of("AIR_STALL",
            "AIRPORT_HANDLING", "CUSTOMS_DECLARATION", "TERMINAL_HANDLING");

    private final ServiceSourceRepository sources;
    private final ObjectMapper mapper;

    /**
     * Creates typed source read contracts.
     * @param sources actual version-bound persistence, no mocked READY
     * @param mapper strict source envelope reader
     */
    public ServiceAssessmentFacade(ServiceSourceRepository sources,
            ObjectMapper mapper) {
        this.sources = sources;
        this.mapper = mapper;
    }

    /**
     * Shares F06 retail snapshots and evidence with F05 read-only disclosure.
     * The caller must authorize the parent before reading this private type.
     * @param order owned parent
     * @param revision exact current revision, null for draft
     * @param now server time
     * @return supported commercial snapshot, never a public response
     */
    public Optional<QuoteSnapshot> commercialQuote(long order, Long revision,
            OffsetDateTime now) {
        if (revision == null) {
            return Optional.empty();
        }
        return sources.commercialSnapshot(order, revision, now).map(encoded -> {
            try {
                var snapshot = mapper.readValue(encoded, QuoteSnapshot.class);

                if (snapshot != null && snapshot.schemaVersion() == 1
                        && snapshot.request() != null
                        && snapshot.lines() != null
                        && snapshot.sources() != null
                        && snapshot.blockers() != null
                        && snapshot.request().revisionId() == revision) {
                    return snapshot;
                }
            } catch (JsonProcessingException error) {
                throw unsupportedQuote();
            }
            throw unsupportedQuote();
        });
    }

    /**
     * Reads current unit prices for selected and unselected product options.
     * @param now server UTC time
     * @return retail-only bounded catalogue, without private source references
     */
    public List<ServiceRateResponse> retailRates(OffsetDateTime now) {
        var products = ServiceProduct.values();
        var catalogue = sources.tariffs(java.util.Arrays.stream(products)
                .map(ServiceProduct::code)
                .distinct().toList(), now);

        return java.util.Arrays.stream(products).map(product -> {
            var matches = catalogue.stream().filter(row ->
                    row.service().equals(product.code())
                            && row.option().equals(product.option()))
                    .map(PublishedTariffRecord::tariff).toList();
            var rate = matches.size() == 1 ? matches.getFirst() : null;
            boolean usable = rate != null
                    && product.supportsUnit(rate.unit())
                    && rate.costGroup().equals(product.group())
                    && rate.rate().signum() >= 0
                    && rate.rate().stripTrailingZeros().scale() <= 2
                    && product.mode() != PricingMode.INCLUDED;

            return new ServiceRateResponse(
                    product, product.section(), usable ? rate.unit()
                            : product.unit(), usable
                            ? rate.rate().setScale(2,
                                    java.math.RoundingMode.HALF_UP)
                                    .toPlainString() : null,
                    "USD", product.mode(), usable ? rate.tariffId() : null,
                    usable ? rate.version() : null);
        }).toList();
    }

    private OrderException unsupportedQuote() {
        return new OrderException(HttpStatus.CONFLICT,
                "QUOTE_DATA_UNSUPPORTED",
                "This quotation requires a supported data version.");
    }

    /**
     * Resolves current sourced itinerary kilometres.
     * @param order authorized order
     * @param revision exact current revision, null for draft
     * @param now server time
     * @return sourced quantities or pending
     */
    public ItineraryPricingContext itinerary(long order, Long revision,
            OffsetDateTime now) {
        return revision == null ? new ItineraryPricingContext(null, null,
                null, "PENDING") : sources.itinerary(order, revision, now);
    }

    /**
     * Supplies quoted draft distances without confirming resources or gates.
     * @param order authorized locked order
     * @param revision current submitted revision
     * @param now source evaluation time
     * @return sourced draft itinerary quantities or pending
     */
    public ItineraryPricingContext quotationItinerary(long order,
            long revision, OffsetDateTime now) {
        return sources.itinerary(order, revision, now, true);
    }

    /**
     * @param order authorized parent
     * @param revision exact revision, null before submission
     * @param now server time
     * @return current plan retail airfare estimate, if recorded
     */
    public Optional<java.math.BigDecimal> airfare(long order, Long revision,
            OffsetDateTime now) {
        return revision == null ? Optional.empty()
                : sources.airfare(order, revision, now);
    }

    /**
     * Keeps UNKNOWN distinct from explicitly verified NOT_REQUIRED.
     * @param order authorized parent
     * @param revision exact revision or null
     * @param now server time
     * @param horizon required validity horizon
     * @return actual assessment or unknown
     */
    public QuarantineAssessmentResult quarantine(long order, Long revision,
            OffsetDateTime now, OffsetDateTime horizon) {
        return revision == null ? new QuarantineAssessmentResult("UNKNOWN",
                null, null) : sources.quarantine(order, revision, now, horizon);
    }

    /**
     * Reads verified clinical counts or null quantities.
     * @param order authorized parent
     * @param revision exact revision or null
     * @param now server time
     * @param horizon validity horizon
     * @return actual clinical unit counts
     */
    public VeterinaryAssessmentResult veterinary(long order, Long revision,
            OffsetDateTime now, OffsetDateTime horizon) {
        return revision == null ? new VeterinaryAssessmentResult(null, null,
                null, null) : sources.veterinary(order, revision, now, horizon);
    }

    /**
     * Resolves explicit selected-offer inclusions without leaking supplier
     * cost.
     * @param revision exact current revision or null
     * @param now server time
     * @return explicit inclusions or an ambiguity blocker
     */
    public BundleResult bundles(Long revision, OffsetDateTime now) {
        List<BundleSourceRecord> records = revision == null ? List.of()
                : sources.bundles(revision, now);
        Set<String> included = new HashSet<>();
        BundleSourceRecord source;
        JsonNode node;

        if (records.isEmpty()) {
            return new BundleResult(Set.of(), false);
        }
        if (records.size() != 1) {
            return new BundleResult(Set.of(), true);
        }
        source = records.getFirst();
        if (source.includesStall()) {
            included.add("AIR_STALL");
        }
        if (source.includedCharges() == null) {
            return new BundleResult(Set.copyOf(included), !included.isEmpty());
        }
        try {
            node = mapper.readTree(source.includedCharges());
            if (node.path("schemaVersion").asInt() != 1
                    || !node.path("services").isArray()) {
                return new BundleResult(Set.of(), true);
            }
            for (JsonNode code : node.path("services")) {
                if (!code.isTextual()
                        || !BUNDLE_CODES.contains(code.asText())) {
                    return new BundleResult(Set.of(), true);
                }
                included.add(code.asText());
            }
            return new BundleResult(Set.copyOf(included), !included.isEmpty()
                    && !"ENTIRE_ROUTE".equals(node.path("coverage").asText()));
        } catch (JsonProcessingException error) {
            return new BundleResult(Set.of(), true);
        }
    }
}
