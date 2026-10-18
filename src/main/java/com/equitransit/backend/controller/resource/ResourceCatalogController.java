/*
 * ResourceCatalogController
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Maintain sourced resources and transactional availability.
 */
package com.equitransit.backend.controller.resource;

import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceVehicleWrite;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallWrite;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceSupplierWrite;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyWrite;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceVehicleView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceSupplierView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceVehiclePage;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallPage;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceSupplierPage;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyPage;
import com.equitransit.backend.service.resource.ResourceCatalogService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Manager catalogue; archive uses typed state changes, never DELETE. */
@RestController
@SecurityRequirement(name = "bearerAuth")
@Validated
@Tag(name = "Resource catalogues")
@RequestMapping(value = "/api/manager/resources",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class ResourceCatalogController {

    private final ResourceCatalogService service;

    /** @param service transactional Manager catalogue */
    public ResourceCatalogController(ResourceCatalogService service) {
        this.service = service;
    }

    /** @param page zero-based @param size bounded @return catalogue page */
    @GetMapping("/vehicles")
    public ResourceVehiclePage listVehicle(
            @Parameter(example = "0") @Min(0)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(example = "20") @Min(1) @Max(100)
            @RequestParam(defaultValue = "20") int size) {
        return new ResourceVehiclePage(service.list(ResourceKind.VEHICLE,
                page, size, ResourceVehicleView.class), page, size,
                service.count(ResourceKind.VEHICLE));
    }

    /** @param id resource key @return internal detail */
    @GetMapping("/vehicles/{id}")
    public ResourceVehicleView getVehicle(
            @Parameter(example = "11") @PathVariable @Positive long id) {
        return service.get(ResourceKind.VEHICLE, id,
                ResourceVehicleView.class);
    }

    /** @param request typed creation @return new resource or replay receipt */
    @PostMapping(value = "/vehicles",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResourceVehicleView createVehicle(
            @Valid @RequestBody ResourceVehicleWrite request) {
        return service.write(ResourceKind.VEHICLE, 0, request.command(),
                request.data(), ResourceVehicleView.class);
    }

    /** @param id resource key @param request CAS replacement
     * @return new observed version; historical references remain intact */
    @PutMapping(value = "/vehicles/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResourceVehicleView updateVehicle(
            @Parameter(example = "11") @PathVariable @Positive long id,
            @Valid @RequestBody ResourceVehicleWrite request) {
        return service.write(ResourceKind.VEHICLE, id, request.command(),
                request.data(), ResourceVehicleView.class);
    }

    /** @param page zero-based @param size bounded @return catalogue page */
    @GetMapping("/stalls")
    public ResourceStallPage listStall(
            @Parameter(example = "0") @Min(0)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(example = "20") @Min(1) @Max(100)
            @RequestParam(defaultValue = "20") int size) {
        return new ResourceStallPage(service.list(ResourceKind.STALL,
                page, size, ResourceStallView.class), page, size,
                service.count(ResourceKind.STALL));
    }

    /** @param id resource key @return internal detail */
    @GetMapping("/stalls/{id}")
    public ResourceStallView getStall(
            @Parameter(example = "11") @PathVariable @Positive long id) {
        return service.get(ResourceKind.STALL, id,
                ResourceStallView.class);
    }

    /** @param request typed creation @return new resource or replay receipt */
    @PostMapping(value = "/stalls",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResourceStallView createStall(
            @Valid @RequestBody ResourceStallWrite request) {
        return service.write(ResourceKind.STALL, 0, request.command(),
                request.data(), ResourceStallView.class);
    }

    /** @param id resource key @param request CAS replacement
     * @return new observed version; historical references remain intact */
    @PutMapping(value = "/stalls/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResourceStallView updateStall(
            @Parameter(example = "11") @PathVariable @Positive long id,
            @Valid @RequestBody ResourceStallWrite request) {
        return service.write(ResourceKind.STALL, id, request.command(),
                request.data(), ResourceStallView.class);
    }

    /** @param page zero-based @param size bounded @return catalogue page */
    @GetMapping("/suppliers")
    public ResourceSupplierPage listSupplier(
            @Parameter(example = "0") @Min(0)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(example = "20") @Min(1) @Max(100)
            @RequestParam(defaultValue = "20") int size) {
        return new ResourceSupplierPage(service.list(ResourceKind.SUPPLIER,
                page, size, ResourceSupplierView.class), page, size,
                service.count(ResourceKind.SUPPLIER));
    }

    /** @param id resource key @return internal detail */
    @GetMapping("/suppliers/{id}")
    public ResourceSupplierView getSupplier(
            @Parameter(example = "11") @PathVariable @Positive long id) {
        return service.get(ResourceKind.SUPPLIER, id,
                ResourceSupplierView.class);
    }

    /** @param request typed creation @return new resource or replay receipt */
    @PostMapping(value = "/suppliers",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResourceSupplierView createSupplier(
            @Valid @RequestBody ResourceSupplierWrite request) {
        return service.write(ResourceKind.SUPPLIER, 0, request.command(),
                request.data(), ResourceSupplierView.class);
    }

    /** @param id resource key @param request CAS replacement
     * @return new observed version; historical references remain intact */
    @PutMapping(value = "/suppliers/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResourceSupplierView updateSupplier(
            @Parameter(example = "11") @PathVariable @Positive long id,
            @Valid @RequestBody ResourceSupplierWrite request) {
        return service.write(ResourceKind.SUPPLIER, id, request.command(),
                request.data(), ResourceSupplierView.class);
    }

    /** @param page zero-based @param size bounded @return catalogue page */
    @GetMapping("/fit-policies")
    public ResourcePolicyPage listPolicy(
            @Parameter(example = "0") @Min(0)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(example = "20") @Min(1) @Max(100)
            @RequestParam(defaultValue = "20") int size) {
        return new ResourcePolicyPage(service.list(ResourceKind.POLICY,
                page, size, ResourcePolicyView.class), page, size,
                service.count(ResourceKind.POLICY));
    }

    /** @param id resource key @return internal detail */
    @GetMapping("/fit-policies/{id}")
    public ResourcePolicyView getPolicy(
            @Parameter(example = "11") @PathVariable @Positive long id) {
        return service.get(ResourceKind.POLICY, id,
                ResourcePolicyView.class);
    }

    /** @param request typed creation @return new resource or replay receipt */
    @PostMapping(value = "/fit-policies",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResourcePolicyView createPolicy(
            @Valid @RequestBody ResourcePolicyWrite request) {
        return service.write(ResourceKind.POLICY, 0, request.command(),
                request.data(), ResourcePolicyView.class);
    }

    /** @param id resource key @param request CAS replacement
     * @return new observed version; historical references remain intact */
    @PutMapping(value = "/fit-policies/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResourcePolicyView updatePolicy(
            @Parameter(example = "11") @PathVariable @Positive long id,
            @Valid @RequestBody ResourcePolicyWrite request) {
        return service.write(ResourceKind.POLICY, id, request.command(),
                request.data(), ResourcePolicyView.class);
    }
}
