/*
 * PlanExamples
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Provide reusable OpenAPI 3.1 examples for F10 planning DTOs.
 */
package com.equitransit.backend.dto.plan;

/** Reusable JSON examples for F10 operational planning contracts. */
public final class PlanExamples {

    public static final String COMMAND = "{\"operationId\":"
            + "\"d2046c13-4c31-49b9-8695-edbab59eec2b\","
            + "\"recordVersion\":\"000000000001A2B3\"}";

    public static final String LEG_ITEM = "{\"sequenceNo\":1,"
            + "\"transportMode\":\"ROAD\",\"startPoint\":\"Hanoi Stable\","
            + "\"endPoint\":\"Noi Bai Airport\",\"distanceKm\":\"45.500\","
            + "\"distanceSource\":\"Google Maps verified route\","
            + "\"plannedDeparture\":\"2027-02-01T06:00:00Z\","
            + "\"plannedArrival\":\"2027-02-01T08:00:00Z\","
            + "\"startTimezone\":\"Asia/Ho_Chi_Minh\","
            + "\"endTimezone\":\"Asia/Ho_Chi_Minh\","
            + "\"connectionBufferMinutes\":60,\"restDurationMinutes\":0,"
            + "\"careInstructions\":\"Provide water before boarding.\"}";

    private PlanExamples() { }
}
