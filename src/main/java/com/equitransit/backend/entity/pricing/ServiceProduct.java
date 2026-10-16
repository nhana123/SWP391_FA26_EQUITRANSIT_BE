/*
 * ServiceProduct
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.entity.pricing;


/**
 * Fixed service identities and units; clients cannot invent pricing formulas.
 */
public enum ServiceProduct {
    ROAD_TRANSPORT("ROAD_TRANSPORT", "EQUITRANSIT",
            "TRANSPORTATION", "TRANSPORTATION",
            "KM", PricingMode.RATE),
    AIRFARE("AIRFARE", "DEFAULT",
            "TRANSPORTATION", "TRANSPORTATION",
            "SHIPMENT", PricingMode.EVIDENCE),
    AIR_STALL("AIR_STALL", "DEFAULT",
            "AIR_STALL", "HORSE_ACCOMMODATION",
            "SLOT_KM", PricingMode.RATE),
    BASIC_CARE("BASIC_CARE", "DEFAULT",
            "HORSE_CARE", "HORSE_CARE",
            "INCLUDED", PricingMode.INCLUDED),
    ENHANCED_CARE("HORSE_CARE", "ENHANCED",
            "HORSE_CARE", "HORSE_CARE",
            "HORSE_KM", PricingMode.RATE),
    GROOMING("GROOMING", "EXTRA",
            "HORSE_CARE", "HORSE_CARE",
            "PERSON_DAY", PricingMode.RATE),
    BOARDING("BOARDING", "EXTRA",
            "HORSE_CARE", "HORSE_ACCOMMODATION",
            "HORSE_NIGHT", PricingMode.RATE),
    VETERINARY_EXAM("VETERINARY", "EXAM",
            "VETERINARY", "VETERINARY",
            "HORSE_VISIT", PricingMode.RATE),
    VETERINARY_TEST("VETERINARY", "TEST",
            "VETERINARY", "VETERINARY",
            "TEST_SAMPLE", PricingMode.RATE),
    VETERINARY_VACCINE("VETERINARY", "VACCINE",
            "VETERINARY", "VETERINARY",
            "DOSE", PricingMode.RATE),
    VETERINARY_CERTIFICATE("VETERINARY", "CERTIFICATE",
            "VETERINARY", "VETERINARY",
            "CERTIFICATE", PricingMode.RATE),
    QUARANTINE_STANDARD("QUARANTINE", "STANDARD",
            "QUARANTINE", "DOCUMENTATION_QUARANTINE",
            "HORSE_DAY", PricingMode.RATE),
    QUARANTINE_ENHANCED("QUARANTINE", "ENHANCED",
            "QUARANTINE", "DOCUMENTATION_QUARANTINE",
            "HORSE_DAY", PricingMode.RATE),
    INSURANCE_MORTALITY("INSURANCE", "MORTALITY",
            "INSURANCE", "INSURANCE",
            "POLICY", PricingMode.EVIDENCE),
    INSURANCE_COMBINED("INSURANCE", "COMBINED",
            "INSURANCE", "INSURANCE",
            "POLICY", PricingMode.EVIDENCE),
    DOCUMENT_COORDINATION("DOCUMENT_COORDINATION", "DEFAULT",
            "ADDITIONAL_CHARGES", "DOCUMENTATION_QUARANTINE",
            "ROUTE_KM", PricingMode.RATE),
    DOCUMENT_REVIEW("DOCUMENT_REVIEW", "DEFAULT",
            "ADDITIONAL_CHARGES", "DOCUMENTATION_QUARANTINE",
            "INCLUDED", PricingMode.INCLUDED),
    AIRPORT_HANDLING("AIRPORT_HANDLING", "DEFAULT",
            "ADDITIONAL_CHARGES", "AIRPORT_CUSTOMS",
            "HORSE_AIRPORT", PricingMode.EVIDENCE),
    TERMINAL_HANDLING("TERMINAL_HANDLING", "DEFAULT",
            "ADDITIONAL_CHARGES", "AIRPORT_CUSTOMS",
            "HORSE_AIRPORT", PricingMode.EVIDENCE),
    CUSTOMS_DECLARATION("CUSTOMS_DECLARATION", "DEFAULT",
            "ADDITIONAL_CHARGES", "AIRPORT_CUSTOMS",
            "DECLARATION", PricingMode.EVIDENCE),
    GOVERNMENT_DUTY("GOVERNMENT_DUTY", "DEFAULT",
            "ADDITIONAL_CHARGES", "AIRPORT_CUSTOMS",
            "ASSESSMENT", PricingMode.EVIDENCE),
    WAITING("WAITING", "DEFAULT",
            "ADDITIONAL_CHARGES", "ADDITIONAL_CHARGES",
            "HOUR", PricingMode.EVENT),
    ESCORT("ESCORT", "REQUESTED",
            "HORSE_CARE", "HORSE_CARE",
            "PERSON_LEG", PricingMode.EVIDENCE);

    private final String code;
    private final String option;
    private final String section;
    private final String group;
    private final String unit;
    private final PricingMode mode;

    ServiceProduct(String code, String option, String section, String group,
            String unit, PricingMode mode) {
        this.code = code;
        this.option = option;
        this.section = section;
        this.group = group;
        this.unit = unit;
        this.mode = mode;
    }

    /** @return server-owned code */
    public String code() {
        return code;
    }

    /** @return server-owned option */
    public String option() {
        return option;
    }

    /** @return server-owned section */
    public String section() {
        return section;
    }

    /** @return server-owned group */
    public String group() {
        return group;
    }

    /** @return server-owned unit */
    public String unit() {
        return unit;
    }

    /** @return server-owned mode */
    public PricingMode mode() {
        return mode;
    }

    /**
     * @param code persisted service identity
     * @param option persisted option
     * @return supported product or null for an unsupported legacy row
     */
    public static ServiceProduct find(String code, String option) {
        for (var value : values()) {
            if (value.code.equals(code) && value.option.equals(option)) {
                return value;
            }
        }
        return null;
    }
}
