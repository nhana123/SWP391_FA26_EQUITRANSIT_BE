package com.equitransit.backend;

import com.equitransit.backend.journey.JourneyModels;
import com.equitransit.backend.journey.JourneyPolicy;
import com.equitransit.backend.exception.order.OrderException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Price estimates never silently omit unavailable or unquantified services. */
public class JourneyPricingTest {
    /** Missing base fare cannot produce a misleading cheap estimate. */
    @Test
    public void rejectsMissingBaseFare() {
        assertThatThrownBy(() -> JourneyPolicy.price(List.of(rate("SHIPMENT",
                "HORSE_CARE")), input()))
                .hasMessageContaining("BASE_FARE_REQUIRED");
    }

    /** Unsupported quantity units require an explicit staff assessment. */
    @Test
    public void rejectsUnknownQuantities() {
        assertThatThrownBy(() -> JourneyPolicy.price(List.of(rate("HORSE_DAY",
                "ROAD_TRANSPORT")), input()))
                .isInstanceOfSatisfying(OrderException.class, e ->
                        assertThat(e.getCode())
                                .isEqualTo("QUANTITY_ASSESSMENT_REQUIRED"));
    }

    /** Per-horse per-km rates multiply both dimensions. */
    @Test
    public void calculatesHorseKilometres() {
        var price = JourneyPolicy.price(List.of(rate("HORSE_KM",
                "ROAD_TRANSPORT")), input());
        assertThat((BigDecimal) price.get("total"))
                .isEqualByComparingTo("400.00");
    }

    /** A disappeared tariff is not silently priced at zero. */
    @Test
    public void rejectsMissingTariff() {
        assertThatThrownBy(() -> JourneyPolicy.price(List.of(), input()))
                .hasMessageContaining("TARIFF_UNAVAILABLE");
    }

    private JourneyModels.Estimate input() {
        return new JourneyModels.Estimate("ROAD", new BigDecimal("100"), 2,
                List.of(1L));
    }

    private Map<String, Object> rate(String unit, String service) {
        return Map.of("TariffID", 1L, "UnitCode", unit,
                "ServiceCode", service, "UnitPrice", new BigDecimal("2"));
    }
}
