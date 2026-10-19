/*
 * OrderJsonCodec
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Encode versioned, typed request snapshots without client metadata.
 */
package com.equitransit.backend.json.order;


import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.equitransit.backend.dto.order.request.OrderDraftRequest;
import com.equitransit.backend.dto.order.response.OrderSnapshot;
import com.equitransit.backend.exception.order.OrderException;


/** Keeps schema version, canonical retry hashes and typed read contracts. */
@Component
public class OrderJsonCodec {

    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper mapper;

    /**
     * Creates the JSON codec using application date and strict-input settings.
     * @param mapper the configured mapper
     */
    public OrderJsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Serializes validated server-owned values, never arbitrary client JSON.
     * @param value the typed value
     * @return its JSON representation
     */
    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot encode order data.");
        }
    }

    /**
     * Serializes a draft envelope with an explicit schema version.
     * @param request the normalized editable request
     * @return the versioned JSON
     */
    public String draft(OrderDraftRequest request) {
        return write(Map.of("schemaVersion", SCHEMA_VERSION,
                "request", request));
    }

    /**
     * Rehydrates a supported saved draft, preserving null for legacy rows.
     * @param encoded the stored envelope
     * @return the supported request or null if no draft was stored
     */
    public OrderDraftRequest readDraft(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(encoded);

            checkVersion(node);
            if (!node.hasNonNull("request")) {
                throw unsupported();
            }
            return mapper.treeToValue(node.get("request"),
                    OrderDraftRequest.class);
        } catch (JsonProcessingException error) {
            throw unsupported();
        }
    }

    /**
     * Reads an immutable supported revision.
     * @param encoded the stored snapshot
     * @return the typed snapshot
     */
    public OrderSnapshot snapshot(String encoded) {
        try {
            JsonNode node = mapper.readTree(encoded);

            checkVersion(node);
            return mapper.treeToValue(node, OrderSnapshot.class);
        } catch (JsonProcessingException error) {
            throw unsupported();
        }
    }

    /**
     * Produces a stable representation for idempotency conflict detection.
     * @param value the normalized command fields
     * @return JSON with recursively sorted map keys
     */
    public String canonical(Object value) {
        try {
            Object plain = mapper.convertValue(value, Object.class);

            return mapper.writer().with(
                    SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(plain);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot encode retry fields.");
        }
    }

    private void checkVersion(JsonNode node) {
        if (node == null || node.path("schemaVersion").asInt()
                != SCHEMA_VERSION) {
            throw unsupported();
        }
    }

    private OrderException unsupported() {
        return new OrderException(HttpStatus.CONFLICT,
                "ORDER_DATA_VERSION_UNSUPPORTED",
                "This request requires a supported data version.");
    }
}
