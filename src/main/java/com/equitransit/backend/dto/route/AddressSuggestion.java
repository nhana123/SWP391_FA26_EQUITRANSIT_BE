package com.equitransit.backend.dto.route;

import java.math.BigDecimal;

/** Geocoder result; attribution must accompany results in the UI.
 * @param displayName provider label @param formattedAddress compact address
 * @param latitude latitude @param longitude longitude @param countryCode ISO
 * @param city locality @param district district @param road street */
public record AddressSuggestion(String displayName, String formattedAddress,
        BigDecimal latitude, BigDecimal longitude, String countryCode,
        String city, String district, String road) { }
