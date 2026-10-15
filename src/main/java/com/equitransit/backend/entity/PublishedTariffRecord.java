/*
 * PublishedTariffRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Carry a bounded published catalogue without per-charge SQL reads.
 */
package com.equitransit.backend.entity;


/**
 * Published catalogue projection, preserving overlapping versions.
 * @param service catalogue service code
 * @param option catalogue option code
 * @param tariff versioned rate and unit
 */
public record PublishedTariffRecord(String service, String option,
        TariffRecord tariff) {
}
