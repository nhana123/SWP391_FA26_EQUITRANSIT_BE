/*
 * TariffManagementService
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.service;

import com.equitransit.backend.dto.pricing.ServiceRateResponse;
import com.equitransit.backend.dto.pricing.TariffPage;
import com.equitransit.backend.dto.pricing.TariffResponse;
import com.equitransit.backend.dto.pricing.TariffWriteRequest;
import com.equitransit.backend.entity.pricing.PricingMode;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.json.OrderJsonCodec;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.repository.TariffRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;


/**
 * Manager publishes sourced price versions; past quote items stay immutable.
 */
@Service
@Validated
@PreAuthorize("hasRole('MANAGER')")
public class TariffManagementService {

    private final TariffRepository tariffs;
    private final CurrentActorService actors;
    private final AssessmentRepository access;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ServiceAssessmentFacade assessments;

    /**
     * @param tariffs versioned catalog persistence
     * @param actors live actor identity
     * @param access active staff lock boundary
     * @param json canonical audit encoding
     * @param mapper strict receipt decoder
     * @param assessments shared public retail catalogue
     * @param clock UTC time
     */
    public TariffManagementService(TariffRepository tariffs,
            CurrentActorService actors, AssessmentRepository access,
            OrderJsonCodec json, ObjectMapper mapper, Clock clock,
            ServiceAssessmentFacade assessments) {
        this.tariffs = tariffs;
        this.actors = actors;
        this.access = access;
        this.json = json;
        this.mapper = mapper;
        this.clock = clock;
        this.assessments = assessments;
    }

    /** @return fixed product options, units and pricing modes for the editor */
    @Transactional(readOnly = true)
    public List<ServiceRateResponse> products() {
        actors.current();
        ready();
        return assessments.retailRates(OffsetDateTime.now(clock));
    }

    /**
     * @param page zero-based page
     * @param size bounded history size
     * @return versions, including expired and inactive history
     */
    @Transactional(readOnly = true)
    public TariffPage list(@Min(0) int page, @Min(1) @Max(100) int size) {
        actors.current();
        ready();
        long count = tariffs.count();

        return new TariffPage(tariffs.list(page, size), page, size,
                count, (count + size - 1) / size);
    }

    /**
     * @param id requested version
     * @return manager provenance and observed rowversion
     */
    @Transactional(readOnly = true)
    public TariffResponse get(@Positive long id) {
        actors.current();
        ready();
        return find(id);
    }

    /**
     * @param previous null for a new family, exact latest ID for replacement
     * @param request sourced retail rate and UUID; never supplier cost
     * @return new version or original immutable retry receipt
     */
    @Transactional(timeout = 15)
    public TariffResponse publish(Long previous,
            @Valid TariffWriteRequest request) {
        var actor = actors.current();

        ready();
        if (!"MANAGER".equals(actor.roleCode()) || actor.staffId() == null
                || !access.activeStaff(actor.staffId(), "MANAGER")) {
            throw new OrderException(HttpStatus.FORBIDDEN,
                    "MANAGER_REQUIRED", "An active Manager is required.");
        }
        if (tariffs.lock() < 0) {
            throw conflict("TARIFF_BUSY", "Retry the same operation later.");
        }
        String hash = TokenTools.hash(json.canonical(Map.of("previous",
                previous == null ? 0L : previous, "request", request)));
        var saved = tariffs.operation(actor.userId(),
                request.operationId().toString());

        if (saved.isPresent()) {
            try {
                var envelope = mapper.readTree(saved.get());

                if (!hash.equals(envelope.path("requestHash").asText())) {
                    throw conflict("TARIFF_RETRY_CONFLICT",
                            "This operation identifier has other input.");
                }
                return mapper.treeToValue(envelope.get("response"),
                        TariffResponse.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw conflict("TARIFF_DATA_UNSUPPORTED",
                        "This tariff receipt requires a supported version.");
            }
        }
        if (request.product().mode() == PricingMode.INCLUDED) {
            throw conflict("TARIFF_INCLUDED",
                    "Included services cannot be charged a second time.");
        }
        BigDecimal price = new BigDecimal(request.unitPrice());

        if (price.setScale(4).precision() > 18) {
            throw conflict("TARIFF_AMOUNT_INVALID",
                    "The rate exceeds supported SQL precision.");
        }
        var latest = tariffs.latest(request.product());
        OffsetDateTime at = OffsetDateTime.now(clock).withNano(0);
        int version = latest.map(TariffResponse::version).orElse(0) + 1;

        if (previous == null) {
            if (request.recordVersion() != null
                    || tariffs.overlaps(request.product(), null, at)) {
                throw conflict("TARIFF_EXISTS",
                        "Replace the latest version using its rowversion.");
            }
        } else {
            var prior = find(previous);

            if (prior.product() != request.product() || latest.isEmpty()
                    || latest.get().tariffId() != previous
                    || request.recordVersion() == null
                    || !prior.recordVersion().equalsIgnoreCase(
                            request.recordVersion())
                    || tariffs.overlaps(request.product(), previous, at)) {
                throw conflict("TARIFF_VERSION_CONFLICT",
                        "Reload and compare the latest tariff version.");
            }
            // DATETIME2(0) cannot represent two activation instants per second.
            if (!at.isAfter(prior.effectiveFrom())) {
                at = prior.effectiveFrom().plusSeconds(1);
            }
        }
        if (request.validUntil() != null
                && !request.validUntil().withNano(0).isAfter(at)) {
            throw conflict("TARIFF_EXPIRY_INVALID",
                    "Rate expiry must follow its activation time.");
        }
        if (previous != null) {
            tariffs.end(find(previous), at);
        }
        long id = tariffs.insert(request, version, actor.staffId(), at);
        var response = find(id);

        tariffs.audit(actor.userId(), id, json.write(Map.of("schemaVersion", 1,
                "operationId", request.operationId(), "requestHash", hash,
                "response", response)));
        return response;
    }

    private void ready() {
        if (!tariffs.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TARIFF_SCHEMA_PENDING",
                    "Deploy the reviewed tariff management migration first.");
        }
    }

    private TariffResponse find(long id) {
        return tariffs.find(id).orElseThrow(() -> new OrderException(
                HttpStatus.NOT_FOUND, "TARIFF_NOT_FOUND",
                "The requested tariff was not found."));
    }

    private OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }
}
