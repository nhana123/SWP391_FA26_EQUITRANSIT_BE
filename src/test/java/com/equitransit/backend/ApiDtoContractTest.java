/*
 * ApiDtoContractTest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Check documented examples and real HTTP schemas together.
 */
package com.equitransit.backend;


import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import jakarta.validation.Validation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.equitransit.backend.dto.ApiExamples;
import com.equitransit.backend.dto.request.HorseProfileRequest;
import com.equitransit.backend.dto.request.EmailRequest;
import com.equitransit.backend.dto.request.LoginRequest;
import com.equitransit.backend.dto.request.RegisterRequest;
import com.equitransit.backend.dto.request.ResetPasswordRequest;
import com.equitransit.backend.dto.request.OrderDraftRequest;
import com.equitransit.backend.entity.OrderServiceIntent;
import com.equitransit.backend.entity.CaseRole;
import com.equitransit.backend.entity.DecisionCode;
import com.equitransit.backend.entity.OrderStatus;
import com.equitransit.backend.dto.request.AssessmentRequest;
import com.equitransit.backend.dto.response.AssessmentReceipt;
import com.equitransit.backend.dto.response.CaseAssignmentResponse;
import com.equitransit.backend.dto.response.CaseDecisionResponse;
import com.equitransit.backend.dto.response.CaseResponse;
import com.equitransit.backend.dto.response.CaseStaffResponse;
import com.equitransit.backend.dto.response.CaseSummaryResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;


/** Examples must be useful commands, not merely annotation text. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApiDtoContractTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    /**
     * Checks real Swagger output and saves an ignored diagnostic artifact.
     * @throws Exception if schema generation fails
     */
    @Test
    public void generatedContractHasExamplesEnumsAndJsonMediaTypes()
            throws Exception {
        String encoded = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        JsonNode api = mapper.readTree(encoded);
        JsonNode schemas = api.at("/components/schemas");

        Files.writeString(Path.of("target/api-dto-contract.json"), encoded);
        for (String name : List.of(
                "EmailRequest",
                "LoginRequest",
                "RegisterRequest",
                "ResetPasswordRequest",
                "TokenRequest",
                "LoginResponse",
                "CustomerResponse",
                "MessageResponse",
                "HealthResponse",
                "AssessmentReceipt",
                "AssessmentRequest",
                "BookingSectionResponse",
                "CaseAssignmentRequest",
                "CaseAssignmentResponse",
                "CaseDecisionResponse",
                "CasePageResponse",
                "CaseResponse",
                "CaseStaffPageResponse",
                "CaseStaffResponse",
                "CaseSummaryResponse",
                "CustomerAssessmentResponse",
                "EndCaseAssignmentRequest",
                "HorsePageResponse",
                "HorseProfileRequest",
                "HorseResponse",
                "HorseUpdateRequest",
                "OrderChangeResponse",
                "OrderDraftRequest",
                "OrderDraftUpdateRequest",
                "OrderPageResponse",
                "OrderResponse",
                "OrderRevisionRequest",
                "OrderRevisionResponse",
                "OrderServiceIntent",
                "OrderServicePreferences",
                "OrderSnapshot",
                "OrderSubmitRequest",
                "QuarantineAssessmentResult",
                "ServiceChargeResponse",
                "ServiceConfirmationRequest",
                "ServiceConfirmationResponse",
                "ServiceDisclosureResponse",
                "StallFitResult",
                "RequestedServiceInputsSchema")) {
            assertThat(schemas.has(name)).as(name).isTrue();
            assertExamples(schemas, schemas.get(name), name);
        }
        assertThat(schemas.at("/CaseStaffResponse/properties/availabilityStatus"
                + "/enum").toString()).contains("NOT_CONFIGURED")
                .doesNotContain("AVAILABLE");
        assertThat(schemas.at("/OrderSnapshot/properties/operationType/enum")
                .toString()).contains("SUBMIT", "REVISE", "CHANGE");
        assertThat(schemas.at("/OrderServiceIntent/properties/requestedInputs")
                .toString()).contains("RequestedServiceInputsSchema")
                .doesNotContain("additionalProp1");
        assertThat(schemas.at("/OrderDraftRequest/properties/transitAirports"
                + "/items/pattern").asText()).isEqualTo("^[A-Z]{3}$");
        for (String name : List.of("OrderDraftUpdateRequest",
                "OrderRevisionRequest", "HorseUpdateRequest",
                "AssessmentRequest", "ServiceConfirmationRequest")) {
            JsonNode schema = schemas.get(name);
            JsonNode example = schema.get("example");

            for (JsonNode required : schema.path("required")) {
                assertThat(example.hasNonNull(required.asText()))
                        .as(name + "." + required.asText()).isTrue();
            }
        }
        assertJsonMedia(api);
    }

    /**
     * OpenAPI 3.1 nullable references and numeric bounds must remain usable.
     * @throws Exception if the generated contract cannot be read
     */
    @Test
    public void finalOpenApiDetailsMatchFrontendContract() throws Exception {
        JsonNode api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        JsonNode schemas = api.at("/components/schemas");

        assertThat(api.path("openapi").asText()).startsWith("3.1");
        for (String name : List.of("OrderDraftRequest",
                "OrderDraftUpdateRequest", "OrderRevisionRequest")) {
            JsonNode services = schemas.path(name).at("/properties/services");

            assertThat(services.has("$ref")).isFalse();
            assertThat(services.path("anyOf").size()).isEqualTo(2);
            assertThat(services.at("/anyOf/0/$ref").asText())
                    .isEqualTo("#/components/schemas/OrderServicePreferences");
            assertThat(services.at("/anyOf/1/type").asText())
                    .isEqualTo("null");
            assertThat(schemas.path(name).path("example")
                    .path("dateFlexibilityNotes").asText())
                    .isEqualTo("Departure may shift by one day.");
        }
        JsonNode confirmation = schemas.path("ServiceConfirmationRequest");
        JsonNode services = confirmation.at("/properties/services");

        assertThat(services.path("$ref").asText())
                .isEqualTo("#/components/schemas/OrderServicePreferences");
        assertThat(services.path("type").asText()).isNotEqualTo("null");
        assertThat(confirmation.path("required").toString())
                .contains("services");
        for (String name : List.of("EmailRequest", "LoginRequest",
                "RegisterRequest", "ResetPasswordRequest", "TokenRequest",
                "CaseAssignmentRequest", "EndCaseAssignmentRequest",
                "AssessmentRequest")) {
            for (var fields = schemas.path(name).path("properties")
                    .properties().iterator(); fields.hasNext();) {
                JsonNode field = fields.next().getValue();
                String types = field.path("type").toString();

                if (types.contains("string") && !field.has("enum")
                        && !"uuid".equals(field.path("format").asText())
                        && !"date-time".equals(field.path("format").asText())) {
                    assertThat(field.path("minLength").asInt()).as(name)
                            .isGreaterThanOrEqualTo(1);
                }
            }
        }
        for (String name : List.of("HorseResponse", "HorseProfileRequest")) {
            for (String field : List.of("weightKg", "heightCm",
                    "bodyWidthCm")) {
                assertThat(schemas.path(name).path("properties").path(field)
                        .path("minimum").decimalValue())
                        .isEqualByComparingTo("0.01");
            }
        }
        assertThat(schemas.at("/AssessmentRequest/properties/coordinatorId"
                + "/example").asInt()).isEqualTo(32);
        JsonNode slot = schemas.at(
                "/RequestedServiceInputsSchema/properties/slotQuantity");

        assertThat(slot.has("enum")).isFalse();
        assertThat(slot.path("type").toString()).contains("number");
        assertThat(slot.path("description").asText())
                .isEqualTo("Allowed values: 1, 1.5, 3");
    }

    /**
     * Credentials have useful documentation and health has fixed properties.
     * @throws Exception if the HTTP contract cannot be read
     */
    @Test
    public void authAndHealthSchemasAreExplicit() throws Exception {
        JsonNode api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        JsonNode schemas = api.at("/components/schemas");

        for (String name : List.of("EmailRequest", "LoginRequest",
                "RegisterRequest", "CustomerResponse")) {
            JsonNode email = schemas.path(name).at("/properties/email");

            assertThat(email.path("format").asText()).isEqualTo("email");
            assertThat(email.has("pattern")).isFalse();
            assertThat(email.path("example").asText())
                    .isEqualTo("customer@equitransit.com");
        }
        for (String name : List.of("LoginRequest", "RegisterRequest",
                "ResetPasswordRequest")) {
            JsonNode password = schemas.path(name).at("/properties/password");

            assertThat(password.path("example").asText())
                    .isEqualTo("ExamplePass!2026");
            assertThat(password.path("format").asText())
                    .isEqualTo("password");
            assertThat(password.path("writeOnly").asBoolean()).isTrue();
        }
        assertThat(schemas.at("/HealthResponse/additionalProperties")
                .asBoolean(true)).isFalse();
        assertThat(schemas.at("/HealthResponse/properties").size())
                .isEqualTo(2);
        assertThat(api.at("/paths/~1api~1health/get/responses/200/content/"
                + "application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/HealthResponse");
        JsonNode health = mapper.readTree(mvc.perform(get("/api/health"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());

        assertThat(health).isEqualTo(mapper.valueToTree(Map.of(
                "status", "UP", "application", "EquiTransit")));
    }

    /**
     * Email uses standard validation and supplied phone uses strict E164.
     * @throws Exception if test input cannot be parsed
     */
    @Test
    public void authInputExamplesValidateWithoutCustomEmailRegex()
            throws Exception {
        ObjectNode registration = mapper.valueToTree(Map.of(
                "email", "customer@equitransit.com",
                "password", "ExamplePass!2026",
                "confirmPassword", "ExamplePass!2026",
                "fullName", "Alex Nguyen", "phone", "+84901234567"));
        RegisterRequest valid = mapper.treeToValue(registration,
                RegisterRequest.class);
        LoginRequest login = mapper.treeToValue(mapper.valueToTree(Map.of(
                "email", "customer@equitransit.com",
                "password", "ExamplePass!2026")), LoginRequest.class);
        ResetPasswordRequest reset = mapper.treeToValue(
                mapper.valueToTree(Map.of("token", "demo_reset_token",
                        "password", "ExamplePass!2026",
                        "confirmPassword", "ExamplePass!2026")),
                ResetPasswordRequest.class);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(valid)).isEmpty();
            assertThat(validator.validate(login)).isEmpty();
            assertThat(validator.validate(reset)).isEmpty();
            for (String value : List.of("not-an-email", "", "   ")) {
                EmailRequest invalid = new EmailRequest();

                invalid.setEmail(value);
                assertThat(validator.validate(invalid)).isNotEmpty();
                valid.setEmail(value);
                login.setEmail(value);
                assertThat(validator.validate(valid)).isNotEmpty();
                assertThat(validator.validate(login)).isNotEmpty();
            }
            valid.setEmail("customer@equitransit.com");
            valid.setPhone("call me tomorrow");
            assertThat(validator.validate(valid)).isNotEmpty();
            valid.setPhone(null);
            assertThat(validator.validate(valid)).isEmpty();
        }
    }

    /**
     * Query defaults and enum examples must work for both staff surfaces.
     * @throws Exception if the generated API cannot be read
     */
    @Test
    public void managerStaffParametersHaveUsableExamples() throws Exception {
        JsonNode api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        JsonNode schemas = api.at("/components/schemas");
        JsonNode decision = schemas.at(
                "/AssessmentRequest/properties/decision");

        assertThat(decision.path("example").asText())
                .isEqualTo("CHANGES_REQUESTED");
        for (var paths = api.path("paths").properties().iterator();
                paths.hasNext();) {
            var path = paths.next();

            if (!path.getKey().startsWith("/api/manager/")
                    && !path.getKey().startsWith("/api/staff/")) {
                continue;
            }
            for (JsonNode operation : path.getValue()) {
                for (JsonNode param : operation.path("parameters")) {
                    String name = param.path("name").asText();
                    JsonNode schema = param.path("schema");
                    JsonNode example = param.has("example")
                            ? param.get("example") : schema.path("example");

                    if ("page".equals(name)) {
                        assertThat(schema.path("type").asText())
                                .isEqualTo("integer");
                        assertThat(example.asText()).isEqualTo("0");
                        assertThat(schema.path("minimum").asInt())
                                .isZero();
                    } else if ("size".equals(name)) {
                        assertThat(schema.path("type").asText())
                                .isEqualTo("integer");
                        assertThat(example.asText()).isEqualTo("20");
                        assertThat(schema.path("maximum").asInt())
                                .isEqualTo(100);
                    } else if ("role".equals(name)) {
                        assertThat(example.asText()).isEqualTo("SPECIALIST");
                        assertThat(schema.path("enum").toString())
                                .contains("SPECIALIST", "COORDINATOR")
                                .doesNotContain("MANAGER", "DRIVER");
                    }
                }
            }
        }
    }

    /**
     * Java enum types preserve existing JSON codes and stored retry receipts.
     * @throws Exception if enum serialization is incompatible
     */
    @Test
    public void caseEnumsRoundTripWithoutChangingJsonCodes() throws Exception {
        assertThat(CaseAssignmentResponse.class.getMethod("role")
                .getReturnType()).isEqualTo(CaseRole.class);
        assertThat(CaseStaffResponse.class.getMethod("role")
                .getReturnType()).isEqualTo(CaseRole.class);
        assertThat(CaseDecisionResponse.class.getMethod("decision")
                .getReturnType()).isEqualTo(DecisionCode.class);
        for (Class<?> type : List.of(AssessmentReceipt.class,
                CaseResponse.class, CaseSummaryResponse.class)) {
            assertThat(type.getMethod("orderStatus").getReturnType())
                    .isEqualTo(OrderStatus.class);
        }
        for (OrderStatus state : OrderStatus.values()) {
            ObjectNode receipt = (ObjectNode) mapper.readTree(
                    ApiExamples.RECEIPT);

            receipt.put("orderStatus", state.name());
            AssessmentReceipt decoded = mapper.treeToValue(receipt,
                    AssessmentReceipt.class);
            assertThat(decoded.orderStatus()).isEqualTo(state);
            assertThat(mapper.valueToTree(decoded).path("orderStatus").asText())
                    .isEqualTo(state.name());
        }
        AssessmentRequest command = mapper.readValue(ApiExamples.ASSESSMENT,
                AssessmentRequest.class);

        assertThat(command.decision()).isEqualTo(DecisionCode.APPROVED);
        assertThat(mapper.readValue(ApiExamples.RECEIPT,
                AssessmentReceipt.class).decision().decision())
                .isEqualTo(DecisionCode.APPROVED);
    }

    /**
     * Defaults remain coherent with nullable intent and assessment dates.
     * @throws Exception if parsing fails
     */
    @Test
    public void exampleCommandsAreValidAndChronological() throws Exception {
        OrderDraftRequest draft = mapper.readValue(ApiExamples.DRAFT,
                OrderDraftRequest.class);
        HorseProfileRequest horse;
        ObjectNode profile = (ObjectNode) mapper.readTree(ApiExamples.HORSE);

        profile.remove(List.of("horseId", "recordVersion",
                "measurementsComplete"));
        horse = mapper.treeToValue(profile, HorseProfileRequest.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(draft)).isEmpty();
            assertThat(factory.getValidator().validate(horse)).isEmpty();
        }
        assertThat(draft.getPreferredDepartureAt())
                .isBefore(draft.getDeliveryDeadline());
        assertThat(draft.getDeliveryDeadline())
                .isBefore(draft.getEventStartAt());
        assertThat(OffsetDateTime.parse("2026-10-14T14:05:00Z"))
                .isBefore(OffsetDateTime.parse("2026-10-16T18:00:00Z"));
    }

    /**
     * Null is allowed, but supplied malformed/empty optional inputs are not.
     * @throws Exception if parsing fails
     */
    @Test
    public void optionalFormatsRejectInvalidValuesAndAcceptNull()
            throws Exception {
        ObjectNode valid = (ObjectNode) mapper.readTree(ApiExamples.DRAFT);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (var entry : Map.of("receiverEmail", "invalid-email",
                    "receiverPhone", "telephone", "originTimezone", "UTC",
                    "originCountryCode", "vn",
                    "receiverName", "   ").entrySet()) {
                ObjectNode invalid = valid.deepCopy();

                invalid.put(entry.getKey(), entry.getValue());
                assertThat(factory.getValidator().validate(mapper.treeToValue(
                        invalid, OrderDraftRequest.class))).isNotEmpty();
                invalid.put(entry.getKey(), "");
                assertThat(factory.getValidator().validate(mapper.treeToValue(
                        invalid, OrderDraftRequest.class))).isNotEmpty();
            }
            valid.putNull("receiverEmail");
            valid.putNull("receiverPhone");
            valid.putNull("eventTimezone");
            assertThat(factory.getValidator().validate(mapper.treeToValue(
                    valid, OrderDraftRequest.class))).isEmpty();
        }
    }

    /**
     * A documentation schema does not expand/rewrite immutable stored inputs.
     * @throws Exception if serialization fails
     */
    @Test
    public void serviceInputJsonShapeIsPreserved() throws Exception {
        OrderServiceIntent intent = new OrderServiceIntent("GROOMING", "EXTRA",
                "EQUITRANSIT", Map.of("schemaVersion", 1,
                        "status", "REQUESTED", "requestedDays", 2), null, null);
        JsonNode tree = mapper.readTree(mapper.writeValueAsString(intent));

        assertThat(tree.get("requestedInputs").size()).isEqualTo(3);
        assertThat(tree.at("/requestedInputs/requestedDays").asInt())
                .isEqualTo(2);
        assertThat(tree.at("/requestedInputs/policyId").isMissingNode())
                .isTrue();
    }

    private void assertExamples(JsonNode schemas, JsonNode schema,
            String name) {
        JsonNode actual = schema.has("$ref") ? schemas.get(schema.get("$ref")
                .asText().substring("#/components/schemas/".length())) : schema;

        actual.path("properties").properties().forEach(field ->
                assertThat(field.getValue().has("example"))
                        .as(name + "." + field.getKey()).isTrue());
        for (JsonNode parent : actual.path("allOf")) {
            assertExamples(schemas, parent, name);
        }
    }

    private void assertJsonMedia(JsonNode api) {
        api.path("paths").properties().forEach(path -> {
            if (path.getKey().startsWith("/api/horses")
                    || path.getKey().contains("transport-orders")
                    || path.getKey().startsWith("/api/auth/")
                    || path.getKey().startsWith("/api/customers/")
                    || path.getKey().equals("/api/health")) {
                path.getValue().properties().forEach(method ->
                        method.getValue().path("responses").properties()
                                .forEach(response -> {
                                    JsonNode content = response.getValue()
                                            .path("content");

                                    if (!content.isMissingNode()) {
                                        assertThat(content.has("*/*"))
                                                .isFalse();
                                        assertThat(content.has(
                                                "application/json"))
                                                .isTrue();
                                    }
                                }));
            }
        });
    }
}
