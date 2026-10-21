/*
 * JourneyGraphPolicy
 * Author: EquiTransit development team
 * Date: 2026-10-21
 * Purpose: Validate a bounded single-horse itinerary graph before persistence.
 */
package com.equitransit.backend.service;

import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphDraftRequest;
import com.equitransit.backend.dto.plan.JourneyGraphModels.GraphNodeInput;
import com.equitransit.backend.dto.plan.JourneyGraphModels.NodeType;
import com.equitransit.backend.exception.OrderException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/** Pure graph invariants; branches/cycles cannot compile into F10 legs. */
public final class JourneyGraphPolicy {

    private JourneyGraphPolicy() { }

    /** @param request bounded typed compilation input */
    public static void validate(GraphDraftRequest request) {
        var nodes = request.checkpointNodes();
        var wires = request.wires();
        var legs = request.draft().legs();

        OperationalPlanPolicy.validateLegs(legs);
        require(nodes.size() == legs.size() + 1
                && wires.size() == legs.size(), "GRAPH_CHAIN_INVALID");
        var keys = new HashSet<String>();

        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);

            require(keys.add(node.nodeKey().toLowerCase(Locale.ROOT)),
                    "GRAPH_NODE_DUPLICATE");
            require(node.sequenceOrder() == i + 1, "GRAPH_SEQUENCE_INVALID");
            require((i == 0) == (node.nodeType() == NodeType.ORIGIN)
                    && (i == nodes.size() - 1)
                        == (node.nodeType() == NodeType.DESTINATION),
                    "GRAPH_ENDPOINT_INVALID");
            validateNode(node);
            if (i == legs.size()) {
                continue;
            }
            var next = nodes.get(i + 1);
            var wire = wires.get(i);
            var leg = legs.get(i);

            require(wire.sequenceNo() == i + 1
                    && wire.fromNodeKey().equals(node.nodeKey())
                    && wire.toNodeKey().equals(next.nodeKey()),
                    "GRAPH_CHAIN_INVALID");
            require(leg.startPoint().trim().equals(node.locationName().trim())
                    && leg.endPoint().trim().equals(
                            next.locationName().trim()),
                    "GRAPH_LOCATION_MISMATCH");
            require(node.plannedDepartureAt() != null
                    && next.plannedArrivalAt() != null
                    && same(node.plannedDepartureAt(), leg.plannedDeparture())
                    && same(next.plannedArrivalAt(), leg.plannedArrival()),
                    "GRAPH_TIME_MISMATCH");
            require("Asia/Ho_Chi_Minh".equals(leg.startTimezone())
                    && "Asia/Ho_Chi_Minh".equals(leg.endTimezone()),
                    "GRAPH_TIMEZONE_INVALID");
        }
    }

    private static void validateNode(GraphNodeInput node) {
        var arrival = node.plannedArrivalAt();
        var departure = node.plannedDepartureAt();

        require(validTime(arrival) && validTime(departure),
                "GRAPH_TIME_INVALID");
        if (arrival != null && departure != null) {
            require(!departure.isBefore(arrival)
                    && Duration.between(arrival, departure).getSeconds()
                        >= node.dwellMinutes() * 60L,
                    "GRAPH_DWELL_INVALID");
        }
        require(node.nodeType() == NodeType.QUARANTINE
                || node.quarantineReservationId() == null,
                "GRAPH_QUARANTINE_NODE_REQUIRED");
        require(node.nodeType() == NodeType.TRANSIT_HUB
                || node.transitCustomsMode() == null
                    && node.transitPermitRef() == null,
                "GRAPH_TRANSIT_NODE_REQUIRED");
        if (node.nodeType() == NodeType.ORIGIN && node.details() != null) {
            require(!Boolean.FALSE.equals(
                    node.details().requireMicrochipCheck()),
                    "GRAPH_MICROCHIP_CHECK_REQUIRED");
        }
        if (node.nodeType() == NodeType.QUARANTINE) {
            require(arrival != null && departure != null
                    && departure.isAfter(arrival),
                    "GRAPH_QUARANTINE_WINDOW_REQUIRED");
        }
    }

    private static boolean same(OffsetDateTime a, OffsetDateTime b) {
        return a.toInstant().equals(b.toInstant()) && a.getNano() == 0;
    }

    private static boolean validTime(OffsetDateTime time) {
        return time == null || time.getYear() >= 1900
                && time.getYear() <= 9997 && time.getNano() == 0;
    }

    private static void require(boolean valid, String code) {
        if (!valid) {
            throw new OrderException(HttpStatus.BAD_REQUEST, code,
                    "Journey graph does not match the ordered itinerary.");
        }
    }
}
