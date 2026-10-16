/*
 * QuoteApiExamples
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.dto.quote;





/** Fake documentation examples; IDs and prices are not production fixtures. */
public final class QuoteApiExamples {

    public static final String COMMAND = """
            {"recordVersion":"000000000001A2B3",
             "operationId":"d86b4f63-5acc-4f20-8da3-e24323268814"}
            """;
    public static final String SCHEDULE = """
            {"sequenceNo":1,"paymentStage":"FULL","amountDue":"1500.00",
             "dueAt":"2027-01-25T08:00:00Z"}
            """;
    public static final String SCHEDULES = "[" + SCHEDULE + "]";
    public static final String EVIDENCE = """
            {"code":"AIRFARE","status":"PRICED","quantity":"1",
             "tariffId":null,"offerId":51,
             "sourceReference":"Verified retail offer REF-2027-001",
             "validUntil":"2027-02-01T08:00:00Z",
             "reason":"Evidence supplied by the assigned Coordinator."}
            """;
    public static final String PREPARE = "{\"command\":" + COMMAND + """
            ,"revisionId":201,"validUntil":"2027-01-20T08:00:00Z",
             "termsVersion":"TERMS-2027-01",
             "terms":"Payment follows the attached agreed schedule.",
             "cancellationTerms":"Cancellation follows the accepted terms.",
             "paymentSchedule":
            """ + SCHEDULES + ",\"assessments\":[" + EVIDENCE + "]}";
    public static final String ACTION = "{\"command\":" + COMMAND
            + ",\"action\":\"APPROVE\"}";
    public static final String LINE = """
            {"code":"ROAD_TRANSPORT","costGroup":"TRANSPORTATION",
             "description":"Road transport","unit":"KM","quantity":"100",
             "unitRate":"15.00","amount":"1500.00",
             "pricingStatus":"PRICED","blockers":[]}
            """;
    public static final String SUMMARY = """
            {"quoteId":301,"revisionId":201,"quoteNumber":"Q-101-1",
             "version":1,"status":"ACCEPTED","total":"1500.00",
             "validUntil":"2027-01-20T08:00:00Z"}
            """;
    public static final String RESPONSE = "{\"quote\":" + SUMMARY + """
            ,"recordVersion":"000000000001A2B3","currency":"USD",
             "termsVersion":"TERMS-2027-01",
             "terms":"Payment follows the attached agreed schedule.",
             "cancellationTerms":"Cancellation follows the accepted terms.",
             "issuedAt":"2027-01-10T08:00:00Z",
             "acceptedAt":"2027-01-11T08:00:00Z","items":[
            """ + LINE + "],\"paymentSchedule\":" + SCHEDULES
            + ",\"blockers\":[]}";
    public static final String OFFER = "{\"command\":" + COMMAND + """
            ,"revisionId":201,"serviceCode":"AIRFARE",
             "providerName":"Example supplier","offerReference":"OFFER-001",
             "unitCode":"SHIPMENT","quantity":"1",
             "purchaseUnitPrice":"900.00","retailUnitPrice":"1000.00",
             "validUntil":"2027-02-01T08:00:00Z",
             "evidenceFileKey":"evidence/verified-offer-001.pdf",
             "includesStall":false,"includedCharges":[]}
            """;

    private QuoteApiExamples() {
    }
}
