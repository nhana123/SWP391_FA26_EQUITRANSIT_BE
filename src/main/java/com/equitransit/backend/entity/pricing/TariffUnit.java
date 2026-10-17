/*
 * TariffUnit
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Bound supported retail units without changing physical schema.
 */
package com.equitransit.backend.entity.pricing;

/** Units remain constrained by the chosen service product. */
public enum TariffUnit {
    KM, SHIPMENT, SLOT_KM, INCLUDED, HORSE_KM, PERSON_DAY, HORSE_NIGHT,
    HORSE_VISIT, TEST_SAMPLE, DOSE, CERTIFICATE, HORSE_DAY, POLICY,
    ROUTE_KM, HORSE_AIRPORT, DECLARATION, ASSESSMENT, HOUR, PERSON_LEG
}
