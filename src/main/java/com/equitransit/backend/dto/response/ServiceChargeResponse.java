/*
 * ServiceChargeResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.dto.response;


import java.util.List;


/**
 * Explicit customer service contract, no supplier costs or secrets.
 * @param code unique charge identity
 * @param uiSection one of seven booking UI sections
 * @param costGroup physical cost group
 * @param label customer display label
 * @param mandatory whether this is mandatory when applicable
 * @param selected explicit request or mandatory disclosure
 * @param unit actual unit
 * @param quantity known source quantity or null
 * @param unitRate published USD unit rate or null
 * @param amount known estimate or null
 * @param pricingStatus explicit pending/included/estimate state
 * @param tariffId published source or null
 * @param tariffVersion published version or null
 * @param formula transparent calculation basis
 * @param blockers safe pending reasons
 */
public record ServiceChargeResponse(
        String code, String uiSection, String costGroup,
        String label, boolean mandatory, boolean selected, String unit,
        String quantity, String unitRate, String amount,
        String pricingStatus, Long tariffId, Integer tariffVersion,
        String formula, List<String> blockers) {
}
