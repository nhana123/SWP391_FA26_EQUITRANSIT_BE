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
}
