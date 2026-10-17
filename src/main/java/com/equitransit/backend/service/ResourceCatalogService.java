/*
 * ResourceCatalogService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Maintain sourced resources and transactional availability.
 */
package com.equitransit.backend.service;

import com.equitransit.backend.dto.resource.ResourceModels.ResourceCommand;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceWindow;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceVehicleData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceSupplierData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyView;
import com.equitransit.backend.dto.resource.ResourceModels.PolicyState;
import com.equitransit.backend.dto.resource.ResourceModels.AssetState;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceSupplierView;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.repository.ResourceRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.json.OrderJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogue mutations are serialized with downstream resource reservations. */
@Service
@PreAuthorize("@domainAccessPolicy.isManager()")
public class ResourceCatalogService {

    private final ResourceRepository repository;
    private final CurrentActorService actors;
    private final AssessmentRepository staff;
    private final OrderJsonCodec json;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** @param repository persistence @param actors live identity
     * @param staff active staff checks @param json canonical hashing
     * @param mapper typed replay codec @param clock server UTC */
    public ResourceCatalogService(ResourceRepository repository,
            CurrentActorService actors, AssessmentRepository staff,
            OrderJsonCodec json, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.actors = actors;
        this.staff = staff;
        this.json = json;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** @param kind catalogue @param page bounded @param size bounded
     * @param type response @return internal staff page */
    @Transactional(readOnly = true, timeout = 15)
    public <T> List<T> list(ResourceKind kind, int page, int size,
            Class<T> type) {
        ready();
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("Use page>=0 and size1..100.");
        }
        return repository.list(kind, page, size, type);
    }

    /** @param kind catalogue @return count for the bounded page */
    @Transactional(readOnly = true, timeout = 15)
    public long count(ResourceKind kind) {
        ready();
        return repository.count(kind);
    }

    /** @param kind catalogue @param id key @param type DTO @return detail */
    @Transactional(readOnly = true, timeout = 15)
    public <T> T get(ResourceKind kind, long id, Class<T> type) {
        ready();
        return repository.get(kind, id, type);
    }

    /** @param kind catalogue @param id zero creates, nonzero replaces
     * @param command UUID/CAS @param data typed body @param type response
     * @return original replay receipt or newly written resource */
    @Transactional(timeout = 15)
    public <T> T write(ResourceKind kind, long id, ResourceCommand command,
            Object data, Class<T> type) {
        ready();
        var actor = actors.current();

        if (actor.staffId() == null
                || !staff.activeStaff(actor.staffId(), "MANAGER")) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "An active Manager is required.");
        }
        repository.lock();
        String hash = TokenTools.hash(json.canonical(Map.of("kind", kind,
                "id", id, "command", command, "data", data)));
        var prior = repository.operation(actor.userId(), command.operationId());

        if (!prior.isEmpty()) {
            if (!hash.equals(prior.getFirst().get("RequestHash"))) {
                throw conflict("RESOURCE_RETRY_CONFLICT",
                        "Operation UUID belongs to different input.");
            }
            try {
                return mapper.readValue((String) prior.getFirst()
                        .get("ReceiptJSON"), type);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw conflict("RESOURCE_DATA_UNSUPPORTED",
                        "The saved receipt needs review.");
            }
        }
        Object previous = id == 0 ? null : repository.get(kind, id, type);
        String observed = previous == null ? null
                : mapper.valueToTree(previous).path("recordVersion").asText();

        if (id == 0 ? command.recordVersion() != null
                : command.recordVersion() == null || !observed.equalsIgnoreCase(
                        command.recordVersion())) {
            throw conflict("RESOURCE_VERSION_CONFLICT",
                    "Reload and compare the current resource version.");
        }
        validate(data);
        int version = 1;
        Long reviewer = null;
        OffsetDateTime reviewedAt = OffsetDateTime.now(clock);

        if (data instanceof ResourcePolicyData policy) {
            if (previous instanceof ResourcePolicyView old) {
                version = old.version();
                if (old.data().status() != PolicyState.DRAFT
                        && (!samePolicy(old.data(), policy)
                                || policy.status() != PolicyState.INACTIVE)) {
                    throw conflict("RESOURCE_POLICY_IMMUTABLE",
                            "Create a new version; do not edit approval.");
                }
                reviewer = old.reviewedBy();
                reviewedAt = old.reviewedAt();
            } else {
                version = repository.nextVersion(policy.policyCode());
            }
            if (policy.status() == PolicyState.APPROVED) {
                reviewer = actor.staffId();
                reviewedAt = OffsetDateTime.now(clock);
            }
        }
        long key = repository.save(kind, id, data, version, reviewer,
                reviewedAt);

        if (data instanceof ResourcePolicyData p
                && p.status() == PolicyState.APPROVED) {
            repository.retirePolicies(key, p.policyCode());
        }
        T receipt = repository.get(kind, key, type);

        repository.finish(actor.userId(), command.operationId(), hash,
                receipt, kind, key);
        return receipt;
    }

    private boolean samePolicy(ResourcePolicyData old,
            ResourcePolicyData next) {
        com.fasterxml.jackson.databind.node.ObjectNode before =
                mapper.valueToTree(old);
        com.fasterxml.jackson.databind.node.ObjectNode after =
                mapper.valueToTree(next);

        before.remove("status");
        after.remove("status");
        return before.equals(after);
    }

    private void validate(Object data) {
        var tree = mapper.valueToTree(data);

        for (var entry : tree.properties()) {
            if (entry.getValue().isTextual()
                    && entry.getValue().asText().isBlank()) {
                throw invalid("Supplied text must not be blank.");
            }
        }
        if (data instanceof ResourceVehicleData v) {
            owner(v.ownerSupplierId(), v.status() == AssetState.ACTIVE);
            window(v.window());
        } else if (data instanceof ResourceStallData v) {
            owner(v.ownerSupplierId(), v.status() == AssetState.ACTIVE);
            window(v.window());
        } else if (data instanceof ResourceSupplierData v) {
            if (v.services().stream().distinct().count()
                    != v.services().size()) {
                throw invalid("Supplier services must be distinct.");
            }
        } else if (data instanceof ResourcePolicyData v) {
            owner(v.carrierSupplierId(), v.status() == PolicyState.APPROVED);
            boundedDate(v.effectiveFrom());
            boundedDate(v.effectiveTo());
            if (v.stallId() != null) {
                repository.get(ResourceKind.STALL, v.stallId(),
                        ResourceStallView.class);
            }
            if (List.of("1", "1.5", "3").stream().noneMatch(value ->
                    v.slotQuantity().compareTo(new java.math.BigDecimal(value))
                            == 0)) {
                throw invalid("Allowed slot quantities:1,1.5,3.");
            }
            if (v.effectiveTo() != null && !v.effectiveTo().isAfter(
                    v.effectiveFrom())) {
                throw invalid("Policy expiry must follow its start.");
            }
            if (v.sourceUrl() != null) {
                try {
                    var uri = java.net.URI.create(v.sourceUrl());
                    if (!("https".equals(uri.getScheme())
                            || "http".equals(uri.getScheme()))
                            || uri.getHost() == null
                            || uri.getUserInfo() != null) {
                        throw invalid("Use HTTP(S) without credentials.");
                    }
                } catch (IllegalArgumentException e) {
                    throw invalid("Invalid source URL.");
                }
            }
            if (v.sourceFileKey() != null
                    && !v.sourceFileKey().matches("^[A-Za-z0-9_-]{1,1024}$")) {
                throw invalid("Use an existing opaque source file key.");
            }
            if (v.status() == PolicyState.APPROVED
                    && (v.maxWeightKg() == null || v.maxHeightCm() == null
                            || v.maxWidthCm() == null
                            || (v.sourceUrl() == null
                                    && v.sourceFileKey() == null))) {
                throw invalid("Approval needs positive limits and a source.");
            }
        }
    }

    private void owner(Long supplier, boolean mustBeActive) {
        if (mustBeActive && !repository.activeSupplier(supplier)) {
            throw invalid("Supplier must exist and be active.");
        }
        if (!mustBeActive && supplier != null) {
            repository.get(ResourceKind.SUPPLIER, supplier,
                    ResourceSupplierView.class);
        }
    }

    private void window(ResourceWindow value) {
        if (value.notes() != null && value.notes().isBlank()) {
            throw invalid("Supplied notes must not be blank.");
        }
        boundedDate(value.maintenanceFrom());
        boundedDate(value.maintenanceTo());
        boundedDate(value.documentExpiresAt());
        if (value.maintenanceTo() != null && (value.maintenanceFrom() == null
                || !value.maintenanceTo().isAfter(value.maintenanceFrom()))) {
            throw invalid("Maintenance end must follow its start.");
        }
    }

    private void boundedDate(OffsetDateTime value) {
        if (value != null && (value.getYear() < 1900
                || value.getYear() > 9997)) {
            throw invalid("Use SQL-safe dates between1900 and9997.");
        }
    }

    private void ready() {
        actors.current();
        if (!repository.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RESOURCE_SCHEMA_PENDING", "Deploy resource migration010.");
        }
    }

    private OrderException invalid(String message) {
        return new OrderException(HttpStatus.BAD_REQUEST,
                "RESOURCE_INPUT_INVALID", message);
    }

    private OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }
}
