package com.equitransit.backend.dto.route;

/** A geocoding match is not proof of ownership or deliverability.
 * @param isValid query matched @param matchedAddress nullable matching result
 * @param message outcome */
public record AddressValidationResponse(boolean isValid,
        AddressSuggestion matchedAddress, String message) { }
