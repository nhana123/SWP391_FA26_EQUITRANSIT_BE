/*
 * QuotationService
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.service;


import com.equitransit.backend.dto.quote.QuoteActionRequest;
import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.dto.quote.QuotePage;
import com.equitransit.backend.dto.quote.QuotePrepareRequest;
import com.equitransit.backend.dto.quote.QuoteResponse;
import com.equitransit.backend.dto.quote.QuoteSummary;
import com.equitransit.backend.dto.quote.SupplierOfferReceipt;
import com.equitransit.backend.dto.quote.SupplierOfferRequest;
import com.equitransit.backend.entity.CaseOrder;
import com.equitransit.backend.entity.CaseRole;
import com.equitransit.backend.entity.quote.QuoteAction;
import com.equitransit.backend.entity.quote.QuoteRecord;
import com.equitransit.backend.entity.quote.QuoteSnapshot;
import com.equitransit.backend.entity.quote.QuoteStatus;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.json.OrderJsonCodec;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.repository.OrderRepository;
import com.equitransit.backend.repository.QuoteRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;


/** Parent-first locks serialize quote commands with F03/F04 commitments. */
@Service
@Validated
@PreAuthorize("isAuthenticated()")
public class QuotationService {

    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final QuoteRepository quotes;
    private final QuotePricingPolicy pricing;
    private final OrderCommitmentPolicy commitments;
    private final CurrentActorService actors;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * @param cases shared parent lock and live assignment access
     * @param orders immutable F03 revision storage
     * @param quotes commercial persistence
     * @param pricing sourced F05 pricing
     * @param commitments protected accepted/operational commitments
     * @param actors authenticated live identities
     * @param json canonical input fingerprinting
     * @param mapper strict typed snapshot decoder
     * @param clock server clock
     */
    public QuotationService(AssessmentRepository cases, OrderRepository orders,
            QuoteRepository quotes, QuotePricingPolicy pricing,
            OrderCommitmentPolicy commitments, CurrentActorService actors,
            OrderJsonCodec json, ObjectMapper mapper, Clock clock) {
        this.cases = cases;
        this.orders = orders;
        this.quotes = quotes;
        this.pricing = pricing;
        this.commitments = commitments;
        this.actors = actors;
        this.json = json;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * @param id authorized order
     * @param page zero-based page
     * @param size bounded page size
     * @return customer-filtered or authorized staff quote headers
     */
    @Transactional(timeout = 15)
    public QuotePage list(@Positive long id, @Min(0) int page,
            @Min(1) @Max(100) int size) {
        CaseOrder order = parent(id);
        CurrentActor actor = access(order, false);
        boolean customer = "CUSTOMER".equals(actor.roleCode());

        return new QuotePage(quotes.list(id, customer, page, size), page,
                size, quotes.count(id, customer));
    }

    /**
     * @param id authorized order
     * @param quote parent-bound quote
     * @return public-safe details; never supplier purchase costs
     */
    @Transactional(timeout = 15)
    public QuoteResponse get(@Positive long id, @Positive long quote) {
        CaseOrder order = parent(id);
        CurrentActor actor = access(order, false);
        QuoteRecord record = record(id, quote);

        if ("CUSTOMER".equals(actor.roleCode())
                && (record.issuedAt() == null
                        || record.status() == QuoteStatus.SUPERSEDED)) {
            throw missing();
        }
        return response(order, record);
    }

    /**
     * Creates a separate version; no update endpoint can mutate issued items.
     * @param id locked order
     * @param command full evidenced quote preparation
     * @return immutable command completion receipt
     */
    @Transactional(timeout = 15)
    public QuoteResponse prepare(@Positive long id,
            @Valid QuotePrepareRequest command) {
        QuotePrepareRequest request = normalize(command);
        CaseOrder order = parent(id);
        CurrentActor actor = access(order, true);
        String hash = fingerprint("PREPARE", request);
        var replay = replay(id, actor, request.command(), hash,
                QuoteResponse.class);

        if (replay.isPresent()) {
            return replay.get();
        }
        editable(order, request.command(), request.revisionId());
        future(request.validUntil());
        validateSchedule(request);
        var revision = orders.revision(id, request.revisionId())
                .orElseThrow(this::missing);
        var snapshot = pricing.prepare(id, json.snapshot(revision.snapshot()),
                request);
        long quote = quotes.prepare(id, actor.staffId(), snapshot);

        touch(order);
        QuoteResponse receipt = response(parent(id), record(id, quote));

        finish(id, actor, request.command(), "PREPARE", hash, receipt);
        return receipt;
    }

    /**
     * @param id locked order
     * @param request cost and retail price with supplier evidence
     * @return saved immutable offer key and new parent version
     */
    @Transactional(timeout = 15)
    public SupplierOfferReceipt offer(@Positive long id,
            @Valid SupplierOfferRequest request) {
        CaseOrder order = parent(id);
        CurrentActor actor = access(order, true);
        String hash = fingerprint("OFFER", request);
        var replay = replay(id, actor, request.command(), hash,
                SupplierOfferReceipt.class);

        if (replay.isPresent()) {
            return replay.get();
        }
        editable(order, request.command(), request.revisionId());
        future(request.validUntil());
        if (new BigDecimal(request.quantity()).signum() <= 0
                || new BigDecimal(request.quantity())
                        .setScale(4, RoundingMode.HALF_UP)
                        .precision() > 18
                || new BigDecimal(request.purchaseUnitPrice())
                        .setScale(4, RoundingMode.HALF_UP)
                        .precision() > 18
                || new BigDecimal(request.retailUnitPrice())
                        .setScale(4, RoundingMode.HALF_UP)
                        .precision() > 18) {
            throw conflict("QUOTE_SOURCE_INVALID", "Invalid offer amount.");
        }
        if ((request.includesStall() || !request.includedCharges().isEmpty())
                && !"AIRFARE".equals(request.serviceCode().name())) {
            throw conflict("QUOTE_SOURCE_INVALID",
                    "Bundled charges require an airfare offer.");
        }
        for (var code : request.includedCharges()) {
            if (!List.of("AIRPORT_HANDLING", "TERMINAL_HANDLING",
                    "CUSTOMS_DECLARATION").contains(code.name())) {
                throw conflict("QUOTE_SOURCE_INVALID",
                        "Unsupported included charge.");
            }
        }
        long offer = quotes.offer(actor.staffId(), request,
                OffsetDateTime.now(clock));

        touch(order);
        var receipt = new SupplierOfferReceipt(offer, parent(id).version());

        finish(id, actor, request.command(), "OFFER", hash, receipt);
        return receipt;
    }

    /**
     * @param id locked parent order
     * @param quote quote within that order
     * @param request explicit transition with version and retry UUID
     * @return immutable completion receipt, including on duplicate accept
     */
    @Transactional(timeout = 15)
    public QuoteResponse action(@Positive long id, @Positive long quote,
            @Valid QuoteActionRequest request) {
        CaseOrder order = parent(id);
        CurrentActor actor = access(order, false);
        boolean customer = "CUSTOMER".equals(actor.roleCode());
        boolean manager = "MANAGER".equals(actor.roleCode());

        if ((!customer && !manager)
                || (request.action() == QuoteAction.ACCEPT && !customer)
                || (!List.of(QuoteAction.ACCEPT, QuoteAction.REJECT)
                        .contains(request.action()) && !manager)) {
            throw new AccessDeniedException("Access is denied.");
        }
        String hash = fingerprint("ACTION_" + quote, request);
        var replay = replay(id, actor, request.command(), hash,
                QuoteResponse.class);

        if (replay.isPresent()) {
            return replay.get();
        }
        QuoteRecord record = record(id, quote);

        version(order, request.command());
        if (!"APPROVED".equals(order.status())
                || !java.util.Objects.equals(order.revisionId(),
                        record.revision())) {
            throw conflict("QUOTE_REVISION_CONFLICT",
                    "Quote does not match the approved current revision.");
        }
        if (customer && record.status() != QuoteStatus.ISSUED) {
            throw conflict("QUOTE_STATE_CONFLICT",
                    "Only an issued quotation can receive your response.");
        }
        QuoteStatus target = transition(record, request.action(), customer);

        if (target == QuoteStatus.ACCEPTED
                && commitments.protectedOrder(id)) {
            throw conflict("QUOTE_ORDER_PROTECTED",
                    "Another commitment already protects this order.");
        }
        if (target == QuoteStatus.APPROVED || target == QuoteStatus.ISSUED) {
            requirePublishable(id, order, record);
        }
        quotes.transition(quote, target, actor.staffId(), actor.customerId());
        touch(order);
        QuoteResponse receipt = response(parent(id), record(id, quote));

        finish(id, actor, request.command(), request.action().name(), hash,
                receipt);
        return receipt;
    }

    private QuoteStatus transition(QuoteRecord record, QuoteAction action,
            boolean customer) {
        if (action == QuoteAction.EXPIRE) {
            if (record.validUntil() == null
                    || record.validUntil().isAfter(OffsetDateTime.now(clock))
                    || !List.of(QuoteStatus.DRAFT, QuoteStatus.APPROVED,
                            QuoteStatus.ISSUED).contains(record.status())) {
                throw conflict("QUOTE_STATE_CONFLICT", "Quote is not expired.");
            }
            return QuoteStatus.EXPIRED;
        }
        future(record.validUntil());
        return switch (action) {
            case APPROVE -> requireState(record, QuoteStatus.DRAFT,
                    QuoteStatus.APPROVED);
            case ISSUE -> requireState(record, QuoteStatus.APPROVED,
                    QuoteStatus.ISSUED);
            case ACCEPT -> requireState(record, QuoteStatus.ISSUED,
                    QuoteStatus.ACCEPTED);
            case REJECT -> {
                if (!customer && !List.of(QuoteStatus.DRAFT,
                        QuoteStatus.APPROVED).contains(record.status())) {
                    throw conflict("QUOTE_STATE_CONFLICT",
                            "Only unissued preparation can be rejected.");
                }
                yield QuoteStatus.REJECTED;
            }
            default -> throw conflict("QUOTE_STATE_CONFLICT",
                    "Unsupported quotation transition.");
        };
    }

    private void requirePublishable(long id, CaseOrder order,
            QuoteRecord record) {
        QuoteSnapshot snapshot = snapshot(record);

        if (!snapshot.blockers().isEmpty() || record.total() == null) {
            throw conflict("QUOTE_PRICING_PENDING",
                    "Resolve all required pricing evidence before approval.");
        }
        validateSchedule(snapshot.request());
        BigDecimal scheduled = snapshot.request().paymentSchedule().stream()
                .map(s -> new BigDecimal(s.amountDue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (scheduled.compareTo(record.total()) != 0) {
            throw conflict("QUOTE_SCHEDULE_INVALID",
                    "Payment schedule must equal the quotation total.");
        }
        pricing.requireSources(snapshot);
        var revision = orders.revision(id, order.revisionId())
                .orElseThrow(this::missing);
        var current = pricing.prepare(id, json.snapshot(revision.snapshot()),
                snapshot.request());

        if (!current.blockers().isEmpty()
                || !current.lines().equals(snapshot.lines())
                || !current.calculationContext().equals(
                        snapshot.calculationContext())
                || record.total().compareTo(QuoteRepository.total(snapshot))
                        != 0
                || record.total().compareTo(quotes.itemTotal(record.id())) != 0
                || !quotes.schedule(record.id()).equals(
                        snapshot.request().paymentSchedule())) {
            throw conflict("QUOTE_PRICING_CHANGED",
                    "Pricing or evidence changed; prepare a new version.");
        }
    }

    private void validateSchedule(QuotePrepareRequest request) {
        BigDecimal sum = BigDecimal.ZERO;
        int sequence = 1;

        for (var item : request.paymentSchedule()) {
            BigDecimal value = new BigDecimal(item.amountDue());

            if (item.sequenceNo() != sequence++ || value.signum() <= 0
                    || value.precision() > 18) {
                throw conflict("QUOTE_SCHEDULE_INVALID",
                        "Use positive consecutive payment stages.");
            }
            future(item.dueAt());
            sum = sum.add(value);
        }
        if (sum.precision() > 18) {
            throw conflict("QUOTE_SCHEDULE_INVALID", "Schedule is too large.");
        }
    }

    private QuotePrepareRequest normalize(QuotePrepareRequest request) {
        var schedule = request.paymentSchedule().stream().map(s ->
                new com.equitransit.backend.dto.quote.QuoteSchedule(
                        s.sequenceNo(), s.paymentStage(),
                        new BigDecimal(s.amountDue())
                                .setScale(2, RoundingMode.HALF_UP)
                                .toPlainString(),
                        s.dueAt().withOffsetSameInstant(
                                java.time.ZoneOffset.UTC).withNano(0)))
                .toList();

        return new QuotePrepareRequest(request.command(),
                request.revisionId(), request.validUntil()
                        .withOffsetSameInstant(java.time.ZoneOffset.UTC)
                        .withNano(0), request.termsVersion(), request.terms(),
                request.cancellationTerms(), schedule, request.assessments());
    }

    private void editable(CaseOrder order, QuoteCommand command,
            long revision) {
        version(order, command);
        if (!"APPROVED".equals(order.status())
                || order.revisionId() == null || order.revisionId() != revision
                || commitments.protectedOrder(order.orderId())) {
            throw conflict("QUOTE_ORDER_PROTECTED",
                    "Prepare only the current approved uncommitted revision.");
        }
    }

    private CurrentActor access(CaseOrder order, boolean prepare) {
        CurrentActor actor = actors.current();
        String role = actor.roleCode();

        if ("CUSTOMER".equals(role) && !prepare) {
            if (!quotes.activeAccount(actor.userId(), role)) {
                throw new AccessDeniedException("Access is denied.");
            }
            if (orders.findOwned(order.orderId(), actor.customerId(), false)
                    .isEmpty()) {
                throw missing();
            }
            return actor;
        }
        if (actor.staffId() == null
                || !cases.activeStaff(actor.staffId(), role)) {
            throw new AccessDeniedException("Access is denied.");
        }
        if ("MANAGER".equals(role)) {
            return actor;
        }
        if ("COORDINATOR".equals(role)
                && "APPROVED".equals(order.status())
                && cases.assigned(order.orderId(), actor.staffId(),
                        CaseRole.COORDINATOR)) {
            return actor;
        }
        throw new AccessDeniedException("Access is denied.");
    }

    private CaseOrder parent(long id) {
        if (!quotes.schemaReady()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "QUOTE_SCHEMA_PENDING",
                    "Deploy quotation migration before using this feature.");
        }
        return cases.order(id, true).orElseThrow(this::missing);
    }

    private QuoteRecord record(long order, long quote) {
        return quotes.find(order, quote).orElseThrow(this::missing);
    }

    private QuoteSnapshot snapshot(QuoteRecord quote) {
        try {
            if (quote.snapshot() == null) {
                throw missing();
            }
            QuoteSnapshot result = mapper.readValue(quote.snapshot(),
                    QuoteSnapshot.class);

            if (result.schemaVersion() != 1) {
                throw missing();
            }
            return result;
        } catch (JsonProcessingException error) {
            throw conflict("QUOTE_DATA_UNSUPPORTED",
                    "This quote needs a supported pricing snapshot.");
        }
    }

    private QuoteResponse response(CaseOrder order, QuoteRecord record) {
        var saved = snapshot(record);
        var request = saved.request();
        var summary = new QuoteSummary(record.id(), record.revision(),
                record.number(), record.version(), record.status(),
                record.total() == null ? null : record.total().toPlainString(),
                record.validUntil());

        return new QuoteResponse(summary, order.version(), "USD",
                request.termsVersion(), request.terms(),
                request.cancellationTerms(), record.issuedAt(),
                record.acceptedAt(), saved.lines(),
                quotes.scheduleViews(record.id()),
                saved.blockers());
    }

    private void version(CaseOrder order, QuoteCommand command) {
        if (!order.version().equalsIgnoreCase(command.recordVersion())) {
            throw conflict("ORDER_VERSION_CONFLICT",
                    "Request changed. Reload before acting on a quotation.");
        }
    }

    private void touch(CaseOrder order) {
        if (!cases.transition(order, order.status())) {
            throw conflict("ORDER_VERSION_CONFLICT", "Request changed.");
        }
    }

    private QuoteStatus requireState(QuoteRecord record, QuoteStatus previous,
            QuoteStatus next) {
        if (record.status() != previous) {
            throw conflict("QUOTE_STATE_CONFLICT",
                    "The quotation is not in the required state.");
        }
        return next;
    }

    private void future(OffsetDateTime value) {
        if (value == null || !value.isAfter(OffsetDateTime.now(clock))) {
            throw conflict("QUOTE_EXPIRED", "Quotation evidence has expired.");
        }
    }

    private String fingerprint(String action, Object request) {
        return TokenTools.hash(json.canonical(Map.of("action", action,
                "request", request)));
    }

    private <T> Optional<T> replay(long order, CurrentActor actor,
            QuoteCommand command, String hash, Class<T> type) {
        var encoded = quotes.replay(order, actor.userId(),
                command.operationId());

        if (encoded.isEmpty()) {
            return Optional.empty();
        }
        try {
            var node = mapper.readTree(encoded.get());

            if (node.path("schemaVersion").asInt() != 1
                    || !hash.equals(node.path("requestHash").asText())) {
                throw conflict("QUOTE_RETRY_CONFLICT",
                        "This retry identifier belongs to another input.");
            }
            return Optional.of(mapper.treeToValue(node.get("response"), type));
        } catch (JsonProcessingException error) {
            throw conflict("QUOTE_DATA_UNSUPPORTED",
                    "The operation receipt cannot be read.");
        }
    }

    private void finish(long order, CurrentActor actor, QuoteCommand command,
            String action, String hash, Object receipt) {
        quotes.audit(order, actor.userId(), action, json.write(Map.of(
                "schemaVersion", 1, "operationId", command.operationId(),
                "requestHash", hash, "response", receipt)));
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND",
                "The requested quotation was not found.");
    }

    private OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }
}
