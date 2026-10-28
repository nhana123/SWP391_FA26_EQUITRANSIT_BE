package com.equitransit.backend.service;

import com.equitransit.backend.dto.payment.DepositRequestCommand;
import com.equitransit.backend.dto.payment.DepositRequested;
import com.equitransit.backend.dto.payment.DepositRequestReceipt;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.dto.quote.QuoteLine;
import com.equitransit.backend.dto.quote.QuotePrepareRequest;
import com.equitransit.backend.dto.quote.QuoteSchedule;
import com.equitransit.backend.entity.CaseOrder;
import com.equitransit.backend.entity.quote.QuoteCalculationContext;
import com.equitransit.backend.entity.quote.QuoteSnapshot;
import com.equitransit.backend.entity.quote.QuoteStatus;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.json.OrderJsonCodec;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.repository.ManagerDepositRepository;
import com.equitransit.backend.repository.PaymentRepository;
import com.equitransit.backend.repository.OrderRepository;
import com.equitransit.backend.repository.QuoteRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Manager pricing preserves customer acceptance and the existing ledger. */
@Service
@Validated
public class ManagerDepositService {
    private final AssessmentRepository cases;
    private final ManagerDepositRepository data;
    private final QuoteRepository quotes;
    private final PaymentRepository ledger;
    private final CurrentActorService actors;
    private final OrderJsonCodec json;
    private final Clock clock;
    private final OrderRepository orders;
    private final OrderCommitmentPolicy commitments;

    /** @param cases parent locks @param data issuance metadata
     * @param quotes existing contracts @param ledger real allocations
     * @param actors identity @param json encoding @param clock UTC
     * @param commitments existing contract/travel protections
     * @param orders current immutable route */
    public ManagerDepositService(AssessmentRepository cases,
            ManagerDepositRepository data, QuoteRepository quotes,
            PaymentRepository ledger, CurrentActorService actors,
            OrderJsonCodec json, Clock clock,
            OrderCommitmentPolicy commitments, OrderRepository orders) {
        this.cases = cases;
        this.data = data;
        this.quotes = quotes;
        this.ledger = ledger;
        this.actors = actors;
        this.json = json;
        this.clock = clock;
        this.commitments = commitments;
        this.orders = orders;
    }

    /** @param order parent @param command expected version and Manager amounts
     * @return issued request, or the same request on an exact retry */
    @Transactional(timeout = 15)
    @PreAuthorize("@domainAccessPolicy.isManager()")
    public DepositRequestReceipt request(long order,
            @Valid DepositRequestCommand command) {
        var actor = actors.current();
        if (actor.staffId() == null
                || !cases.activeStaff(actor.staffId(), "MANAGER")) {
            throw failure("MANAGER_REQUIRED", "An active Manager is required.");
        }
        var parent = cases.order(order, true).orElseThrow(this::missing);
        if (!List.of("SUBMITTED", "APPROVED").contains(parent.status())
                || parent.revisionId() == null) {
            throw failure("DEPOSIT_ORDER_STATE",
                    "Request deposits only for submitted or approved orders.");
        }
        BigDecimal total = command.totalAmount().setScale(2);
        BigDecimal deposit = command.depositAmount().setScale(2);
        if (deposit.compareTo(total) > 0) {
            throw failure("DEPOSIT_AMOUNT_INVALID",
                    "Deposit cannot exceed the total amount.");
        }
        if (!quotes.schemaReady() || !ledger.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_SCHEMA_PENDING", "Deploy the payment schema.");
        }
        String note = command.note() == null ? null : command.note().strip();
        var input = new DepositRequestCommand(total, deposit, "USD", note,
                command.recordVersion().toLowerCase(java.util.Locale.ROOT));
        String hash = TokenTools.hash(json.write(input));
        var previous = data.latest(order, parent.revisionId()).orElse(null);
        if (previous != null && hash.equals(previous.path("hash").asText())) {
            var view = read(parent);
            if (view != null) {
                return new DepositRequestReceipt(order, parent.version(), view);
            }
        }
        if (!parent.version().equalsIgnoreCase(command.recordVersion())) {
            throw failure("ORDER_VERSION_CONFLICT",
                    "Order changed. Reload it before requesting a deposit.");
        }
        if (previous != null) {
            throw failure("DEPOSIT_REQUEST_EXISTS",
                    "A request already exists. Review its quotation instead.");
        }
        Long quote = data.quote(parent.revisionId());
        OffsetDateTime now = OffsetDateTime.now(clock).withNano(0);
        if (quote == null) {
            if (commitments.protectedOrder(order)) {
                throw failure("DEPOSIT_ORDER_PROTECTED",
                        "Existing travel or financial commitments protect "
                                + "this order.");
            }
            quote = prepare(parent, total, deposit, note, now);
        }
        var record = quotes.find(order, quote).orElseThrow(this::missing);
        var schedules = quotes.schedule(quote);
        var stages = schedules.stream().filter(s -> "DEPOSIT".equals(
                s.paymentStage())).toList();
        if (!List.of(QuoteStatus.ISSUED, QuoteStatus.ACCEPTED)
                .contains(record.status()) || record.total() == null
                || total.compareTo(record.total()) != 0 || stages.size() != 1
                || deposit.compareTo(new BigDecimal(
                        stages.getFirst().amountDue())) != 0
                || record.status() == QuoteStatus.ISSUED
                        && (record.validUntil() == null
                                || !record.validUntil().isAfter(now))
                || record.status() == QuoteStatus.ACCEPTED
                        && (record.acceptedAt() == null
                                || "SUBMITTED".equals(parent.status()))) {
            throw failure("DEPOSIT_QUOTE_MISMATCH",
                    "Amounts must match a current issued or accepted quote.");
        }
        if (!cases.transition(parent, parent.status())) {
            throw failure("ORDER_VERSION_CONFLICT", "Reload the order.");
        }
        var metadata = new HashMap<String, Object>();
        metadata.put("schemaVersion", 1);
        metadata.put("revisionId", parent.revisionId());
        metadata.put("quoteId", quote);
        metadata.put("customerId", data.customer(order));
        metadata.put("totalAmount", total);
        metadata.put("depositAmount", deposit);
        metadata.put("note", note);
        metadata.put("requestedAt", now);
        metadata.put("hash", hash);
        data.issued(order, actor.userId(), json.write(metadata));
        if (record.status() == QuoteStatus.ACCEPTED
                && "APPROVED".equals(parent.status())) {
            ledger.lockLedger();
            data.open(quote);
        }
        var updated = cases.order(order, false).orElseThrow(this::missing);
        return new DepositRequestReceipt(order, updated.version(),
                read(updated));
    }

    /** Caller owns customer authorization; no separate public endpoint.
     * @param parent already authorized order @return current request or null */
    @Transactional(readOnly = true)
    public DepositRequested read(CaseOrder parent) {
        if (parent.revisionId() == null || !quotes.schemaReady()) {
            return null;
        }
        var saved = data.latest(parent.orderId(), parent.revisionId())
                .orElse(null);
        if (saved == null) {
            return null;
        }
        long quote = saved.path("quoteId").asLong();
        var record = quotes.find(parent.orderId(), quote).orElse(null);
        if (record == null || !List.of(QuoteStatus.ISSUED, QuoteStatus.ACCEPTED)
                .contains(record.status())) {
            return null;
        }
        BigDecimal due = new BigDecimal(saved.path("depositAmount").asText());
        BigDecimal paid = BigDecimal.ZERO;
        for (var row : ledger.rows("balances", Map.of("quote", quote,
                "asOf", Timestamp.from(clock.instant())))) {
            if ("DEPOSIT".equals(row.get("PaymentStage"))) {
                paid = paid.add((BigDecimal) row.get("PaidAmount"));
            }
        }
        boolean accepted = record.status() == QuoteStatus.ACCEPTED
                && record.acceptedAt() != null;
        Long payment = data.payment(quote);
        boolean available = accepted && payment != null
                && List.of("APPROVED", "IN_PREPARATION")
                .contains(parent.status());
        String note = saved.path("note").isNull() ? null
                : saved.path("note").asText();
        return new DepositRequested(record.total(), due, note,
                OffsetDateTime.parse(saved.path("requestedAt").asText()),
                accepted && paid.compareTo(due) >= 0, "USD", quote,
                payment, available);
    }

    private long prepare(CaseOrder parent, BigDecimal total,
            BigDecimal deposit, String note, OffsetDateTime now) {
        var schedule = new ArrayList<QuoteSchedule>();
        schedule.add(new QuoteSchedule(1, "DEPOSIT", deposit.toPlainString(),
                now.plusDays(1)));
        if (total.compareTo(deposit) > 0) {
            schedule.add(new QuoteSchedule(2, "BALANCE",
                    total.subtract(deposit).toPlainString(), now.plusDays(7)));
        }
        var request = new QuotePrepareRequest(new QuoteCommand(parent.version(),
                UUID.randomUUID()), parent.revisionId(), now.plusDays(7),
                "MANAGER-DEPOSIT-V1", note == null || note.isBlank()
                        ? "Manager-assessed transport price; customer approval "
                                + "required before payment." : note,
                "Changes and cancellation require reviewed settlement.",
                schedule, List.of());
        var revision = json.snapshot(orders.revision(parent.orderId(),
                parent.revisionId()).orElseThrow(this::missing).snapshot());
        var route = revision.routeMatch();
        String code = route != null && route.transportMode() != null
                && !"ROAD".equals(route.transportMode().name())
                        ? "AIRFARE" : "ROAD_TRANSPORT";
        var line = new QuoteLine(code, "TRANSPORTATION",
                "Manager-assessed transport total (manual price)", "SHIPMENT",
                "1.000", total.toPlainString(), total.toPlainString(),
                "PRICED", List.of());
        var context = new QuoteCalculationContext(null, null, null, null,
                null, null, null, null, List.of());
        long id = quotes.prepare(parent.orderId(), actors.current().staffId(),
                new QuoteSnapshot(1, request, List.of(line), List.of(),
                        List.of(), context));
        quotes.transition(id, QuoteStatus.APPROVED, actors.current().staffId(),
                null);
        quotes.transition(id, QuoteStatus.ISSUED, actors.current().staffId(),
                null);
        return id;
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND,
                "DEPOSIT_NOT_FOUND", "Deposit request is unavailable.");
    }

    private OrderException failure(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }
}
