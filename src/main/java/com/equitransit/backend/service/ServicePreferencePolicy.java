/*
 * ServicePreferencePolicy
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Recheck requested configurations using actual server policy.
 */
package com.equitransit.backend.service;


import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.equitransit.backend.dto.request.OrderServicePreferences;
import com.equitransit.backend.dto.response.HorseResponse;
import com.equitransit.backend.entity.AirStallPreference;
import com.equitransit.backend.entity.FitPolicyRecord;
import com.equitransit.backend.entity.InsurancePreference;
import com.equitransit.backend.entity.StallFitResult;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.ServiceSourceRepository;


/**
 * No prices, mandatory days or measured eligibility are accepted from clients.
 */
@Component
public class ServicePreferencePolicy {

    private final ServiceSourceRepository sources;
    private final StallFitEvaluator fit;
    private final Clock clock;

    /**
     * Creates the server-side selection boundary.
     * @param sources approved persisted policies
     * @param fit measurement evaluator
     * @param clock server time
     */
    public ServicePreferencePolicy(ServiceSourceRepository sources,
            StallFitEvaluator fit, Clock clock) {
        this.sources = sources;
        this.fit = fit;
        this.clock = clock;
    }

    /**
     * Lists measured options for the order's already selected horse.
     * @param horse owned current or captured profile
     * @param deadline requested delivery horizon
     * @return eligible/pending options, never oversized or unknown one-slot
     */
    public List<StallFitResult> options(HorseResponse horse,
            OffsetDateTime deadline) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<FitPolicyRecord> policies = sources.fitPolicies(now,
                horizon(deadline, now));

        return java.util.Arrays.stream(AirStallPreference.values())
                .map(option -> fit.evaluate(option, horse, policies, now))
                .filter(result -> !"UNSUITABLE".equals(result.status()))
                .filter(result -> !"SHARED_ONE".equals(result.option())
                        || "ELIGIBLE".equals(result.status())).toList();
    }

    /**
     * Rechecks each write, including direct F03 submission/revision endpoints.
     * @param preferences requested options
     * @param horse owned profile captured in the submission transaction
     * @param deadline delivery horizon
     * @return measured fit result for the chosen configuration or null
     */
    public StallFitResult validate(OrderServicePreferences preferences,
            HorseResponse horse, OffsetDateTime deadline) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        StallFitResult result;

        if (preferences == null) {
            return null;
        }
        insurance(preferences);
        if (preferences.getAirStall() == null) {
            return null;
        }
        result = fit.evaluate(preferences.getAirStall(), horse,
                sources.fitPolicies(now, horizon(deadline, now)), now);
        if ("UNSUITABLE".equals(result.status())) {
            throw failure("AIR_STALL_UNSUITABLE",
                    "The selected configuration exceeds approved limits.");
        }
        if (preferences.getAirStall() == AirStallPreference.SHARED_ONE
                && !"ELIGIBLE".equals(result.status())) {
            throw failure("AIR_STALL_ASSESSMENT_REQUIRED",
                    "One-slot selection requires complete measurements "
                            + "and an unambiguous approved policy.");
        }
        return result;
    }

    /**
     * Computes the minimum valid source horizon without inventing journey
     * dates.
     * @param deadline known delivery horizon or null
     * @param now server time
     * @return later of known deadline and now
     */
    public OffsetDateTime horizon(OffsetDateTime deadline, OffsetDateTime now) {
        return deadline == null || deadline.isBefore(now) ? now : deadline;
    }

    private void insurance(OrderServicePreferences preferences) {
        InsurancePreference coverage = preferences.getInsurance();
        String reference = preferences.getExistingPolicyReference();

        if (coverage == InsurancePreference.EXISTING
                && (reference == null || reference.isBlank())) {
            throw failure("INSURANCE_REFERENCE_REQUIRED",
                    "Supply your existing policy reference for verification.");
        }
        if (coverage != InsurancePreference.EXISTING
                && reference != null && !reference.isBlank()) {
            throw failure("INSURANCE_INPUT_INVALID",
                    "Existing policy reference requires EXISTING coverage.");
        }
        if ((coverage == null || coverage == InsurancePreference.NONE)
                && preferences.getDeclaredValue() != null) {
            throw failure("INSURANCE_INPUT_INVALID",
                    "Declare value only when insurance is requested.");
        }
    }

    private OrderException failure(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }
}
