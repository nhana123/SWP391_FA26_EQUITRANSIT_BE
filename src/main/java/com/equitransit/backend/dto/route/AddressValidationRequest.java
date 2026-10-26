package com.equitransit.backend.dto.route;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param address full address @param countryCode optional ISO country */
public record AddressValidationRequest(
        @NotBlank @Size(min = 2, max = 500) String address,
        @Pattern(regexp = "[A-Za-z]{2}") String countryCode) { }
