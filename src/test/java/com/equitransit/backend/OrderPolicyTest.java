/*
 * OrderPolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify journey normalization and safe service-intent contracts.
 */
package com.equitransit.backend;


import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import jakarta.validation.Validation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import com.equitransit.backend.dto.request.HealthDeclaration;
import com.equitransit.backend.dto.request.OrderDraftRequest;
import com.equitransit.backend.entity.ImportType;
import com.equitransit.backend.dto.request.OrderServicePreferences;
import com.equitransit.backend.entity.AirStallPreference;
import com.equitransit.backend.entity.QuarantinePackage;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.json.OrderJsonCodec;
import com.equitransit.backend.service.OrderDraftPolicy;
import com.equitransit.backend.service.OrderServiceIntentFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/** Pure tests without fabricated prices, approvals or supplier thresholds. */
public class OrderPolicyTest {

    private final OrderJsonCodec json = new OrderJsonCodec(new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    private final OrderDraftPolicy policy = new OrderDraftPolicy(Clock.fixed(
            Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC), json);

    /** Partial drafts preserve unknown fields but cannot be submitted. */
    @Test
    public void partialDraftIsNotAssessmentReady() {
        OrderDraftRequest draft = policy.normalize(new OrderDraftRequest());

        assertThat(draft.getHorseId()).isNull();
        assertThat(draft.getTransitAirports()).isEmpty();
        assertThatThrownBy(() -> policy.requireComplete(draft, true))
                .isInstanceOf(OrderException.class);
    }

    /** Countries, airport codes and instants are normalized predictably. */
    @Test
    public void normalizationPreservesUtcMeaning() {
        OrderDraftRequest input = complete();
        OrderDraftRequest normalized;

        input.setOriginCountryCode("us");
        input.setTransitAirports(List.of("sin"));
        input.setPreferredDepartureAt(
                OffsetDateTime.parse("2026-10-15T09:00:00+07:00"));
        normalized = policy.normalize(input);
        assertThat(normalized.getOriginCountryCode()).isEqualTo("US");
        assertThat(normalized.getTransitAirports()).containsExactly("SIN");
        assertThat(normalized.getPreferredDepartureAt()).isEqualTo(
                OffsetDateTime.parse("2026-10-15T02:00:00Z"));
        policy.requireComplete(normalized, true);
    }

    /**
     * Invalid timezone and duplicate transit codes are not silently
     * repaired.
     */
    @Test
    public void invalidRouteMetadataIsRejected() {
        OrderDraftRequest input = complete();

        input.setOriginTimezone("Unknown/Timezone");
        assertThatThrownBy(() -> policy.normalize(input))
                .isInstanceOf(OrderException.class);
        input.setOriginTimezone("UTC");
        input.setTransitAirports(List.of("SIN", "sin"));
        assertThatThrownBy(() -> policy.normalize(input))
                .isInstanceOf(OrderException.class);
    }

    /**
     * Same-country routes and impossible date order are refused at
     * submission.
     */
    @Test
    public void invalidInternationalScheduleCannotSubmit() {
        OrderDraftRequest input = complete();

        input.setDestinationCountryCode("US");
        assertThatThrownBy(() -> policy.requireComplete(input, true))
                .isInstanceOf(OrderException.class);
        input.setPreferredDepartureAt(input.getDeliveryDeadline().plusDays(1));
        assertThatThrownBy(() -> policy.normalize(input))
                .isInstanceOf(OrderException.class);
    }

    /**
     * A past initial deadline is invalid; a proposal does not auto-apply it.
     */
    @Test
    public void submissionRequiresFutureDeadline() {
        OrderDraftRequest input = complete();

        input.setDeliveryDeadline(OffsetDateTime.parse("2026-10-13T00:00:00Z"));
        assertThatThrownBy(() -> policy.requireComplete(input, true))
                .isInstanceOf(OrderException.class);
        policy.requireComplete(input, false);
    }

    /** Preferences are requested inputs without fabricated required days. */
    @Test
    public void requestedQuarantineDoesNotApproveMandatoryDays() {
        OrderServicePreferences preferences = new OrderServicePreferences();
        OrderServiceIntentFactory factory = new OrderServiceIntentFactory();

        preferences.setQuarantinePackage(QuarantinePackage.STANDARD);
        assertThat(factory.requested(preferences).getFirst().requestedInputs())
                .containsEntry("requiredDaysStatus", "UNKNOWN")
                .containsEntry("status", "REQUESTED")
                .doesNotContainKeys("unitPrice", "requiredDays");
    }

    /** One-slot fit cannot be inferred from fixture weights or bare intent. */
    @Test
    public void oneSlotRequiresTheEligibilityWorkflow() {
        OrderServicePreferences preferences = new OrderServicePreferences();

        preferences.setAirStall(AirStallPreference.SHARED_ONE);
        assertThatThrownBy(() -> new OrderServiceIntentFactory()
                .requested(preferences)).isInstanceOf(OrderException.class);
    }

    /**
     * Envelopes and canonical fingerprints survive JSON property reordering.
     */
    @Test
    public void draftSchemaAndCanonicalHashAreStable() {
        OrderDraftRequest input = policy.normalize(complete());

        assertThat(json.readDraft(json.draft(input)).getHorseId())
                .isEqualTo(1L);
        assertThat(json.canonical(Map.of("b", 2, "a", 1)))
                .isEqualTo(json.canonical(Map.of("a", 1, "b", 2)));
        assertThatThrownBy(() -> json.readDraft("{\"schemaVersion\":2}"))
                .isInstanceOf(OrderException.class);
    }

    /** Bean validation covers nested counts and persistence lengths. */
    @Test
    public void nestedInputBoundsAreValidated() {
        OrderDraftRequest input = complete();
        OrderServicePreferences preferences = new OrderServicePreferences();

        preferences.setGroomingDays(-1);
        input.setServices(preferences);
        input.setReceiverName("x".repeat(151));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(input)).hasSize(2);
        }
    }

    /** Valid Java dates outside the SQL range must fail as client input. */
    @Test
    public void sqlDateRangeIsCheckedBeforePersistence() {
        OrderDraftRequest draft = complete();

        draft.setDeliveryDeadline(OffsetDateTime.parse("+10000-01-01T00:00Z"));
        assertThatThrownBy(() -> policy.normalize(draft))
                .isInstanceOf(OrderException.class);
    }

    /** Submission needs routing intent, but receiver/event may be unknown. */
    @Test
    public void submissionRequiresDepartureAndImportType() {
        OrderDraftRequest draft = complete();
        OrderDraftRequest missingDeparture;
        OrderDraftRequest missingImport;

        policy.requireComplete(draft, true);
        draft.setPreferredDepartureAt(null);
        missingDeparture = draft;

        assertThatThrownBy(() -> policy.requireComplete(missingDeparture, true))
                .isInstanceOf(OrderException.class);
        draft = complete();
        draft.setImportType(null);
        missingImport = draft;

        assertThatThrownBy(() -> policy.requireComplete(missingImport, true))
                .isInstanceOf(OrderException.class);
    }

    /** The health statement is trimmed and kept with the request. */
    @Test
    public void healthDeclarationIsNormalizedAndStored() {
        OrderDraftRequest input = complete();
        OrderDraftRequest normalized;

        input.setHealthDeclaration(new HealthDeclaration(true,
                LocalDate.parse("2026-08-20"), "  FEI passport p.12 ", " ",
                true));
        normalized = policy.normalize(input);
        assertThat(normalized.getHealthDeclaration()).isEqualTo(
                new HealthDeclaration(true, LocalDate.parse("2026-08-20"),
                        "FEI passport p.12", null, true));
        assertThat(json.readDraft(json.draft(normalized))
                .getHealthDeclaration()).isEqualTo(
                        normalized.getHealthDeclaration());
    }

    /** A dose date without a vaccinated answer is contradictory. */
    @Test
    public void vaccinationDateNeedsVaccinatedAnswer() {
        OrderDraftRequest input = complete();

        input.setHealthDeclaration(new HealthDeclaration(false,
                LocalDate.parse("2026-08-20"), null, null, true));
        assertThatThrownBy(() -> policy.normalize(input))
                .isInstanceOf(OrderException.class);
    }

    /** Future doses and oversized references fail bean validation. */
    @Test
    public void healthDeclarationBoundsAreValidated() {
        OrderDraftRequest input = complete();

        input.setHealthDeclaration(new HealthDeclaration(true,
                LocalDate.now().plusDays(2), "x".repeat(101), null, true));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(input)).hasSize(2);
        }
    }

    private OrderDraftRequest complete() {
        OrderDraftRequest draft = new OrderDraftRequest();

        draft.setHorseId(1L);
        draft.setImportType(ImportType.TEMPORARY);
        draft.setPreferredDepartureAt(
                OffsetDateTime.parse("2026-10-15T00:00:00Z"));
        draft.setOriginCountryCode("US");
        draft.setDestinationCountryCode("GB");
        draft.setOriginAddress("Pickup");
        draft.setDestinationAddress("Delivery");
        draft.setOriginTimezone("America/New_York");
        draft.setDestinationTimezone("Europe/London");
        draft.setDeliveryDeadline(OffsetDateTime.parse("2026-10-20T00:00:00Z"));
        return draft;
    }
}
