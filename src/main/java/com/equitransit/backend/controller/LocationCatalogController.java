package com.equitransit.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import com.equitransit.backend.service.LocationCatalogService;
import com.equitransit.backend.repository.LocationCatalogRepository.Country;
import com.equitransit.backend.repository.LocationCatalogRepository.Location;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Active master data for dependent route selectors. */
@RestController
@Validated
@RequestMapping("/api/lookups")
public class LocationCatalogController {
    private final LocationCatalogService service;

    /** @param service route lookup use cases */
    public LocationCatalogController(LocationCatalogService service) {
        this.service = service;
    }

    /** @return countries with capabilities derived from active locations */
    @Operation(summary = "List active route countries and transport modes")
    @GetMapping("/countries")
    public List<Country> countries() {
        return service.countries();
    }

    /** @param country ISO code @param type CITY or AIRPORT @return locations */
    @Operation(summary = "List active locations for a country and mode")
    @GetMapping("/locations")
    public List<Location> locations(
            @RequestParam @Pattern(regexp = "[A-Z]{2}") String country,
            @RequestParam @Pattern(regexp = "CITY|AIRPORT|HUB") String type) {
        return service.locations(country, type);
    }

    /** @param country ISO country @param primaryOnly primary filter
     * @return active company hubs with optional coordinates */
    @GetMapping("/hubs")
    public List<Location> hubs(
            @RequestParam @Pattern(regexp = "[A-Z]{2}") String country,
            @RequestParam(defaultValue = "false") boolean primaryOnly) {
        return service.hubs(country, primaryOnly);
    }
}
