package com.equitransit.backend.controller;

import com.equitransit.backend.dto.location.LocationRequest;
import com.equitransit.backend.repository.LocationCatalogRepository.Location;
import com.equitransit.backend.service.ManagerLocationService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Manager and coordinator maintenance of company locations. */
@RestController
@Validated
@RequestMapping(value = "/api/manager/locations", produces = "application/json")
@PreAuthorize("hasAnyRole('MANAGER','COORDINATOR')")
public class ManagerLocationController {
    private final ManagerLocationService service;

    /** @param service catalog use cases */
    public ManagerLocationController(ManagerLocationService service) {
        this.service = service;
    }

    /** @param country filter @param type filter @param includeInactive hidden
     * @param page page @param size limit @return locations */
    @GetMapping
    public List<Location> list(
            @RequestParam(required = false)
            @Pattern(regexp = "[A-Z]{2}") String country,
            @RequestParam(required = false)
            @Pattern(regexp = "CITY|AIRPORT|HUB") String type,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") @Min(0)
            @Parameter(example = "0") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100)
            @Parameter(example = "20") int size) {
        return service.list(country, type, includeInactive, page, size);
    }

    /** @param locationId identity @return location */
    @GetMapping("/{locationId}")
    public Location get(@PathVariable @Min(1) int locationId) {
        return service.get(locationId);
    }

    /** @param request new row @return created location */
    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Location create(@Valid @RequestBody LocationRequest request) {
        return service.save(null, request);
    }

    /** @param locationId identity @param request edit @return saved location */
    @PutMapping(value = "/{locationId}", consumes = "application/json")
    public Location update(@PathVariable @Min(1) int locationId,
            @Valid @RequestBody LocationRequest request) {
        return service.save(locationId, request);
    }

    /** @param locationId row to hide */
    @DeleteMapping("/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Min(1) int locationId) {
        service.delete(locationId);
    }
}
