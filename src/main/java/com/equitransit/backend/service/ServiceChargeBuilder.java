/*
 * ServiceChargeBuilder
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Disclose all cost groups in the seven customer booking sections.
 */
package com.equitransit.backend.service;


import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.equitransit.backend.dto.request.OrderServicePreferences;
import com.equitransit.backend.dto.request.OrderDraftRequest;
import com.equitransit.backend.dto.response.ServiceChargeResponse;
import com.equitransit.backend.entity.BundleResult;
import com.equitransit.backend.entity.PublishedTariffRecord;
import com.equitransit.backend.entity.TariffRecord;
import com.equitransit.backend.entity.InsurancePreference;
import com.equitransit.backend.entity.ItineraryPricingContext;
import com.equitransit.backend.entity.QuarantineAssessmentResult;
import com.equitransit.backend.entity.ServiceArrangement;
import com.equitransit.backend.entity.VeterinaryAssessmentResult;
import com.equitransit.backend.repository.ServiceSourceRepository;


/**
 * Catalogue identifiers are contract keys, not hard-coded commercial prices.
 */
@Component
public class ServiceChargeBuilder {

    private static final List<String> CODES = List.of("ROAD_TRANSPORT",
            "AIRFARE", "AIR_STALL", "BASIC_CARE", "HORSE_CARE", "GROOMING",
            "BOARDING", "VETERINARY", "QUARANTINE", "INSURANCE",
            "DOCUMENT_COORDINATION", "DOCUMENT_REVIEW", "AIRPORT_HANDLING",
            "TERMINAL_HANDLING", "CUSTOMS_DECLARATION", "GOVERNMENT_DUTY",
            "WAITING", "ESCORT");

    private final ServiceSourceRepository sources;
    private final ChargeCalculator calculator;

    /**
     * Creates charge disclosure using published tariff versions.
     * @param sources published rate adapter
     * @param calculator exact decimal arithmetic and pending semantics
     */
    public ServiceChargeBuilder(ServiceSourceRepository sources,
            ChargeCalculator calculator) {
        this.sources = sources;
        this.calculator = calculator;
    }

    /**
     * Builds each charge once; Show all reveals these rows without mutating
     * fees.
     * @param preferences explicitly selected inputs or null
     * @param itinerary current sourced plan quantities
     * @param quarantine revision-bound Specialist applicability
     * @param veterinary revision-bound clinical counts
     * @param bundle explicit supplier inclusions without purchase cost
     * @param now server calculation time
     * @return full selected/optional/mandatory/pending/event-only breakdown
     */
    public List<ServiceChargeResponse> build(
            OrderServicePreferences preferences,
            ItineraryPricingContext itinerary,
            QuarantineAssessmentResult quarantine,
            VeterinaryAssessmentResult veterinary, BundleResult bundle,
            OffsetDateTime now) {
        return build(preferences, itinerary, quarantine, veterinary, bundle,
                now, null);
    }

    /**
     * Adds route-based commercial estimates without claiming legal approval.
     * @param preferences explicit or view-default preferences
     * @param itinerary sourced distances, including explicit road-only routes
     * @param quarantine clinical applicability
     * @param veterinary clinical counts
     * @param bundle evidenced inclusions
     * @param now calculation time
     * @param request route and transit inputs, null for legacy callers
     * @return one disclosed row per charge
     */
    public List<ServiceChargeResponse> build(
            OrderServicePreferences preferences,
            ItineraryPricingContext itinerary,
            QuarantineAssessmentResult quarantine,
            VeterinaryAssessmentResult veterinary, BundleResult bundle,
            OffsetDateTime now, OrderDraftRequest request) {
        OrderServicePreferences selected = preferences == null
                ? new OrderServicePreferences() : preferences;
        List<ServiceChargeResponse> rows = new ArrayList<>();
        List<PublishedTariffRecord> catalogue = sources.tariffs(CODES, now);
        boolean international = request != null
                && request.getOriginCountryCode() != null
                && request.getDestinationCountryCode() != null
                && !request.getOriginCountryCode().isBlank()
                && !request.getDestinationCountryCode().isBlank()
                && !request.getOriginCountryCode().equals(
                        request.getDestinationCountryCode());
        boolean airRoute = itinerary.airKm() == null ? international
                : itinerary.airKm().signum() > 0;
        int transits = request == null || request.getTransitAirports() == null
                ? 0 : request.getTransitAirports().size();
        BigDecimal airports = airRoute
                ? BigDecimal.valueOf(2L + transits) : null;
        String stallUnit = unit(catalogue, "AIR_STALL", "DEFAULT",
                "SLOT_KM");
        String careUnit = unit(catalogue, "HORSE_CARE", "ENHANCED",
                "HORSE_KM");
        String documentUnit = unit(catalogue, "DOCUMENT_COORDINATION",
                "DEFAULT", "ROUTE_KM");
        BigDecimal route = itinerary.roadKm() == null
                || itinerary.airKm() == null ? null
                : itinerary.roadKm().add(itinerary.airKm());
        BigDecimal slotKm = selected.getAirStall() == null
                || itinerary.airKm() == null ? null : itinerary.airKm()
                        .multiply(StallFitEvaluator.slots(
                                selected.getAirStall()));
        if ("SHIPMENT".equals(stallUnit)
                && selected.getAirStall() != null) {
            slotKm = StallFitEvaluator.slots(selected.getAirStall());
        }
        boolean companyRoad = selected.getRoadArrangement()
                != ServiceArrangement.CUSTOMER;
        boolean companyVet = selected.getVeterinaryArrangement()
                != ServiceArrangement.CUSTOMER;

        add(rows, "ROAD_TRANSPORT", "EQUITRANSIT", "TRANSPORTATION",
                "TRANSPORTATION", "Road transport", "KM", "ROAD km × rate",
                true, true, itinerary.roadKm(), catalogue,
                selected.getRoadArrangement() == null ? "SELECTION_REQUIRED"
                        : companyRoad ? null : "EXTERNAL_PROVIDER");
        add(rows, "AIRFARE", "DEFAULT", "TRANSPORTATION", "TRANSPORTATION",
                "Air freight", "SHIPMENT", "Customer airfare quote pending",
                true, true, null, catalogue, itinerary.airKm() != null
                        && itinerary.airKm().signum() == 0
                        ? "NOT_REQUIRED" : "AIRFARE_PENDING");
        add(rows, "AIR_STALL", "DEFAULT", "AIR_STALL", "HORSE_ACCOMMODATION",
                "Air stall", stallUnit, "SHIPMENT".equals(stallUnit)
                        ? "slots × shipment rate" : "slots × AIR km × rate",
                true, true, slotKm, catalogue,
                itinerary.airKm() != null && itinerary.airKm().signum() == 0
                        ? "NOT_REQUIRED" : selected.getAirStall() == null
                                && !bundle.included().contains("AIR_STALL")
                                ? "SELECTION_REQUIRED"
                                : inclusion("AIR_STALL", bundle));
        add(rows, "BASIC_CARE", "DEFAULT", "HORSE_CARE", "HORSE_CARE",
                "Basic care", "INCLUDED", "Included; no duplicate charge",
                true, true, null, catalogue, "INCLUDED");
        add(rows, "HORSE_CARE", "ENHANCED", "HORSE_CARE", "HORSE_CARE",
                "Enhanced care", careUnit, "SHIPMENT".equals(careUnit)
                        ? "1 shipment × rate" : "1 horse × route km × rate",
                Boolean.TRUE.equals(selected.getEnhancedCare()), false,
                "SHIPMENT".equals(careUnit) ? BigDecimal.ONE : route,
                catalogue, null);
        add(rows, "GROOMING", "EXTRA", "HORSE_CARE", "HORSE_CARE",
                "Extra groom", "PERSON_DAY", "requested groom days × rate",
                positive(selected.getGroomingDays()), false,
                number(selected.getGroomingDays()), catalogue, null);
        add(rows, "BOARDING", "EXTRA", "HORSE_CARE", "HORSE_ACCOMMODATION",
                "Extra boarding", "HORSE_NIGHT",
                "extra nights only; quarantine not added again",
                positive(selected.getBoardingNights()), false,
                number(selected.getBoardingNights()), catalogue, null);
        clinical(rows, "EXAM", "Veterinary visit", "HORSE_VISIT",
                veterinary.visits(), companyVet,
                selected.getVeterinaryArrangement() == null, catalogue);
        clinical(rows, "TEST", "Laboratory sample", "TEST_SAMPLE",
                veterinary.tests(), companyVet,
                selected.getVeterinaryArrangement() == null, catalogue);
        clinical(rows, "VACCINE", "Vaccination", "DOSE",
                veterinary.doses(), companyVet,
                selected.getVeterinaryArrangement() == null, catalogue);
        clinical(rows, "CERTIFICATE", "Veterinary certificate", "CERTIFICATE",
                veterinary.certificates(), companyVet,
                selected.getVeterinaryArrangement() == null, catalogue);
        add(rows, "QUARANTINE", selected.getQuarantinePackage() == null
                ? "" : selected.getQuarantinePackage().name(), "QUARANTINE",
                "DOCUMENTATION_QUARANTINE", "Quarantine care package",
                "HORSE_DAY", "1 horse × Specialist required days × rate",
                true, true, number(quarantine.requiredDays()), catalogue,
                "NOT_REQUIRED".equals(quarantine.applicability())

                ? "NOT_REQUIRED" : selected.getQuarantinePackage() == null
                                ? "SELECTION_REQUIRED" : null);
        add(rows, "INSURANCE", selected.getInsurance() == null
                ? "" : selected.getInsurance().name(), "INSURANCE", "INSURANCE",
                "Insurance", "POLICY", "Declared value is not the premium",
                selected.getInsurance() != null, false, null, catalogue,
                selected.getInsurance() == null ? "NOT_SELECTED"
                        : selected.getInsurance() == InsurancePreference.NONE
                        ? "NOT_REQUIRED"

                : selected.getInsurance() == InsurancePreference.EXISTING

                ? "EXTERNAL_PROVIDER" : "INSURER_TERMS_PENDING");
        add(rows, "DOCUMENT_COORDINATION", "DEFAULT", "ADDITIONAL_CHARGES",
                "DOCUMENTATION_QUARANTINE",
                "Document processing and coordination",
                documentUnit, "SHIPMENT".equals(documentUnit)
                        ? "1 shipment × rate; once per booking"
                        : "(ROAD km + AIR km) × rate; once per booking",
                true, true, "SHIPMENT".equals(documentUnit)
                        ? BigDecimal.ONE : route, catalogue, null);
        add(rows, "DOCUMENT_REVIEW", "DEFAULT", "ADDITIONAL_CHARGES",
                "DOCUMENTATION_QUARANTINE", "Document review and follow-up",
                "INCLUDED", "Included in coordination; no processing fee twice",
                true, true, null, catalogue, "INCLUDED");
        add(rows, "AIRPORT_HANDLING", "DEFAULT", "ADDITIONAL_CHARGES",
                "AIRPORT_CUSTOMS", "Airport handling", "HORSE_AIRPORT",
                "Route estimate: (2 + transit airports) × rate",
                true, true, airports, catalogue,
                inclusion("AIRPORT_HANDLING", bundle));
        add(rows, "TERMINAL_HANDLING", "DEFAULT", "ADDITIONAL_CHARGES",
                "AIRPORT_CUSTOMS", "Terminal handling", "HORSE_AIRPORT",
                "Route estimate: (2 + transit airports) × rate",
                true, true, airports, catalogue,
                inclusion("TERMINAL_HANDLING", bundle));
        add(rows, "CUSTOMS_DECLARATION", "DEFAULT", "ADDITIONAL_CHARGES",
                "AIRPORT_CUSTOMS", "Customs declaration service", "DECLARATION",
                "Route estimate: 2 declarations × rate; excludes duty",
                true, true, international ? new BigDecimal("2") : null,
                catalogue, inclusion("CUSTOMS_DECLARATION",
                bundle));
        add(rows, "GOVERNMENT_DUTY", "DEFAULT", "ADDITIONAL_CHARGES",
                "AIRPORT_CUSTOMS", "Government duties and taxes", "ASSESSMENT",
                "Authority assessment pending; never assumed zero",
                true, true, null, catalogue, "AUTHORITY_ASSESSMENT_PENDING");
        add(rows, "WAITING", "DEFAULT", "ADDITIONAL_CHARGES",
                "ADDITIONAL_CHARGES", "Waiting beyond configured free time",
                "HOUR", "Eligible elapsed time; rule and event assessment",
                false, false, null, catalogue, "EVENT_ONLY");
        add(rows, "ESCORT", "REQUESTED", "HORSE_CARE", "HORSE_CARE",
                "Requested escort support", "PERSON_LEG",
                "Route estimate: (1 + transit airports) × rate",
                selected.getEscortNotes() != null
                        && !selected.getEscortNotes().isBlank(), false,
                airRoute ? BigDecimal.valueOf(1L + transits) : null,
                catalogue, selected.getEscortNotes() == null
                        || selected.getEscortNotes().isBlank()
                        ? "NOT_SELECTED" : null);
        return List.copyOf(rows);
    }

    private void clinical(List<ServiceChargeResponse> rows, String option,
            String label, String unit, Integer quantity, boolean company,
            boolean notChosen,
            List<PublishedTariffRecord> catalogue) {
        rows.add(calculator.line("VETERINARY_" + option, "VETERINARY",
                "VETERINARY", label, unit, "Specialist quantity × unit rate",
                true, true, number(quantity),
                rates(catalogue, "VETERINARY", option),
                quantity != null && quantity == 0
                        ? "NOT_REQUIRED" : notChosen ? "SELECTION_REQUIRED"
                                : company ? null : "EXTERNAL_PROVIDER"));
    }

    private void add(List<ServiceChargeResponse> rows, String code,
            String option, String section, String group, String label,
            String unit, String formula, boolean selected, boolean mandatory,
            BigDecimal quantity, List<PublishedTariffRecord> catalogue,
            String override) {
        rows.add(calculator.line(code, section, group, label, unit, formula,
                selected, mandatory, quantity, rates(catalogue, code, option),
                override));
    }

    private List<TariffRecord> rates(List<PublishedTariffRecord> catalogue,
            String code, String option) {
        return catalogue.stream().filter(row -> code.equals(row.service())
                && option.equals(row.option()))
                .map(PublishedTariffRecord::tariff).toList();
    }

    private String unit(List<PublishedTariffRecord> catalogue, String code,
            String option, String fallback) {
        var matching = rates(catalogue, code, option);

        return matching.size() == 1
                && "SHIPMENT".equals(matching.getFirst().unit())
                ? "SHIPMENT" : fallback;
    }

    private String inclusion(String code, BundleResult bundle) {
        return bundle.ambiguous() && (bundle.included().isEmpty()
                || bundle.included().contains(code)) ? "BUNDLE_REVIEW_REQUIRED"
                : bundle.included().contains(code) ? "INCLUDED" : null;
    }

    private BigDecimal number(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
