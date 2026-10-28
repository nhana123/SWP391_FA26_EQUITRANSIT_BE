package com.equitransit.backend.controller;

import com.equitransit.backend.dto.payment.DepositRequestCommand;
import com.equitransit.backend.dto.payment.DepositRequestReceipt;
import com.equitransit.backend.service.ManagerDepositService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One-click commercial issuance; never impersonates customer acceptance. */
@RestController
@SecurityRequirement(name = "bearerAuth")
@Validated
@RequestMapping(value = "/api/manager/transport-orders",
        produces = "application/json")
@PreAuthorize("@domainAccessPolicy.isManager()")
public class ManagerDepositController {
    private final ManagerDepositService deposits;

    /** @param deposits transactional Manager command */
    public ManagerDepositController(ManagerDepositService deposits) {
        this.deposits = deposits;
    }

    /** @param id order @param command version/amounts @return current view */
    @PostMapping(value = "/{id}/request-deposit", consumes = "application/json")
    @Operation(summary = "Issue or link a customer deposit request")
    public DepositRequestReceipt request(@PathVariable @Positive long id,
            @Valid @RequestBody DepositRequestCommand command) {
        return deposits.request(id, command);
    }
}
