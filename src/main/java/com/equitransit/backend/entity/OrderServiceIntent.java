/*
 * OrderServiceIntent
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.entity;


import java.util.Map;




/**
 * Requested service input, not pricing, fit or external confirmation.
 * @param serviceCode the serviceCode
 * @param optionCode the optionCode
 * @param arrangedBy the arrangedBy
 * @param requestedInputs the requestedInputs
 * @param providerName the providerName
 * @param providerContact the providerContact
 */
public record OrderServiceIntent(
        String serviceCode,
        String optionCode,
        String arrangedBy,
        Map<String, Object> requestedInputs,
        String providerName,
        String providerContact) {
}
