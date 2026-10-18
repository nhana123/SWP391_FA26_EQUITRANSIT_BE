/*
 * ServiceChargeBuilder
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Disclose all cost groups in the seven customer booking sections.
 */
package com.equitransit.backend.service.pricing;


import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.equitransit.backend.dto.order.request.OrderServicePreferences;
import com.equitransit.backend.dto.pricing.response.ServiceChargeResponse;
import com.equitransit.backend.entity.pricing.BundleResult;
import com.equitransit.backend.entity.pricing.PublishedTariffRecord;
import com.equitransit.backend.entity.pricing.TariffRecord;
import com.equitransit.backend.entity.order.InsurancePreference;
import com.equitransit.backend.entity.pricing.ItineraryPricingContext;
import com.equitransit.backend.entity.pricing.QuarantineAssessmentResult;
import com.equitransit.backend.entity.order.ServiceArrangement;
import com.equitransit.backend.entity.pricing.VeterinaryAssessmentResult;
import com.equitransit.backend.repository.pricing.ServiceSourceRepository;


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
        OrderServicePreferences selected = preferences == null
                ? new OrderServicePreferences() : preferences;
        List<ServiceChargeResponse> rows = new ArrayList<>();
        List<PublishedTariffRecord> catalogue = sources.tariffs(CODES, now);
        BigDecimal route = itinerary.roadKm() == null
                || itinerary.airKm() == null ? null
                : itinerary.roadKm().add(itinerary.airKm());
        BigDecimal slotKm = selected.getAirStall() == null
                || itinerary.airKm() == null ? null : itinerary.airKm()
                        .multiply(StallFitEvaluator.slots(
                                selected.getAirStall()));
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
                "Air stall", "SLOT_KM", "slots × AIR km × rate",
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
                "Enhanced care", "HORSE_KM", "1 horse × route km × rate",
                Boolean.TRUE.equals(selected.getEnhancedCare()), false,
                route, catalogue, null);
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
                "ROUTE_KM", "(ROAD km + AIR km) × rate; once per booking",
                true, true, route, catalogue, null);
        add(rows, "DOCUMENT_REVIEW", "DEFAULT", "ADDITIONAL_CHARGES",
                "DOCUMENTATION_QUARANTINE", "Document review and follow-up",
                "INCLUDED", "Included in coordination; no processing fee twice",
                true, true, null, catalogue, "INCLUDED");
        add(rows, "AIRPORT_HANDLING", "DEFAULT", "ADDITIONAL_CHARGES",
                "AIRPORT_CUSTOMS", "Airport handling", "HORSE_AIRPORT",
                "1 horse × assessed handling airports × rate",
                true, true, null, catalogue,
                inclusion("AIRPORT_HANDLING", bundle));
        add(rows, "TERMINAL_HANDLING", "DEFAULT", "ADDITIONAL_CHARGES",
                "AIRPORT_CUSTOMS", "Terminal handling", "HORSE_AIRPORT",
                "Review bundled terminal scope before charging separately",
                true, true, null, catalogue, bundle.included()
                        .contains("TERMINAL_HANDLING") ? "INCLUDED"
                                : "BUNDLE_REVIEW_REQUIRED");
        add(rows, "CUSTOMS_DECLARATION", "DEFAULT", "ADDITIONAL_CHARGES",
                "AIRPORT_CUSTOMS", "Customs declaration service", "DECLARATION",
                "assessed declarations × rate; excludes government duty",
                true, true, null, catalogue, inclusion("CUSTOMS_DECLARATION",
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
                "Verified scope and provider confirmation required",
                selected.getEscortNotes() != null
                        && !selected.getEscortNotes().isBlank(), false,
                null, catalogue, selected.getEscortNotes() == null
                        || selected.getEscortNotes().isBlank()
                        ? "NOT_SELECTED" : "SUPPLIER_CONFIRMATION_PENDING");
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

    private String inclusion(String code, BundleResult bundle) {
        return bundle.ambiguous() ? "BUNDLE_REVIEW_REQUIRED"
                : bundle.included().contains(code) ? "INCLUDED" : null;
    }

    private BigDecimal number(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
