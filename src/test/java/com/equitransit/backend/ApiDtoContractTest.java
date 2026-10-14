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
import com.equitransit.backend.dto.request.OrderDraftRequest;
import com.equitransit.backend.entity.OrderServiceIntent;
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
                    || path.getKey().contains("transport-orders")) {
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
