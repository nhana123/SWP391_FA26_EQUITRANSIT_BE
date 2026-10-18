package com.equitransit.backend.dto.compliance.response;





/**
 * Public F08 result without storage keys.
 * @param typeCode the typeCode value
 * @param displayName the displayName value
 * @param description the description value
 * @param active the active value
 */
public record DocumentTypeCatalogResponse(
        String typeCode,
        String displayName,
        String description,
        boolean active) {
}
