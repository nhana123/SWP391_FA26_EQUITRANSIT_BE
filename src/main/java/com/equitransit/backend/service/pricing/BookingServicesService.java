/*
 * BookingServicesService
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Own customer service disclosure and versioned confirmation flows.
 */
package com.equitransit.backend.service.pricing;

import com.equitransit.backend.dto.pricing.ServiceRateResponse;

import com.equitransit.backend.dto.pricing.ServicePreviewRequest;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.equitransit.backend.dto.order.request.OrderDraftRequest;
import com.equitransit.backend.dto.order.request.OrderServicePreferences;
import com.equitransit.backend.dto.order.request.OrderDraftUpdateRequest;
import com.equitransit.backend.dto.order.request.OrderRevisionRequest;
import com.equitransit.backend.dto.pricing.request.ServiceConfirmationRequest;
import com.equitransit.backend.dto.horse.response.HorseResponse;
import com.equitransit.backend.dto.pricing.response.BookingSectionResponse;
import com.equitransit.backend.entity.pricing.StallFitResult;
import com.equitransit.backend.dto.order.response.OrderRevisionResponse;
import com.equitransit.backend.dto.pricing.response.ServiceChargeResponse;
import com.equitransit.backend.dto.pricing.response.ServiceConfirmationResponse;
import com.equitransit.backend.dto.pricing.response.ServiceDisclosureResponse;
import com.equitransit.backend.dto.order.response.OrderSnapshot;
import com.equitransit.backend.entity.order.TransportOrderRecord;
import com.equitransit.backend.entity.pricing.BundleResult;
import com.equitransit.backend.entity.quote.QuoteSnapshot;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.pricing.ServiceSelectionAuditRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.service.order.TransportOrderService;
import com.equitransit.backend.service.horse.HorseService;


/**
 * Confirmation captures choices only; no payment or insurer cover activation.
 */
@Service
@Validated
@PreAuthorize("@domainAccessPolicy.isCustomer()")
@SuppressWarnings("null")
public class BookingServicesService {

    private final OrderRepository orders;
    private final TransportOrderService lifecycle;
    private final HorseService horses;
    private final CurrentActorService actors;
    private final ServicePreferencePolicy policy;
    private final ServiceAssessmentFacade assessments;
    private final ServiceChargeBuilder charges;
    private final ServiceSelectionAuditRepository audit;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * Creates the authorized service-selection facade.
     * @param orders owner-scoped persistence
     * @param lifecycle atomic draft and immutable revision facade
     * @param horses owned profile and measurement-lock facade
     * @param actors live customer identity
     * @param policy actual server policy validation
     * @param assessments typed source contracts from F08/F09/F10
     * @param charges public charge disclosure
     * @param audit operation receipts in the same transaction
     * @param json versioned snapshot codec
     * @param mapper typed command copying and replay reader
     * @param clock server UTC time
     */
    public BookingServicesService(OrderRepository orders,
            TransportOrderService lifecycle, HorseService horses,
            CurrentActorService actors, ServicePreferencePolicy policy,
            ServiceAssessmentFacade assessments, ServiceChargeBuilder charges,
            ServiceSelectionAuditRepository audit, OrderJsonCodec json,
            ObjectMapper mapper, Clock clock) {
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.horses = horses;
        this.actors = actors;
        this.policy = policy;
        this.assessments = assessments;
        this.charges = charges;
        this.audit = audit;
        this.json = json;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Reads all charges using captured measurements and exact current sources.
     * @param id owned order key
     * @return seven-section disclosure, always nonpayable
     */
    @Transactional(readOnly = true)
    public ServiceDisclosureResponse get(@Positive long id) {
        return disclosure(id, null);
    }

    /**
     * Computes unsaved choices without changing orders, quotes or assignments.
     * @param id owned order
     * @param preview observed versions and full unsaved preferences
     * @return nonpayable live-source estimate, never an issued quote
     */
    @Transactional(readOnly = true)
    public ServiceDisclosureResponse preview(@Positive long id,
            @Valid ServicePreviewRequest
                    preview) {
        return disclosure(id, preview);
    }

    /**
     * @param id owned order
     * @return all fixed product options and effective public retail unit rates
     */
    @Transactional(readOnly = true)
    public List<ServiceRateResponse> rates(
            @Positive long id) {
        owned(id, false);
        return assessments.retailRates(OffsetDateTime.now(clock));
    }

    private ServiceDisclosureResponse disclosure(long id,
            ServicePreviewRequest preview) {
        TransportOrderRecord order = owned(id, false);
        OrderDraftRequest request = mapper.convertValue(request(order),
                OrderDraftRequest.class);
        HorseResponse horse = horse(order, request);
        defaultStall(request, horse);
        OrderServicePreferences saved = request.getServices();
        if (preview != null) {
            if (!order.recordVersion().equalsIgnoreCase(preview.recordVersion())
                    || (horse != null && (preview.horseRecordVersion() == null
                            || !horse.recordVersion().equalsIgnoreCase(
                                    preview.horseRecordVersion())))) {
                throw failure("SERVICE_PREVIEW_VERSION_CONFLICT",
                        "Reload and compare the current order and horse.");
            }
            if (horse == null && preview.services().getAirStall() != null) {
                throw failure("AIR_STALL_ASSESSMENT_REQUIRED",
                        "Choose a horse before checking an air stall.");
            }
            policy.validate(preview.services(), horse,
                    request.getDeliveryDeadline());
            request.setServices(mapper.convertValue(preview.services(),
                    OrderServicePreferences.class));
            defaultStall(request, horse);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime horizon =
                policy.horizon(request.getDeliveryDeadline(), now);
        var quarantine = assessments.quarantine(id, order.currentRevisionId(),
                now, horizon);
        var bundle = assessments.bundles(order.currentRevisionId(), now);

        if (preview != null && !unchanged("AIR_STALL", saved,
                request.getServices())) {
            bundle = new BundleResult(Set.of(), false);
        }
        List<ServiceChargeResponse> rows = charges.build(request.getServices(),
                assessments.itinerary(id, order.currentRevisionId(), now),
                quarantine, assessments.veterinary(id,
                order.currentRevisionId(),
                        now, horizon),
                bundle, now, request);
        var commercial = assessments.commercialQuote(id,
                order.currentRevisionId(), now);

        if (commercial.isPresent()) {
            rows = quotedRows(rows, commercial.get(), saved,
                    preview == null ? null : request.getServices());
        }
        BigDecimal known = rows.stream().filter(row -> row.amount() != null)
                .map(row -> new BigDecimal(row.amount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        boolean unresolved = rows.stream().anyMatch(row -> row.selected()
                && row.amount() == null && !List.of("INCLUDED", "NOT_SELECTED",
                        "NOT_REQUIRED",
                        "EXTERNAL_PROVIDER", "EVENT_ONLY")
                        .contains(row.pricingStatus()));
        unresolved |= commercial.isPresent()
                && unresolvedSnapshot(commercial.get(), saved,
                        preview == null ? null : request.getServices());
        List<StallFitResult> options = horse == null ? List.of()
                : policy.options(horse, request.getDeliveryDeadline());

        return new ServiceDisclosureResponse(id, order.recordVersion(),
                order.currentRevisionId(), horse == null ? null
                        : horse.recordVersion(), now, "USD",
                request.getServices(),
                sections(options, quarantine.applicability()), options,
                quarantine, rows,
                known.toPlainString(),
                unresolved ? null : known.toPlainString(),
                false, "NOT_CONFIRMED");
    }

    /**
     * Replaces choices with retry/concurrency protection and immutable history.
     * @param id owned order key
     * @param command complete preferences, observed versions and retry UUID
     * @return draft-only save or new revision; never a charge or payment
     */
    @Transactional
    public ServiceConfirmationResponse confirm(@Positive long id,
            @Valid ServiceConfirmationRequest command) {
        TransportOrderRecord order = owned(id, true);
        long user = actors.current().userId();
        String hash = TokenTools.hash(json.canonical(Map.of("version",
                command.recordVersion().toLowerCase(Locale.ROOT),
                "horseVersion",
                command.horseRecordVersion().toLowerCase(Locale.ROOT),
                "services", command.services(), "reason",
                command.changeReason() == null
                ? "" : command.changeReason().trim())));
        Optional<ServiceConfirmationResponse> replay = replay(id, user, command,
                hash);
        OrderDraftRequest request;
        HorseResponse horse;
        OrderRevisionResponse revision = null;
        ServiceConfirmationResponse receipt;

        if (replay.isPresent()) {
            return replay.get();
        }
        if (!order.recordVersion().equalsIgnoreCase(command.recordVersion())) {
            throw failure("ORDER_VERSION_CONFLICT",
                    "Request changed. Reload it before confirming choices.");
        }
        request = request(order);
        if (request.getHorseId() == null) {
            throw failure("ORDER_HORSE_REQUIRED",
                    "Choose a horse in the request before selecting services.");
        }
        horse = horses.lockSnapshot(request.getHorseId(),
                command.horseRecordVersion());
        if (order.currentRevisionId() != null && !horse.recordVersion().equals(
                snapshot(order).horse().recordVersion())) {
            throw failure("HORSE_SNAPSHOT_STALE",
                    "Refresh the request revision with your updated horse "
                            + "profile before confirming services.");
        }
        request.setServices(mapper.convertValue(command.services(),
                OrderServicePreferences.class));
        defaultStall(request, horse);
        policy.validate(request.getServices(), horse,
                request.getDeliveryDeadline());
        if ("DRAFT".equals(order.orderStatus())) {
            OrderDraftUpdateRequest update = mapper.convertValue(request,
                    OrderDraftUpdateRequest.class);

            update.setRecordVersion(command.recordVersion());
            lifecycle.saveDraft(id, update);
        } else {
            OrderRevisionRequest update = mapper.convertValue(request,
                    OrderRevisionRequest.class);

            if (command.changeReason() == null
                    || command.changeReason().isBlank()) {
                throw failure("SERVICE_CHANGE_REASON_REQUIRED",
                        "Explain the change to your submitted service "
                + "choices.");
            }
            update.setRecordVersion(command.recordVersion());
            update.setOperationId(command.operationId());
            update.setChangeReason(command.changeReason().trim());
            revision = lifecycle.revise(id, update);
        }
        receipt = new ServiceConfirmationResponse(lifecycle.get(id), revision);
        audit.append(id, user, json.write(Map.of("schemaVersion", 1,
                "operationId", command.operationId(), "requestHash", hash,
                "response", receipt)));
        return receipt;
    }

    private List<ServiceChargeResponse> quotedRows(
            List<ServiceChargeResponse> rows, QuoteSnapshot quote,
            OrderServicePreferences saved, OrderServicePreferences preview) {
        return rows.stream().map(row -> {
            if (preview != null && !unchanged(row.code(), saved, preview)) {
                return row;
            }
            var line = quote.lines().stream()
                    .filter(item -> item.code().equals(row.code()))
                    .findFirst().orElse(null);

            if (line == null) {
                return row;
            }
            var evidence = quote.request().assessments().stream()
                    .filter(item -> item.code().name().equals(row.code()))
                    .findFirst().orElse(null);
            Long tariff = evidence == null ? row.tariffId()
                    : evidence.tariffId();
            var source = quote.sources().stream()
                    .filter(item -> !item.offer() && tariff != null
                            && item.id() == tariff).findFirst().orElse(null);
            String state = switch (line.pricingStatus()) {
                case "PRICED" -> "ESTIMATED";
                case "PENDING" -> "RATE_AND_QUANTITY_PENDING";
                default -> line.pricingStatus();
            };

            return new ServiceChargeResponse(row.code(), row.uiSection(),
                    line.costGroup(), line.description(), row.mandatory(),
                    row.selected() && !"NOT_SELECTED".equals(state),
                    line.unit(), line.quantity(),
                    line.unitRate(), line.amount(), state,
                    source == null ? null : source.id(),
                    source == null ? null : source.version(),
                    evidence == null ? row.formula()
                            : "Assessed quantity x quoted retail rate; "
                                    + "HALF_UP (2 decimals)",
                    line.blockers());
        }).toList();
    }

    private void defaultStall(OrderDraftRequest request, HorseResponse horse) {
        if (horse == null) {
            return;
        }
        var selected = request.getServices();

        if (selected == null) {
            selected = new OrderServicePreferences();
            request.setServices(selected);
        }
        if (selected.getAirStall() == null) {
            selected.setAirStall(policy.defaultAirStall(horse,
                    request.getDeliveryDeadline()));
        }
    }

    private boolean unchanged(String code, OrderServicePreferences saved,
            OrderServicePreferences preview) {
        List<String> fields = switch (code) {
            case "ROAD_TRANSPORT" -> List.of("roadArrangement",
                    "roadProviderName", "roadProviderContact");
            case "AIRFARE", "AIR_STALL", "AIRPORT_HANDLING",
                    "TERMINAL_HANDLING", "CUSTOMS_DECLARATION" ->
                    List.of("airStall");
            case "HORSE_CARE" -> List.of("enhancedCare");
            case "GROOMING" -> List.of("groomingDays");
            case "BOARDING" -> List.of("boardingNights");
            case "VETERINARY_EXAM", "VETERINARY_TEST", "VETERINARY_VACCINE",
                    "VETERINARY_CERTIFICATE" ->
                    List.of("veterinaryArrangement", "veterinaryProviderName",
                            "veterinaryProviderContact");
            case "QUARANTINE" -> List.of("quarantinePackage");
            case "INSURANCE" -> List.of("insurance", "declaredValue",
                    "existingPolicyReference");
            case "ESCORT" -> List.of("escortNotes");
            default -> List.of();
        };
        JsonNode before = mapper.valueToTree(saved == null
                ? new OrderServicePreferences() : saved);
        JsonNode after = mapper.valueToTree(preview);

        return fields.stream().allMatch(field ->
                Objects.equals(before.path(field), after.path(field))
                        || "declaredValue".equals(field)
                        && before.path(field).isTextual()
                        && after.path(field).isTextual()
                        && new BigDecimal(before.path(field).asText())
                                .compareTo(new BigDecimal(after.path(field)
                                        .asText())) == 0);
    }

    private boolean unresolvedSnapshot(QuoteSnapshot quote,
            OrderServicePreferences saved, OrderServicePreferences preview) {
        return quote.blockers().stream().anyMatch(blocker -> preview == null
                || quote.lines().stream().noneMatch(line ->
                        blocker.equals(line.code() + "_PENDING")
                                && !unchanged(line.code(), saved, preview)));
    }

    private OrderDraftRequest request(TransportOrderRecord order) {
        OrderDraftRequest request = order.currentRevisionId() == null
                ? json.readDraft(order.draftPayload())
                : snapshot(order).request();

        if (request == null) {
            throw failure("ORDER_DRAFT_REQUIRED",
                    "Save a supported request before selecting services.");
        }
        return request;
    }

    private HorseResponse horse(TransportOrderRecord order,
            OrderDraftRequest request) {
        return order.currentRevisionId() != null ? snapshot(order).horse()
                : request.getHorseId() == null ? null
                        : horses.get(request.getHorseId());
    }

    private OrderSnapshot snapshot(TransportOrderRecord order) {
        return json.snapshot(orders.revision(order.orderId(),
                order.currentRevisionId()).orElseThrow(() ->
                failure("ORDER_DATA_VERSION_UNSUPPORTED",
                        "This request requires a supported revision."))
                .snapshot());
    }

    private TransportOrderRecord owned(long id, boolean lock) {
        return orders.findOwned(id, actors.current().customerId(), lock)
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                "The requested record was not found."));
    }

    private Optional<ServiceConfirmationResponse> replay(long id, long user,
            ServiceConfirmationRequest command, String hash) {
        Optional<String> encoded = audit.operation(id, user,
                command.operationId());
        JsonNode node;

        if (encoded.isEmpty()) {
            return Optional.empty();
        }
        try {
            node = mapper.readTree(encoded.get());
            if (node.path("schemaVersion").asInt() != 1) {
                throw failure("SERVICE_DATA_UNSUPPORTED",
                        "This operation requires a supported data version.");
            }
            if (!hash.equals(node.path("requestHash").asText())) {
                throw failure("SERVICE_RETRY_CONFLICT",
                        "This operation identifier was used for other input.");
            }
            return Optional.of(mapper.treeToValue(node.get("response"),
                    ServiceConfirmationResponse.class));
        } catch (JsonProcessingException error) {
            throw failure("SERVICE_DATA_UNSUPPORTED",
                    "This operation requires a supported data version.");
        }
    }

    private OrderException failure(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }

    private List<BookingSectionResponse> sections(List<StallFitResult> options,
            String quarantine) {
        return List.of(new BookingSectionResponse("TRANSPORTATION",
                "Transportation", List.of("EQUITRANSIT", "CUSTOMER")),
                new BookingSectionResponse("AIR_STALL",
                        "Available Air Stalls for Your Horse", options.stream()
                                .map(StallFitResult::option).toList()),
                new BookingSectionResponse("HORSE_CARE", "Horse Care",
                        List.of("ENHANCED", "GROOMING", "BOARDING", "ESCORT")),
                new BookingSectionResponse("VETERINARY", "Veterinary",
                        List.of("EQUITRANSIT", "CUSTOMER")),
                new BookingSectionResponse("QUARANTINE", "Quarantine Packages",
                        "NOT_REQUIRED".equals(quarantine) ? List.of()
                                : List.of("STANDARD", "ENHANCED")),
                new BookingSectionResponse("INSURANCE", "Insurance", List.of(
                        "NONE", "EXISTING", "MORTALITY", "COMBINED")),
                new BookingSectionResponse("ADDITIONAL_CHARGES",
                        "Additional Charges", List.of("SHOW_ALL")));
    }
}
