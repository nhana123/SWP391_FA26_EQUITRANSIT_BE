/*
 * CustomerAssessmentController
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose typed assessment and case access contracts.
 */
package com.equitransit.backend.controller;


import org.springframework.http.MediaType;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.equitransit.backend.service.CaseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.equitransit.backend.dto.response.CustomerAssessmentResponse;


/** Customer assessment outcome */
@RestController
@Validated
@RequestMapping(value = "/api/transport-orders",
        produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer assessment outcome")
public class CustomerAssessmentController {

    private final CaseQueryService queries;

    /**
     * Creates the thin HTTP adapter.
     * @param queries scoped case read facade
     */
    public CustomerAssessmentController(CaseQueryService queries) {
        this.queries = queries;
    }

    /**
     * Shows only the authenticated customer's public current outcome.
     * @param id owned order key
     * @return current decision and public reason, no staff/internal notes
     */
    @GetMapping("/{id}/assessment")
    @Operation(summary = "Read my request assessment outcome")
    public CustomerAssessmentResponse get(@PathVariable @Positive long id) {
        return queries.customerAssessment(id);
    }
}
