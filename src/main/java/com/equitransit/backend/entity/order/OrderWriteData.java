/*
 * OrderWriteData
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.entity.order;


import java.time.LocalDateTime;


/**
 * Typed SQL write data; never accepts arbitrary column names.
 * @param originCountryCode the originCountryCode
 * @param destinationCountryCode the destinationCountryCode
 * @param originAddress the originAddress
 * @param destinationAddress the destinationAddress
 * @param originTimezone the originTimezone
 * @param destinationTimezone the destinationTimezone
 * @param preferredDepartureAt the preferredDepartureAt
 * @param deliveryDeadline the deliveryDeadline
 * @param dateFlexibilityNotes the dateFlexibilityNotes
 * @param importType the importType
 * @param receiverName the receiverName
 * @param receiverPhone the receiverPhone
 * @param receiverEmail the receiverEmail
 * @param receiverAuthority the receiverAuthority
 * @param eventStartAt the eventStartAt
 * @param eventName the eventName
 * @param eventVenue the eventVenue
 * @param eventTimezone the eventTimezone
 * @param transitAirportsJson the transitAirportsJson
 * @param draftPayloadJson the draftPayloadJson
 */
public record OrderWriteData(
        String originCountryCode,
        String destinationCountryCode,
        String originAddress,
        String destinationAddress,
        String originTimezone,
        String destinationTimezone,
        LocalDateTime preferredDepartureAt,
        LocalDateTime deliveryDeadline,
        String dateFlexibilityNotes,
        String importType,
        String receiverName,
        String receiverPhone,
        String receiverEmail,
        String receiverAuthority,
        LocalDateTime eventStartAt,
        String eventName,
        String eventVenue,
        String eventTimezone,
        String transitAirportsJson,
        String draftPayloadJson) {
}
