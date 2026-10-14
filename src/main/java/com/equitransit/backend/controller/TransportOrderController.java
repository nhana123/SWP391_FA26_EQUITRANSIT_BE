/*
 * TransportOrderController
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Expose typed customer draft, revision and proposal contracts.
 */
package com.equitransit.backend.controller;


import org.springframework.http.MediaType;
import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
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
import com.equitransit.backend.dto.request.OrderDraftRequest;
import com.equitransit.backend.dto.request.OrderDraftUpdateRequest;
import com.equitransit.backend.dto.request.OrderRevisionRequest;
import com.equitransit.backend.dto.request.OrderSubmitRequest;
import com.equitransit.backend.dto.response.OrderChangeResponse;
import com.equitransit.backend.dto.response.OrderPageResponse;
import com.equitransit.backend.dto.response.OrderResponse;
import com.equitransit.backend.dto.response.OrderRevisionResponse;
import com.equitransit.backend.service.TransportOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;


/** Thin HTTP adapter; transactions, state and ownership stay in the service. */
@RestController
@Validated
@RequestMapping(value = "/api/transport-orders",
        produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transport requests", description = "Single-horse requests")
public class TransportOrderController {

    private final TransportOrderService orders;

    /**
     * Creates the request HTTP adapter.
     * @param orders the authorized request lifecycle facade
     */
    public TransportOrderController(TransportOrderService orders) {
        this.orders = orders;
    }

    /**
     * Creates an incomplete saved journey without submitting it.
     * @param request the editable draft fields
     * @return 201, Location, current data and database version
     */
    @PostMapping
    @Operation(summary = "Create my draft transport request")
    @ApiResponse(responseCode = "201", description = "Saved draft",
            content = @Content(schema =
                    @Schema(implementation = OrderResponse.class)))
    public ResponseEntity<OrderResponse> create(
            @RequestBody @Valid OrderDraftRequest request) {
        OrderResponse created = orders.create(request);

        return ResponseEntity.created(URI.create("/api/transport-orders/"
                + created.orderId())).body(created);
    }

    /**
     * Lists only current requests belonging to the authenticated customer.
     * @param page the zero-based page
     * @param size the bounded page size
     * @return an explicit pagination response
     */
    @GetMapping
    @Operation(summary = "List my transport requests")
    public OrderPageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.list(page, size);
    }

    /**
     * Reads owned current data with explicit revision and plan pointers.
     * @param id the positive request key
     * @return the current request view
     */
    @GetMapping("/{id}")
    @Operation(summary = "Read my transport request")
    public OrderResponse get(@PathVariable @Positive long id) {
        return orders.get(id);
    }

    /**
     * Replaces all editable draft fields while retaining optimistic protection.
     * @param id the positive owned request key
     * @param request all editable fields and expected version
     * @return the saved current draft
     */
    @PutMapping("/{id}/draft")
    @Operation(summary = "Replace my saved draft",
            description = "DRAFT only. Omitted optional fields become null. "
                    + "Supply the last GET recordVersion; "
                    + "stale writes are 409.")
    public OrderResponse saveDraft(@PathVariable @Positive long id,
            @RequestBody @Valid OrderDraftUpdateRequest request) {
        return orders.saveDraft(id, request);
    }

    /**
     * Atomically captures the saved draft, horse and requested service inputs.
     * @param id the owned request key
     * @param request the observed version and client-generated operation UUID
     * @return the immutable revision, including an identical successful retry
     */
    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit my saved draft",
            description = "Records a request for assessment, not a quote or "
                    + "confirmed booking. Reuse the same operationId and "
                    + "recordVersion for retries; then GET the current order.")
    public OrderRevisionResponse submit(@PathVariable @Positive long id,
            @RequestBody @Valid OrderSubmitRequest request) {
        return orders.submit(id, request);
    }

    /**
     * Appends a submitted version without overwriting historical snapshots.
     * @param id the owned request key
     * @param request the replacement, version, operation UUID and reason
     * @return the immutable new revision
     */
    @PostMapping("/{id}/revisions")
    @Operation(summary = "Revise my uncommitted request",
            description = "SUBMITTED/CHANGES_REQUESTED/APPROVED before "
                    + "protected commitments. Requires reason and operationId. "
                    + "Historical revisions and currentPlanId are preserved.")
    public OrderRevisionResponse revise(@PathVariable @Positive long id,
            @RequestBody @Valid OrderRevisionRequest request) {
        return orders.revise(id, request);
    }

    /**
     * Reads a bounded immutable history page through the owned parent.
     * @param id the request key
     * @param page the zero-based history page
     * @param size the bounded size
     * @return revisions descending by sequence
     */
    @GetMapping("/{id}/revisions")
    @Operation(summary = "Read my request revision history")
    public List<OrderRevisionResponse> revisions(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.revisions(id, page, size);
    }

    /**
     * Reads captured data, never recapturing current horse or service state.
     * @param id the owned request key
     * @param revision the revision key
     * @return the immutable revision
     */
    @GetMapping("/{id}/revisions/{revision}")
    @Operation(summary = "Read one immutable request revision")
    public OrderRevisionResponse revision(@PathVariable @Positive long id,
            @PathVariable @Positive long revision) {
        return orders.revision(id, revision);
    }

    /**
     * Queues an unapplied proposal after a protected commitment.
     * @param id the owned request key
     * @param request the proposed replacement with reason/version/operation
     * @return a receipt; no route, plan, quote or revision is changed
     */
    @PostMapping("/{id}/change-requests")
    @Operation(summary = "Request a controlled change",
            description = "Intake only for F13 after acceptance or operational "
                    + "commitment. PENDING does not mean approved or applied.")
    public OrderChangeResponse requestChange(@PathVariable @Positive long id,
            @RequestBody @Valid OrderRevisionRequest request) {
        return orders.requestChange(id, request);
    }

    /**
     * Reads the owned proposal receipt without exposing staff-only handling.
     * @param id the owned request key
     * @param change the proposal key
     * @return the pending or handled receipt
     */
    @GetMapping("/{id}/change-requests/{change}")
    @Operation(summary = "Read my change request receipt")
    public OrderChangeResponse change(@PathVariable @Positive long id,
            @PathVariable @Positive long change) {
        return orders.change(id, change);
    }
}
