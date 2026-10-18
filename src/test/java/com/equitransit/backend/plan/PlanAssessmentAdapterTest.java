package com.equitransit.backend.plan;

import com.equitransit.backend.entity.pricing.ItineraryPricingContext;
import com.equitransit.backend.repository.pricing.ServiceSourceRepository;
import com.equitransit.backend.service.plan.PlanAssessmentAdapter;
import com.equitransit.backend.service.plan.PlanItineraryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F05/F06 quantities can precede acceptance without approving operations. */
public class PlanAssessmentAdapterTest {

    /** Confirmed sourced BE1 quantities retain their existing meaning. */
    @Test
    public void confirmedSourceDoesNotFallBackToDraft() {
        var source = mock(ServiceSourceRepository.class);
        var feed = mock(PlanItineraryService.class);
        var now = OffsetDateTime.parse("2026-10-18T00:00:00Z");
        var original = new ItineraryPricingContext(311L, BigDecimal.TEN,
                BigDecimal.ZERO, "SOURCED");

        when(source.itinerary(101, 201, now)).thenReturn(original);
        var adapter = new PlanAssessmentAdapter(source, new ObjectMapper(),
                feed);

        assertThat(adapter.itinerary(101, 201L, now)).isEqualTo(original);
        verify(feed, never()).pricing(101, 201);
    }

    /** A sourced draft breaks the quotation/confirmation dependency cycle. */
    @Test
    public void pendingConfirmedFeedCanUseSourcedDraftQuantities() {
        var source = mock(ServiceSourceRepository.class);
        var feed = mock(PlanItineraryService.class);
        var now = OffsetDateTime.parse("2026-10-18T00:00:00Z");

        when(source.itinerary(101, 201, now)).thenReturn(
                new ItineraryPricingContext(null, null, null, "PENDING"));
        when(feed.pricing(101, 201)).thenReturn(new ItineraryPricingContext(
                311L, BigDecimal.TEN, BigDecimal.ZERO, "SOURCED"));
        var adapter = new PlanAssessmentAdapter(source, new ObjectMapper(),
                feed);

        assertThat(adapter.itinerary(101, 201L, now).status())
                .isEqualTo("SOURCED");
        assertThat(adapter.itinerary(101, 201L, now).planId())
                .isEqualTo(311L);
    }
}
