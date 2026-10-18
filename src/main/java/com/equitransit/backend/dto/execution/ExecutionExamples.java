/*
 * ExecutionExamples
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Example JSON payloads for OpenAPI 3.1 documentation and testing.
 */
package com.equitransit.backend.dto.execution;

/** Verifiable example payloads for Feature 12 execution operations. */
public final class ExecutionExamples {

    /** Example road milestone submission. */
    public static final String ROAD_MILESTONE = """
            {\
            "milestoneType":"REST_STOP",\
            "occurredAt":"2026-10-18T08:30:00Z",\
            "latitude":21.028511,\
            "longitude":105.854444,\
            "notes":"Scheduled rest stop; horse hydrated.",\
            "locationDescription":"Highway 1A Rest Area Km 45",\
            "delayReason":"Heavy highway traffic",\
            "evidenceFileKey":"f12-evidence-road-stop-01.jpg"}""";

    /** Example air milestone submission. */
    public static final String AIR_MILESTONE = """
            {\
            "milestoneType":"WHEELS_UP",\
            "occurredAt":"2026-10-18T04:15:00Z",\
            "sourceCode":"CARRIER_API",\
            "sourceReference":"AWB-618-91234567",\
            "locationDescription":"Noi Bai International Airport (HAN)",\
            "notes":"Flight VN123 departed on schedule.",\
            "latitude":21.221200,\
            "longitude":105.807200,\
            "evidenceFileKey":"f12-evidence-flight-dispatch.pdf"}""";

    /** Example estimated arrival update. */
    public static final String ESTIMATED_ARRIVAL = """
            {\
            "estimatedArrivalAt":"2026-10-18T18:00:00Z",\
            "delayReason":"Flight delay due to adverse weather."}""";

    /** Example horse welfare log submission. */
    public static final String WELFARE_LOG = """
            {\
            "checkTime":"2026-10-18T08:30:00Z",\
            "healthStatus":"HEALTHY",\
            "temperatureCelsius":37.80,\
            "waterIntakeLiters":15.50,\
            "feedAmountKg":2.50,\
            "behaviorNotes":"Horse calm, alert, responsive.",\
            "careNotes":"Provided electrolyte water.",\
            "photoFileKey":"welfare-photo-check1.jpg",\
            "instructionFileKey":"vet-instructions-f12.pdf"}""";

    /** Example horse welfare superseding correction. */
    public static final String WELFARE_CORRECTION = """
            {\
            "checkTime":"2026-10-18T08:30:00Z",\
            "healthStatus":"HEALTHY",\
            "temperatureCelsius":37.80,\
            "waterIntakeLiters":15.50,\
            "feedAmountKg":2.50,\
            "behaviorNotes":"Horse calm, alert.",\
            "careNotes":"Corrected faulty initial thermometer reading.",\
            "photoFileKey":"welfare-photo-check1-corrected.jpg",\
            "instructionFileKey":"vet-instructions-f12.pdf"}""";

    /** Example milestone response. */
    public static final String MILESTONE_RESPONSE = """
            {\
            "milestoneId":101,\
            "legId":901,\
            "reportedBy":41,\
            "reportedByName":"Nguyen Van Driver",\
            "milestoneType":"REST_STOP",\
            "occurredAt":"2026-10-18T08:30:00Z",\
            "recordedAt":"2026-10-18T08:31:00Z",\
            "latitude":21.028511,\
            "longitude":105.854444,\
            "notes":"Rest stop completed.",\
            "sourceCode":"DRIVER_APP",\
            "sourceReference":"TRIP-REG-2026",\
            "locationDescription":"Highway 1A Rest Area Km 45",\
            "estimatedArrivalAt":"2026-10-18T18:00:00Z",\
            "delayReason":"Minor highway traffic",\
            "evidenceFileKey":"evidence-key.jpg"}""";

    /** Example horse welfare response. */
    public static final String WELFARE_RESPONSE = """
            {\
            "welfareLogId":501,\
            "legId":901,\
            "recordedBy":41,\
            "recordedByName":"Nguyen Van Driver",\
            "checkTime":"2026-10-18T08:30:00Z",\
            "healthStatus":"HEALTHY",\
            "temperatureCelsius":37.80,\
            "waterIntakeLiters":15.50,\
            "feedAmountKg":2.50,\
            "behaviorNotes":"Calm and drinking well.",\
            "careNotes":"Provided electrolyte water.",\
            "photoFileKey":"photo-key.jpg",\
            "instructionFileKey":"vet-inst.pdf",\
            "supersedesLogId":null,\
            "fever":false,\
            "emergencyAlert":false,\
            "alertReason":null}""";

    /** Example customer journey tracking timeline response. */
    public static final String TIMELINE_RESPONSE = """
            {\
            "orderId":101,\
            "orderStatus":"CONFIRMED",\
            "planId":801,\
            "currentLegId":901,\
            "currentLegSequenceNo":1,\
            "currentTransportMode":"ROAD",\
            "currentLegStatus":"IN_PROGRESS",\
            "currentMilestone":"REST_STOP",\
            "lastUpdated":"2026-10-18T08:30:00Z",\
            "estimatedArrival":"2026-10-18T18:00:00Z",\
            "latestWelfareStatus":"HEALTHY",\
            "latestTemperatureCelsius":37.80,\
            "latestWelfarePhotoKey":"photo-key.jpg",\
            "legs":[\
            {"legId":901,"sequenceNo":1,"transportMode":"ROAD",\
            "startPoint":"Hanoi Stable","endPoint":"Noi Bai Airport",\
            "legStatus":"IN_PROGRESS",\
            "actualDeparture":"2026-10-18T07:00:00Z",\
            "actualArrival":null,\
            "milestones":[{"milestoneId":101,"milestoneType":"REST_STOP",\
            "occurredAt":"2026-10-18T08:30:00Z",\
            "locationDescription":"Highway 1A Rest Area Km 45"}],\
            "welfareChecks":[{"checkTime":"2026-10-18T08:30:00Z",\
            "healthStatus":"HEALTHY","temperatureCelsius":37.80,\
            "photoFileKey":"photo-key.jpg"}]}]}""";

    private ExecutionExamples() { }
}
