/* F14 manager closure HTTP contract. */
package com.equitransit.backend.controller.handover;

import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.dto.handover.response.OrderClosureResponse;
import com.equitransit.backend.service.handover.OrderClosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated manager closes one delivered order without double audit. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
public class OrderClosureController {

    private final OrderClosureService service;

    /** @param service closure command boundary */
    public OrderClosureController(OrderClosureService service) {
        this.service = service;
    }

    /** @param orderId delivered order @param command retry and version
     * @return updated order status and rowversion */
    @PostMapping(value = "/api/transport-orders/{orderId}"
            + "/handovers/close-order",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Close a delivered transport order")
    public OrderClosureResponse close(@PathVariable @Positive long orderId,
            @RequestBody @Valid QuoteCommand command) {
        return service.close(orderId, command);
    }
}
