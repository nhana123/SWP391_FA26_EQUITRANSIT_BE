/*
 * DepartureExamples
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Provide deterministic OpenAPI examples for F11 departure endpoints.
 */
package com.equitransit.backend.dto.departure;

/** Request and response JSON examples for F11 OpenAPI documentation. */
public final class DepartureExamples {

    public static final String EVALUATE = """
            {"command":{"operationId":"a1046c13-4c31-49b9-8695-edbab59eec01",\
            "recordVersion":"000000000001A2B3"}}""";

    public static final String AUTHORIZE = """
            {"command":{"operationId":"a1046c13-4c31-49b9-8695-edbab59eec02",\
            "recordVersion":"000000000001A2B4"},"planId":801}""";

    public static final String RECEIPT = """
            {"command":{"operationId":"a1046c13-4c31-49b9-8695-edbab59eec03",\
            "recordVersion":"000000000001A2B5"},"planId":801,"legId":901,\
            "observedMicrochip":"981020000123456",\
            "observedPassportNumber":"FEI-VN-2026-009","fitToTravel":true,\
            "conditionNotes":"Calm, hydrated, normal temperature.",\
            "evidenceFileKey":"f3046c13-4c31-49b9-8695-edbab59eec2f",\
            "locationDescription":"Hanoi Stable Gate A"}""";

    public static final String DEPART = """
            {"command":{"operationId":"a1046c13-4c31-49b9-8695-edbab59eec04",\
            "recordVersion":"000000000001A2B6"},"planId":801,"legId":901,\
            "receiptId":1001}""";

    public static final String READINESS = """
            {"orderId":101,"recordVersion":"000000000001A2B7",\
            "orderStatus":"IN_TRANSIT","currentRevisionId":501,"planId":801,\
            "planVersionNo":1,"planStatus":"IN_TRANSIT","acceptedQuoteId":301,\
            "firstLegId":901,"firstLegStatus":"IN_TRANSIT",\
            "firstLegActualDeparture":"2027-02-01T05:30:00Z",\
            "asOf":"2027-02-01T05:30:00Z",\
            "gatesCheckedAt":"2027-02-01T05:30:00Z",\
            "financialGate":{"status":"PASS","blockers":[]},\
            "legalGate":{"status":"PASS","blockers":[]},\
            "operationalGate":{"status":"PASS","blockers":[]},\
            "readyForAuthorization":true,"departureAuthorizedBy":11,\
            "departureAuthorizedAt":"2027-02-01T05:15:00Z",\
            "readyForDeparture":true,"departureBlockers":[],\
            "receipts":[{"receiptId":1001,"planId":801,"legId":901,\
            "receivedBy":41,"observedMicrochip":"981020000123456",\
            "observedPassportNumber":"FEI-VN-2026-009",\
            "identityCheckResult":"MATCHED","receiptStatus":"ACCEPTED",\
            "conditionNotes":"Calm, hydrated, normal temperature.",\
            "evidenceFileKey":"f3046c13-4c31-49b9-8695-edbab59eec2f",\
            "locationDescription":"Hanoi Stable Gate A",\
            "receivedAt":"2027-02-01T05:25:00Z"}]}""";

    private DepartureExamples() { }
}
