/*
 * JourneyGraphPolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-21
 * Purpose: Verify graph topology, scheduling and bounded input contracts.
 */
package com.equitransit.backend;

import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphDraftRequest;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphLayout;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphNodeInput;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphWireInput;
import com.equitransit.backend.dto.plan.JourneyGraphModels.NodeType;
import com.equitransit.backend.dto.plan.PlanModels.PlanCommand;
import com.equitransit.backend.dto.plan.PlanModels.PlanDraftRequest;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegInput;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.service.JourneyGraphPolicy;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Meaningful invalid graphs cannot compile into operational TripLeg rows. */
public class JourneyGraphPolicyTest {

    private static final OffsetDateTime START = OffsetDateTime.parse(
            "2027-02-01T08:00:00+07:00");

    /** Dwell and explicit canonical timezone fit an ordered single path. */
    @Test
    public void acceptsConnectedChainAndEquivalentInstants() {
        var request = request();

        assertThatCode(() -> JourneyGraphPolicy.validate(request))
                .doesNotThrowAnyException();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    /** A backwards/cyclic or disconnected wire cannot masquerade as a path. */
    @Test
    public void rejectsCycleBranchAndMissingEndpoint() {
        var request = request();

        assertBad(new GraphDraftRequest(request.draft(),
                request.checkpointNodes(), List.of(
                        new GraphWireInput(1, "origin", "rest"),
                        new GraphWireInput(2, "origin", "destination")),
                request.layout()));
        assertBad(new GraphDraftRequest(request.draft(),
                request.checkpointNodes(), List.of(
                        new GraphWireInput(1, "origin", "rest"),
                        new GraphWireInput(2, "rest", "origin")),
                request.layout()));
    }

    /** SQL Server's case-insensitive keys are rejected before a constraint. */
    @Test
    public void rejectsDuplicateKeysAndExtraOrigin() {
        var request = request();
        var nodes = List.of(request.checkpointNodes().getFirst(),
                node("ORIGIN", NodeType.REST_STOP, 2, "Rest", 60,
                        START.plusHours(1), START.plusHours(2)),
                request.checkpointNodes().getLast());

        assertBad(new GraphDraftRequest(request.draft(), nodes,
                List.of(new GraphWireInput(1, "origin", "ORIGIN"),
                        new GraphWireInput(2, "ORIGIN", "destination")),
                request.layout()));
        nodes = List.of(request.checkpointNodes().getFirst(),
                node("rest", NodeType.ORIGIN, 2, "Rest", 60,
                        START.plusHours(1), START.plusHours(2)),
                request.checkpointNodes().getLast());
        assertBad(new GraphDraftRequest(request.draft(), nodes,
                request.wires(), request.layout()));
    }

    /** Labels and schedule facts cannot diverge from the compiled legs. */
    @Test
    public void rejectsLocationAndDwellMismatch() {
        var request = request();
        var badName = List.of(node("origin", NodeType.ORIGIN, 1,
                "Wrong location", 0, null, START),
                request.checkpointNodes().get(1),
                request.checkpointNodes().getLast());

        assertBad(new GraphDraftRequest(request.draft(), badName,
                request.wires(), request.layout()));
        var badDwell = List.of(request.checkpointNodes().getFirst(),
                node("rest", NodeType.REST_STOP, 2, "Rest", 120,
                        START.plusHours(1), START.plusHours(2)),
                request.checkpointNodes().getLast());

        assertBad(new GraphDraftRequest(request.draft(), badDwell,
                request.wires(), request.layout()));
    }

    /** A wrong country format or oversized viewport is rejected by DTOs. */
    @Test
    public void boundsUserControlledCanvasInput() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = request();
            var invalid = new GraphLayout(new BigDecimal("10"),
                    BigDecimal.ZERO, BigDecimal.ZERO);

            assertThat(validator.validate(new GraphDraftRequest(
                    request.draft(), request.checkpointNodes(),
                    request.wires(), invalid))).isNotEmpty();
        }
    }

    private static GraphDraftRequest request() {
        var nodes = List.of(node("origin", NodeType.ORIGIN, 1,
                "Stable", 0, null, START),
                node("rest", NodeType.REST_STOP, 2, "Rest", 60,
                        START.plusHours(1), START.plusHours(2)),
                node("destination", NodeType.DESTINATION, 3, "Finish", 0,
                        START.plusHours(3), null));
        var legs = List.of(leg(1, "Stable", "Rest", START),
                leg(2, "Rest", "Finish", START.plusHours(2)));

        return new GraphDraftRequest(new PlanDraftRequest(new PlanCommand(
                UUID.randomUUID(), "000000000001A2B3"), 201, true, legs),
                nodes, List.of(new GraphWireInput(1, "origin", "rest"),
                        new GraphWireInput(2, "rest", "destination")),
                new GraphLayout(BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.ZERO));
    }

    private static PlanLegInput leg(int sequence, String from, String to,
            OffsetDateTime start) {
        return new PlanLegInput(sequence, PlanLegMode.ROAD, from, to,
                new BigDecimal("45.000"), "MANUAL_VERIFIED", start,
                start.plusHours(1), "Asia/Ho_Chi_Minh", "Asia/Ho_Chi_Minh",
                null, null, 0, 0, null);
    }

    private static GraphNodeInput node(String key, NodeType type, int sequence,
            String name, int dwell, OffsetDateTime arrival,
            OffsetDateTime departure) {
        return new GraphNodeInput(key, type, sequence, name, "VN", arrival,
                departure, dwell, null, null, null, null, BigDecimal.ZERO,
                BigDecimal.ZERO, null);
    }

    private static void assertBad(GraphDraftRequest request) {
        assertThatThrownBy(() -> JourneyGraphPolicy.validate(request))
                .isInstanceOf(OrderException.class);
    }
}
