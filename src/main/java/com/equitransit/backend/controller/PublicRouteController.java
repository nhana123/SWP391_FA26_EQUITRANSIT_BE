package com.equitransit.backend.controller;

import com.equitransit.backend.dto.route.RouteMatchRequest;
import com.equitransit.backend.dto.route.RouteMatchResponse;
import com.equitransit.backend.service.RouteTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous route preview; never creates an order, booking or payment. */
@RestController
@RequestMapping("/api/routes")
public class PublicRouteController {
    private final RouteTemplateService service;

    /** @param service route resolver */
    public PublicRouteController(RouteTemplateService service) {
        this.service = service;
    }

    /** @param body endpoints @return indicative match or custom-routing flag */
    @PostMapping("/match")
    @Operation(summary = "Match endpoints and estimate road/air costs in USD")
    public RouteMatchResponse match(@Valid @RequestBody
            RouteMatchRequest body) {
        return service.matchRoute(body);
    }
}
