package com.equitransit.backend.service;

import com.equitransit.backend.dto.payment.BookingFlowStatus;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.dto.quote.QuoteLine;
import com.equitransit.backend.dto.quote.QuotePrepareRequest;
import com.equitransit.backend.dto.quote.QuoteSchedule;
import com.equitransit.backend.dto.order.response.OrderSnapshot;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.assessment.CaseRole;
import com.equitransit.backend.entity.quote.QuoteCalculationContext;
import com.equitransit.backend.entity.quote.QuoteSnapshot;
import com.equitransit.backend.entity.quote.QuoteStatus;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.BookingFlowRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.payment.PaymentRepository;
import com.equitransit.backend.repository.quote.QuoteRepository;
import com.equitransit.backend.security.CurrentActorService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Shared current-revision booking gates and transactional ROAD automation. */
@Service
@Transactional(timeout = 15)
public class BookingFlowService {
    private final BookingFlowRepository data;
    private final QuoteRepository quotes;
    private final PaymentRepository ledger;
    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final CurrentActorService actors;
    private final OrderJsonCodec json;
    private final BookingFlowPolicy policy;
    private final Clock clock;

    /** @param data automation SQL @param quotes commercial records
     * @param ledger net allocations @param cases parent lock/live access
     * @param orders snapshots/ownership @param actors identity
     * @param json codec
     * @param policy fixed-price rules @param clock UTC */
    public BookingFlowService(BookingFlowRepository data,
            QuoteRepository quotes,
            PaymentRepository ledger, AssessmentRepository cases,
            OrderRepository orders, CurrentActorService actors,
            OrderJsonCodec json, BookingFlowPolicy policy, Clock clock) {
        this.data = data;
        this.quotes = quotes;
        this.ledger = ledger;
        this.cases = cases;
        this.orders = orders;
        this.actors = actors;
        this.json = json;
        this.policy = policy;
        this.clock = clock;
    }

    /** @param order parent @return authorized, consistent flow view */
    public BookingFlowStatus status(long order) {
        var parent = cases.order(order, true).orElseThrow(this::missing);
        var actor = actors.current();
        if ("CUSTOMER".equals(actor.roleCode())) {
            if (orders.findOwned(order, actor.customerId(), false).isEmpty()) {
                throw missing();
            }
        } else if (actor.staffId() == null
                || !List.of("MANAGER", "COORDINATOR").contains(actor.roleCode())
                || !cases.activeStaff(actor.staffId(), actor.roleCode())) {
            throw new AccessDeniedException("Booking flow access denied.");
        } else if ("COORDINATOR".equals(actor.roleCode())
                && !cases.assigned(order, actor.staffId(),
                        CaseRole.COORDINATOR)) {
            throw missing();
        }
        return read(parent);
    }

    /** Consumer has authorized and locked the parent before calling.
     * @param order parent @return current net booking gates */
    @Transactional(propagation = Propagation.MANDATORY)
    public BookingFlowStatus internal(long order) {
        return read(cases.order(order, true).orElseThrow(this::missing));
    }

    /** @param order parent @param revision current revision
     * @return whether initial Manager approval may attach the case team */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean automaticRoad(long order, long revision) {
        return quotes.schemaReady() && data.autoRoad(order, revision);
    }

    /** Runs after approval and team allocation in the assessment transaction.
     * @param order parent @param revision Manager-reviewed revision */
    @Transactional(propagation = Propagation.MANDATORY)
    public void approved(long order, long revision) {
        requireApproval(order);
        var snapshot = json.snapshot(orders.revision(order, revision)
                .orElseThrow(this::missing).snapshot());
        submitted(snapshot, revision, data.customer(order));
    }
    /** @param order parent @param quote newly accepted quote */
    @Transactional(propagation = Propagation.MANDATORY)
    public void accepted(long order, long quote) {
        requireApproval(order);
        if (!ledger.ready()) {
            throw unavailable();
        }
        ledger.lockLedger();
        data.requestDeposit(quote);
    }

    /** @param order parent @return whether the explicit route API was used */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean modeAware(long order) {
        var parent = cases.order(order, true).orElseThrow(this::missing);
        if (parent.revisionId() == null) {
            return false;
        }
        return json.snapshot(orders.revision(order, parent.revisionId())
                .orElseThrow(this::missing).snapshot()).request()
                .getRoute() != null;
    }

    /** @param order parent @param quote candidate contract */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireSchedule(long order, long quote) {
        if (!modeAware(order)) {
            return;
        }
        if (!ledger.ready()) {
            throw unavailable();
        }
        var record = quotes.find(order, quote).orElseThrow(this::missing);
        var rows = ledger.rows("schedule", Map.of("quote", quote));
        BigDecimal deposit = record.total() == null ? null
                : policy.deposit(record.total());
        boolean valid = deposit != null && rows.size() == 2;
        for (var row : rows) {
            String stage = row.get("PaymentStage").toString();
            BigDecimal expected = "DEPOSIT".equals(stage) ? deposit
                    : "BALANCE".equals(stage) && record.total() != null
                            ? record.total().subtract(deposit) : null;
            valid &= expected != null && expected.signum() > 0
                    && expected.compareTo(
                            (BigDecimal) row.get("AmountDue")) == 0
                    && "USD".equals(row.get("Currency"))
                    && row.get("DueAt") != null
                    && ((Number) row.get("SequenceNo")).intValue()
                            == ("DEPOSIT".equals(stage) ? 1 : 2);
        }
        valid &= rows.stream().filter(r -> "DEPOSIT".equals(
                r.get("PaymentStage"))).count() == 1;
        if (!valid) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "QUOTE_DEPOSIT_SCHEDULE_INVALID",
                    "Use a 30% DEPOSIT and the remaining 70% BALANCE.");
        }
    }

    /** Manager approval and deposit creation remain in one transaction.
     * @param snapshot server-priced revision @param revision inserted revision
     * @param customer customer accepting the displayed fixed ROAD contract */
    @Transactional(propagation = Propagation.MANDATORY)
    public void submitted(OrderSnapshot snapshot, long revision,
            long customer) {
        requireApproval(snapshot.orderId());
        BigDecimal total = policy.fixedRoadTotal(snapshot);
        if (total == null) {
            return;
        }
        if (!quotes.schemaReady() || !ledger.ready()) {
            throw unavailable();
        }
        if (data.quote(revision) != null) {
            return;
        }
        BigDecimal deposit = policy.deposit(total);
        OffsetDateTime now = OffsetDateTime.now(clock).withNano(0);
        var due = snapshot.request().getPreferredDepartureAt();
        var request = new QuotePrepareRequest(new QuoteCommand(
                snapshot.sourceOrderVersion(), snapshot.operationId()),
                revision,
                now.plusDays(7), "ROAD-AUTO-V1",
                "Fixed ROAD contract accepted by customer submission. "
                        + "Manager approved; deposit 30%; "
                        + "balance before departure.",
                "Changes/cancellation require reviewed settlement.",
                List.of(new QuoteSchedule(1, "DEPOSIT", deposit.toPlainString(),
                        now), new QuoteSchedule(2, "BALANCE",
                        total.subtract(deposit).toPlainString(), due)),
                List.of());
        var line = new QuoteLine("ROAD_TRANSPORT", "TRANSPORTATION",
                "Fixed ROAD and Hub transfer price", "SHIPMENT", "1.000",
                total.toPlainString(), total.toPlainString(), "PRICED",
                List.of());
        var route = snapshot.routeMatch();
        var pricing = new QuoteSnapshot(1, request, List.of(line), List.of(),
                List.of(), new QuoteCalculationContext(null,
                        route.estimatedRoadKm(), BigDecimal.ZERO, null, null,
                        null, null, null, List.of()));
        long quote = quotes.prepare(snapshot.orderId(), null, pricing);
        quotes.transition(quote, QuoteStatus.ISSUED, null, null);
        quotes.transition(quote, QuoteStatus.ACCEPTED, null, customer);
        accepted(snapshot.orderId(), quote);
        quotes.audit(snapshot.orderId(), actors.current().userId(),
                "ROAD_AUTO_ACCEPT", json.write(Map.of("schemaVersion", 1,
                        "operationId", snapshot.operationId(), "quoteId", quote,
                        "revisionId", revision, "acceptance",
                        "MANAGER_APPROVED_SUBMISSION",
                        "total", total.toPlainString())));
    }

    private BookingFlowStatus read(CaseOrder parent) {
        OrderSnapshot snapshot = parent.revisionId() == null ? null
                : json.snapshot(orders.revision(parent.orderId(),
                        parent.revisionId()).orElseThrow(this::missing)
                        .snapshot());
        String mode = snapshot == null || snapshot.routeMatch() == null
                || snapshot.routeMatch().transportMode() == null ? null
                : snapshot.routeMatch().transportMode().name();
        if ("SUBMITTED".equals(parent.status())) {
            return new BookingFlowStatus(parent.orderId(), mode,
                    parent.status(), false, null, false, "0.00", "0.00",
                    false, false, "AWAITING_MANAGER_APPROVAL");
        }
        Long id = parent.revisionId() == null || !quotes.schemaReady() ? null
                : data.quote(parent.revisionId());
        QuoteSnapshot quote = id == null ? null
                : quotes.find(parent.orderId(), id)
                        .orElseThrow(this::missing);
        boolean accepted = quote != null
                && quote.status() == QuoteStatus.ACCEPTED
                && quote.acceptedAt() != null && quote.total() != null
                && quote.total().signum() > 0;
        boolean published = quote != null && quote.issuedAt() != null
                && List.of(QuoteStatus.ISSUED, QuoteStatus.ACCEPTED)
                        .contains(quote.status());
        BigDecimal due = BigDecimal.ZERO.setScale(2);
        BigDecimal paid = BigDecimal.ZERO.setScale(2);
        boolean hasDeposit = false;
        boolean refunds = false;
        if (published) {
            if (!ledger.ready()) {
                throw unavailable();
            }
            ledger.lockLedger();
            var rows = ledger.rows("balances", Map.of("quote", id, "asOf",
                    Timestamp.from(clock.instant())));
            for (var row : rows) {
                if ("DEPOSIT".equals(row.get("PaymentStage"))) {
                    hasDeposit = true;
                    due = due.add((BigDecimal) row.get("AmountDue"));
                    if (accepted && "USD".equals(row.get("Currency"))) {
                        paid = paid.add((BigDecimal) row.get("PaidAmount"));
                    }
                }
            }
            refunds = ledger.number("pendingRefunds", Map.of("quote", id)) > 0;
        }
        boolean satisfied = accepted && hasDeposit && due.signum() > 0
                && paid.compareTo(due) >= 0;
        String block = !accepted ? (!published ? "AWAITING_COORDINATOR_QUOTE"
                : "QUOTE_ACCEPTANCE_REQUIRED") : !hasDeposit
                ? "DEPOSIT_SCHEDULE_REQUIRED" : !satisfied ? "DEPOSIT_REQUIRED"
                : refunds ? "REFUND_PENDING"
                : !List.of("APPROVED", "IN_PREPARATION")
                        .contains(parent.status()) ? "ORDER_NOT_ASSIGNABLE"
                : null;
        return new BookingFlowStatus(parent.orderId(), mode, parent.status(),
                accepted, !published || quote.total() == null ? null
                        : quote.total().toPlainString(), true,
                due.toPlainString(),
                paid.toPlainString(), satisfied, block == null, block);
    }

    private void requireApproval(long order) {
        var parent = cases.order(order, true).orElseThrow(this::missing);
        if (!List.of("APPROVED", "IN_PREPARATION", "CONFIRMED",
                "IN_TRANSIT", "DELIVERED").contains(parent.status())) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "AWAITING_MANAGER_APPROVAL",
                    "Manager approval is required before opening payments.");
        }
    }
    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND,
                "BOOKING_FLOW_NOT_FOUND", "Booking flow is unavailable.");
    }

    private OrderException unavailable() {
        return new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                "PAYMENT_SCHEMA_PENDING", "Deploy quotation/payment schema.");
    }
}
