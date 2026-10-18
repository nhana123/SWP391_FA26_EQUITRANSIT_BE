/*
 * BundleSourceRecord
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Read bundle evidence without supplier purchase prices.
 */
package com.equitransit.backend.entity.pricing;


/**
 * Internal selected-offer projection.
 * @param includesStall whether the selected offer explicitly includes stall
 * @param includedCharges source JSON envelope, parsed conservatively by facade
 */
public record BundleSourceRecord(
        boolean includesStall, String includedCharges) {
}
