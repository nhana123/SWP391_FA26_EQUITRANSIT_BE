/*
 * TariffRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.entity.pricing;


import java.math.BigDecimal;


/**
 * Internal sourced service contract.
 * @param tariffId published tariff key
 * @param version published version
 * @param unit actual quantity unit
 * @param costGroup physical cost group
 * @param rate USD unit rate
 */
public record TariffRecord(
        long tariffId, int version, String unit,
        String costGroup, BigDecimal rate) {
}
