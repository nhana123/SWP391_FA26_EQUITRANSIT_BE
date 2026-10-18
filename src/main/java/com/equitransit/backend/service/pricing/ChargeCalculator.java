/*
 * ChargeCalculator
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Compute explicit USD estimates without treating unknowns as zero.
 */
package com.equitransit.backend.service.pricing;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.equitransit.backend.dto.pricing.response.ServiceChargeResponse;
import com.equitransit.backend.entity.pricing.TariffRecord;


/** One line rounds once, HALF_UP at two decimals; never a payable quote. */
@Component
public class ChargeCalculator {

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("9999999999999999.99");

    /**
     * Resolves one published rate and one known quantity with visible blockers.
     * @param code unique line identity
     * @param section customer UI section
     * @param group real physical cost group
     * @param label customer display label
     * @param unit expected actual tariff unit
     * @param formula transparent calculation
     * @param selected explicit request or mandatory disclosure
     * @param mandatory required when applicable
     * @param quantity source quantity or null
     * @param tariffs all effective matching rate versions
     * @param override included/external/event/not-required status, or null
     * @return explicit estimate row with null amount while unresolved
     */
    public ServiceChargeResponse line(String code, String section, String group,
            String label, String unit, String formula, boolean selected,
            boolean mandatory, BigDecimal quantity, List<TariffRecord> tariffs,
            String override) {
        List<String> blockers = new ArrayList<>();
        TariffRecord tariff = tariffs.size() == 1 ? tariffs.getFirst() : null;
        BigDecimal amount = null;
        String status;

        if (tariff != null && (!unit.equals(tariff.unit())
                || !group.equals(tariff.costGroup())
                || tariff.rate().signum() < 0)) {
            tariff = null;
            blockers.add("RATE_CONFIGURATION_INVALID");
        }
        if (override != null) {
            status = override;
            if (override.endsWith("PENDING")
                    || override.endsWith("REQUIRED")) {
                blockers.add(override);
            }
        } else if (!selected) {
            status = "NOT_SELECTED";
        } else if (quantity != null && quantity.signum() == 0) {
            status = "NOT_REQUIRED";
        } else {
            if (tariff == null) {
                blockers.add(tariffs.size() > 1
                        ? "RATE_AMBIGUOUS" : "RATE_PENDING");
            }
            if (quantity == null) {
                blockers.add("QUANTITY_PENDING");
            }
            if (blockers.isEmpty()) {
                amount = quantity.multiply(tariff.rate())
                        .setScale(2, RoundingMode.HALF_UP);
                status = "ESTIMATED";
                if (amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
                    amount = null;
                    status = "AMOUNT_REVIEW_REQUIRED";
                    blockers.add(status);
                }
            } else {
                status = tariff == null ? quantity == null
                        ? "RATE_AND_QUANTITY_PENDING" : "RATE_PENDING"
                        : "QUANTITY_PENDING";
            }
        }
        return new ServiceChargeResponse(code, section, group, label, mandatory,
                selected, unit, decimal(quantity), tariff == null ? null
                        : decimal(tariff.rate()), decimal(amount), status,
                tariff == null ? null : tariff.tariffId(),
                tariff == null ? null : tariff.version(), formula,
                List.copyOf(blockers));
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
