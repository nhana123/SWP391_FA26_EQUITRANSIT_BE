package com.equitransit.backend.controller;

import com.equitransit.backend.dto.route.AddressSuggestion;
import com.equitransit.backend.dto.route.AddressValidationRequest;
import com.equitransit.backend.dto.route.AddressValidationResponse;
import com.equitransit.backend.service.AddressGeocodingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Provider-backed explicit address search and strict match validation. */
@RestController
@Validated
@RequestMapping(value = "/api/routes/address", produces = "application/json")
public class AddressController {
    private final AddressGeocodingService service;

    /** @param service shared provider gateway */
    public AddressController(AddressGeocodingService service) {
        this.service = service;
    }

    /** @param query search text @param countryCode ISO filter @param limit cap
     * @return provider suggestions without offline-coordinate fallback */
    @GetMapping("/search")
    @Operation(summary = "Search addresses on explicit user action",
            description = "Public Nominatim forbids keystroke autocomplete. "
                    + "Display attribution: © OpenStreetMap contributors.")
    public List<AddressSuggestion> search(
            @RequestParam @Size(min = 2, max = 500) String query,
            @RequestParam(required = false)
            @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int limit) {
        return service.search(query, countryCode, limit);
    }

    /** @param request full address @return explicit match result */
    @PostMapping(value = "/validate", consumes = "application/json")
    @Operation(summary = "Validate address against geocoder matches")
    public AddressValidationResponse validate(
            @Valid @RequestBody AddressValidationRequest request) {
        return service.validate(request.address(), request.countryCode());
    }
}
