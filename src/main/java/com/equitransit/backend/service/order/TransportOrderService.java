/*
 * TransportOrderService
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Own transactional draft, submission and immutable revision flows.
 */
package com.equitransit.backend.service.order;


import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.equitransit.backend.dto.order.request.OrderDraftRequest;
import com.equitransit.backend.dto.order.request.OrderDraftUpdateRequest;
import com.equitransit.backend.dto.order.request.OrderRevisionRequest;
import com.equitransit.backend.dto.order.request.OrderSubmitRequest;
import com.equitransit.backend.dto.horse.response.HorseResponse;
import com.equitransit.backend.dto.order.response.OrderChangeResponse;
import com.equitransit.backend.dto.order.response.OrderPageResponse;
import com.equitransit.backend.dto.order.response.OrderResponse;
import com.equitransit.backend.dto.order.response.OrderRevisionResponse;
import com.equitransit.backend.dto.order.response.OrderSnapshot;
import com.equitransit.backend.entity.order.OrderChangeRecord;
import com.equitransit.backend.entity.order.OrderHorseData;
import com.equitransit.backend.entity.order.OrderRevisionRecord;
import com.equitransit.backend.entity.order.OrderServiceIntent;
import com.equitransit.backend.entity.order.TransportOrderRecord;
import com.equitransit.backend.entity.pricing.StallFitResult;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.service.horse.HorseService;
import com.equitransit.backend.service.pricing.ServicePreferencePolicy;
import com.equitransit.backend.service.activity.AuditWriter;
import com.equitransit.backend.repository.activity.ActivityRepository;


/** A submitted revision is never updated or deleted by this service. */
@Service
@Validated
@PreAuthorize("@domainAccessPolicy.isCustomer()")
public class TransportOrderService {

    private final OrderRepository orders;
    private final CurrentActorService actors;
    private final HorseService horses;
    private final OrderDraftPolicy drafts;
    private final OrderServiceIntentFactory intents;
    private final OrderCommitmentPolicy commitments;
    private final OrderJsonCodec json;
    private final Clock clock;
    private final ServicePreferencePolicy selections;
    private final AuditWriter activity;
    private final ActivityRepository events;

    /**
     * Creates the customer request lifecycle facade.
     * @param orders the owner-scoped persistence adapter
     * @param actors the live domain identity resolver
     * @param horses the authorized horse-profile facade
     * @param drafts the typed input policy
     * @param intents the unpriced request-intent factory
     * @param commitments the finance/operation protection boundary
     * @param json the supported JSON schema codec
     * @param clock the application's UTC clock
     * @param selections actual source-backed service selection policy
     * @param activity append-only producer facade
     * @param events feature deployment check
     */
    public TransportOrderService(OrderRepository orders,
            CurrentActorService actors, HorseService horses,
            OrderDraftPolicy drafts, OrderServiceIntentFactory intents,
            OrderCommitmentPolicy commitments, OrderJsonCodec json,
            Clock clock, ServicePreferencePolicy selections,
            AuditWriter activity,
            ActivityRepository events) {
        this.orders = orders;
        this.actors = actors;
        this.horses = horses;
        this.drafts = drafts;
        this.intents = intents;
        this.commitments = commitments;
        this.json = json;
        this.clock = clock;
        this.selections = selections;
        this.activity = activity;
        this.events = events;
    }

    /**
     * Creates a partial draft with no fake submitted timestamp or snapshot.
     * @param request the editable journey fields
     * @return the saved draft with its rowversion
     */
    @Transactional
    public OrderResponse create(@Valid OrderDraftRequest request) {
        OrderDraftRequest draft = drafts.normalize(request);
        long owner = actors.current().customerId();
        String number = "TR-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 24);
        long id;

        checkHorse(draft);
        try {
            id = orders.create(owner, number, drafts.writeData(draft));
        } catch (DataIntegrityViolationException error) {
            throw conflict("ORDER_CONFLICT",
                    "The request conflicts with an existing record.");
        }
        return response(owned(id, false));
    }

    /**
     * Replaces a draft only; submitted data uses the revision endpoint.
     * @param id the owned request key
     * @param request the full draft and last observed version
     * @return the saved current view
     */
    @Transactional
    public OrderResponse saveDraft(@Positive long id,
            @Valid OrderDraftUpdateRequest request) {
        TransportOrderRecord order = owned(id, true);
        OrderDraftRequest draft = drafts.normalize(request);

        expectedVersion(order, request.getRecordVersion());
        requireState(order, "DRAFT");
        requireUncommitted(order);
        checkHorse(draft);
        replace(order, draft, "DRAFT", null);
        return response(owned(id, false));
    }

    /**
     * Reads only the current owned request view, never another customer's ID.
     * @param id the positive request key
     * @return current metadata and the editable payload
     */
    @Transactional(readOnly = true)
    public OrderResponse get(@Positive long id) {
        return response(owned(id, false));
    }

    /**
     * Lists only current metadata scoped to the live customer.
     * @param page the zero-based page
     * @param size the bounded page size
     * @return explicit pagination data
     */
    @Transactional(readOnly = true)
    public OrderPageResponse list(@Min(0) int page,
            @Min(1) @Max(100) int size) {
        long owner = actors.current().customerId();
        List<OrderResponse> items = orders.list(owner, page, size).stream()
                .map(this::response).toList();
        long total = orders.count(owner);

        return new OrderPageResponse(items, page, size, total,
                total / size + (total % size == 0 ? 0 : 1));
    }

    /**
     * Atomically submits the saved draft and returns the immutable revision.
     * @param id the owned request key
     * @param command the expected version and retry identifier
     * @return the exact revision, including on a repeated successful command
     */
    @Transactional
    public OrderRevisionResponse submit(@Positive long id,
            @Valid OrderSubmitRequest command) {
        TransportOrderRecord order = owned(id, true);
        String hash = fingerprint("SUBMIT", command.recordVersion(),
                Map.of());
        Optional<OrderRevisionResponse> replay = replay(order,
                command.operationId(), "SUBMIT", hash);
        OrderDraftRequest draft;

        if (replay.isPresent()) {
            return replay.get();
        }
        expectedVersion(order, command.recordVersion());
        requireState(order, "DRAFT");
        requireUncommitted(order);
        draft = json.readDraft(order.draftPayload());
        if (draft == null) {
            throw conflict("ORDER_DRAFT_REQUIRED",
                    "Save a supported draft before submission.");
        }
        return append(order, draft, command.operationId(),
                "SUBMIT", hash, null);
    }

    /**
     * Creates an immutable revision before protected commitments.
     * @param id the owned request key
     * @param command the replacement, reason, expected version and retry key
     * @return the immutable newly submitted revision or its safe replay
     */
    @Transactional
    public OrderRevisionResponse revise(@Positive long id,
            @Valid OrderRevisionRequest command) {
        TransportOrderRecord order = owned(id, true);
        OrderDraftRequest draft = drafts.normalize(command);
        String reason = command.getChangeReason().trim();
        String hash = fingerprint("REVISE", command.getRecordVersion(),
                Map.of("request", draft, "reason", reason));
        Optional<OrderRevisionResponse> replay = replay(order,
                command.getOperationId(), "REVISE", hash);

        if (replay.isPresent()) {
            return replay.get();
        }
        expectedVersion(order, command.getRecordVersion());
        if (!"SUBMITTED".equals(order.orderStatus())
                && !"CHANGES_REQUESTED".equals(order.orderStatus())
                && !"APPROVED".equals(order.orderStatus())) {
            throw conflict("ORDER_NOT_EDITABLE",
                    "This state does not permit direct revision.");
        }
        requireUncommitted(order);
        return append(order, draft, command.getOperationId(), "REVISE",
                hash, reason);
    }

    /**
     * Records an unapplied proposal after acceptance/authorization/departure.
     * This is the intake boundary for F13, not an approval or itinerary change.
     * @param id the owned protected request key
     * @param command the proposed replacement and reason
     * @return the pending proposal receipt, including safe retries
     */
    @Transactional
    public OrderChangeResponse requestChange(@Positive long id,
            @Valid OrderRevisionRequest command) {
        TransportOrderRecord order = owned(id, true);
        OrderDraftRequest draft = drafts.normalize(command);
        String reason = command.getChangeReason().trim();
        String hash = fingerprint("CHANGE", command.getRecordVersion(),
                Map.of("request", draft, "reason", reason));
        Optional<OrderChangeRecord> replay = orders.change(id,
                command.getOperationId(), null);
        long change;

        if (replay.isPresent()) {
            if (!hash.equals(orders.changeHash(
                    replay.get().changeRequestId()))) {
                throw retryConflict();
            }
            return changeResponse(replay.get());
        }
        expectedVersion(order, command.getRecordVersion());
        if (order.currentRevisionId() == null
                || !commitments.protectedOrder(id)) {
            throw conflict("ORDER_DIRECT_REVISION_REQUIRED",
                    "Use the revision endpoint before protected commitment.");
        }
        if (List.of("COMPLETED", "DELIVERED", "CANCELLED", "CANCELED",
                "CLOSED", "REJECTED").contains(order.orderStatus())) {
            throw conflict("ORDER_NOT_EDITABLE", "This order is closed.");
        }
        if (orders.hasPendingChange(id)) {
            throw conflict("ORDER_CHANGE_PENDING",
                    "A proposal is already awaiting staff handling.");
        }
        drafts.requireComplete(draft, false);
        checkHorse(draft);
        change = orders.insertChange(order, actors.current().userId(), reason,
                json.write(Map.of("schemaVersion", 1, "operationId",
                        command.getOperationId(), "operationType", "CHANGE",
                        "requestHash", hash, "request", draft)));
        return changeResponse(orders.change(id, null, change).orElseThrow());
    }

    /**
     * Reads a proposal receipt only after resolving ownership of its parent.
     * @param id the owned request key
     * @param change the proposal key
     * @return its safe receipt
     */
    @Transactional(readOnly = true)
    public OrderChangeResponse change(@Positive long id,
            @Positive long change) {
        owned(id, false);
        return changeResponse(orders.change(id, null, change).orElseThrow(() ->
                notFound()));
    }

    /**
     * Reads an immutable revision through its owned parent.
     * @param id the order key
     * @param revision the revision key
     * @return the captured snapshot, not the current horse profile
     */
    @Transactional(readOnly = true)
    public OrderRevisionResponse revision(@Positive long id,
            @Positive long revision) {
        owned(id, false);
        return revisionResponse(orders.revision(id, revision)
                .orElseThrow(this::notFound));
    }

    /**
     * Reads bounded history while retaining explicit current revision pointers.
     * @param id the owned request key
     * @param page the zero-based page
     * @param size the bounded page size
     * @return the immutable history page
     */
    @Transactional(readOnly = true)
    public List<OrderRevisionResponse> revisions(@Positive long id,
            @Min(0) int page, @Min(1) @Max(100) int size) {
        owned(id, false);
        return orders.revisions(id, page, size).stream()
                .map(this::revisionResponse).toList();
    }

    private OrderRevisionResponse append(TransportOrderRecord order,
            OrderDraftRequest draft, UUID operation, String type,
            String hash, String reason) {
        HorseResponse horse;
        List<OrderServiceIntent> services;
        int number;
        OffsetDateTime at = OffsetDateTime.now(clock).withNano(0);
        OrderSnapshot snapshot;
        long revision;
        StallFitResult fit;

        drafts.requireComplete(draft, true);
        horse = horses.get(draft.getHorseId());
        fit = selections.validate(draft.getServices(), horse,
                draft.getDeliveryDeadline());
        services = intents.requested(draft.getServices(), fit);
        number = orders.nextRevision(order.orderId());
        snapshot = new OrderSnapshot(1, operation, type, hash,
                order.recordVersion(), order.orderId(), number, at,
                draft, horse, services);
        commitments.invalidatePreparation(order.orderId());
        revision = orders.insertRevision(order.orderId(), number,
                json.write(snapshot), reason, at);
        orders.saveHorse(order.orderId(), new OrderHorseData(horse.horseId(),
                horse.microchipId(), horse.sexCode() == null ? null
                        : horse.sexCode().name(),
                horse.weightKg(), horse.heightCm(), horse.bodyWidthCm(),
                horse.measuredAt() == null ? null
                        : horse.measuredAt().toLocalDateTime(),
                draft.getSpecialNeeds()));
        orders.saveIntents(revision, services);
        replace(order, draft, "SUBMITTED", revision);
        if (events.ready()) {
            activity.append(order.orderId(),
                    com.equitransit.backend.entity.activity.ActivityCode
                            .SUBMITTED, operation, null);
        }
        return revisionResponse(orders.revision(order.orderId(), revision)
                .orElseThrow());
    }

    private Optional<OrderRevisionResponse> replay(TransportOrderRecord order,
            UUID operation, String type, String hash) {
        Optional<OrderRevisionRecord> record = orders.operation(
                order.orderId(), operation);
        OrderRevisionResponse existing;

        if (record.isEmpty()) {
            return Optional.empty();
        }
        existing = revisionResponse(record.get());

        if (!type.equals(existing.snapshot().operationType())
                || !hash.equals(existing.snapshot().requestHash())) {
            throw retryConflict();
        }
        return Optional.of(existing);
    }

    private void replace(TransportOrderRecord order, OrderDraftRequest draft,
            String status, Long revision) {
        if (!orders.replace(order, drafts.writeData(draft), status, revision)) {
            throw versionConflict();
        }
    }

    private void checkHorse(OrderDraftRequest draft) {
        if (draft.getHorseId() != null) {
            horses.get(draft.getHorseId());
        }
    }

    private void requireUncommitted(TransportOrderRecord order) {
        if (commitments.protectedOrder(order.orderId())) {
            throw conflict("ORDER_CHANGE_REQUEST_REQUIRED",
                    "Accepted or operational commitments require a "
                            + "controlled change request.");
        }
    }

    private void requireState(TransportOrderRecord order, String required) {
        if (!required.equals(order.orderStatus())) {
            throw conflict("ORDER_NOT_EDITABLE",
                    "Use the endpoint appropriate to the current state.");
        }
    }

    private void expectedVersion(TransportOrderRecord order, String expected) {
        if (!order.recordVersion().equalsIgnoreCase(expected)) {
            throw versionConflict();
        }
    }

    private String fingerprint(String type, String version, Object fields) {
        return TokenTools.hash(json.canonical(Map.of("type", type,
                "version", version.toLowerCase(Locale.ROOT),
                "fields", fields)));
    }

    private TransportOrderRecord owned(long id, boolean lock) {
        return orders.findOwned(id, actors.current().customerId(), lock)
                .orElseThrow(this::notFound);
    }

    private OrderResponse response(TransportOrderRecord order) {
        return new OrderResponse(order.orderId(), order.orderNumber(),
                order.orderStatus(), order.currency(), order.recordVersion(),
                order.currentRevisionId(), order.currentPlanId(),
                order.createdAt(), order.updatedAt(),
                json.readDraft(order.draftPayload()));
    }

    private OrderRevisionResponse revisionResponse(OrderRevisionRecord record) {
        return new OrderRevisionResponse(record.revisionId(), record.orderId(),
                record.revisionNo(), record.changeReason(),
                record.submittedAt(),
                json.snapshot(record.snapshot()));
    }

    private OrderChangeResponse changeResponse(OrderChangeRecord record) {
        return new OrderChangeResponse(record.changeRequestId(),
                record.orderId(),
                record.baseRevisionId(), record.status(), record.createdAt());
    }

    private OrderException notFound() {
        return new OrderException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                "The requested record was not found.");
    }

    private OrderException versionConflict() {
        return conflict("ORDER_VERSION_CONFLICT",
                "Request changed. Reload it before saving again.");
    }

    private OrderException retryConflict() {
        return conflict("ORDER_RETRY_CONFLICT",
                "The operation identifier was already used for other input.");
    }

    private OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }
}
