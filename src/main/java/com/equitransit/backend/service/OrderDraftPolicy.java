/*
 * OrderDraftPolicy
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Normalize editable data and validate assessment-ready journeys.
 */
package com.equitransit.backend.service;


import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.equitransit.backend.dto.request.HealthDeclaration;
import com.equitransit.backend.dto.request.OrderDraftRequest;
import com.equitransit.backend.entity.OrderWriteData;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.json.OrderJsonCodec;


/** Validation here is journey input validation, never legal clearance. */
@Component
public class OrderDraftPolicy {

    private static final Set<String> COUNTRIES =
            Set.of(Locale.getISOCountries());

    private final Clock clock;
    private final OrderJsonCodec json;

    /**
     * Creates the input policy.
     * @param clock the application's UTC clock
     * @param json the versioned persistence codec
     */
    public OrderDraftPolicy(Clock clock, OrderJsonCodec json) {
        this.clock = clock;
        this.json = json;
    }

    /**
     * Copies only editable fields and normalizes blanks, country codes and UTC.
     * @param source the validated draft or replacement command
     * @return an independent base request without client metadata
     */
    public OrderDraftRequest normalize(OrderDraftRequest source) {
        OrderDraftRequest draft = new OrderDraftRequest();
        List<String> airports = source.getTransitAirports() == null ? List.of()
                : source.getTransitAirports().stream()
                        .map(code -> code.toUpperCase(Locale.ROOT)).toList();

        draft.setHorseId(source.getHorseId());
        draft.setOriginCountryCode(country(source.getOriginCountryCode()));
        draft.setDestinationCountryCode(country(
                source.getDestinationCountryCode()));
        draft.setOriginAddress(optional(source.getOriginAddress()));
        draft.setDestinationAddress(optional(source.getDestinationAddress()));
        draft.setOriginTimezone(timezone(source.getOriginTimezone()));
        draft.setDestinationTimezone(timezone(source.getDestinationTimezone()));
        draft.setPreferredDepartureAt(utc(source.getPreferredDepartureAt()));
        draft.setDeliveryDeadline(utc(source.getDeliveryDeadline()));
        draft.setDateFlexibilityNotes(optional(
                source.getDateFlexibilityNotes()));
        draft.setImportType(source.getImportType());
        draft.setReceiverName(optional(source.getReceiverName()));
        draft.setReceiverPhone(optional(source.getReceiverPhone()));
        draft.setReceiverEmail(optional(source.getReceiverEmail()));
        draft.setReceiverAuthority(optional(source.getReceiverAuthority()));
        draft.setEventStartAt(utc(source.getEventStartAt()));
        draft.setEventName(optional(source.getEventName()));
        draft.setEventVenue(optional(source.getEventVenue()));
        draft.setEventTimezone(timezone(source.getEventTimezone()));
        draft.setTransitAirports(airports);
        draft.setSpecialNeeds(optional(source.getSpecialNeeds()));
        draft.setServices(source.getServices());
        draft.setHealthDeclaration(declaration(source.getHealthDeclaration()));
        draft.setRoute(source.getRoute());
        if (new HashSet<>(airports).size() != airports.size()) {
            throw invalid("Transit airports must not be duplicated.");
        }
        validateSequence(draft);
        return draft;
    }

    /**
     * Requires enough route/horse/time data for staff assessment.
     * Receiver, event details and provider contacts may still be unknown.
     * @param draft the normalized request
     * @param enforceFuture whether this is a submission rather than a proposal
     */
    public void requireComplete(OrderDraftRequest draft,
            boolean enforceFuture) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (draft.getHorseId() == null || draft.getOriginCountryCode() == null
                || draft.getDestinationCountryCode() == null
                || draft.getOriginAddress() == null
                || draft.getDestinationAddress() == null
                || draft.getOriginTimezone() == null
                || draft.getDestinationTimezone() == null
                || draft.getPreferredDepartureAt() == null
                || draft.getDeliveryDeadline() == null
                || draft.getImportType() == null) {
            throw invalid("Complete horse, route, addresses, timezones and "
                    + "departure, delivery deadline and import type "
                            + "before submission.");
        }
        if (draft.getOriginCountryCode().equals(
                draft.getDestinationCountryCode())) {
            throw invalid("This workflow requires an international route.");
        }
        if (enforceFuture && (!draft.getDeliveryDeadline().isAfter(now)
                || draft.getPreferredDepartureAt() != null
                && !draft.getPreferredDepartureAt().isAfter(now))) {
            throw invalid("Submission dates must be in the future.");
        }
    }

    /**
     * Maps normalized fields explicitly to their existing SQL columns.
     * @param draft the normalized editable data
     * @return the typed persistence write model
     */
    public OrderWriteData writeData(OrderDraftRequest draft) {
        return new OrderWriteData(draft.getOriginCountryCode(),
                draft.getDestinationCountryCode(), draft.getOriginAddress(),
                draft.getDestinationAddress(), draft.getOriginTimezone(),
                draft.getDestinationTimezone(),
                local(draft.getPreferredDepartureAt()),
                local(draft.getDeliveryDeadline()),
                draft.getDateFlexibilityNotes(),
                draft.getImportType() == null ? null
                        : draft.getImportType().name(),
                draft.getReceiverName(), draft.getReceiverPhone(),
                draft.getReceiverEmail(), draft.getReceiverAuthority(),
                local(draft.getEventStartAt()), draft.getEventName(),
                draft.getEventVenue(), draft.getEventTimezone(),
                json.write(draft.getTransitAirports()), json.draft(draft));
    }

    private void validateSequence(OrderDraftRequest draft) {
        if (draft.getPreferredDepartureAt() != null
                && draft.getDeliveryDeadline() != null
                && !draft.getPreferredDepartureAt().isBefore(
                        draft.getDeliveryDeadline())) {
            throw invalid("Preferred departure must precede "
                    + "delivery deadline.");
        }
        if (draft.getEventStartAt() != null
                && draft.getDeliveryDeadline() != null
                && draft.getDeliveryDeadline().isAfter(
                        draft.getEventStartAt())) {
            throw invalid("Delivery deadline must not follow the event start.");
        }
    }

    /** Trims text; a vaccination date needs a vaccinated answer. */
    private HealthDeclaration declaration(HealthDeclaration value) {
        if (value == null) {
            return null;
        }
        if (value.lastInfluenzaVaccinationDate() != null
                && !Boolean.TRUE.equals(value.influenzaVaccinated())) {
            throw invalid("A vaccination date needs a vaccinated answer.");
        }
        return new HealthDeclaration(value.influenzaVaccinated(),
                value.lastInfluenzaVaccinationDate(),
                optional(value.vaccinationRecordReference()),
                optional(value.healthNotes()), value.declaredAccurate());
    }

    private String country(String value) {
        String normalized = optional(value);

        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!COUNTRIES.contains(normalized)) {
            throw invalid("Use an ISO country code supported by the API.");
        }
        return normalized;
    }

    private String timezone(String value) {
        String normalized = optional(value);

        if (normalized != null && !ZoneId.getAvailableZoneIds()
                .contains(normalized)) {
            throw invalid("Use a supported named timezone, such as "
                    + "Europe/London or UTC.");
        }
        return normalized;
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private OffsetDateTime utc(OffsetDateTime value) {
        OffsetDateTime normalized;

        if (value == null) {
            return null;
        }
        if (value.getYear() < 1 || value.getYear() > 9999) {
            throw invalid("Dates must be within SQL Server's supported range.");
        }
        normalized = value.withOffsetSameInstant(ZoneOffset.UTC).withNano(0);
        if (normalized.getYear() < 1 || normalized.getYear() > 9999) {
            throw invalid("UTC dates must be within the supported range.");
        }
        return normalized;
    }

    private LocalDateTime local(OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private OrderException invalid(String message) {
        return new OrderException(HttpStatus.BAD_REQUEST,
                "ORDER_INPUT_INVALID", message);
    }
}
