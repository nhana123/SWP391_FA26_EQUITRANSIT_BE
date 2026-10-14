/*
 * OrderServiceIntentFactory
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Snapshot typed requests without inventing payable services.
 */
package com.equitransit.backend.service;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.equitransit.backend.dto.request.OrderServicePreferences;
import com.equitransit.backend.entity.AirStallPreference;
import com.equitransit.backend.entity.OrderServiceIntent;
import com.equitransit.backend.entity.ServiceArrangement;
import com.equitransit.backend.exception.OrderException;


/** F05/F08/F09 assess these intents before quote or external confirmation. */
@Component
public class OrderServiceIntentFactory {

    /**
     * Creates immutable requested choices with no price or compulsory-day
     * claim.
     * @param preferences the optional typed service needs
     * @return intent rows and snapshot content
     */
    public List<OrderServiceIntent> requested(
            OrderServicePreferences preferences) {
        List<OrderServiceIntent> intents = new ArrayList<>();

        if (preferences == null) {
            return List.of();
        }
        arrangement(intents, "ROAD_TRANSPORT", preferences.getRoadArrangement(),
                preferences.getRoadProviderName(),
                preferences.getRoadProviderContact());
        arrangement(intents, "VETERINARY",
                preferences.getVeterinaryArrangement(),
                preferences.getVeterinaryProviderName(),
                preferences.getVeterinaryProviderContact());
        if (preferences.getAirStall() == AirStallPreference.SHARED_ONE) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "AIR_STALL_ASSESSMENT_REQUIRED",
                    "One-slot selection requires the confirmed fit policy "
                            + "from the service eligibility workflow.");
        }
        if (preferences.getAirStall() != null) {
            add(intents, "AIR_STALL", preferences.getAirStall().name(),
                    Map.of("fitStatus", "PENDING"));
        }
        if (Boolean.TRUE.equals(preferences.getEnhancedCare())) {
            add(intents, "HORSE_CARE", "ENHANCED", Map.of());
        }
        quantity(intents, "GROOMING", preferences.getGroomingDays(),
                "requestedDays");
        quantity(intents, "BOARDING", preferences.getBoardingNights(),
                "requestedNights");
        if (preferences.getQuarantinePackage() != null) {
            add(intents, "QUARANTINE",
                    preferences.getQuarantinePackage().name(),
                    Map.of("requiredDaysStatus", "UNKNOWN"));
        }
        if (preferences.getInsurance() != null) {
            add(intents, "INSURANCE",
                    preferences.getInsurance().name(), Map.of());
        }
        if (optional(preferences.getEscortNotes()) != null) {
            add(intents, "ESCORT", "REQUESTED", Map.of("notes",
                    optional(preferences.getEscortNotes())));
        }
        return List.copyOf(intents);
    }

    private void arrangement(List<OrderServiceIntent> intents, String code,
            ServiceArrangement arrangement, String name, String contact) {
        if (arrangement != null) {
            intents.add(new OrderServiceIntent(code, arrangement.name(),
                    arrangement.name(), Map.of("schemaVersion", 1,
                            "status", "REQUESTED"),
                    arrangement == ServiceArrangement.CUSTOMER
                            ? optional(name) : null,
                    arrangement == ServiceArrangement.CUSTOMER
                            ? optional(contact) : null));
        }
    }

    private void quantity(List<OrderServiceIntent> intents, String code,
            Integer quantity, String unit) {
        if (quantity != null && quantity > 0) {
            add(intents, code, "EXTRA", Map.of(unit, quantity));
        }
    }

    private void add(List<OrderServiceIntent> intents, String code,
            String option, Map<String, Object> fields) {
        Map<String, Object> inputs = new java.util.LinkedHashMap<>(fields);

        inputs.put("schemaVersion", 1);
        inputs.put("status", "REQUESTED");
        intents.add(new OrderServiceIntent(code, option, "EQUITRANSIT",
                Map.copyOf(inputs), null, null));
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
