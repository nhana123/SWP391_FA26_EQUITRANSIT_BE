package com.equitransit.backend.service.plan;

import com.equitransit.backend.service.pricing.ServiceAssessmentFacade;


import com.equitransit.backend.entity.pricing.ItineraryPricingContext;
import com.equitransit.backend.repository.pricing.ServiceSourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** F10 supplies sourced draft quantities through the existing BE1 contract. */
@Service
@Primary
public class PlanAssessmentAdapter extends ServiceAssessmentFacade {

    private final PlanItineraryService itinerary;

    /** @param sources original evidence @param mapper existing source codec
     * @param itinerary scoped F10 quantity feed */
    public PlanAssessmentAdapter(ServiceSourceRepository sources,
            ObjectMapper mapper, PlanItineraryService itinerary) {
        super(sources, mapper);
        this.itinerary = itinerary;
    }

    /** @param order authorized parent @param revision exact revision or null
     * @param now server time @return original or sourced-draft quantities */
    @Override
    public ItineraryPricingContext itinerary(long order, Long revision,
            OffsetDateTime now) {
        var original = super.itinerary(order, revision, now);

        if (revision == null || "SOURCED".equals(original.status())) {
            return original;
        }
        return itinerary.pricing(order, revision);
    }
}
