/*
 * HandoverExamples
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Centralized OpenAPI schema example constants for Feature 14 DTOs.
 */
package com.equitransit.backend.dto.handover;

/**
 * Clean schema examples for Trip Handover and Actual Cost DTOs.
 */
public final class HandoverExamples {

    /** Sample handover ID. */
    public static final String HANDOVER_ID = "701";

    /** Sample leg ID. */
    public static final String LEG_ID = "401";

    /** Sample plan ID. */
    public static final String PLAN_ID = "311";

    /** Sample order ID. */
    public static final String ORDER_ID = "101";

    /** Sample cost ID. */
    public static final String COST_ID = "801";

    /** Sample receiver name. */
    public static final String RECEIVER_NAME = "Jean-Pierre Dubois";

    /** Sample receiver authority. */
    public static final String RECEIVER_AUTH = "STABLE_MANAGER";

    /** Sample signature file key. */
    public static final String SIG_KEY =
            "signatures/2026/10/rec-701-dubois.png";

    /** Sample photo file key. */
    public static final String PHOTO_KEY =
            "photos/2026/10/handover-701-stall.jpg";

    /** Sample invoice file key. */
    public static final String EVIDENCE_KEY =
            "invoices/2026/10/af-airwaybill-618.pdf";

    /** Sample location description. */
    public static final String LOCATION =
            "Ecurie Dubois, Box 14, 14800 Deauville, France";

    /** Sample exception notes. */
    public static final String NOTES =
            "Right flank superficial graze recorded, antiseptic applied.";

    /** Sample feedback from customer. */
    public static final String FEEDBACK =
            "Horse arrived in great condition, very professional delivery.";

    /** Sample ISO timestamp. */
    public static final String TIME = "2026-10-18T16:00:00Z";

    /** Sample currency. */
    public static final String CURRENCY = "EUR";

    /** Sample cost amount. */
    public static final String AMOUNT = "1850.00";

    /** Sample quoted total. */
    public static final String QUOTED_TOTAL = "14500.00";

    /** Sample profit. */
    public static final String GROSS_PROFIT = "4200.00";

    /** Sample margin percent. */
    public static final String MARGIN_PCT = "28.97";

    /** Sample evidence JSON. */
    public static final String EVIDENCE_JSON =
            "{\"checksheetKey\":\"checks/cs-701.pdf\","
                    + "\"vetReportKey\":\"vets/vr-701.pdf\"}";

    private HandoverExamples() { }
}
