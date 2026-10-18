package com.equitransit.backend.controller;


import com.equitransit.backend.dto.execution.ExecutionModels.ExecutionOverview;
import com.equitransit.backend.dto.execution.ExecutionModels.ExecutionReceipt;
import com.equitransit.backend.dto.execution.ExecutionModels.MilestoneRequest;
import com.equitransit.backend.dto.execution.ExecutionModels.TransitionRequest;
import com.equitransit.backend.dto.execution.ExecutionModels.WelfareRequest;
import com.equitransit.backend.service.LegExecutionService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/** Actual execution facts, kept separate from F11 departure/F14 handover. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Leg execution and welfare")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Execution result", useReturnTypeSchema = true),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Malformed DTO, decimal, timestamp or pagination"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "Authentication required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
            description = "Active assignment or transport mode denied"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "Order or current-plan leg not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "Stale version, retry conflict or plan mismatch"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
            description = "Invalid sequence, source, time or correction"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
            description = "Execution schema deployment pending")
})
@RequestMapping(value = "/api/transport-orders/{orderId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class LegExecutionController {
    private final LegExecutionService service;

    /** @param service transactional execution boundary */
    public LegExecutionController(LegExecutionService service) {
        this.service = service;
    }

    /** @param orderId parent @param page page @param size bound
     * @return facts */
    @GetMapping("/execution")
    public ExecutionOverview overview(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "0") @RequestParam(defaultValue = "0")
            @Min(0) int page,
            @Parameter(example = "20") @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size) {
        return service.overview(orderId, page, size);
    }

    /** @param orderId parent @param legId leg @param body event @return key */
    @PostMapping(value = "/legs/{legId}/start",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ExecutionReceipt start(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "201") @PathVariable @Positive long legId,
            @Valid @RequestBody TransitionRequest body) {
        return service.transition(orderId, legId, body, true);
    }

    /** @param orderId parent @param legId leg @param body event @return key */
    @PostMapping(value = "/legs/{legId}/complete",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ExecutionReceipt complete(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "201") @PathVariable @Positive long legId,
            @Valid @RequestBody TransitionRequest body) {
        return service.transition(orderId, legId, body, false);
    }

    /** @param orderId parent @param legId leg @param body fact @return key */
    @PostMapping(value = "/legs/{legId}/milestones",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ExecutionReceipt milestone(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "201") @PathVariable @Positive long legId,
            @Valid @RequestBody MilestoneRequest body) {
        return service.milestone(orderId, legId, body);
    }

    /** @param orderId parent @param legId leg @param body care @return key */
    @PostMapping(value = "/legs/{legId}/welfare-logs",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ExecutionReceipt welfare(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Parameter(example = "201") @PathVariable @Positive long legId,
            @Valid @RequestBody WelfareRequest body) {
        return service.welfare(orderId, legId, body);
    }
}
