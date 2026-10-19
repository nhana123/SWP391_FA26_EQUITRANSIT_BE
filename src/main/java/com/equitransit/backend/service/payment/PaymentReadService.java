/*
 * PaymentReadService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Bounded owner-safe accounting views.
 */
package com.equitransit.backend.service.payment;


import com.equitransit.backend.dto.payment.PaymentOverview;
import com.equitransit.backend.dto.payment.PaymentObligation;
import com.equitransit.backend.dto.payment.PaymentReportView;
import com.equitransit.backend.dto.payment.PaymentAllocationView;
import com.equitransit.backend.dto.payment.RefundView;
import com.equitransit.backend.dto.payment.BankReceiptView;
import com.equitransit.backend.entity.payment.ReportState;
import com.equitransit.backend.entity.payment.RefundState;
import com.equitransit.backend.entity.payment.BankState;
import com.equitransit.backend.repository.payment.PaymentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.exception.order.OrderException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;


/** Queries are bounded and set-based, not one request per row. */
@Service
@PreAuthorize("isAuthenticated()")
@Transactional(timeout = 15)
public class PaymentReadService {

    private final PaymentRepository ledger;
    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final CurrentActorService actors;

    /**
     * @param ledger bounded persistence
     * @param cases parent and live role checks
     * @param orders customer ownership
     * @param actors trusted actor
     */
    public PaymentReadService(PaymentRepository ledger,
            AssessmentRepository cases, OrderRepository orders,
            CurrentActorService actors) {
        this.ledger = ledger;
        this.cases = cases;
        this.orders = orders;
        this.actors = actors;
    }

    /**
     * @param order owned or managed order
     * @param page zero-based page, applied independently to each collection
     * @param size per-collection size1..100
     * @return safe report/obligation/allocation/refund records
     */
    public PaymentOverview overview(long order, int page, int size) {
        var parent = access(order, false);
        var args = Map.of("order", order, "offset", (long) page * size,
                "size", size);
        var quote = ledger.rows("quote", args);
        var balances = quote.isEmpty() ? java.util.List
                .<Map<String, Object>>of() : ledger.rows("balances",
                Map.of("quote", id(quote.getFirst(), "QuoteID"),
                        "asOf", Timestamp.from(java.time.Instant.now())));
        var requests = ledger.rows("requests", args).stream().map(row -> {
            String paid = balances.stream().filter(b -> id(b, "ScheduleID")
                    == id(row, "ScheduleID")).findFirst()
                    .map(b -> money(b, "PaidAmount")).orElse("0.00");

            return new PaymentObligation(id(row, "PaymentRequestID"),
                    id(row, "ScheduleID"), id(row, "QuoteID"),
                    row.get("PaymentStage").toString(), money(row, "AmountDue"),
                    row.get("Currency").toString(), at(row, "DueAt"), paid);
        }).toList();
        var reports = ledger.rows("reports", args).stream().map(row ->
                new PaymentReportView(id(row, "ReportID"),
                        id(row, "PaymentRequestID"),
                        money(row, "ReportedAmount"),
                        string(row, "ReportedCurrency"), at(row,
                "TransferredAt"),
                        string(row, "ProofFileKey"), ReportState.valueOf(
                                row.get("Status").toString()))).toList();
        var allocations = ledger.rows("allocations", args).stream().map(row ->
                new PaymentAllocationView(id(row, "AllocationID"),
                        id(row, "TransactionID"), id(row, "PaymentRequestID"),
                        money(row, "AllocatedAmount"))).toList();
        var refunds = ledger.rows("refunds", args).stream().map(row ->
                new RefundView(id(row, "RefundID"), id(row, "AllocationID"),
                        money(row, "Amount"), row.get("Reason").toString(),
                        RefundState.valueOf(row.get("Status").toString()),
                        at(row, "RefundedAt"))).toList();

        return new PaymentOverview(parent.version(), requests, reports,
                allocations, refunds, page, size);
    }

    /**
     * @param order managed parent
     * @param transaction known ledger key
     * @return bank status only, no private bank notes or accounts
     */
    public BankReceiptView bank(long order, long transaction) {
        access(order, true);
        if (ledger.number("bankBound", Map.of("order", order,
                "id", transaction)) == 0) {
            throw missing();
        }
        var row = ledger.rows("bank", Map.of("id", transaction)).getFirst();

        return new BankReceiptView(transaction, money(row, "AmountReceived"),
                row.get("Currency").toString(), BankState.valueOf(
                        row.get("Status").toString()), at(row, "PostedAt"));
    }

    private CaseOrder access(long order,
            boolean manager) {
        if (!ledger.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_SCHEMA_PENDING", "Deploy payment migration007.");
        }
        var parent = cases.order(order, true).orElseThrow(this::missing);
        var actor = actors.current();

        if ("CUSTOMER".equals(actor.roleCode()) && !manager) {
            if (orders.findOwned(order, actor.customerId(), false).isEmpty()) {
                throw missing();
            }
        } else if (!"MANAGER".equals(actor.roleCode())
                || actor.staffId() == null
                || !cases.activeStaff(actor.staffId(), "MANAGER")) {
            throw new OrderException(HttpStatus.FORBIDDEN,
                    "PAYMENT_FORBIDDEN", "Access is denied.");
        }
        ledger.lockLedger();
        return parent;
    }

    private long id(Map<String, Object> row, String key) {
        return row.get(key) == null ? 0 : ((Number) row.get(key)).longValue();
    }

    private String string(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : row.get(key).toString();
    }

    private String money(Map<String, Object> row, String key) {
        return ((BigDecimal) row.get(key)).setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private OffsetDateTime at(Map<String, Object> row, String key) {
        var value = (Timestamp) row.get(key);

        return value == null ? null
                : value.toInstant().atOffset(ZoneOffset.UTC);
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND,
                "PAYMENT_NOT_FOUND", "Payment record unavailable.");
    }
}
