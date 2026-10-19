/*
 * QuotationController
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.controller.quote;


import com.equitransit.backend.dto.quote.QuoteActionRequest;
import com.equitransit.backend.dto.quote.QuotePage;
import com.equitransit.backend.dto.quote.QuotePrepareRequest;
import com.equitransit.backend.dto.quote.QuoteResponse;
import com.equitransit.backend.dto.quote.SupplierOfferReceipt;
import com.equitransit.backend.dto.quote.SupplierOfferRequest;
import com.equitransit.backend.service.quote.QuotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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


/** Thin quotation transport; service owns roles, locks and state changes. */
@RestController
@Validated
@Tag(name = "Versioned quotations")
@RequestMapping(value = "/api/transport-orders/{orderId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class QuotationController {

    private final QuotationService service;

    /** @param service transactional quotation lifecycle */
    public QuotationController(QuotationService service) {
        this.service = service;
    }

    /**
     * @param orderId authorized order
     * @param page zero-based page
     * @param size bounded page size
     * @return published customer headers or internal authorized staff headers
     */
    @GetMapping("/quotes")
    @Operation(summary = "List quotations visible to the current actor")
    public QuotePage list(
            @Parameter(example = "101") @Positive @PathVariable long orderId,
            @Parameter(example = "0") @Min(0)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(example = "20") @Min(1) @Max(100)
            @RequestParam(defaultValue = "20") int size) {
        return service.list(orderId, page, size);
    }

    /**
     * @param orderId authorized order
     * @param quoteId parent-bound quotation
     * @return safe captured retail breakdown and terms
     */
    @GetMapping("/quotes/{quoteId}")
    public QuoteResponse get(
            @Parameter(example = "101") @Positive @PathVariable long orderId,
            @Parameter(example = "301") @Positive @PathVariable long quoteId) {
        return service.get(orderId, quoteId);
    }

    /**
     * @param orderId approved current order
     * @param request full snapshot preparation and observed parent version
     * @return new DRAFT version; pending prices never become zero items
     */
    @PostMapping(value = "/quotes",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Prepare a version (Manager or assigned Coordinator)")
    public QuoteResponse prepare(
            @Parameter(example = "101") @Positive @PathVariable long orderId,
            @Valid @RequestBody QuotePrepareRequest request) {
        return service.prepare(orderId, request);
    }

    /**
     * @param orderId approved current order
     * @param request supplier cost, separate retail rate and evidence
     * @return offer identity only; no supplier cost in customer responses
     */
    @PostMapping(value = "/supplier-offers",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Record an evidenced supplier offer; not a booking")
    public SupplierOfferReceipt offer(
            @Parameter(example = "101") @Positive @PathVariable long orderId,
            @Valid @RequestBody SupplierOfferRequest request) {
        return service.offer(orderId, request);
    }

    /**
     * @param orderId authorized order
     * @param quoteId parent-bound quotation
     * @param request versioned explicit action and retry UUID
     * @return immutable receipt; ACCEPT never confirms payment or departure
     */
    @PostMapping(value = "/quotes/{quoteId}/actions",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public QuoteResponse action(
            @Parameter(example = "101") @Positive @PathVariable long orderId,
            @Parameter(example = "301") @Positive @PathVariable long quoteId,
            @Valid @RequestBody QuoteActionRequest request) {
        return service.action(orderId, quoteId, request);
    }
}
