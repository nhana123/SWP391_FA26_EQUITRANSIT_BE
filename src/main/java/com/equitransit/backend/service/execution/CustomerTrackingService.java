/*
 * CustomerTrackingService
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Aggregates safe, redacted customer journey tracking timelines.
 */
package com.equitransit.backend.service.execution;

import com.equitransit.backend.dto.execution.response.CustomerMilestoneItem;
import com.equitransit.backend.dto.execution.response.CustomerWelfareItem;
import com.equitransit.backend.dto.execution.response.JourneyTimelineResponse;
import com.equitransit.backend.dto.execution.response.LegTimelineItem;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.HorseWelfareLogRepository;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.repository.execution.LegMilestoneLogRepository;
import com.equitransit.backend.security.DomainAccessPolicy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service synthesizing public redacted tracking timelines for customers. */
@Service
public class CustomerTrackingService {

    private final LegExecutionRepository legs;
    private final LegMilestoneLogRepository milestones;
    private final HorseWelfareLogRepository welfare;
    private final DomainAccessPolicy access;

    /**
     * Creates customer tracking service.
     *
     * @param legs leg execution repository
     * @param milestones milestone repository
     * @param welfare welfare repository
     * @param access domain access policy
     */
    public CustomerTrackingService(LegExecutionRepository legs,
            LegMilestoneLogRepository milestones,
            HorseWelfareLogRepository welfare,
            DomainAccessPolicy access) {
        this.legs = legs;
        this.milestones = milestones;
        this.welfare = welfare;
        this.access = access;
    }

    /**
     * Synthesizes redacted customer tracking timeline for a transport order.
     *
     * @param orderId transport order PK
     * @return journey tracking timeline
     */
    @Transactional(readOnly = true)
    public JourneyTimelineResponse getTrackingTimeline(long orderId) {
        var order = legs.findOrderByOrderId(orderId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                        "Transport order #" + orderId + " was not found."));

        if (access.isCustomer()) {
            access.requireCustomerOwner(order.customerId());
        }

        if (order.currentPlanId() == null) {
            throw new OrderException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
                    "No operational plan assigned to this transport order.");
        }

        var legRows = legs.findLegsByPlanId(order.currentPlanId());
        var timelineLegs = new ArrayList<LegTimelineItem>();

        Long currentLegId = null;
        Integer currentSeq = null;
        String currentMode = null;
        String currentLegStatus = null;
        String currentMilestone = null;
        OffsetDateTime lastUpdated = null;
        OffsetDateTime estimatedArrival = null;
        String latestWelfareStatus = null;
        BigDecimal latestTemp = null;
        String latestPhotoKey = null;

        for (var leg : legRows) {
            var rawMilestones = milestones.findByLegId(leg.legId());
            var customerMilestones = new ArrayList<CustomerMilestoneItem>();

            for (var m : rawMilestones) {
                customerMilestones.add(new CustomerMilestoneItem(
                        m.getMilestoneId(),
                        m.getMilestoneType(),
                        m.getOccurredAt(),
                        m.getLocationDescription()));

                if (lastUpdated == null
                        || m.getOccurredAt().isAfter(lastUpdated)) {
                    lastUpdated = m.getOccurredAt();
                }
                if (m.getEstimatedArrivalAt() != null) {
                    estimatedArrival = m.getEstimatedArrivalAt();
                }
            }

            var activeWelfare = welfare.findActiveByLegId(leg.legId());
            var customerWelfare = new ArrayList<CustomerWelfareItem>();

            for (var w : activeWelfare) {
                customerWelfare.add(new CustomerWelfareItem(
                        w.getCheckTime(),
                        w.getHealthStatus(),
                        w.getTemperatureCelsius(),
                        w.getPhotoFileKey()));

                if (lastUpdated == null
                        || w.getCheckTime().isAfter(lastUpdated)) {
                    lastUpdated = w.getCheckTime();
                }
                latestWelfareStatus = w.getHealthStatus();
                latestTemp = w.getTemperatureCelsius();
                if (w.getPhotoFileKey() != null) {
                    latestPhotoKey = w.getPhotoFileKey();
                }
            }

            if ("IN_PROGRESS".equalsIgnoreCase(leg.legStatus())
                    || (currentLegId == null
                            && !"COMPLETED".equalsIgnoreCase(
                                    leg.legStatus()))) {
                currentLegId = leg.legId();
                currentSeq = leg.sequenceNo();
                currentMode = leg.transportMode();
                currentLegStatus = leg.legStatus();
                if (!rawMilestones.isEmpty()) {
                    currentMilestone = rawMilestones.getLast()
                            .getMilestoneType();
                }
            }

            timelineLegs.add(new LegTimelineItem(
                    leg.legId(),
                    leg.sequenceNo(),
                    leg.transportMode(),
                    leg.startPoint(),
                    leg.endPoint(),
                    leg.legStatus(),
                    leg.actualDeparture(),
                    leg.actualArrival(),
                    customerMilestones,
                    customerWelfare));
        }

        if (currentLegId == null && !legRows.isEmpty()) {
            var lastLeg = legRows.getLast();
            currentLegId = lastLeg.legId();
            currentSeq = lastLeg.sequenceNo();
            currentMode = lastLeg.transportMode();
            currentLegStatus = lastLeg.legStatus();
            var lastMs = milestones.findByLegId(lastLeg.legId());
            if (!lastMs.isEmpty()) {
                currentMilestone = lastMs.getLast().getMilestoneType();
            }
        }

        return new JourneyTimelineResponse(
                orderId,
                order.orderStatus(),
                order.currentPlanId(),
                currentLegId,
                currentSeq,
                currentMode,
                currentLegStatus,
                currentMilestone,
                lastUpdated,
                estimatedArrival,
                latestWelfareStatus,
                latestTemp,
                latestPhotoKey,
                timelineLegs);
    }
}
