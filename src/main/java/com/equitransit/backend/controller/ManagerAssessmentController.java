/*
 * ManagerAssessmentController
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose typed assessment and case access contracts.
 */
package com.equitransit.backend.controller;


import org.springframework.http.MediaType;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.equitransit.backend.service.CaseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.equitransit.backend.dto.request.AssessmentRequest;
import com.equitransit.backend.dto.request.CaseAssignmentRequest;
import com.equitransit.backend.dto.request.EndCaseAssignmentRequest;
import com.equitransit.backend.dto.response.AssessmentReceipt;
import com.equitransit.backend.dto.response.CaseAssignmentResponse;
import com.equitransit.backend.dto.response.CaseDecisionResponse;
import com.equitransit.backend.dto.response.CasePageResponse;
import com.equitransit.backend.dto.response.CaseResponse;
import com.equitransit.backend.dto.response.CaseStaffPageResponse;
import com.equitransit.backend.entity.CaseRole;
import com.equitransit.backend.entity.ReviewState;
import com.equitransit.backend.service.OrderAssessmentService;


/** Manager request assessment */
@RestController
@Validated
@RequestMapping(value = "/api/manager/transport-orders",
        produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Manager request assessment")
public class ManagerAssessmentController {

    private final CaseQueryService queries;
    private final OrderAssessmentService assessments;

    /**
     * Creates the thin HTTP adapter.
     * @param queries scoped case read facade
     * @param assessments transactional Manager command facade
     */
    public ManagerAssessmentController(CaseQueryService queries,
            OrderAssessmentService assessments) {
        this.queries = queries;
        this.assessments = assessments;
    }

    /**
     * Lists submitted requests by default.
     * @param state explicit review state
     * @param page zero-based page
     * @param size bounded size
     * @return queue envelope
     */
    @GetMapping
    @Operation(summary = "List requests requiring Manager assessment")
    public CasePageResponse list(
            @Parameter(example = "SUBMITTED")
            @RequestParam(defaultValue = "SUBMITTED") ReviewState state,
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
        return queries.managerQueue(state, page, size);
    }

    /**
     * Shows only active staff of the selected preparation role.
     * @param role required role
     * @param page zero-based page
     * @param size bounded size
     * @return staff and current workload, not travel availability
     */
    @GetMapping("/staff")
    @Operation(summary = "List active Specialist or Coordinator candidates")
    public CaseStaffPageResponse staff(
            @Parameter(example = "SPECIALIST")
            @RequestParam CaseRole role,
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
        return queries.staffDirectory(role, page, size);
    }

    /**
     * Reads the exact current captured revision for review.
     * @param id submitted order key
     * @return current revision, version, decision and team
     */
    @GetMapping("/{id}")
    @Operation(summary = "Review the current submitted revision")
    public CaseResponse get(@Parameter(example = "101",
                    schema = @Schema(type = "integer", format = "int64",
                            minimum = "1"))
            @PathVariable @Positive long id) {
        return queries.managerCase(id);
    }

    /**
     * Approves and assigns atomically, or records a reasoned negative outcome.
     * @param id submitted order key
     * @param request reviewed revision, expected version and retry UUID
     * @return immutable completion receipt
     */
    @PostMapping("/{id}/decisions")
    @Operation(summary = "Assess the reviewed revision",
            description = "APPROVED starts preparation, not quote acceptance "
                    + "or departure. Supply both active staff IDs; "
                    + "REJECTED/CHANGES_REQUESTED require reason. Reuse UUID "
                    + "and exact input for retries, then GET current state.")
    public AssessmentReceipt decide(@Parameter(example = "101",
                    schema = @Schema(type = "integer", format = "int64",
                            minimum = "1"))
            @PathVariable @Positive long id,
            @RequestBody @Valid AssessmentRequest request) {
        return assessments.decide(id, request);
    }

    /**
     * Replaces responsibility and preserves the ended assignment.
     * @param id approved uncommitted order key
     * @param role preparation role
     * @param request replacement staff and audit reason
     * @return completion receipt
     */
    @PutMapping("/{id}/assignments/{role}")
    @Operation(summary = "Assign or replace a case role",
            description = "Full replacement metadata; omitted dueAt/note "
                    + "clear those fields. Reassignment immediately revokes "
                    + "old access and invalidates unaccepted preparation.")
    public AssessmentReceipt assign(@Parameter(example = "101",
                    schema = @Schema(type = "integer", format = "int64",
                            minimum = "1"))
            @PathVariable @Positive long id,
            @Parameter(example = "SPECIALIST")
            @PathVariable CaseRole role,
            @RequestBody @Valid CaseAssignmentRequest request) {
        return assessments.assign(id, role, request);
    }

    /**
     * Ends responsibility without deleting history.
     * @param id approved uncommitted order key
     * @param role case role being revoked
     * @param request version, reason and retry UUID
     * @return completion receipt
     */
    @PostMapping("/{id}/assignments/{role}/end")
    @Operation(summary = "End a current case assignment")
    public AssessmentReceipt end(@Parameter(example = "101",
                    schema = @Schema(type = "integer", format = "int64",
                            minimum = "1"))
            @PathVariable @Positive long id,
            @Parameter(example = "SPECIALIST")
            @PathVariable CaseRole role,
            @RequestBody @Valid EndCaseAssignmentRequest request) {
        return assessments.end(id, role, request);
    }

    /**
     * Reads bounded decision history.
     * @param id submitted order
     * @param page zero-based page
     * @param size bounded size
     * @return immutable decisions, newest first
     */
    @GetMapping("/{id}/decisions")
    @Operation(summary = "Read assessment history")
    public List<CaseDecisionResponse> decisions(
            @Parameter(example = "101",
                    schema = @Schema(type = "integer", format = "int64",
                            minimum = "1"))
            @PathVariable @Positive long id,
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
        return queries.decisions(id, page, size);
    }

    /**
     * Reads current and ended assignment history.
     * @param id submitted order
     * @param page zero-based page
     * @param size bounded size
     * @return immutable responsibility history
     */
    @GetMapping("/{id}/assignments")
    @Operation(summary = "Read assignment and reassignment history")
    public List<CaseAssignmentResponse> assignments(
            @Parameter(example = "101",
                    schema = @Schema(type = "integer", format = "int64",
                            minimum = "1"))
            @PathVariable @Positive long id,
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
        return queries.assignments(id, page, size);
    }
}
