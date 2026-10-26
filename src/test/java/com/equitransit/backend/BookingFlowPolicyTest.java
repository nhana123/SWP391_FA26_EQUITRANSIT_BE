package com.equitransit.backend;

import com.equitransit.backend.dto.request.OrderDraftRequest;
import com.equitransit.backend.dto.response.OrderSnapshot;
import com.equitransit.backend.dto.route.HubDistanceOption;
import com.equitransit.backend.dto.route.RouteMatchRequest;
import com.equitransit.backend.dto.route.RouteMatchResponse;
import com.equitransit.backend.entity.OrderServiceIntent;
import com.equitransit.backend.entity.RouteTransportMode;
import com.equitransit.backend.service.BookingFlowPolicy;
import com.equitransit.backend.service.OperationalPlanPolicy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fixed billing eligibility and exact deposit split without SQL. */
public class BookingFlowPolicyTest {
    private final BookingFlowPolicy policy = new BookingFlowPolicy();

    /** Split once, retain cents in the balance rather than rounding twice. */
    @Test
    public void roundingAndFixedRoadEligibility() {
        var total = policy.fixedRoadTotal(snapshot(RouteTransportMode.ROAD,
                List.of(), true));
        assertThat(total).isEqualByComparingTo("100.05");
        assertThat(policy.deposit(total)).isEqualByComparingTo("30.02");
        assertThat(total.subtract(policy.deposit(total)))
                .isEqualByComparingTo("70.03");
    }

    /** Pending add-ons and unmapped miles require Coordinator pricing. */
    @Test
    public void pendingExtrasAndUnknownTransfersNeverAutoInvoice() {
        var vet = new OrderServiceIntent("VETERINARY", "EQUITRANSIT",
                "EQUITRANSIT", Map.of(), null, null);
        assertThat(policy.fixedRoadTotal(snapshot(RouteTransportMode.ROAD,
                List.of(vet), true))).isNull();
        assertThat(policy.fixedRoadTotal(snapshot(RouteTransportMode.ROAD,
                List.of(), false))).isNull();
        assertThat(policy.fixedRoadTotal(snapshot(RouteTransportMode.AIR,
                List.of(), true))).isNull();
        assertThat(policy.fixedRoadTotal(snapshot(RouteTransportMode.MULTIMODAL,
                List.of(), true))).isNull();
    }

    /** All assignment branches require fully satisfied deposit evidence. */
    @Test
    public void depositIsMandatoryBeforeResourceCommitment() {
        assertThatThrownBy(() -> OperationalPlanPolicy.requireDeposit(false,
                true)).hasMessageContaining("tiền cọc");
        assertThatThrownBy(() -> OperationalPlanPolicy.requireDeposit(false,
                false)).hasMessageContaining("tiền cọc");
        OperationalPlanPolicy.requireDeposit(true, true);
        OperationalPlanPolicy.requireDeposit(true, false);
    }

    private OrderSnapshot snapshot(RouteTransportMode mode,
            List<OrderServiceIntent> services, boolean measured) {
        var draft = new OrderDraftRequest();
        draft.setRoute(new RouteMatchRequest("VN", "Hanoi", null,
                "CN", "Nanning", null, mode, null));
        var option = new HubDistanceOption(1, "Hub", null, null,
                BigDecimal.TEN, BigDecimal.TEN, true,
                measured ? BigDecimal.ZERO : null, BigDecimal.ZERO, true);
        var route = new RouteMatchResponse(true, 1L, mode, "Fixed route",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE,
                new BigDecimal("100.05"), BigDecimal.ZERO,
                new BigDecimal("100.05"), false, "Fixed")
                .withHubOptions(List.of(option), List.of(option), 1, 1);
        return new OrderSnapshot(1, UUID.randomUUID(), "SUBMIT", "hash",
                "0000000000000001", 1, 1, OffsetDateTime.now(), draft,
                null, services, route);
    }
}

