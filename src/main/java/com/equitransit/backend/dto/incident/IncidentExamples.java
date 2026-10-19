/*
 * IncidentExamples
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Provide reusable OpenAPI 3.1 examples for F13 incident DTOs.
 */
package com.equitransit.backend.dto.incident;

/** Reusable JSON examples for F13 incidents and adjustments. */
public final class IncidentExamples {

    public static final String INCIDENT_CREATE = "{\"incidentType\":"
            + "\"HORSE_WELFARE_EMERGENCY\",\"severityLevel\":\"CRITICAL\","
            + "\"incidentTime\":\"2026-10-18T14:30:00Z\","
            + "\"description\":"
            + "\"Horse showing signs of acute abdominal colic.\","
            + "\"immediateActionNotes\":\"Administered sedative; called vet.\","
            + "\"emergencyContact\":\"Dr. Dupont: +33 4 72 00 00 00\","
            + "\"evidenceFilesJson\":\"[\\\"vet-colic-report.pdf\\\"]\"}";

    public static final String ADJUSTMENT_CREATE = "{\"incidentId\":501,"
            + "\"changeRequestId\":31,\"proposedChanges\":"
            + "\"{\\\"reason\\\":\\\"Detour\\\",\\\"shiftMinutes\\\":120}\","
            + "\"additionalCostEstimate\":250.00,"
            + "\"customerDescription\":\"Reroute via Highway A6.\"}";

    public static final String CUSTOMER_DECISION = "{\"decision\":"
            + "\"ACCEPTED\",\"responseNotes\":\"Approved extra charge.\"}";

    private IncidentExamples() { }
}
