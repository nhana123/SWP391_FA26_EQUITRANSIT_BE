/*
 * HorseController
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose a frontend contract for owned horse profiles.
 */
package com.equitransit.backend.controller;


import java.net.URI;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.equitransit.backend.dto.request.HorseProfileRequest;
import com.equitransit.backend.dto.request.HorseUpdateRequest;
import com.equitransit.backend.dto.response.HorsePageResponse;
import com.equitransit.backend.dto.response.HorseResponse;
import com.equitransit.backend.service.HorseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;


/** Thin HTTP adapter; ownership and concurrency belong to the service. */
@RestController
@Validated
@RequestMapping("/api/horses")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Horse profiles", description = "Customer-owned profiles")
public class HorseController {

    private final HorseService horses;

    /**
     * Creates the profile HTTP adapter.
     * @param horses the authorized transactional service
     */
    public HorseController(HorseService horses) {
        this.horses = horses;
    }

    /**
     * Lists the customer's horses with optional name search.
     * @param page the zero-based page, default zero
     * @param size the page size, default twenty and at most one hundred
     * @param q the literal name fragment
     * @return the matching owner-scoped page
     */
    @GetMapping
    @Operation(summary = "List my horses",
            description = "Sorted by horseId descending; q is a literal "
                    + "name fragment. Complete measurements "
                    + "are not clearance.")
    public HorsePageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") @Size(max = 150) String q) {
        return horses.list(page, size, q);
    }

    /**
     * Reads a horse owned by the authenticated customer.
     * @param id the positive horse key
     * @return the profile, or an indistinguishable 404 for foreign keys
     */
    @GetMapping("/{id}")
    @Operation(summary = "Read my horse")
    public HorseResponse get(@PathVariable @Positive long id) {
        return horses.get(id);
    }

    /**
     * Saves an incomplete or complete profile with server-owned identity.
     * @param request the editable profile fields
     * @return 201, relative Location and the persisted profile
     */
    @PostMapping
    @ApiResponse(responseCode = "201", description = "Created profile",
            content = @Content(schema =
                    @Schema(implementation = HorseResponse.class)))
    @Operation(summary = "Create my horse",
            description = "Only name is required. Missing measurements "
                    + "remain unknown. No ownership fields are accepted.")
    public ResponseEntity<HorseResponse> create(
            @RequestBody @Valid HorseProfileRequest request) {
        HorseResponse created = horses.create(request);

        return ResponseEntity.created(URI.create("/api/horses/"
                + created.horseId())).body(created);
    }

    /**
     * Replaces the editable profile without changing order snapshots.
     * @param id the positive horse key
     * @param request the complete replacement and last observed version
     * @return the saved profile; stale versions return 409
     */
    @PutMapping("/{id}")
    @ApiResponse(responseCode = "409",
            description = "HORSE_VERSION_CONFLICT or HORSE_CONFLICT",
            content = @Content(schema = @Schema(type = "object")))
    @Operation(summary = "Replace my horse profile",
            description = "Send all editable fields and recordVersion from "
                    + "the last GET. Omitted optional fields become null. "
                    + "A stale version returns 409; submitted snapshots stay.")
    public HorseResponse update(@PathVariable @Positive long id,
            @RequestBody @Valid HorseUpdateRequest request) {
        return horses.update(id, request);
    }
}
