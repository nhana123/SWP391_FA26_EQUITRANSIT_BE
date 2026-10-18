package com.equitransit.backend.controller.plan;

import com.equitransit.backend.dto.plan.PlanPublicModels.PlanDriverLeg;
import com.equitransit.backend.dto.plan.PlanPublicModels.PlanPublicItinerary;
import com.equitransit.backend.service.plan.PlanItineraryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Role-scoped itinerary projections use explicit DTO allowlists. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Scoped transport itineraries")
public class PlanItineraryController {

    private final PlanItineraryService service;

    /** @param service ownership and assignment boundary */
    public PlanItineraryController(PlanItineraryService service) {
        this.service = service;
    }

    /** @param orderId owned order @return public itinerary */
    @GetMapping(value = "/api/transport-orders/{orderId}/itinerary",
            produces = "application/json")
    public PlanPublicItinerary customer(
            @PathVariable @Positive long orderId) {
        return service.customer(orderId);
    }

    /** @param orderId assigned order @return driver's own ROAD tasks */
    @GetMapping(value = "/api/driver/transport-orders/{orderId}/legs",
            produces = "application/json")
    public List<PlanDriverLeg> driver(
            @PathVariable @Positive long orderId) {
        return service.driver(orderId);
    }
}
