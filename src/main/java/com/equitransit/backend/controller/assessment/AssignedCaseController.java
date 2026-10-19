/*
 * AssignedCaseController
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose typed assessment and case access contracts.
 */
package com.equitransit.backend.controller.assessment;


import org.springframework.http.MediaType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.equitransit.backend.service.assessment.CaseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.equitransit.backend.dto.assessment.response.CasePageResponse;
import com.equitransit.backend.dto.assessment.response.CaseResponse;


/** Assigned preparation cases */
@RestController
@Validated
@RequestMapping(value = "/api/staff/transport-orders",
        produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Assigned preparation cases")
public class AssignedCaseController {

    private final CaseQueryService queries;

    /**
     * Creates the thin HTTP adapter.
     * @param queries scoped case read facade
     */
    public AssignedCaseController(CaseQueryService queries) {
        this.queries = queries;
    }

    /**
     * Lists only currently assigned approved cases.
     * @param page zero-based page
     * @param size bounded size
     * @return visible workload
     */
    @GetMapping
    @Operation(summary = "List my current assigned cases")
    public CasePageResponse list(
            @Parameter(example = "0",
                    description = "Zero-based page index.",
                    schema = @Schema(type = "integer", format = "int32",
                            minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(example = "20",
                    description = "Items per page; maximum 100.",
                    schema = @Schema(type = "integer", format = "int32",
                            minimum = "1", maximum = "100",
                            defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.myQueue(page, size);
    }

    /**
     * Checks live responsibility before returning captured case data.
     * @param id case key
     * @return current revision and this staff member's instructions
     */
    @GetMapping("/{id}")
    @Operation(summary = "Read my currently assigned case")
    public CaseResponse get(@Parameter(example = "101",
                    schema = @Schema(type = "integer", format = "int64",
                            minimum = "1"))
            @PathVariable @Positive long id) {
        return queries.myCase(id);
    }
}
