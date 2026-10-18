/*
 * AuditRedactor
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Allowlisted, bounded audit metadata only.
 */
package com.equitransit.backend.service.activity;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Set;
import org.springframework.stereotype.Component;


/** Free text is never trusted, even under an innocently named JSON key. */
@Component
public class AuditRedactor {

    private static final Set<String> SAFE_TEXT = Set.of("DRAFT", "SUBMITTED",
            "ACCEPTED", "APPROVED", "ISSUED", "REJECTED", "SUPERSEDED",
            "EXPIRED", "PAID", "CLEARED", "DEPARTED", "DELIVERED", "CLOSED",
            "REQUESTED", "COMPLETED", "RECONCILED", "REVERSED", "USD");
    private static final Set<String> SAFE_FIELDS = Set.of("schemaVersion",
            "operationId", "orderId", "revisionId", "quoteId", "entityId",
            "status", "decision", "milestone", "recordVersion", "count",
            "metadata");

    private final ObjectMapper mapper;

    /** @param mapper shared strict JSON codec */
    public AuditRedactor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param input arbitrary producer metadata; never a file body
     * @return bounded redacted JSON; unsupported fields are redacted wholesale
     */
    public JsonNode redact(JsonNode input) {
        return redact(input, 0);
    }

    private JsonNode redact(JsonNode node, int depth) {
        if (node == null || node.isNull()) {
            return mapper.nullNode();
        }
        if (depth > 8 || node.size() > 100) {
            return TextNode.valueOf("[REDACTED]");
        }
        if (node.isObject()) {
            var result = mapper.createObjectNode();

            node.properties().forEach(field -> result.set(
                    SAFE_FIELDS.contains(field.getKey())
                            ? field.getKey() : "redactedField",
                    SAFE_FIELDS.contains(field.getKey())
                            ? redact(field.getValue(), depth + 1)
                            : TextNode.valueOf("[REDACTED]")));
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();

            node.forEach(value -> result.add(redact(value, depth + 1)));
            return result;
        }
        if (node.isBoolean() || node.isIntegralNumber()) {
            return node;
        }
        if (node.isTextual() && (SAFE_TEXT.contains(node.asText())
                || node.asText().matches("^[0-9a-fA-F]{16}$")
                || node.asText().matches("^[0-9a-fA-F-]{36}$"))) {
            return node;
        }
        return TextNode.valueOf("[REDACTED]");
    }
}
