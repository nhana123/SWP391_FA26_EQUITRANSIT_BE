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
import com.equitransit.backend.dto.quote.QuotePrepareRequest;
import com.equitransit.backend.dto.quote.QuoteApiExamples;
import com.equitransit.backend.dto.quote.QuoteResponse;
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

    /** @throws Exception if login/refresh JSON drifts from the FE contract */
    @Test
    public void refreshRetainsTheFourFieldLoginResponse() throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var properties = api.at("/components/schemas/LoginResponse/properties");
        assertThat(properties.size()).isEqualTo(4);
        assertThat(properties.has("accessToken")).isTrue();
        assertThat(properties.has("tokenType")).isTrue();
        assertThat(properties.has("expiresIn")).isTrue();
        assertThat(properties.has("role")).isTrue();
        assertThat(api.at("/components/schemas/RefreshError/properties")
                .has("errorCode")).isTrue();
    }

    /** @throws Exception if private F15 staff schemas drift */
    @Test
    public void staffProvisioningContractsAreTypedAndDocumented()
            throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var schemas = api.at("/components/schemas");
        int fields = 0;
        int operations = 0;
        for (String name : List.of("StaffCommand", "StaffCreateRequest",
                "StaffProfileInput", "StaffUpdateRequest",
                "StaffStatusRequest", "StaffView", "StaffPage")) {
            var model = schemas.path(name);
            assertThat(model.isMissingNode()).as(name).isFalse();
            assertActivityModel(model, schemas,
                    new java.util.HashSet<>(), false);
            for (var property : model.path("properties")) {
                assertThat(property.has("example")).as(name).isTrue();
                if (property.path("type").asText().equals("string")
                        && !property.has("enum")
                        && !property.path("format").asText().equals("uuid")) {
                    assertThat(property.path("minLength").asInt())
                            .as(name).isGreaterThanOrEqualTo(1);
                }
                fields++;
            }
        }
        assertThat(schemas.at("/StaffCreateRequest/properties/initialPassword/"
                + "writeOnly").asBoolean()).isTrue();
        assertThat(schemas.path("StaffView").toString())
                .doesNotContain("Password", "Hash", "initialPassword");
        for (var path : api.path("paths").properties()) {
            if (!path.getKey().startsWith("/api/manager/staff")) {
                continue;
            }
            for (var operation : path.getValue().properties()) {
                assertThat(operation.getValue().path("security").toString())
                        .contains("bearerAuth");
                assertThat(operation.getValue().at("/responses/200/content")
                        .has("application/json")).isTrue();
                operations++;
            }
        }
        assertThat(fields).isGreaterThan(30);
        assertThat(operations).isEqualTo(5);
    }

    /** @throws Exception if F09 schemas or media types drift */
    @Test
    public void resourceContractsAreTypedAndDocumented() throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var schemas = api.at("/components/schemas");
        int fields = 0;
        int operations = 0;

        for (var entry : schemas.properties()) {
            if (!entry.getKey().startsWith("Resource")) {
                continue;
            }
            assertActivityModel(entry.getValue(), schemas,
                    new java.util.HashSet<>(), false);
            for (var field : entry.getValue().path("properties")) {
                assertThat(field.has("example")).as(entry.getKey()).isTrue();
                fields++;
            }
        }
        for (var entry : api.path("paths").properties()) {
            if (!entry.getKey().startsWith("/api/manager/resources")
                    && !entry.getKey().startsWith("/api/resources")) {
                continue;
            }
            for (var operation : entry.getValue().properties()) {
                assertThat(operation.getValue().path("security").toString())
                        .contains("bearerAuth");
                assertThat(operation.getValue().at("/responses/200/content")
                        .has("application/json")).isTrue();
                operations++;
            }
        }
        assertThat(fields).isGreaterThan(70);
        assertThat(operations).isEqualTo(18);
        assertThat(api.at("/paths/~1api~1resources~1{kind}"
                + "~1candidates/post/requestBody/content"
                + "/application~1json").isMissingNode()).isFalse();
        assertThat(schemas.path("ResourceCandidatePage")
                .path("properties").has("items")).isTrue();
        assertThat(schemas.at("/ResourceCandidatePage/properties/items/items/"
                + "$ref").asText()).endsWith("/ResourceCandidate");
        assertThat(schemas.at("/ResourceCandidate/properties/recordVersion/"
                + "pattern").asText()).isEqualTo("^[0-9a-fA-F]{16}$");
        var candidate = api.at("/paths/~1api~1resources~1{kind}"
                + "~1candidates/post");

        assertThat(candidate.at("/responses/200/content/application~1json/"
                + "schema/$ref").asText()).endsWith("/ResourceCandidatePage");
        for (var parameter : candidate.path("parameters")) {
            if ("kind".equals(parameter.path("name").asText())) {
                assertThat(parameter.at("/schema/enum").toString())
                        .isEqualTo("[\"VEHICLE\",\"STALL\"]");
            }
            if ("size".equals(parameter.path("name").asText())) {
                assertThat(parameter.at("/schema/maximum").asInt())
                        .isEqualTo(10);
            }
        }
    }

    /** @throws Exception if F10 planning schemas or operations drift */
    @Test
    public void operationalPlanContractsAreTypedAndDocumented()
            throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var schemas = api.at("/components/schemas");
        int fields = 0;
        int operations = 0;

        for (var entry : schemas.properties()) {
            if (!entry.getKey().startsWith("Plan")
                    && !entry.getKey().equals("SharedStallCandidateView")) {
                continue;
            }
            assertActivityModel(entry.getValue(), schemas,
                    new java.util.HashSet<>(), false);
            for (var field : entry.getValue().path("properties")) {
                assertThat(field.has("example")).as(entry.getKey()).isTrue();
                fields++;
            }
        }
        String root = "/api/transport-orders/{orderId}/plans";

        for (var entry : api.path("paths").properties()) {
            if (!entry.getKey().startsWith(root)) {
                continue;
            }
            for (var operation : entry.getValue().properties()) {
                assertThat(operation.getValue().path("security").toString())
                        .contains("bearerAuth");
                assertThat(operation.getValue().at("/responses/200/content")
                        .has("application/json")).isTrue();
                operations++;
            }
        }
        assertThat(fields).isGreaterThan(100);
        assertThat(operations).isEqualTo(14);
    }

    /** @throws Exception if graph schemas lose typed or bounded contracts */
    @Test
    public void journeyGraphContractsAreTypedAndBounded() throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var schemas = api.at("/components/schemas");
        String root = "/api/transport-orders/{orderId}/plans";

        for (String name : List.of("GraphDraftRequest", "GraphLayoutRequest",
                "GraphNodeInput", "GraphNodeDetails", "GraphWireInput",
                "GraphWireView", "GraphNodePosition", "GraphLayout",
                "GraphDriverCandidate", "JourneyGraphView")) {
            assertThat(schemas.has(name)).as(name).isTrue();
            assertActivityModel(schemas.path(name), schemas,
                    new java.util.HashSet<>(), false);
        }
        assertThat(api.path("paths").path(root + "/graph-drafts")
                .at("/post/requestBody/content/application~1json/schema/$ref")
                .asText()).endsWith("/GraphDraftRequest");
        var graph = api.path("paths").path(root + "/{planId}/graph");

        assertThat(graph.at("/get/responses/200/content/application~1json/"
                + "schema/$ref").asText()).endsWith("/JourneyGraphView");
        assertThat(graph.at("/put/requestBody/content/application~1json/"
                + "schema/$ref").asText()).endsWith("/GraphLayoutRequest");
        assertThat(api.path("paths").path(root
                + "/{planId}/legs/{legId}/graph-drivers")
                .at("/get/responses/200/content/application~1json/schema/"
                        + "items/$ref").asText())
                .endsWith("/GraphDriverCandidate");
        assertThat(schemas.at("/GraphDraftRequest/properties/"
                + "checkpointNodes/maxItems").asInt()).isEqualTo(21);
        assertThat(schemas.at("/GraphDraftRequest/properties/wires/maxItems")
                .asInt()).isEqualTo(20);
        assertThat(schemas.at("/GraphNodeInput/properties/locationName/"
                + "maxLength").asInt()).isEqualTo(200);
        assertThat(schemas.at("/GraphLayoutRequest/properties/positions/"
                + "maxItems").asInt()).isEqualTo(21);
        assertThat(schemas.at("/GraphLayout/properties/zoom/minimum")
                .asDouble()).isEqualTo(0.5);
        assertThat(schemas.at("/GraphLayout/properties/zoom/maximum")
                .asDouble()).isEqualTo(1.5);
    }

    /** @throws Exception if F11 departure schemas or operations drift */
    @Test
    public void departureContractsAreTypedAndDocumented() throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var schemas = api.at("/components/schemas");
        int fields = 0;
        int operations = 0;

        for (var entry : schemas.properties()) {
            if (!entry.getKey().startsWith("Departure")
                    && !entry.getKey().startsWith("HorseReceipt")) {
                continue;
            }
            assertActivityModel(entry.getValue(), schemas,
                    new java.util.HashSet<>(), false);
            for (var field : entry.getValue().path("properties")) {
                assertThat(field.has("example")).as(entry.getKey()).isTrue();
                fields++;
            }
        }
        String root = "/api/transport-orders/{orderId}/departure";

        for (var entry : api.path("paths").properties()) {
            if (!entry.getKey().startsWith(root)) {
                continue;
            }
            for (var operation : entry.getValue().properties()) {
                assertThat(operation.getValue().path("security").toString())
                        .contains("bearerAuth");
                assertThat(operation.getValue().at("/responses/200/content")
                        .has("application/json")).isTrue();
                operations++;
            }
        }
        assertThat(fields).isGreaterThan(40);
        assertThat(operations).isEqualTo(5);
    }

    /** @throws Exception if F08 schemas or operations drift */
    @Test
    public void complianceContractsAreTypedPrivateAndDocumented()
            throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var schemas = api.at("/components/schemas");
        int fields = 0;

        for (var entry : schemas.properties()) {
            if (!entry.getKey().startsWith("Compliance")) {
                continue;
            }
            assertActivityModel(entry.getValue(), schemas,
                    new java.util.HashSet<>(), false);
            assertThat(entry.getValue().toString()).doesNotContain(
                    "supplierCost", "actualCost", "internalNotes", "margin");
            for (var field : entry.getValue().path("properties")) {
                assertThat(field.has("example")).as(entry.getKey()).isTrue();
                fields++;
            }
        }
        assertThat(fields).isGreaterThan(80);
        String root = "/api/transport-orders/{order}/compliance";
        int operations = 0;

        for (var entry : api.path("paths").properties()) {
            if (!entry.getKey().startsWith(root)) {
                continue;
            }
            for (var operation : entry.getValue().properties()) {
                assertThat(operation.getValue().path("security").toString())
                        .contains("bearerAuth");
                assertThat(operation.getValue().at(
                        "/responses/200/content").has("application/json"))
                        .isTrue();
                operations++;
            }
        }
        assertThat(operations).isEqualTo(17);
    }

    /** @throws Exception if the F16 HTTP schemas cannot be generated */
    @Test
    public void activityContractsAreTypedScopedAndBounded() throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var schemas = api.at("/components/schemas");

        for (String name : List.of("DashboardResponse", "TaskPage",
                "NotificationPage", "TimelineResponse", "EmailPreference",
                "AuditPage", "FinancialReport")) {
            boolean customer = !List.of("AuditPage", "FinancialReport")
                    .contains(name);

            assertThat(schemas.has(name)).as(name).isTrue();
            assertActivityModel(schemas.path(name), schemas,
                    new java.util.HashSet<>(), customer);
        }
        var routes = Map.of(
                "/api/dashboard", "DashboardResponse",
                "/api/dashboard/tasks", "TaskPage",
                "/api/notifications", "NotificationPage",
                "/api/activity/orders/{orderId}/timeline", "TimelineResponse",
                "/api/manager/audit-events", "AuditPage",
                "/api/manager/reports/financial", "FinancialReport",
                "/api/customers/me/notification-preferences",
                "EmailPreference");

        routes.forEach((path, model) -> {
            var operation = api.path("paths").path(path).path("get");

            assertThat(operation.path("responses").path("200")
                    .path("content").path("application/json")
                    .path("schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/" + model);
        });
        var read = api.path("paths").path("/api/notifications/{id}/read")
                .path("put");

        assertThat(read.path("responses").path("200").path("content")
                .path("application/json").path("schema").path("$ref")
                .asText()).isEqualTo("#/components/schemas/NotificationView");
        var preference = api.path("paths")
                .path("/api/customers/me/notification-preferences").path("put");

        assertThat(preference.path("requestBody").path("content")
                .path("application/json").path("schema").path("$ref")
                .asText()).isEqualTo("#/components/schemas/EmailPreference");
        assertJsonMedia(api);
        assertThat(schemas.at("/DashboardResponse/properties/role/enum")
                .toString()).contains("CUSTOMER", "DRIVER", "MANAGER");
        assertThat(schemas.path("DashboardOrder").toString())
                .doesNotContain("actualCost", "internalNotes", "unitPrice");
        assertThat(schemas.path("TimelinePoint").toString())
                .contains("CLEARED", "DEPARTED", "DELIVERED", "CLOSED");
        for (String name : List.of("quotedValue", "reportedTransfers",
                "netBankReceipts", "netAllocated", "actualCost")) {
            var field = schemas.path("FinancialReport")
                    .path("properties").path(name);

            assertThat(field.path("type").asText()).isEqualTo("string");
            assertThat(field.path("pattern").asText())
                    .isEqualTo("^\\d+(\\.\\d{1,2})?$");
        }
        for (var parameter : api.path("paths").path("/api/dashboard")
                .path("get").path("parameters")) {
            var schema = parameter.path("schema");

            if ("size".equals(parameter.path("name").asText())) {
                assertThat(schema.path("minimum").asInt()).isEqualTo(1);
                assertThat(schema.path("maximum").asInt()).isEqualTo(100);
                assertThat(schema.path("example").asInt()).isEqualTo(20);
            }
        }
    }

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
                "AuditPage",
                "AuditView",
                "DashboardOrder",
                "DashboardResponse",
                "EmailPreference",
                "FinancialReport",
                "NotificationPage",
                "NotificationView",
                "StatusCount",
                "TimelinePoint",
                "TaskView", "TaskPage",
                "TimelineResponse",
                "AllocationCommand",
                "BankReceiptCommand",
                "BankReceiptView",
                "BankReportCommand",
                "PaymentAllocationView",
                "PaymentMutationReceipt",
                "PaymentObligation",
                "PaymentOverview",
                "PaymentProofCommand",
                "PaymentProofReceipt",
                "PaymentProofView",
                "PaymentReportView",
                "PaymentRequestCommand",
                "RefundActionCommand",
                "RefundRequestCommand",
                "RefundView",
                "ReportReviewCommand",
                "ReversalCommand",
                "ServicePreviewRequest", "ServiceRateResponse",
                "TariffWriteRequest", "TariffResponse", "TariffPage",
                "QuoteCommand",
                "QuoteActionRequest",
                "QuotePrepareRequest",
                "QuoteEvidence",
                "QuoteSchedule",
                "QuoteLine",
                "QuoteSummary",
                "QuoteResponse",
                "QuotePage",
                "SupplierOfferRequest",
                "SupplierOfferReceipt",
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
                "RequestedServiceInputsSchema",
                "IncidentReportResponse",
                "OperationalAdjustmentResponse",
                "CustomerAdjustmentResponse",
                "PlanEvolutionReceipt",
                "IntermediateHandoverCreateRequest",
                "FinalDeliveryHandoverCreateRequest",
                "CustomerHandoverDecisionRequest",
                "ActualCostCreateRequest",
                "TripHandoverResponse",
                "CustomerHandoverResponse",
                "OperationalCompletionReceipt",
                "ActualCostResponse",
                "PlanProfitabilityResponse")) {
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
            for (var fields = schemas.path(name).path("properties").fields();
                    fields.hasNext();) {
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
        for (var paths = api.path("paths").fields(); paths.hasNext();) {
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
                                .contains("SPECIALIST", "COORDINATOR");
                        if (path.getKey().equals("/api/manager/staff")) {
                            assertThat(schema.path("enum").toString())
                                    .contains("MANAGER", "DRIVER");
                        } else {
                            assertThat(schema.path("enum").toString())
                                    .doesNotContain("MANAGER", "DRIVER");
                        }
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

    private void assertActivityModel(JsonNode node, JsonNode schemas,
            java.util.Set<String> visited, boolean customer) {
        if (node.isObject()) {
            assertThat(node.toString()).doesNotContain("additionalProp1");
            if (node.has("$ref")) {
                assertThat(node.path("type").toString())
                        .doesNotContain("null");
                String name = node.path("$ref").asText()
                        .replace("#/components/schemas/", "");

                if (visited.add(name)) {
                    assertActivityModel(schemas.path(name), schemas, visited,
                            customer);
                }
            }
            if (node.has("additionalProperties")) {
                assertThat(node.path("additionalProperties").isBoolean())
                        .isTrue();
                assertThat(node.path("additionalProperties").asBoolean())
                        .isFalse();
            }
            if (customer) {
                for (String field : List.of("actualCost", "supplierCost",
                        "unitPrice", "margin", "internalNotes",
                        "sourceReference", "recipientAccount")) {
                    assertThat(node.path("properties").has(field))
                            .as(field).isFalse();
                }
            }
        }
        if (node.isContainerNode()) {
            node.forEach(child -> assertActivityModel(child, schemas,
                    visited, customer));
        }
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

    /**
     * F06 commands have usable examples, bounded text and closed states.
     * @throws Exception if OpenAPI or example decoding fails
     */
    @Test
    public void quotationSchemasMatchTheFrontendContract() throws Exception {
        JsonNode api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        JsonNode schemas = api.at("/components/schemas");
        String base = "/api/transport-orders/{orderId}/quotes";

        assertThat(api.path("paths").has(base)).isTrue();
        assertThat(schemas.get("QuoteResponse").toString())
                .doesNotContain("supplierCost", "margin", "purchaseUnitPrice",
                        "additionalProp1");
        assertThat(schemas.at("/QuoteResponse/properties/paymentSchedule/items"
                + "/$ref").asText()).endsWith("/QuoteScheduleView");
        assertThat(schemas.at("/QuoteScheduleView/properties/scheduleId"
                + "/example").asLong()).isEqualTo(401);
        assertThat(schemas.at("/QuoteSchedule/properties")
                .has("scheduleId")).isFalse();
        ObjectNode legacyReceipt = (ObjectNode) mapper.readTree(
                QuoteApiExamples.RESPONSE);
        ObjectNode legacyInstallment = (ObjectNode) legacyReceipt
                .path("paymentSchedule").get(0);

        legacyInstallment.remove("scheduleId");
        QuoteResponse replay = mapper.treeToValue(legacyReceipt,
                QuoteResponse.class);

        assertThat(replay.paymentSchedule().getFirst().scheduleId())
                .isNull();
        assertThat(replay.paymentSchedule().getFirst().sequenceNo())
                .isEqualTo(1);
        assertThat(schemas.at("/QuotePrepareRequest/properties/terms/minLength")
                .asInt()).isEqualTo(1);
        assertThat(schemas.at("/QuoteCommand/properties/recordVersion/pattern")
                .asText()).isEqualTo("^[0-9a-fA-F]{16}$");
        assertThat(schemas.at("/QuoteSummary/properties/status/enum")
                .toString()).contains("DRAFT", "APPROVED", "ISSUED",
                        "ACCEPTED", "REJECTED", "EXPIRED", "SUPERSEDED");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var command = mapper.readValue(
                    QuoteApiExamples.PREPARE, QuotePrepareRequest.class);

            assertThat(factory.getValidator().validate(command)).isEmpty();
            for (String field : List.of("paymentSchedule", "assessments")) {
                ObjectNode invalid = (ObjectNode) mapper.readTree(
                        QuoteApiExamples.PREPARE);

                invalid.set(field, mapper.readTree("[null]"));
                assertThat(factory.getValidator().validate(
                        mapper.treeToValue(invalid, QuotePrepareRequest.class)))
                        .isNotEmpty();
            }
        }
    }

    /**
     * Manager publication commands and read-only previews have strict schemas.
     * @throws Exception if generated examples fail decoding
     */
    @Test
    public void pricingCommandsHaveUsableValidationAndSchemas()
            throws Exception {
        var body = mapper.createObjectNode();

        body.put("product", "GROOMING");
        body.put("unitPrice", "1.25");
        body.put("sourceReference", "Artificial test publication");
        body.put("operationId", java.util.UUID.randomUUID().toString());
        var type = com.equitransit.backend.dto.pricing.TariffWriteRequest.class;

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(
                    mapper.treeToValue(body, type))).isEmpty();
            body.put("unitPrice", "1.234");
            assertThat(factory.getValidator().validate(
                    mapper.treeToValue(body, type))).isNotEmpty();
        }
        JsonNode api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        assertThat(api.at("/components/schemas/TariffResponse/properties/"
                + "product/anyOf/1/type").asText()).isEqualTo("null");
        var schema = api.at("/components/schemas/ServicePreviewRequest");

        assertThat(schema.path("required").toString()).contains("services");
        assertThat(schema.at("/properties/services/type").asText())
                .isNotEqualTo("null");
        assertThat(api.at("/components/schemas/ServiceRateResponse")
                .toString()).doesNotContain("sourceReference", "purchase");
        var schemas = api.at("/components/schemas");

        assertThat(schemas.at("/TariffWriteRequest/properties/unitCode")
                .toString())
                .contains("SHIPMENT", "SLOT_KM", "HORSE_KM", "ROUTE_KM");
        assertThat(schemas.at("/SupplierOfferRequest/properties/bundleCoverage")
                .toString())
                .contains("ENTIRE_ROUTE", "PARTIAL_ROUTE", "UNKNOWN");
        assertThat(schemas.at("/ServiceRateResponse/properties/allowedUnits")
                .toString()).contains("SHIPMENT");
        for (String name : List.of("TariffWriteRequest",
                "SupplierOfferRequest", "ServicePreviewRequest")) {
            assertActivityModel(schemas.path(name), schemas,
                    new java.util.HashSet<>(), false);
        }
        assertThat(api.path("paths").has("/api/manager/service-tariffs"))
                .isTrue();
        assertThat(api.path("paths").has(
                "/api/transport-orders/{id}/services/preview")).isTrue();
    }

    /** @throws Exception if payment OpenAPI schemas cannot be generated */
    @Test
    public void paymentContractUsesExactTypesAndSafeReadModels()
            throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var schemas = api.at("/components/schemas");

        assertThat(schemas.at("/BankReceiptCommand/properties/"
                + "amountReceived/pattern").asText())
                .isEqualTo("^\\d+(\\.\\d{1,2})?$");
        assertThat(schemas.at("/RefundActionCommand/properties/action/enum")
                .toString()).contains("APPROVE", "REJECT", "COMPLETE");
        var proof = schemas.at("/RefundActionCommand/properties/proofFileKey");

        assertThat(proof.path("minLength").asInt()).isEqualTo(1);
        assertThat(proof.path("maxLength").asInt()).isEqualTo(1024);
        assertThat(schemas.at("/PaymentOverview").toString())
                .doesNotContain("InternalNotes", "RecipientAccount",
                        "additionalProp1");
        assertThat(schemas.at("/PaymentRequestCommand/required").toString())
                .contains("command");
        assertThat(schemas.at("/BankReportCommand/properties/command/type")
                .asText()).isNotEqualTo("null");
        assertThat(api.path("paths").has(
                "/api/transport-orders/{orderId}/payments/refunds"))
                .isTrue();
    }

    /** @throws Exception if the optional proof key cannot be validated */
    @Test
    public void refundProofKeyIsOptionalButBoundedWhenPresent()
            throws Exception {
        var body = mapper.createObjectNode();

        body.put("action", "APPROVE");
        body.set("command", mapper.readTree("{\"recordVersion\":"
                + "\"000000000001A2B3\",\"operationId\":"
                + "\"d86b4f63-5acc-4f20-8da3-e24323268814\"}"));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var type = com.equitransit.backend.dto.payment
                    .RefundActionCommand.class;

            assertThat(validator.validate(mapper.treeToValue(body, type)))
                    .isEmpty();
            for (String value : List.of("", "a".repeat(1025))) {
                body.put("proofFileKey", value);
                assertThat(validator.validate(mapper.treeToValue(body, type)))
                        .anyMatch(v -> v.getPropertyPath().toString()
                                .equals("proofFileKey")
                                && v.getConstraintDescriptor().getAnnotation()
                                .annotationType().equals(jakarta.validation
                                        .constraints.Size.class));
            }
        }
    }

    /**
     * F11 departure schemas have valid examples, bounded strings and no null
     * $ref sibling types.
     * @throws Exception if OpenAPI or example validation fails
     */
    @Test
    public void departureSchemasMatchTheFrontendContract() throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var schemas = api.at("/components/schemas");
        String base = "/api/transport-orders/{orderId}/departure";

        assertThat(api.path("paths").has(base)).isTrue();
        assertThat(api.path("paths").has(base + "/gates/evaluate")).isTrue();
        assertThat(api.path("paths").has(base + "/authorize")).isTrue();
        assertThat(api.path("paths").has(base + "/horse-receipts")).isTrue();
        assertThat(api.path("paths").has(base + "/depart")).isTrue();
        assertThat(schemas.at("/DepartureCommand/properties/recordVersion/"
                + "pattern").asText()).isEqualTo("^[0-9a-fA-F]{16}$");
        assertThat(schemas.at("/HorseReceiptRequest/properties/"
                + "observedMicrochip/minLength").asInt()).isEqualTo(1);
        for (String name : List.of("DepartureGateEvaluateRequest",
                "DepartureAuthorizeRequest", "HorseReceiptRequest",
                "LegDepartRequest", "GateDetailView", "HorseReceiptView",
                "DepartureReadinessResponse")) {
            assertActivityModel(schemas.path(name), schemas,
                    new java.util.HashSet<>(), false);
        }
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var evalReq = mapper.readValue(
                    com.equitransit.backend.dto.departure.DepartureExamples
                            .EVALUATE,
                    com.equitransit.backend.dto.departure.DepartureModels
                            .DepartureGateEvaluateRequest.class);
            var authReq = mapper.readValue(
                    com.equitransit.backend.dto.departure.DepartureExamples
                            .AUTHORIZE,
                    com.equitransit.backend.dto.departure.DepartureModels
                            .DepartureAuthorizeRequest.class);
            var receiptReq = mapper.readValue(
                    com.equitransit.backend.dto.departure.DepartureExamples
                            .RECEIPT,
                    com.equitransit.backend.dto.departure.DepartureModels
                            .HorseReceiptRequest.class);
            var departReq = mapper.readValue(
                    com.equitransit.backend.dto.departure.DepartureExamples
                            .DEPART,
                    com.equitransit.backend.dto.departure.DepartureModels
                            .LegDepartRequest.class);
            var readiness = mapper.readValue(
                    com.equitransit.backend.dto.departure.DepartureExamples
                            .READINESS,
                    com.equitransit.backend.dto.departure.DepartureModels
                            .DepartureReadinessResponse.class);

            assertThat(validator.validate(evalReq)).isEmpty();
            assertThat(validator.validate(authReq)).isEmpty();
            assertThat(validator.validate(receiptReq)).isEmpty();
            assertThat(validator.validate(departReq)).isEmpty();
            assertThat(validator.validate(readiness)).isEmpty();
        }
    }

    private void assertJsonMedia(JsonNode api) {
        api.path("paths").properties().forEach(path -> {
            if (path.getKey().startsWith("/api/horses")
                    || path.getKey().contains("transport-orders")
                    || path.getKey().startsWith("/api/auth/")
                    || path.getKey().startsWith("/api/customers/")
                    || path.getKey().equals("/api/health")
                    || path.getKey().startsWith("/api/dashboard")
                    || path.getKey().startsWith("/api/activity/")
                    || path.getKey().startsWith("/api/notifications")
                    || path.getKey().startsWith("/api/manager/")) {
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
    /** @throws Exception if F12 schema examples/privacy/types drift */
    @Test
    public void executionContractsAreTypedBoundedAndDocumented()
            throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        var schemas = api.path("components").path("schemas");

        for (String name : List.of("TransitionRequest", "MilestoneRequest",
                "WelfareRequest", "ExecutionReceipt", "ExecutionOverview",
                "LegView", "MilestoneView", "WelfareView")) {
            assertThat(schemas.has(name)).as(name).isTrue();
            assertActivityModel(schemas.path(name), schemas,
                    new java.util.HashSet<>(), false);
        }
        assertThat(schemas.at("/ExecutionOverview/properties/recordVersion/"
                + "pattern").asText()).isEqualTo("^[0-9a-fA-F]{16}$");
        var field = schemas.at("/WelfareRequest/properties/feedIntakeKg");

        assertThat(field.path("type").asText()).isEqualTo("string");
        assertThat(field.path("minLength").asInt()).isEqualTo(1);
        assertThat(schemas.at("/MilestoneRequest/properties/sourceCode/enum")
                .toString()).contains("CARRIER");
        assertThat(schemas.at("/TransitionRequest/properties/command/type")
                .asText()).isNotEqualTo("null");
    }

}
