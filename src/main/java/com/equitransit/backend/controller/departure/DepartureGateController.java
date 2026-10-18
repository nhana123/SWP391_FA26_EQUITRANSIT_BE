/*
 * DepartureGateController
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify departure gates and physically receive the horse.
 */
package com.equitransit.backend.controller.departure;

import com.equitransit.backend.dto.departure.DepartureModels.
        DepartureAuthorizeRequest;
import com.equitransit.backend.dto.departure.DepartureModels.
        DepartureGateEvaluateRequest;
import com.equitransit.backend.dto.departure.DepartureModels.
        DepartureReadinessResponse;
import com.equitransit.backend.dto.departure.DepartureModels.
        HorseReceiptRequest;
import com.equitransit.backend.dto.departure.DepartureModels.LegDepartRequest;
import com.equitransit.backend.service.departure.DepartureGateService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import com.equitransit.backend.dto.departure.DepartureModels.DepartureEvidence;
import java.io.IOException;

/** Thin F11 departure gate and horse receipt controller. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Departure gates and horse receipt")
@RequestMapping(value = "/api/transport-orders/{orderId}/departure",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class DepartureGateController {

    private final DepartureGateService service;

    /** @param service transactional departure gate and receipt service */
    public DepartureGateController(DepartureGateService service) {
        this.service = service;
    }

    /**
     * @param orderId transport order ID
     * @return real-time 3-gate readiness and physical receipt state
     */
    @GetMapping
    public DepartureReadinessResponse getReadiness(
            @Parameter(example = "101") @PathVariable @Positive long orderId) {
        return service.getReadiness(orderId);
    }

    /**
     * @param orderId transport order ID
     * @param body gate evaluation command
     * @return updated 3-gate readiness persisted on current plan
     */
    @PostMapping(value = "/gates/evaluate",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public DepartureReadinessResponse evaluateGates(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Valid @RequestBody DepartureGateEvaluateRequest body) {
        return service.evaluateGates(orderId, body);
    }

    /**
     * @param orderId transport order ID
     * @param body manager departure authorization command
     * @return updated readiness with manager departure authorization
     */
    @PostMapping(value = "/authorize",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public DepartureReadinessResponse authorize(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Valid @RequestBody DepartureAuthorizeRequest body) {
        return service.authorize(orderId, body);
    }

    /**
     * @param orderId transport order ID
     * @param body driver physical horse receipt command
     * @return updated readiness with recorded horse receipt
     */
    @PostMapping(value = "/horse-receipts",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public DepartureReadinessResponse recordHorseReceipt(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Valid @RequestBody HorseReceiptRequest body) {
        return service.recordHorseReceipt(orderId, body);
    }

    /**
     * @param orderId transport order ID
     * @param body driver first-leg departure command
     * @return updated readiness transitioned to IN_TRANSIT
     */
    @PostMapping(value = "/depart",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public DepartureReadinessResponse depart(
            @Parameter(example = "101") @PathVariable @Positive long orderId,
            @Valid @RequestBody LegDepartRequest body) {
        return service.depart(orderId, body);
    }
    /** @param orderId parent @param body current plan @return revoked state */
    @PostMapping(value = "/authorization/revoke",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public DepartureReadinessResponse revoke(
            @PathVariable @Positive long orderId,
            @Valid @RequestBody DepartureAuthorizeRequest body) {
        return service.revoke(orderId, body);
    }

    /** @param orderId parent @param planId plan @param legId first road leg
     * @param file image @return scoped key @throws IOException IO */
    @PostMapping(value = "/evidence",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DepartureEvidence uploadEvidence(
            @PathVariable @Positive long orderId,
            @RequestParam @Positive long planId,
            @RequestParam @Positive long legId,
            @RequestParam MultipartFile file) throws IOException {
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new com.equitransit.backend.exception.order.OrderException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "EVIDENCE_SIZE_INVALID", "Maximum image size is 10MB.");
        }
        return service.uploadEvidence(orderId, planId, legId, file.getBytes());
    }

    /** @param orderId scoped parent @param receiptId persisted receipt
     * @return private integrity-checked image as an attachment */
    @GetMapping(value = "/horse-receipts/{receiptId}/file",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> receiptFile(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long receiptId) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition",
                        "attachment; filename=pickup.img")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.receiptFile(orderId, receiptId));
    }
}
