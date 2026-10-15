/*
 * OpenApiConfig
 * Author: EquiTransit development team
 * Date: 2026-10-13
 * Copyright: see repository license.
 * Purpose: Describe the API and its bearer authentication in Swagger UI.
 */
package com.equitransit.backend.config;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Set;


/** Describes public APIs and the customer profile bearer requirement. */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "EquiTransit Backend API",
        version = "0.1.0",
        description = "Verified customer accounts and authentication."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    /**
     * Expresses nullable draft references without a null-only sibling type.
     * Confirmation preferences remain required and nonnullable.
     * @return a scoped OpenAPI reference normalizer
     */
    @Bean
    public OpenApiCustomizer nullableDraftServices() {
        return api -> {
            if (api.getComponents() == null
                    || api.getComponents().getSchemas() == null) {
                return;
            }
            var schemas = api.getComponents().getSchemas();

            for (String name : List.of("OrderDraftRequest",
                    "OrderDraftUpdateRequest", "OrderRevisionRequest")) {
                Schema<?> owner = schemas.get(name);

                if (owner == null || owner.getProperties() == null) {
                    continue;
                }
                Schema<?> property = owner.getProperties().get("services");

                if (property == null) {
                    continue;
                }
                String ref = "#/components/schemas/OrderServicePreferences";

                if (api.getOpenapi().startsWith("3.1")) {
                    var composed = new ComposedSchema();

                    composed.addAnyOfItem(new Schema<>().$ref(ref));
                    composed.addAnyOfItem(new Schema<>().type("null")
                            .types(Set.of("null")));
                    composed.setExample(property.getExample());
                    owner.getProperties().put("services", composed);
                } else {
                    var nullable = new Schema<>().$ref(ref).nullable(true);

                    nullable.setExample(property.getExample());
                    owner.getProperties().put("services", nullable);
                }
            }
        };
    }
}
