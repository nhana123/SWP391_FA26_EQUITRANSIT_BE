package com.equitransit.backend.controller;

import com.equitransit.backend.dto.route.RouteTemplateRequest;
import com.equitransit.backend.dto.route.RouteTemplateResponse;
import com.equitransit.backend.service.RouteTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
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

/** Coordinator/Manager route catalog operations. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/coordinator/route-templates")
public class CoordinatorRouteController {
    private final RouteTemplateService service;

    /** @param service catalog use cases */
    public CoordinatorRouteController(RouteTemplateService service) {
        this.service = service;
    }

    /** @param includeInactive hidden rows @param page page @param size cap
     * @return stable template page */
    @GetMapping
    @Operation(summary = "List predefined routes")
    public List<RouteTemplateResponse> list(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0")
            @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
        return service.list(includeInactive, page, size);
    }

    /** @param id identity @return stored route including hidden state */
    @GetMapping("/{id}")
    @Operation(summary = "Read a predefined route")
    public RouteTemplateResponse detail(@PathVariable @Positive long id) {
        return service.detail(id);
    }

    /** @param body route inputs @return created route */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a predefined route")
    public RouteTemplateResponse create(@Valid @RequestBody
            RouteTemplateRequest body) {
        return service.create(body);
    }

    /** @param id identity @param body full replacement @return updated route */
    @PutMapping("/{id}")
    @Operation(summary = "Update a predefined route")
    public RouteTemplateResponse update(@PathVariable @Positive long id,
            @Valid @RequestBody RouteTemplateRequest body) {
        return service.update(id, body);
    }

    /** @param id identity to hide from matching */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Hide a predefined route without deleting history")
    public void hide(@PathVariable @Positive long id) {
        service.hide(id);
    }
}
