/*
 * OrderDraftRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.validation.OptionalText;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import com.equitransit.backend.dto.ApiPatterns;
import com.equitransit.backend.dto.ApiExamples;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.entity.ImportType;


/** Editable journey draft; complete submission is checked by the service. */
@Schema(example = ApiExamples.DRAFT)
public class OrderDraftRequest {

    @Valid
    @Schema(example = "{}", nullable = true,
            description = "Optional city/airport/mode selection for routing")
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private com.equitransit.backend.dto.route.RouteMatchRequest route;

    @Positive
    @Schema(
            example = "12",
            nullable = true,
            minimum = "1")
    private Long horseId;

    @Size(min = 1, max = 2)
    @Pattern(regexp = ApiPatterns.COUNTRY)
    @Schema(
            example = "VN",
            nullable = true,
            pattern = ApiPatterns.COUNTRY)
    @OptionalText
    private String originCountryCode;

    @Size(min = 1, max = 2)
    @Pattern(regexp = ApiPatterns.COUNTRY)
    @Schema(
            example = "SG",
            nullable = true,
            pattern = ApiPatterns.COUNTRY)
    @OptionalText
    private String destinationCountryCode;

    @Size(min = 1, max = 500)
    @Schema(
            example = "12 Nguyen Hue, Ho Chi Minh City",
            nullable = true)
    @OptionalText
    private String originAddress;

    @Size(min = 1, max = 500)
    @Schema(
            example = "1 Turf Club Avenue, Singapore",
            nullable = true)
    @OptionalText
    private String destinationAddress;

    @Size(min = 1, max = 100)
    @Pattern(regexp = ApiPatterns.TIMEZONE)
    @Schema(
            example = "Asia/Ho_Chi_Minh",
            nullable = true,
            pattern = ApiPatterns.TIMEZONE)
    @OptionalText
    private String originTimezone;

    @Size(min = 1, max = 100)
    @Pattern(regexp = ApiPatterns.TIMEZONE)
    @Schema(
            example = "Asia/Singapore",
            nullable = true,
            pattern = ApiPatterns.TIMEZONE)
    @OptionalText
    private String destinationTimezone;

    @Schema(
            example = "2026-11-01T06:00:00Z",
            nullable = true,
            format = "date-time")
    private OffsetDateTime preferredDepartureAt;

    @Schema(
            example = "2026-11-03T18:00:00Z",
            nullable = true,
            format = "date-time")
    private OffsetDateTime deliveryDeadline;

    @Size(min = 1, max = 500)
    @Schema(
            example = "Departure may shift by one day.",
            nullable = true)
    @OptionalText
    private String dateFlexibilityNotes;

    @Schema(
            example = "TEMPORARY",
            nullable = true)
    private ImportType importType;

    @Size(min = 1, max = 150)
    @Schema(
            example = "Alex Nguyen",
            nullable = true)
    @OptionalText
    private String receiverName;

    @Size(min = 1, max = 30)
    @Pattern(regexp = ApiPatterns.PHONE)
    @Schema(
            example = "+6590123456",
            nullable = true,
            pattern = ApiPatterns.PHONE)
    @OptionalText
    private String receiverPhone;

    @Size(min = 1, max = 255)
    @Email
    @Schema(
            example = "receiver@equitransit.com",
            nullable = true,
            format = "email")
    @OptionalText
    private String receiverEmail;

    @Size(min = 1, max = 500)
    @Schema(
            example = "Authorized horse receiver",
            nullable = true)
    @OptionalText
    private String receiverAuthority;

    @Schema(
            example = "2026-11-10T08:00:00Z",
            nullable = true,
            format = "date-time")
    private OffsetDateTime eventStartAt;

    @Size(min = 1, max = 200)
    @Schema(
            example = "Autumn Racing Event",
            nullable = true)
    @OptionalText
    private String eventName;

    @Size(min = 1, max = 300)
    @Schema(
            example = "Singapore",
            nullable = true)
    @OptionalText
    private String eventVenue;

    @Size(min = 1, max = 100)
    @Pattern(regexp = ApiPatterns.TIMEZONE)
    @Schema(
            example = "Asia/Singapore",
            nullable = true,
            pattern = ApiPatterns.TIMEZONE)
    @OptionalText
    private String eventTimezone;

    @Size(max = 10)
    @Schema(
            example = "[\"BKK\"]",
            nullable = true)
    @ArraySchema(schema = @Schema(type = "string",
            pattern = ApiPatterns.AIRPORT, example = "BKK"))
    private List<@Pattern(regexp = ApiPatterns.AIRPORT)
            @jakarta.validation.constraints.NotNull String> transitAirports;

    @Size(min = 1, max = 1000)
    @Schema(
            example = "Calm handling required.",
            nullable = true)
    @OptionalText
    private String specialNeeds;

    @Valid
    @Schema(
            example = ApiExamples.SERVICES)
    private OrderServicePreferences services;

    @Valid
    @Schema(
            example = ApiExamples.HEALTH_DECLARATION,
            nullable = true)
    private HealthDeclaration healthDeclaration;


    /**
     * Returns horseId.
     * @return the horseId
     */
    public Long getHorseId() {
        return horseId;
    }

    /**
     * Sets horseId.
     * @param horseId the value to store
     */
    public void setHorseId(Long horseId) {
        this.horseId = horseId;
    }

    /**
     * Returns originCountryCode.
     * @return the originCountryCode
     */
    public String getOriginCountryCode() {
        return originCountryCode;
    }

    /**
     * Sets originCountryCode.
     * @param originCountryCode the value to store
     */
    public void setOriginCountryCode(String originCountryCode) {
        this.originCountryCode = originCountryCode;
    }

    /**
     * Returns destinationCountryCode.
     * @return the destinationCountryCode
     */
    public String getDestinationCountryCode() {
        return destinationCountryCode;
    }

    /**
     * Sets destinationCountryCode.
     * @param destinationCountryCode the value to store
     */
    public void setDestinationCountryCode(String destinationCountryCode) {
        this.destinationCountryCode = destinationCountryCode;
    }

    /**
     * Returns originAddress.
     * @return the originAddress
     */
    public String getOriginAddress() {
        return originAddress;
    }

    /**
     * Sets originAddress.
     * @param originAddress the value to store
     */
    public void setOriginAddress(String originAddress) {
        this.originAddress = originAddress;
    }

    /**
     * Returns destinationAddress.
     * @return the destinationAddress
     */
    public String getDestinationAddress() {
        return destinationAddress;
    }

    /**
     * Sets destinationAddress.
     * @param destinationAddress the value to store
     */
    public void setDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    /**
     * Returns originTimezone.
     * @return the originTimezone
     */
    public String getOriginTimezone() {
        return originTimezone;
    }

    /**
     * Sets originTimezone.
     * @param originTimezone the value to store
     */
    public void setOriginTimezone(String originTimezone) {
        this.originTimezone = originTimezone;
    }

    /**
     * Returns destinationTimezone.
     * @return the destinationTimezone
     */
    public String getDestinationTimezone() {
        return destinationTimezone;
    }

    /**
     * Sets destinationTimezone.
     * @param destinationTimezone the value to store
     */
    public void setDestinationTimezone(String destinationTimezone) {
        this.destinationTimezone = destinationTimezone;
    }

    /**
     * Returns preferredDepartureAt.
     * @return the preferredDepartureAt
     */
    public OffsetDateTime getPreferredDepartureAt() {
        return preferredDepartureAt;
    }

    /**
     * Sets preferredDepartureAt.
     * @param preferredDepartureAt the value to store
     */
    public void setPreferredDepartureAt(OffsetDateTime preferredDepartureAt) {
        this.preferredDepartureAt = preferredDepartureAt;
    }

    /**
     * Returns deliveryDeadline.
     * @return the deliveryDeadline
     */
    public OffsetDateTime getDeliveryDeadline() {
        return deliveryDeadline;
    }

    /**
     * Sets deliveryDeadline.
     * @param deliveryDeadline the value to store
     */
    public void setDeliveryDeadline(OffsetDateTime deliveryDeadline) {
        this.deliveryDeadline = deliveryDeadline;
    }

    /**
     * Returns dateFlexibilityNotes.
     * @return the dateFlexibilityNotes
     */
    public String getDateFlexibilityNotes() {
        return dateFlexibilityNotes;
    }

    /**
     * Sets dateFlexibilityNotes.
     * @param dateFlexibilityNotes the value to store
     */
    public void setDateFlexibilityNotes(String dateFlexibilityNotes) {
        this.dateFlexibilityNotes = dateFlexibilityNotes;
    }

    /**
     * Returns importType.
     * @return the importType
     */
    public ImportType getImportType() {
        return importType;
    }

    /**
     * Sets importType.
     * @param importType the value to store
     */
    public void setImportType(ImportType importType) {
        this.importType = importType;
    }

    /**
     * Returns receiverName.
     * @return the receiverName
     */
    public String getReceiverName() {
        return receiverName;
    }

    /**
     * Sets receiverName.
     * @param receiverName the value to store
     */
    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    /**
     * Returns receiverPhone.
     * @return the receiverPhone
     */
    public String getReceiverPhone() {
        return receiverPhone;
    }

    /**
     * Sets receiverPhone.
     * @param receiverPhone the value to store
     */
    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    /**
     * Returns receiverEmail.
     * @return the receiverEmail
     */
    public String getReceiverEmail() {
        return receiverEmail;
    }

    /**
     * Sets receiverEmail.
     * @param receiverEmail the value to store
     */
    public void setReceiverEmail(String receiverEmail) {
        this.receiverEmail = receiverEmail;
    }

    /**
     * Returns receiverAuthority.
     * @return the receiverAuthority
     */
    public String getReceiverAuthority() {
        return receiverAuthority;
    }

    /**
     * Sets receiverAuthority.
     * @param receiverAuthority the value to store
     */
    public void setReceiverAuthority(String receiverAuthority) {
        this.receiverAuthority = receiverAuthority;
    }

    /**
     * Returns eventStartAt.
     * @return the eventStartAt
     */
    public OffsetDateTime getEventStartAt() {
        return eventStartAt;
    }

    /**
     * Sets eventStartAt.
     * @param eventStartAt the value to store
     */
    public void setEventStartAt(OffsetDateTime eventStartAt) {
        this.eventStartAt = eventStartAt;
    }

    /**
     * Returns eventName.
     * @return the eventName
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * Sets eventName.
     * @param eventName the value to store
     */
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    /**
     * Returns eventVenue.
     * @return the eventVenue
     */
    public String getEventVenue() {
        return eventVenue;
    }

    /**
     * Sets eventVenue.
     * @param eventVenue the value to store
     */
    public void setEventVenue(String eventVenue) {
        this.eventVenue = eventVenue;
    }

    /**
     * Returns eventTimezone.
     * @return the eventTimezone
     */
    public String getEventTimezone() {
        return eventTimezone;
    }

    /**
     * Sets eventTimezone.
     * @param eventTimezone the value to store
     */
    public void setEventTimezone(String eventTimezone) {
        this.eventTimezone = eventTimezone;
    }

    /**
     * Returns transitAirports.
     * @return the transitAirports
     */
    public List<String> getTransitAirports() {
        return transitAirports;
    }

    /**
     * Sets transitAirports.
     * @param transitAirports the value to store
     */
    public void setTransitAirports(List<String> transitAirports) {
        this.transitAirports = transitAirports;
    }

    /**
     * Returns specialNeeds.
     * @return the specialNeeds
     */
    public String getSpecialNeeds() {
        return specialNeeds;
    }

    /**
     * Sets specialNeeds.
     * @param specialNeeds the value to store
     */
    public void setSpecialNeeds(String specialNeeds) {
        this.specialNeeds = specialNeeds;
    }

    /**
     * Returns services.
     * @return the services
     */
    public OrderServicePreferences getServices() {
        return services;
    }

    /**
     * Sets services.
     * @param services the value to store
     */
    public void setServices(OrderServicePreferences services) {
        this.services = services;
    }

    /**
     * Returns healthDeclaration.
     * @return the healthDeclaration
     */
    public HealthDeclaration getHealthDeclaration() {
        return healthDeclaration;
    }

    /**
     * Sets healthDeclaration.
     * @param healthDeclaration the value to store
     */
    public void setHealthDeclaration(HealthDeclaration healthDeclaration) {
        this.healthDeclaration = healthDeclaration;
    }

    /** @return selected endpoints, never a client-supplied estimate */
    public com.equitransit.backend.dto.route.RouteMatchRequest getRoute() {
        return route;
    }

    /** @param route optional route selection */
    public void setRoute(
            com.equitransit.backend.dto.route.RouteMatchRequest route) {
        this.route = route;
    }
}
