/*
 * JourneyGraphServiceTest
 * Author: EquiTransit development team
 * Date: 2026-10-21
 * Purpose: Verify graph scope, redaction, stale writes and replay isolation.
 */
package com.equitransit.backend;

import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphLayout;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphLayoutRequest;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphNodeInput;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphNodePosition;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphWireView;
import com.equitransit.backend.dto.plan.JourneyGraphModels.NodeType;
import com.equitransit.backend.dto.plan.PlanModels.GateState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanCommand;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.PlanSummaryView;
import com.equitransit.backend.entity.CaseOrder;
import com.equitransit.backend.entity.CaseRole;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.json.OrderJsonCodec;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.repository.JourneyGraphRepository;
import com.equitransit.backend.repository.JourneyGraphRepository.GraphScope;
import com.equitransit.backend.repository.OrderRepository;
import com.equitransit.backend.repository.PlanRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.AuditWriter;
import com.equitransit.backend.service.JourneyGraphService;
import com.equitransit.backend.service.OperationalPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Focused authorization and disclosure regression tests for graph APIs. */
public class JourneyGraphServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse(
            "2027-02-01T08:00:00+07:00");
    private final JourneyGraphRepository graphs = mock(
            JourneyGraphRepository.class);
    private final OperationalPlanService planning = mock(
            OperationalPlanService.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final AssessmentRepository cases = mock(AssessmentRepository.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules();
    private JourneyGraphService service;

    /** Initializes only the boundaries touched by these service tests. */
    @BeforeEach
    public void setup() {
        service = new JourneyGraphService(graphs, planning, plans, cases,
                mock(OrderRepository.class), actors,
                new OrderJsonCodec(mapper), mapper, mock(AuditWriter.class));
        when(graphs.ready()).thenReturn(true);
        when(plans.ready()).thenReturn(true);
        when(cases.order(101, false)).thenReturn(Optional.of(parent()));
        when(cases.order(101, true)).thenReturn(Optional.of(parent()));
        when(graphs.scope(101)).thenReturn(Optional.of(
                new GraphScope(12, 31L)));
        when(plans.findPlan(101, 31)).thenReturn(Optional.of(
                summary(PlanState.CONFIRMED)));
        when(graphs.nodes(31)).thenReturn(List.of(node()));
        when(graphs.layout(31)).thenReturn(layout());
        when(graphs.wires(31, null)).thenReturn(List.of(new GraphWireView(
                41, 1, "origin", "destination", PlanLegMode.ROAD,
                LegState.IN_TRANSIT, "Stable", "Airport", BigDecimal.TEN,
                "PRIVATE_DISTANCE_SOURCE", NOW, NOW.plusHours(1),
                NOW.plusMinutes(5), null, 51L, 61L, null, 71L, false)));
    }

    /** Owning a request never grants access to an unpublished draft. */
    @Test
    public void customerCannotReadDraftOrForeignOrder() {
        when(actors.current()).thenReturn(new CurrentActor(1L,
                "CUSTOMER", 99L, null));
        assertThatThrownBy(() -> service.get(101, 31))
                .isInstanceOf(OrderException.class);
        when(actors.current()).thenReturn(new CurrentActor(1L,
                "CUSTOMER", 12L, null));
        when(plans.findPlan(101, 31)).thenReturn(Optional.of(
                summary(PlanState.DRAFT)));
        assertThatThrownBy(() -> service.get(101, 31))
                .isInstanceOf(OrderException.class);
        verify(graphs, never()).wires(anyLong(), any());
    }

    /** An owned published graph contains no internal notes or resource IDs. */
    @Test
    public void customerProjectionHidesInternalFacts() throws Exception {
        when(actors.current()).thenReturn(new CurrentActor(1L,
                "CUSTOMER", 12L, null));
        var result = service.get(101, 31);
        String encoded = mapper.writeValueAsString(result);

        assertThat(result.editable()).isFalse();
        assertThat(result.checkpointNodes().getFirst().transitPermitRef())
                .isNull();
        assertThat(result.checkpointNodes().getFirst().welfareCareNotes())
                .isNull();
        assertThat(result.wires().getFirst().driverStaffId()).isNull();
        assertThat(result.wires().getFirst().vehicleId()).isNull();
        assertThat(result.wires().getFirst().stallEquipmentId()).isNull();
        assertThat(encoded).doesNotContain("PRIVATE_PERMIT", "PRIVATE_CARE",
                "PRIVATE_DISTANCE_SOURCE");
    }

    /** Delivered journeys remain visible without leaking internal facts. */
    @Test
    public void customerCanReadCompletedCurrentGraph() {
        when(actors.current()).thenReturn(new CurrentActor(1L,
                "CUSTOMER", 12L, null));
        when(plans.findPlan(101, 31)).thenReturn(Optional.of(
                summary(PlanState.COMPLETED)));
        var result = service.get(101, 31);

        assertThat(result.planStatus()).isEqualTo(PlanState.COMPLETED);
        assertThat(result.editable()).isFalse();
        assertThat(result.wires().getFirst().vehicleId()).isNull();
    }

    /** Driver visibility requires a real leg assignment and active profile. */
    @Test
    public void driverWithoutAssignmentAndInactiveProfileAreDenied() {
        when(actors.current()).thenReturn(new CurrentActor(1L,
                "DRIVER", null, 61L));
        when(cases.activeStaff(61, "DRIVER")).thenReturn(true);
        when(graphs.activeProfile(61)).thenReturn(true);
        assertThatThrownBy(() -> service.get(101, 31))
                .isInstanceOf(OrderException.class);
        when(graphs.assignedDriver(31, 61)).thenReturn(true);
        when(graphs.activeProfile(61)).thenReturn(false);
        assertThatThrownBy(() -> service.get(101, 31))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Assigned Drivers see real progress without internal planning notes. */
    @Test
    public void assignedDriverGetsReadOnlyProjectedGraph() throws Exception {
        when(actors.current()).thenReturn(new CurrentActor(1L,
                "DRIVER", null, 61L));
        when(cases.activeStaff(61, "DRIVER")).thenReturn(true);
        when(graphs.activeProfile(61)).thenReturn(true);
        when(graphs.assignedDriver(31, 61)).thenReturn(true);
        when(graphs.wires(31, 61L)).thenReturn(List.of(new GraphWireView(
                41, 1, "origin", "destination", PlanLegMode.ROAD,
                LegState.IN_TRANSIT, "Stable", "Airport", BigDecimal.TEN,
                "PRIVATE_DISTANCE_SOURCE", NOW, NOW.plusHours(1),
                NOW.plusMinutes(5), null, 51L, 61L, null, 71L, true)));
        var result = service.get(101, 31);
        var wire = result.wires().getFirst();

        assertThat(result.editable()).isFalse();
        assertThat(wire.assignedToCurrentDriver()).isTrue();
        assertThat(wire.actualDeparture()).isEqualTo(NOW.plusMinutes(5));
        assertThat(wire.vehicleId()).isEqualTo(51L);
        assertThat(wire.driverStaffId()).isNull();
        assertThat(wire.stallEquipmentId()).isNull();
        assertThat(mapper.writeValueAsString(result)).doesNotContain(
                "PRIVATE_PERMIT", "PRIVATE_CARE", "PRIVATE_DISTANCE_SOURCE");
    }

    /** A live Coordinator cannot inspect another Coordinator's case. */
    @Test
    public void unassignedCoordinatorIsDenied() {
        editor("COORDINATOR");
        when(cases.assigned(101, 61, CaseRole.COORDINATOR)).thenReturn(false);
        assertThatThrownBy(() -> service.get(101, 31))
                .isInstanceOf(OrderException.class);
    }

    /** A graph layout write cannot bypass parent compare-and-swap guards. */
    @Test
    public void staleLayoutAndOperationReuseCannotMutate() {
        editor("MANAGER");
        when(plans.findPlan(101, 31)).thenReturn(Optional.of(
                summary(PlanState.DRAFT)));
        var command = new PlanCommand(UUID.randomUUID(), "FFFFFFFFFFFFFFFF");
        var body = new GraphLayoutRequest(command,
                List.of(new GraphNodePosition("origin", BigDecimal.ONE,
                        BigDecimal.ONE)), layout());

        assertThatThrownBy(() -> service.layout(101, 31, body))
                .isInstanceOf(OrderException.class)
                .extracting("code").isEqualTo("ORDER_VERSION_CONFLICT");
        when(plans.operation(101, 1, command.operationId())).thenReturn(
                List.of(Map.of("RequestHash", "other-payload")));
        assertThatThrownBy(() -> service.layout(101, 31, body))
                .isInstanceOf(OrderException.class)
                .extracting("code").isEqualTo("PLAN_RETRY_CONFLICT");
        verify(graphs, never()).positions(anyLong(), any(), any());
    }

    /** An unconverted legacy plan is represented honestly as an empty graph. */
    @Test
    public void legacyPlanDoesNotFabricateNodes() {
        editor("MANAGER");
        when(graphs.nodes(31)).thenReturn(List.of());
        var result = service.get(101, 31);

        assertThat(result.checkpointNodes()).isEmpty();
        assertThat(result.wires()).isEmpty();
        verify(graphs, never()).wires(anyLong(), any());
    }

    private void editor(String role) {
        when(actors.current()).thenReturn(new CurrentActor(1L,
                role, null, 61L));
        when(cases.activeStaff(61, role)).thenReturn(true);
        when(graphs.activeProfile(61)).thenReturn(true);
    }

    private static CaseOrder parent() {
        return new CaseOrder(101, "TEST-GRAPH", "APPROVED",
                "000000000001A2B3", 21L);
    }

    private static PlanSummaryView summary(PlanState state) {
        return new PlanSummaryView(31, 101, 21, 1, state, true, null, 61,
                NOW, NOW.plusHours(1), GateState.PENDING, GateState.PENDING,
                GateState.PENDING);
    }

    private static GraphNodeInput node() {
        return new GraphNodeInput("origin", NodeType.ORIGIN, 1, "Stable", "VN",
                NOW, NOW.plusHours(1), 0, null, "PRIVATE_PERMIT",
                "PRIVATE_CARE", 81L, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    private static GraphLayout layout() {
        return new GraphLayout(BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
