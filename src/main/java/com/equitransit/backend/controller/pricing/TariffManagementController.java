/*
 * TariffManagementController
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.controller.pricing;

import com.equitransit.backend.dto.pricing.ServiceRateResponse;
import com.equitransit.backend.dto.pricing.TariffPage;
import com.equitransit.backend.dto.pricing.TariffResponse;
import com.equitransit.backend.dto.pricing.TariffWriteRequest;
import com.equitransit.backend.service.pricing.TariffManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;


/** Manager-only retail price history and publication. */
@RestController
@Validated
@RequestMapping(value = "/api/manager/service-tariffs",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class TariffManagementController {

    private final TariffManagementService service;

    /** @param service authoritative versioned retail pricing */
    public TariffManagementController(TariffManagementService service) {
        this.service = service;
    }

    /** @return fixed products and units; included items cannot be priced */
    @GetMapping("/products")
    @Operation(summary = "Read supported service pricing products (Manager)")
    public List<ServiceRateResponse> products() {
        return service.products();
    }

    /**
     * @param page zero-based page
     * @param size bounded page size
     * @return price history without supplier costs
     */
    @GetMapping
    @Operation(summary = "List retail tariff versions (Manager)")
    public TariffPage list(
            @Parameter(schema = @Schema(type = "integer", example = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(schema = @Schema(type = "integer", example = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    /**
     * @param id exact price version
     * @return observed version and provenance
     */
    @GetMapping("/{id}")
    @Operation(summary = "Read a retail tariff version (Manager)")
    public TariffResponse get(@PathVariable @Positive long id) {
        return service.get(id);
    }

    /**
     * @param request new service price family, source and UUID
     * @return newly published version or identical retry receipt
     */
    @PostMapping
    @Operation(summary = "Publish a new retail tariff (Manager)",
            description = "Currency USD. Source required. Included services "
                    + "cannot be charged. Retail prices only.")
    public TariffResponse create(
            @RequestBody @Valid TariffWriteRequest request) {
        return service.publish(null, request);
    }

    /**
     * @param id exact latest version being replaced
     * @param request replacement with observed rowversion and stable UUID
     * @return new version; historical price and quote snapshots unchanged
     */
    @PutMapping("/{id}")
    @Operation(summary = "Publish a replacement tariff version (Manager)",
            description = "Creates a successor, ends the previous rate's "
                    + "effective interval. Does not rewrite its amount or "
                    + "quote snapshots. Keep UUID and input on retry.")
    public TariffResponse replace(@PathVariable @Positive long id,
            @RequestBody @Valid TariffWriteRequest request) {
        return service.publish(id, request);
    }
}
