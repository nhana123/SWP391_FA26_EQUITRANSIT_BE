/*
 * PricingOpenApiConfig
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.config;


import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/** Legacy unknown products need a genuinely nullable enum in OpenAPI 3.1. */
@Configuration
public class PricingOpenApiConfig {

    /** @return a scoped union normalizer; request product remains required */
    @Bean
    public OpenApiCustomizer nullableLegacyTariffProduct() {
        return api -> {
            if (api.getComponents() == null
                    || api.getComponents().getSchemas() == null) {
                return;
            }
            Schema<?> owner = api.getComponents().getSchemas()
                    .get("TariffResponse");

            if (owner == null || owner.getProperties() == null) {
                return;
            }
            Schema<?> property = owner.getProperties().get("product");

            if (property == null || property.getEnum() == null) {
                return;
            }
            Schema<Object> value = new Schema<>();

            value.setType("string");
            value.setTypes(Set.of("string"));
            value.setEnum(new ArrayList<>(property.getEnum()));
            Schema<Object> nullValue = new Schema<>();

            nullValue.setType("null");
            nullValue.setTypes(Set.of("null"));
            var union = new ComposedSchema();

            union.addAnyOfItem(value);
            union.addAnyOfItem(nullValue);
            union.setExample(property.getExample());
            owner.getProperties().put("product", union);
        };
    }
}
