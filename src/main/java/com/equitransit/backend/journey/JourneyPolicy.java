package com.equitransit.backend.journey;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import com.equitransit.backend.exception.order.OrderException;

/** Pure pricing and lifecycle guards, independent of UI choices. */
public final class JourneyPolicy {
    private JourneyPolicy() {
    }

    /** @param valid invariant @param code stable conflict code */
    public static void require(boolean valid, String code) {
        if (!valid) {
            throw new OrderException(HttpStatus.CONFLICT, code,
                    "Journey flow precondition failed: " + code);
        }
    }

    /** @param ids submitted selections @return validated distinct IDs */
    public static List<Long> distinct(List<Long> ids) {
        require(ids != null && !ids.isEmpty()
                && new HashSet<>(ids).size() == ids.size(), "DUPLICATE_IDS");
        return ids;
    }

    /** @param codes route countries @param origin endpoint @param dest endpoint
     * @return validated ordered transit countries */
    public static List<String> transit(List<String> codes, String origin,
            String dest) {
        var values = codes == null ? List.<String>of() : List.copyOf(codes);
        require(new HashSet<>(values).size() == values.size()
                && values.stream().allMatch(c -> c.matches("[A-Z]{2}")
                        && !c.equals(origin) && !c.equals(dest)),
                "TRANSIT_COUNTRIES_INVALID");
        return values;
    }

    /** @param rates exact active tariffs @param input bounded quantities
     * @return immutable USD estimate lines and total */
    public static Map<String, Object> price(List<Map<String, Object>> rates,
            JourneyModels.Estimate input) {
        distinct(input.tariffIds());
        require(rates.size() == input.tariffIds().size(), "TARIFF_UNAVAILABLE");
        String base = "ROAD".equals(input.mode())
                ? "ROAD_TRANSPORT" : "AIRFARE";
        require(rates.stream().anyMatch(r -> base.equals(r.get("ServiceCode"))),
                "BASE_FARE_REQUIRED");
        var horses = BigDecimal.valueOf(input.horseCount());
        var lines = rates.stream().map(rate -> {
            String unit = rate.get("UnitCode").toString();
            BigDecimal quantity = switch (unit) {
                case "KM" -> input.distanceKm();
                case "HORSE_KM" -> input.distanceKm().multiply(horses);
                case "HORSE", "POLICY" -> horses;
                case "SHIPMENT" -> BigDecimal.ONE;
                default -> throw new OrderException(HttpStatus.CONFLICT,
                        "QUANTITY_ASSESSMENT_REQUIRED",
                        "Tariff quantity needs staff assessment: " + unit);
            };
            var amount = quantity.multiply((BigDecimal) rate.get("UnitPrice"))
                    .setScale(2, RoundingMode.HALF_UP);
            return Map.<String, Object>of("tariffId", rate.get("TariffID"),
                    "service", rate.get("ServiceCode"), "quantity", quantity,
                    "unitPrice", rate.get("UnitPrice"), "amount", amount);
        }).toList();
        BigDecimal total = lines.stream().map(l -> (BigDecimal) l.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        require(total.signum() > 0, "PRICE_REQUIRED");
        require(total.precision() - total.scale() <= 16, "ESTIMATE_TOO_LARGE");
        return Map.of("currency", "USD", "total", total, "lines", lines,
                "estimateOnly", true);
    }
}
