/*
 * PaymentOverview
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.controller.payment;


import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.service.payment.ManualPaymentService;
import com.equitransit.backend.service.payment.PaymentReadService;
import com.equitransit.backend.service.payment.PaymentProofService;
import com.equitransit.backend.dto.payment.PaymentOverview;
import com.equitransit.backend.dto.payment.PaymentMutationReceipt;
import com.equitransit.backend.dto.payment.PaymentProofCommand;
import com.equitransit.backend.dto.payment.PaymentProofReceipt;
import com.equitransit.backend.dto.payment.PaymentProofView;
import com.equitransit.backend.dto.payment.BankReceiptView;
import com.equitransit.backend.dto.payment.PaymentRequestCommand;
import com.equitransit.backend.dto.payment.BankReportCommand;
import com.equitransit.backend.dto.payment.BankReceiptCommand;
import com.equitransit.backend.dto.payment.AllocationCommand;
import com.equitransit.backend.dto.payment.RefundRequestCommand;
import com.equitransit.backend.dto.payment.RefundActionCommand;
import com.equitransit.backend.dto.payment.ReversalCommand;
import com.equitransit.backend.dto.payment.ReportReviewCommand;


/** Manual transfers only; customer reports never recognize received money. */
@RestController
@Validated
@RequestMapping(value = "/api/transport-orders/{orderId}/payments",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class ManualPaymentController {

    private final ManualPaymentService writes;
    private final PaymentReadService reads;
    private final PaymentProofService proofs;

    /**
     * @param writes financial mutations
     * @param reads safe bounded views
     * @param proofs protected image evidence
     */
    public ManualPaymentController(ManualPaymentService writes,
            PaymentReadService reads, PaymentProofService proofs) {
        this.writes = writes;
        this.reads = reads;
        this.proofs = proofs;
    }

    /**
     * @param orderId owned or managed parent
     * @param page zero-based independent collection page
     * @param size per-collection limit
     * @return safe ledger overview
     */
    @GetMapping
    public PaymentOverview overview(@PathVariable @Positive long orderId,
            @Parameter(schema = @Schema(example = "0", type = "integer"))
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(schema = @Schema(example = "20", type = "integer"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reads.overview(orderId, page, size);
    }

    /**
     * @param orderId authorized parent
     * @param body validated command and observed parent version
     * @return immutable idempotent receipt
     */
    @PostMapping("/requests")
    public PaymentMutationReceipt request(
            @PathVariable @Positive long orderId,
            @RequestBody @Valid PaymentRequestCommand body) {
        return writes.request(orderId, body);
    }

    /**
     * @param orderId authorized parent
     * @param body validated command and observed parent version
     * @return immutable idempotent receipt
     */
    @PostMapping("/reports")
    public PaymentMutationReceipt report(
            @PathVariable @Positive long orderId,
            @RequestBody @Valid BankReportCommand body) {
        return writes.report(orderId, body);
    }

    /**
     * @param orderId authorized parent
     * @param body validated command and observed parent version
     * @return immutable idempotent receipt
     */
    @PostMapping("/transactions")
    public PaymentMutationReceipt reconcile(
            @PathVariable @Positive long orderId,
            @RequestBody @Valid BankReceiptCommand body) {
        return writes.reconcile(orderId, body);
    }

    /**
     * @param orderId authorized parent
     * @param body validated command and observed parent version
     * @return immutable idempotent receipt
     */
    @PostMapping("/allocations")
    public PaymentMutationReceipt allocate(
            @PathVariable @Positive long orderId,
            @RequestBody @Valid AllocationCommand body) {
        return writes.allocate(orderId, body);
    }

    /**
     * @param orderId authorized parent
     * @param body validated command and observed parent version
     * @return immutable idempotent receipt
     */
    @PostMapping("/refunds")
    public PaymentMutationReceipt refund(
            @PathVariable @Positive long orderId,
            @RequestBody @Valid RefundRequestCommand body) {
        return writes.refund(orderId, body);
    }

    /**
     * @param orderId authorized parent
     * @param target exact action target
     * @param body validated command and observed parent version
     * @return immutable idempotent receipt
     */
    @PostMapping("/refunds/{target}/actions")
    public PaymentMutationReceipt refundAction(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long target,
            @RequestBody @Valid RefundActionCommand body) {
        return writes.refundAction(orderId, target, body);
    }

    /**
     * @param orderId authorized parent
     * @param target exact action target
     * @param body validated command and observed parent version
     * @return immutable idempotent receipt
     */
    @PostMapping("/transactions/{target}/reversal")
    public PaymentMutationReceipt reverse(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long target,
            @RequestBody @Valid ReversalCommand body) {
        return writes.reverse(orderId, target, body);
    }

    /**
     * @param orderId authorized parent
     * @param target exact action target
     * @param body validated command and observed parent version
     * @return immutable idempotent receipt
     */
    @PostMapping("/reports/{target}/reject")
    public PaymentMutationReceipt rejectReport(
            @PathVariable @Positive long orderId,
            @PathVariable @Positive long target,
            @RequestBody @Valid ReportReviewCommand body) {
        return writes.rejectReport(orderId, target, body);
    }

    /**
     * @param orderId owned or managed parent
     * @param body bounded PNG/JPEG base64 and idempotent command
     * @return opaque file key
     */
    @PostMapping("/proofs")
    public PaymentProofReceipt upload(@PathVariable @Positive long orderId,
            @RequestBody @Valid PaymentProofCommand body) {
        return proofs.upload(orderId, body);
    }

    /**
     * @param orderId authorized parent
     * @param key opaque UUID, never a path
     * @return safe image base64 JSON
     */
    @GetMapping("/proofs/{key}")
    public PaymentProofView proof(@PathVariable @Positive long orderId,
            @PathVariable String key) {
        return proofs.get(orderId, key);
    }

    /**
     * @param orderId managed parent
     * @param transaction exact bank key
     * @return Manager-only reconciled bank status
     */
    @GetMapping("/transactions/{transaction}")
    public BankReceiptView bank(@PathVariable @Positive long orderId,
            @PathVariable @Positive long transaction) {
        return reads.bank(orderId, transaction);
    }
}
