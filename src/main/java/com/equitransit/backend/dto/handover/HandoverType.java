/*
 * HandoverType
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Categorize intermediate transfers vs final delivery.
 */
package com.equitransit.backend.dto.handover;

/**
 * Type of trip handover event in multimodal live animal logistics.
 */
public enum HandoverType {

    /** Transfer to road carrier, relay driver or border partner. */
    INTERMEDIATE_CARRIER,

    /** Transfer to airline cargo handling, apron, or ramp agent. */
    INTERMEDIATE_AIRPORT,

    /** Transfer to national veterinary quarantine station. */
    INTERMEDIATE_QUARANTINE,

    /** Delivery to the final consignee at destination stables. */
    FINAL_RECEIVER;

    /**
     * Checks if this is an intermediate transfer between transit segments.
     * @return true if intermediate carrier, airport, or quarantine transfer
     */
    public boolean isIntermediate() {
        return this == INTERMEDIATE_CARRIER
                || this == INTERMEDIATE_AIRPORT
                || this == INTERMEDIATE_QUARANTINE;
    }

    /**
     * Checks if this is the final delivery to the end receiver.
     * @return true only if final delivery
     */
    public boolean isFinalDelivery() {
        return this == FINAL_RECEIVER;
    }
}
