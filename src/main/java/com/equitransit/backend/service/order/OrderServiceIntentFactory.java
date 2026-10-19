/*
 * OrderServiceIntentFactory
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Snapshot typed requests without inventing payable services.
 */
package com.equitransit.backend.service.order;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.equitransit.backend.dto.order.request.OrderServicePreferences;
import com.equitransit.backend.entity.order.AirStallPreference;
import com.equitransit.backend.entity.order.OrderServiceIntent;
import com.equitransit.backend.entity.order.ServiceArrangement;
import com.equitransit.backend.entity.pricing.StallFitResult;
import com.equitransit.backend.exception.order.OrderException;


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
        return requested(preferences, null);
    }

    /**
     * Captures server-checked measurements without claiming carrier acceptance.
     * @param preferences typed requested inputs
     * @param fit source-backed measurement assessment or pending
     * @return immutable intent rows
     */
    public List<OrderServiceIntent> requested(
            OrderServicePreferences preferences, StallFitResult fit) {
        List<OrderServiceIntent> intents = new ArrayList<>();
        Map<String, Object> fields;

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
        if (preferences.getAirStall() == AirStallPreference.SHARED_ONE
                && (fit == null || !"ELIGIBLE".equals(fit.status()))) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "AIR_STALL_ASSESSMENT_REQUIRED",
                    "One-slot selection requires the confirmed fit policy "
                            + "from the service eligibility workflow.");
        }
        if (preferences.getAirStall() != null) {
            fields = new java.util.LinkedHashMap<>();
            fields.put("fitStatus", fit != null
                    && "ELIGIBLE".equals(fit.status())
                    ? "SUITABLE" : "PENDING");
            fields.put("carrierConfirmation", "NOT_CONFIRMED");
            fields.put("slotQuantity", StallFitEvaluator
                    .slots(preferences.getAirStall()));
            if (fit != null && fit.policyId() != null) {
                fields.put("policyId", fit.policyId());
                fields.put("policyVersion", fit.policyVersion());
            }
            add(intents, "AIR_STALL", preferences.getAirStall().name(),
                    fields);
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
            fields = new java.util.LinkedHashMap<>();
            if (preferences.getDeclaredValue() != null) {
                fields.put("declaredValue", preferences.getDeclaredValue()
                        .toPlainString());
                fields.put("valueCurrency", "USD");
            }
            if (optional(preferences.getExistingPolicyReference()) != null) {
                fields.put("existingPolicyReference", optional(
                        preferences.getExistingPolicyReference()));
            }
            add(intents, "INSURANCE",
                    preferences.getInsurance().name(), fields);
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
