/*
 * StallFitEvaluator
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Evaluate actual measurements against approved sourced limits.
 */
package com.equitransit.backend.service;


import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import com.equitransit.backend.dto.response.HorseResponse;
import com.equitransit.backend.entity.AirStallPreference;
import com.equitransit.backend.entity.FitPolicyRecord;
import com.equitransit.backend.entity.StallFitResult;


/** Measurement eligibility is separate from equipment/carrier confirmation. */
@Component
public class StallFitEvaluator {

    /**
     * Applies one unambiguous effective policy with inclusive dimension limits.
     * @param option requested slot configuration
     * @param horse owned/captured measurements, not a new client horse ID
     * @param policies approved source records covering the journey horizon
     * @param now server time for invalid future measurement detection
     * @return measured eligibility and safe blocker codes
     */
    public StallFitResult evaluate(AirStallPreference option,
            HorseResponse horse, List<FitPolicyRecord> policies,
            OffsetDateTime now) {
        BigDecimal slots = StallFitEvaluator.slots(option);
        List<FitPolicyRecord> matches = policies.stream()
                .filter(policy -> policy.slots().compareTo(slots) == 0)
                .toList();
        FitPolicyRecord policy;

        if (horse.weightKg() == null || horse.heightCm() == null
                || horse.bodyWidthCm() == null || horse.measuredAt() == null
                || horse.weightKg().signum() <= 0
                || horse.heightCm().signum() <= 0
                || horse.bodyWidthCm().signum() <= 0
                || horse.measuredAt().isAfter(now)) {
            return result(option, slots, "PENDING", null,
                    "HORSE_MEASUREMENTS_REQUIRED");
        }
        if (matches.size() != 1) {
            return result(option, slots, "PENDING", null, matches.isEmpty()
                    ? "FIT_POLICY_PENDING" : "FIT_POLICY_AMBIGUOUS");
        }
        policy = matches.getFirst();
        if (policy.maxWeight() == null || policy.maxHeight() == null
                || policy.maxWidth() == null
                || policy.maxWeight().signum() <= 0
                || policy.maxHeight().signum() <= 0
                || policy.maxWidth().signum() <= 0) {
            return result(option, slots, "PENDING", policy,
                    "FIT_POLICY_CONFIGURATION_INVALID");
        }
        if (horse.weightKg().compareTo(policy.maxWeight()) > 0
                || horse.heightCm().compareTo(policy.maxHeight()) > 0
                || horse.bodyWidthCm().compareTo(policy.maxWidth()) > 0) {
            return result(option, slots, "UNSUITABLE", policy,
                    "HORSE_EXCEEDS_APPROVED_LIMITS");
        }
        return new StallFitResult(option.name(), slots, "ELIGIBLE",
                policy.policyId(), policy.version(),
                horse.sexCode() == com.equitransit.backend.entity.HorseSex
                        .STALLION && option != AirStallPreference.PRIVATE
                        ? List.of("STALLION_COMPATIBILITY_REVIEW_REQUIRED")
                        : List.of());
    }

    /**
     * Defines the requested configuration, never a physical seat reservation.
     * @param option shared one, wide or private
     * @return 1, 1.5 or 3 slots
     */
    public static BigDecimal slots(AirStallPreference option) {
        return switch (option) {
            case SHARED_ONE -> BigDecimal.ONE;
            case WIDE -> new BigDecimal("1.5");
            case PRIVATE -> new BigDecimal("3");
            default -> throw new IllegalArgumentException("Unknown slot type.");
        };
    }

    /**
     * Suggests a view-only default after a proven one-slot exclusion.
     * @param horse captured measurements
     * @param policies sourced policies for the same journey horizon
     * @param now server time
     * @return wide unless ruled out, or null if no default is justified
     */
    public AirStallPreference defaultOption(HorseResponse horse,
            List<FitPolicyRecord> policies, OffsetDateTime now) {
        var shared = evaluate(AirStallPreference.SHARED_ONE, horse,
                policies, now);
        var wide = evaluate(AirStallPreference.WIDE, horse, policies, now);

        return "UNSUITABLE".equals(shared.status())
                && !"UNSUITABLE".equals(wide.status())
                ? AirStallPreference.WIDE : null;
    }

    private StallFitResult result(AirStallPreference option, BigDecimal slots,
            String status, FitPolicyRecord policy, String reason) {
        return new StallFitResult(option.name(), slots, status,
                policy == null ? null : policy.policyId(),
                policy == null ? null : policy.version(), List.of(reason));
    }
}
