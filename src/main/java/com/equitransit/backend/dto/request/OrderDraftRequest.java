/*
 * OrderDraftRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.request;


import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.entity.ImportType;


/** Editable journey draft; complete submission is checked by the service. */
public class OrderDraftRequest {

    @Positive
    private Long horseId;

    @Size(max = 2)
    @Pattern(regexp = "[A-Za-z]{2}")
    private String originCountryCode;

    @Size(max = 2)
    @Pattern(regexp = "[A-Za-z]{2}")
    private String destinationCountryCode;

    @Size(max = 500)
    private String originAddress;

    @Size(max = 500)
    private String destinationAddress;

    @Size(max = 100)
    @Pattern(regexp = "[\\x20-\\x7E]*")
    private String originTimezone;

    @Size(max = 100)
    @Pattern(regexp = "[\\x20-\\x7E]*")
    private String destinationTimezone;

    private OffsetDateTime preferredDepartureAt;

    private OffsetDateTime deliveryDeadline;

    @Size(max = 500)
    private String dateFlexibilityNotes;

    private ImportType importType;

    @Size(max = 150)
    private String receiverName;

    @Size(max = 30)
    @Pattern(regexp = "[\\x20-\\x7E]*")
    private String receiverPhone;

    @Size(max = 255)
    @Email
    @Pattern(regexp = "[\\x20-\\x7E]*")
    private String receiverEmail;

    @Size(max = 500)
    private String receiverAuthority;

    private OffsetDateTime eventStartAt;

    @Size(max = 200)
    private String eventName;

    @Size(max = 300)
    private String eventVenue;

    @Size(max = 100)
    @Pattern(regexp = "[\\x20-\\x7E]*")
    private String eventTimezone;

    @Size(max = 10)
    private List<@Pattern(regexp = "[A-Za-z]{3}")
            @jakarta.validation.constraints.NotNull String> transitAirports;

    @Size(max = 1000)
    private String specialNeeds;

    @Valid
    private OrderServicePreferences services;


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
}
