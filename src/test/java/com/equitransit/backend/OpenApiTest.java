/*
 * OpenApiTest
 * Author: EquiTransit development team
 * Date: 2026-10-13
 * Copyright: see repository license.
 * Purpose: Verify public documentation and protected customer access.
 */
package com.equitransit.backend;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;


/** Exercises documentation generation through the HTTP security stack. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OpenApiTest {

    @Autowired
    private MockMvc mvc;

    /**
     * Documents bearer access only on the protected customer operation.
     * @throws Exception if documentation generation fails
     */
    @Test
    public void documentationIsPublicAndDescribesBearerSecurity()
            throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title")
                        .value("EquiTransit Backend API"))
                .andExpect(jsonPath(
                        "$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath(
                        "$.paths['/api/customers/me'].get.security[0]"
                                + ".bearerAuth").isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/login'].post.security")
                        .doesNotExist());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk());
    }

    /**
     * Serves Swagger UI without granting anonymous profile access.
     * @throws Exception if request execution fails
     */
    @Test
    public void swaggerUiIsPublicWhileCustomerProfileStaysProtected()
            throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/customers/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The frontend sees typed profile contracts and bearer requirements.
     * @throws Exception if documentation generation fails
     */
    @Test
    public void horseContractIncludesVersionDocumentation() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/horses'].post"
                        + ".security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/horses'].post"
                        + ".responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/horses/{id}'].put"
                        + ".responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.HorseUpdateRequest"
                        + ".properties.recordVersion").exists())
                .andExpect(jsonPath("$.components.schemas.HorseResponse"
                        + ".properties.measurementsComplete").exists());
    }
    /**
     * Publishes typed order commands and protects customer journey access.
     * @throws Exception if documentation generation fails
     */
    @Test
    public void orderContractIncludesVersionAndRetryCommands()
            throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/transport-orders'].post"
                        + ".responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/transport-orders'].post"
                        + ".security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/transport-orders/{id}']"
                        + ".delete").doesNotExist())
                .andExpect(jsonPath("$.components.schemas"
                        + ".OrderDraftUpdateRequest.properties.recordVersion")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.OrderSubmitRequest"
                        + ".properties.operationId.format").value("uuid"))
                .andExpect(jsonPath("$.components.schemas.OrderRevisionRequest"
                        + ".properties.changeReason").exists())
                .andExpect(jsonPath("$.paths"
                        + "['/api/transport-orders/{id}/change-requests'].post")
                        .exists());
        mvc.perform(get("/api/transport-orders"))
                .andExpect(status().isUnauthorized());
    }
    /**
     * Publishes exact-revision commands and the separate assigned-case surface.
     * @throws Exception if documentation generation fails
     */
    @Test
    public void assessmentContractSeparatesActorsAndTypedDecisions()
            throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths"
                        + "['/api/manager/transport-orders/{id}/decisions']"
                        + ".post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.components.schemas.AssessmentRequest"
                        + ".properties.operationId.format").value("uuid"))
                .andExpect(jsonPath("$.components.schemas.AssessmentRequest"
                        + ".properties.revisionId").exists())
                .andExpect(jsonPath("$.components.schemas.AssessmentRequest"
                        + ".properties.managerId").doesNotExist())
                .andExpect(jsonPath("$.paths"
                        + "['/api/staff/transport-orders/{id}'].get").exists())
                .andExpect(jsonPath("$.components.schemas"
                        + ".CustomerAssessmentResponse.properties.assignments")
                        .doesNotExist());
        mvc.perform(get("/api/manager/transport-orders"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/staff/transport-orders"))
                .andExpect(status().isUnauthorized());
    }
    /**
     * Publishes service choices without accepting prices or compulsory days.
     * @throws Exception if documentation generation fails
     */
    @Test
    public void serviceContractPublishesVersionedNonpayableDisclosure()
            throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths"
                        + "['/api/transport-orders/{id}/services'].get"
                        + ".security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths"
                        + "['/api/transport-orders/{id}/services/confirm'].po"
                                + "st")
                        .exists())
                .andExpect(jsonPath("$.components.schemas"
                        + ".ServiceConfirmationRequest.properties"
                        + ".horseRecordVersion").exists())
                .andExpect(jsonPath("$.components.schemas"
                        + ".ServiceDisclosureResponse.properties.knownSubtotal")
                        .exists())
                .andExpect(jsonPath("$.components.schemas"
                        + ".OrderServicePreferences.properties.unitPrice")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas"
                        + ".OrderServicePreferences.properties.requiredDays")
                        .doesNotExist());
        mvc.perform(get("/api/transport-orders/1/services"))
                .andExpect(status().isUnauthorized());
    }
    /** @throws Exception if F16 documentation/security is incomplete */
    @Test
    public void allActivityOperationsRequireBearerAndProduceJson()
            throws Exception {
        var api = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(mvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString());
        var gets = java.util.List.of("/api/dashboard", "/api/dashboard/tasks",
                "/api/notifications", "/api/activity/orders/{orderId}/timeline",
                "/api/manager/audit-events", "/api/manager/reports/financial",
                "/api/customers/me/notification-preferences");
        int count = 0;

        for (String path : gets) {
            var operation = api.path("paths").path(path).path("get");

            org.assertj.core.api.Assertions.assertThat(
                    operation.path("security").path(0).has("bearerAuth"))
                    .as(path).isTrue();
            org.assertj.core.api.Assertions.assertThat(operation
                    .path("responses").path("200").path("content")
                    .has("application/json")).as(path).isTrue();
            mvc.perform(get(path.replace("{orderId}", "1")))
                    .andExpect(status().isUnauthorized());
            count++;
        }
        for (String path : java.util.List.of("/api/notifications/{id}/read",
                "/api/customers/me/notification-preferences")) {
            var operation = api.path("paths").path(path).path("put");

            org.assertj.core.api.Assertions.assertThat(operation
                    .path("security").path(0).has("bearerAuth"))
                    .as(path).isTrue();
            org.assertj.core.api.Assertions.assertThat(operation
                    .path("responses").path("200").path("content")
                    .has("application/json")).as(path).isTrue();
            mvc.perform(org.springframework.test.web.servlet.request
                    .MockMvcRequestBuilders.put(path.replace("{id}", "1"))
                    .contentType(org.springframework.http.MediaType
                            .APPLICATION_JSON).content("{\"enabled\":true}"))
                    .andExpect(status().isUnauthorized());
            count++;
        }
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(9);
    }


    /** @throws Exception if any F08 operation admits an anonymous caller */
    @Test
    public void allComplianceOperationsRequireAuthentication()
            throws Exception {
        var response = mvc.perform(org.springframework.test.web.servlet.
                request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        var api = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response);
        int checked = 0;

        for (var entry : api.path("paths").properties()) {
            if (!entry.getKey().startsWith(
                    "/api/transport-orders/{order}/compliance")) {
                continue;
            }
            String route = entry.getKey().replace("{order}", "101")
                    .replace("{id}", "102").replace("{key}",
                            "84032724-eac7-45df-8d04-e54f092279d0");

            for (var operation : entry.getValue().properties()) {
                mvc.perform(org.springframework.test.web.servlet.request.
                        MockMvcRequestBuilders.request(
                                org.springframework.http.HttpMethod.valueOf(
                                        operation.getKey().toUpperCase(
                                                java.util.Locale.ROOT)),
                                route).contentType("application/json")
                                        .content("{}"))
                        .andExpect(org.springframework.test.web.servlet.
                                result.MockMvcResultMatchers.status()
                                        .isUnauthorized());
                checked++;
            }
        }
        org.assertj.core.api.Assertions.assertThat(checked).isEqualTo(16);
    }

    /** @throws Exception if any F10 operation admits an anonymous caller */
    @Test
    public void allOperationalPlanOperationsRequireAuthentication()
            throws Exception {
        var response = mvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        var api = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response);
        int checked = 0;

        for (var entry : api.path("paths").properties()) {
            if (!entry.getKey().startsWith(
                    "/api/transport-orders/{orderId}/plans")) {
                continue;
            }
            String route = entry.getKey().replace("{orderId}", "101")
                    .replace("{planId}", "311").replace("{legId}", "401");

            for (var operation : entry.getValue().properties()) {
                mvc.perform(org.springframework.test.web.servlet.request.
                        MockMvcRequestBuilders.request(
                                org.springframework.http.HttpMethod.valueOf(
                                        operation.getKey().toUpperCase(
                                                java.util.Locale.ROOT)),
                                route).contentType("application/json")
                                        .content("{}"))
                        .andExpect(status().isUnauthorized());
                checked++;
            }
        }
        org.assertj.core.api.Assertions.assertThat(checked).isEqualTo(10);
    }

    /** @throws Exception if any F11 operation admits an anonymous caller */
    @Test
    public void allDepartureOperationsRequireAuthentication()
            throws Exception {
        var response = mvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        var api = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response);
        int checked = 0;

        for (var entry : api.path("paths").properties()) {
            if (!entry.getKey().startsWith(
                    "/api/transport-orders/{orderId}/departure")) {
                continue;
            }
            String route = entry.getKey().replace("{orderId}", "101");

            for (var operation : entry.getValue().properties()) {
                mvc.perform(org.springframework.test.web.servlet.request.
                        MockMvcRequestBuilders.request(
                                org.springframework.http.HttpMethod.valueOf(
                                        operation.getKey().toUpperCase(
                                                java.util.Locale.ROOT)),
                                route).contentType("application/json")
                                        .content("{}"))
                        .andExpect(status().isUnauthorized());
                checked++;
            }
        }
        org.assertj.core.api.Assertions.assertThat(checked).isEqualTo(5);
    }
}
