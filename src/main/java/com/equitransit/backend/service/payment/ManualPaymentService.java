/*
 * ManualPaymentService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Transactional manual bank payment lifecycle.
 */
package com.equitransit.backend.service.payment;


import com.equitransit.backend.dto.payment.AllocationCommand;
import com.equitransit.backend.dto.payment.BankReceiptCommand;
import com.equitransit.backend.dto.payment.BankReportCommand;
import com.equitransit.backend.dto.payment.PaymentRequestCommand;
import com.equitransit.backend.dto.payment.PaymentMutationReceipt;
import com.equitransit.backend.dto.payment.RefundRequestCommand;
import com.equitransit.backend.dto.payment.RefundActionCommand;
import com.equitransit.backend.dto.payment.ReversalCommand;
import com.equitransit.backend.dto.payment.ReportReviewCommand;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.payment.RefundAction;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.payment.PaymentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;


/** Parent -> quote -> ledger -> bank lock order for every money mutation. */
@Service
@Validated
@PreAuthorize("isAuthenticated()")
@Transactional(timeout = 15)
public class ManualPaymentService {

    private final PaymentRepository ledger;
    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final CurrentActorService actors;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final PaymentProofService proofs;

    /**
     * @param ledger parameterized accounting adapter
     * @param cases shared parent and actor locks
     * @param orders ownership adapter
     * @param actors trusted live identities
     * @param json canonical command encoder
     * @param mapper immutable receipt decoder
     * @param clock UTC timestamps
     * @param proofs protected image evidence
     */
    public ManualPaymentService(PaymentRepository ledger,
            AssessmentRepository cases, OrderRepository orders,
            CurrentActorService actors, OrderJsonCodec json,
            ObjectMapper mapper, Clock clock, PaymentProofService proofs) {
        this.ledger = ledger;
        this.cases = cases;
        this.orders = orders;
        this.actors = actors;
        this.json = json;
        this.mapper = mapper;
        this.clock = clock;
        this.proofs = proofs;
    }

    /**
     * @param order trusted path parent
     * @param body exact accepted schedule, never a client amount
     * @return one obligation, including on retry
     */
    public PaymentMutationReceipt request(long order,
            @Valid PaymentRequestCommand body) {
        return mutate(order, body.command(), body, true, context -> {
            var quote = context.quote();

            check(id(quote, "QuoteID") == body.quoteId(), "PAYMENT_QUOTE");
            check(ledger.number("requestDuplicate", Map.of("schedule",
                    body.scheduleId())) == 0, "PAYMENT_REQUEST_EXISTS");
            return ledger.number("newRequest", Map.of("schedule",
                    body.scheduleId(), "quote", body.quoteId()));
        });
    }

    /**
     * @param order owned parent
     * @param body reported transfer; does not recognize received money
     * @return report identity only
     */
    public PaymentMutationReceipt report(long order,
            @Valid BankReportCommand body) {
        return mutate(order, body.command(), body, false, context -> {
            check("CUSTOMER".equals(context.actor().roleCode()),
                    "CUSTOMER_REQUIRED");
            obligation(order, body.paymentRequestId(), context);
            past(body.transferredAt());
            proofs.require(order, body.proofFileKey(), context.actor());
            var args = args(order, body.paymentRequestId());

            args.put("request", body.paymentRequestId());
            args.put("amount", money(body.reportedAmount()));
            args.put("currency", body.reportedCurrency());
            args.put("reference", body.bankReference());
            args.put("at", time(body.transferredAt()));
            args.put("proof", body.proofFileKey());
            return ledger.number("report", args);
        });
    }

    /**
     * @param order accepted parent
     * @param body bank-verified receipt; independent of customer report
     * @return immutable bank record identity
     */
    public PaymentMutationReceipt reconcile(long order,
            @Valid BankReceiptCommand body) {
        return mutate(order, body.command(), body, true, context -> {
            past(body.postedAt());
            proofs.require(order, body.proofFileKey(), context.actor());
            var args = args(order, 0);

            args.put("account", body.recipientAccount().strip());
            args.put("reference", body.bankTransactionReference().strip());
            check(ledger.number("bankDuplicate", args) == 0,
                    "BANK_REFERENCE_DUPLICATE");
            args.put("staff", context.actor().staffId());
            args.put("amount", money(body.amountReceived()));
            args.put("currency", body.currency());
            args.put("at", time(body.postedAt()));
            args.put("source", body.verificationSource());
            args.put("proof", body.proofFileKey());
            args.put("notes", body.internalNotes());
            return ledger.number("receipt", args);
        });
    }

    /**
     * @param order accepted parent
     * @param body bank-to-obligation allocation
     * @return allocation identity, without increasing bank receipts
     */
    public PaymentMutationReceipt allocate(long order,
            @Valid AllocationCommand body) {
        return mutate(order, body.command(), body, true, context -> {
            var request = obligation(order, body.paymentRequestId(), context);
            var bank = bank(body.transactionId());
            var amount = money(body.amount());

            check("USD".equals(bank.get("Currency"))
                    && "USD".equals(request.get("Currency")),
                    "PAYMENT_CURRENCY");
            BigDecimal used = ledger.amount("usedBank",
                    Map.of("id", body.transactionId()));
            BigDecimal applied = ledger.amount("usedRequest",
                    Map.of("id", body.paymentRequestId()));

            check(used.add(amount).compareTo(decimal(bank,
                    "AmountReceived")) <= 0, "PAYMENT_OVER_ALLOCATION");
            check(applied.add(amount).compareTo(decimal(request,
                    "AmountDue")) <= 0, "PAYMENT_OVER_OBLIGATION");
            return ledger.number("allocate", Map.of("transaction",
                    body.transactionId(), "request", body.paymentRequestId(),
                    "amount", amount));
        });
    }

    /**
     * @param order owned or managed parent
     * @param body positive amount and reason; reserves refundable balance
     * @return REQUESTED refund; no money has moved
     */
    public PaymentMutationReceipt refund(long order,
            @Valid RefundRequestCommand body) {
        return mutate(order, body.command(), body, false, context -> {
            var allocation = one("allocation", args(order,
                    body.allocationId()));
            bank(id(allocation, "TransactionID"));
            allocation = one("allocationForUpdate", args(order,
                    body.allocationId()));
            var amount = money(body.amount());
            var reserved = ledger.amount("refundReserved",
                    Map.of("id", body.allocationId()));

            check(reserved.add(amount).compareTo(decimal(allocation,
                    "AllocatedAmount")) <= 0, "REFUND_OVER_ALLOCATION");
            return ledger.number("refundRequest", Map.of("allocation",
                    body.allocationId(), "amount", amount, "reason",
                    body.reason(), "user", context.actor().userId()));
        });
    }

    /**
     * @param order parent path
     * @param refund immutable refund request
     * @param body explicit approval or evidence-backed completion
     * @return same refund identity after transition
     */
    public PaymentMutationReceipt refundAction(long order, long refund,
            @Valid RefundActionCommand body) {
        var input = new Target(refund, body);

        return mutate(order, body.command(), input, true, context -> {
            var row = one("refund", args(order, refund));
            var allocation = one("allocation", args(order,
                    id(row, "AllocationID")));

            bank(id(allocation, "TransactionID"));
            allocation = one("allocationForUpdate", args(order,
                    id(row, "AllocationID")));
            var args = args(order, refund);

            args.put("staff", context.actor().staffId());
            if (body.action() == RefundAction.COMPLETE) {
                check("APPROVED".equals(row.get("Status")), "REFUND_STATE");
                check(body.bankRefundReference() != null
                        && !body.bankRefundReference().isBlank()
                        && body.proofFileKey() != null
                        && body.refundedAt() != null, "REFUND_PROOF_REQUIRED");
                past(body.refundedAt());
                check(!body.refundedAt().toInstant().isBefore(
                        ((Timestamp) row.get("ApprovedAt")).toInstant()),
                        "REFUND_TIME_BEFORE_APPROVAL");
                proofs.require(order, body.proofFileKey(), context.actor());
                args.put("reference", body.bankRefundReference().strip());
                check(ledger.number("refundDuplicate", args) == 0,
                        "REFUND_BANK_DUPLICATE");
                args.put("proof", body.proofFileKey());
                args.put("at", time(body.refundedAt()));
                ledger.update("refundComplete", args);
            } else {
                check("REQUESTED".equals(row.get("Status")), "REFUND_STATE");
                check(body.bankRefundReference() == null
                        && body.proofFileKey() == null
                        && body.refundedAt() == null, "REFUND_PREMATURE_PROOF");
                ledger.update(body.action() == RefundAction.APPROVE
                        ? "refundApprove" : "refundReject", args);
            }
            return refund;
        });
    }

    /**
     * @param order a parent with an allocation from this transaction
     * @param transaction bank receipt being fully reversed
     * @param body verified reversal evidence; no partial reversal API
     * @return bank identity; reversed allocations no longer count as paid
     */
    public PaymentMutationReceipt reverse(long order, long transaction,
            @Valid ReversalCommand body) {
        var input = new Target(transaction, body);

        return mutate(order, body.command(), input, true, context -> {
            bank(transaction);
            check(ledger.number("bankBound", args(order, transaction)) > 0,
                    "BANK_ORDER_MISMATCH");
            check(ledger.amount("bankRefunded", Map.of("id", transaction))
                    .signum() == 0, "REVERSAL_REFUND_CONFLICT");
            proofs.require(order, body.proofFileKey(), context.actor());
            var args = args(order, transaction);

            args.put("proof", body.proofFileKey());
            args.put("staff", context.actor().staffId());
            args.put("reason", body.reason());
            ledger.update("reverse", args);
            return transaction;
        });
    }

    /**
     * @param order managed parent
     * @param report owned report identity
     * @param body audit reason; rejection does not alter money
     * @return report identity
     */
    public PaymentMutationReceipt rejectReport(long order, long report,
            @Valid ReportReviewCommand body) {
        var input = new Target(report, body);

        return mutate(order, body.command(), input, true, context -> {
            check(ledger.update("rejectReport", args(order, report)) == 1,
                    "PAYMENT_REPORT_MISSING");
            return report;
        });
    }

    private PaymentMutationReceipt mutate(long order, QuoteCommand command,
            Object input, boolean manager, Function<Context, Long> write) {
        var context = context(order, manager);
        var args = args(order, 0);

        args.put("user", context.actor().userId());
        args.put("op", command.operationId());
        String hash = TokenTools.hash(json.canonical(Map.of("type",
                input.getClass().getSimpleName(), "pathId",
                pathIdentity(input), "input", input)));
        // Path targets are carried by wrapper actions below, never omitted.
        var saved = ledger.rows("replay", args);

        if (!saved.isEmpty()) {
            check(hash.equals(saved.getFirst().get("RequestHash")),
                    "PAYMENT_RETRY_CONFLICT");
            try {
                return mapper.readValue(saved.getFirst().get("ReceiptJSON")
                        .toString(), PaymentMutationReceipt.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw failure("PAYMENT_RECEIPT_UNSUPPORTED");
            }
        }
        check(context.parent().version().equalsIgnoreCase(
                command.recordVersion()), "ORDER_VERSION_CONFLICT");
        long id = write.apply(context);

        check(id > 0, "PAYMENT_SOURCE_MISMATCH");
        ledger.update("bump", args);
        var receipt = new PaymentMutationReceipt(id,
                cases.order(order, false).orElseThrow().version());

        args.put("hash", hash);
        args.put("receipt", json.write(receipt));
        ledger.update("operation", args);
        Object payload = input instanceof Target target ? target.body() : input;
        args.put("action", payload.getClass().getSimpleName());
        args.put("receipt", json.write(Map.of("schemaVersion", 1,
                "operationId", command.operationId(), "entityId", id,
                "requestHash", hash, "reason",
                payload instanceof ReportReviewCommand review
                        ? review.reason() : "Recorded manual payment action")));
        ledger.update("audit", args);
        return receipt;
    }

    private Object pathIdentity(Object input) {
        return input instanceof Target target ? target.id() : 0;
    }

    private Context context(long order, boolean manager) {
        if (!ledger.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_SCHEMA_PENDING", "Deploy payment migration007.");
        }
        var parent = cases.order(order, true).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND,
                        "PAYMENT_NOT_FOUND", "Payment data not found."));
        var actor = actors.current();

        if ("CUSTOMER".equals(actor.roleCode()) && !manager) {
            check(orders.findOwned(order, actor.customerId(),
                    false).isPresent(),
                    "PAYMENT_NOT_FOUND");
        } else if (!"MANAGER".equals(actor.roleCode())
                || actor.staffId() == null
                || !cases.activeStaff(actor.staffId(), "MANAGER")) {
            throw new OrderException(HttpStatus.FORBIDDEN,
                    "MANAGER_REQUIRED", "An active Manager is required.");
        }
        var quote = one("quote", Map.of("order", order));

        check(parent.revisionId() != null
                && parent.revisionId() == id(quote, "RevisionID")
                && quote.get("AcceptedAt") != null
                && "USD".equals(quote.get("Currency")),
                "ACCEPTED_QUOTE_REQUIRED");
        ledger.lockLedger();
        return new Context(parent, actor, quote);
    }

    private Map<String, Object> obligation(long order, long request,
            Context context) {
        var row = one("request", args(order, request));

        check(id(row, "QuoteID") == id(context.quote(), "QuoteID")
                && "USD".equals(row.get("Currency"))
                && row.get("ScheduleID") != null, "PAYMENT_SOURCE_MISMATCH");
        var schedule = ledger.rows("schedule", Map.of("quote",
                id(context.quote(), "QuoteID"))).stream().filter(item ->
                id(item, "ScheduleID") == id(row, "ScheduleID"))
                .findFirst().orElseThrow(() -> failure("PAYMENT_SCHEDULE"));

        check(decimal(schedule, "AmountDue").compareTo(
                decimal(row, "AmountDue")) == 0
                && schedule.get("Currency").equals(row.get("Currency")),
                "PAYMENT_SCHEDULE_MISMATCH");
        return row;
    }

    private Map<String, Object> bank(long transaction) {
        var row = one("bank", Map.of("id", transaction));

        check("RECONCILED".equals(row.get("Status"))
                && row.get("ReconciledAt") != null, "BANK_NOT_RECONCILED");
        return row;
    }

    private Map<String, Object> one(String query, Map<String, ?> args) {
        var rows = ledger.rows(query, args);

        check(rows.size() == 1, "PAYMENT_SOURCE_MISSING");
        return rows.getFirst();
    }

    private Map<String, Object> args(long order, long id) {
        var args = new HashMap<String, Object>();

        args.put("order", order);
        args.put("id", id);
        return args;
    }

    private long id(Map<String, Object> row, String field) {
        return ((Number) row.get(field)).longValue();
    }

    private BigDecimal decimal(Map<String, Object> row, String field) {
        return (BigDecimal) row.get(field);
    }

    private BigDecimal money(String value) {
        var amount = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);

        check(amount.signum() > 0 && amount.precision() <= 18,
                "PAYMENT_AMOUNT_INVALID");
        return amount;
    }

    private void past(OffsetDateTime at) {
        check(!at.isAfter(OffsetDateTime.now(clock)),
                "PAYMENT_FUTURE_TRANSFER");
    }

    private Timestamp time(OffsetDateTime at) {
        return Timestamp.from(at.toInstant().truncatedTo(
                java.time.temporal.ChronoUnit.SECONDS));
    }

    private void check(boolean valid, String code) {
        if (!valid) {
            throw failure(code);
        }
    }

    private OrderException failure(String code) {
        return new OrderException(HttpStatus.CONFLICT, code,
                "Payment conditions changed or evidence is incomplete.");
    }

    private record Context(CaseOrder parent, CurrentActor actor,
            Map<String, Object> quote) {
    }

    private record Target(long id, Object body) {
    }
}
