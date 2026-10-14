/*
 * OrderServicePreferences
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.request;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.equitransit.backend.json.DeclaredValueDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.entity.ServiceArrangement;
import com.equitransit.backend.entity.AirStallPreference;
import com.equitransit.backend.entity.QuarantinePackage;
import com.equitransit.backend.entity.InsurancePreference;


/** Bounded typed intent; no prices, carrier fit or mandatory-day approval. */
public class OrderServicePreferences {

    private ServiceArrangement roadArrangement;

    private ServiceArrangement veterinaryArrangement;

    private AirStallPreference airStall;

    private Boolean enhancedCare;

    @Min(0)
    private Integer groomingDays;

    @Min(0)
    private Integer boardingNights;

    private QuarantinePackage quarantinePackage;

    private InsurancePreference insurance;

    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 16, fraction = 2)
    @JsonDeserialize(using = DeclaredValueDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "decimal", example = "50000.00")
    private BigDecimal declaredValue;

    @Size(max = 100)
    private String existingPolicyReference;

    @Size(max = 1000)
    private String escortNotes;

    @Size(max = 200)
    private String roadProviderName;

    @Size(max = 500)
    private String roadProviderContact;

    @Size(max = 200)
    private String veterinaryProviderName;

    @Size(max = 500)
    private String veterinaryProviderContact;

    /** @return requested declared value, not an insurance premium */
    public BigDecimal getDeclaredValue() {
        return declaredValue;
    }

    /** @param value requested USD horse value, never a premium */
    public void setDeclaredValue(BigDecimal value) {
        declaredValue = value;
    }

    /** @return existing policy reference requiring later verification */
    public String getExistingPolicyReference() {
        return existingPolicyReference;
    }

    /** @param value bounded existing policy reference */
    public void setExistingPolicyReference(String value) {
        existingPolicyReference = value;
    }


    /**
     * Returns roadArrangement.
     * @return the roadArrangement
     */
    public ServiceArrangement getRoadArrangement() {
        return roadArrangement;
    }

    /**
     * Sets roadArrangement.
     * @param roadArrangement the value to store
     */
    public void setRoadArrangement(ServiceArrangement roadArrangement) {
        this.roadArrangement = roadArrangement;
    }

    /**
     * Returns veterinaryArrangement.
     * @return the veterinaryArrangement
     */
    public ServiceArrangement getVeterinaryArrangement() {
        return veterinaryArrangement;
    }

    /**
     * Sets veterinaryArrangement.
     * @param veterinaryArrangement the value to store
     */
    public void setVeterinaryArrangement(
            ServiceArrangement veterinaryArrangement) {
        this.veterinaryArrangement = veterinaryArrangement;
    }

    /**
     * Returns airStall.
     * @return the airStall
     */
    public AirStallPreference getAirStall() {
        return airStall;
    }

    /**
     * Sets airStall.
     * @param airStall the value to store
     */
    public void setAirStall(AirStallPreference airStall) {
        this.airStall = airStall;
    }

    /**
     * Returns enhancedCare.
     * @return the enhancedCare
     */
    public Boolean getEnhancedCare() {
        return enhancedCare;
    }

    /**
     * Sets enhancedCare.
     * @param enhancedCare the value to store
     */
    public void setEnhancedCare(Boolean enhancedCare) {
        this.enhancedCare = enhancedCare;
    }

    /**
     * Returns groomingDays.
     * @return the groomingDays
     */
    public Integer getGroomingDays() {
        return groomingDays;
    }

    /**
     * Sets groomingDays.
     * @param groomingDays the value to store
     */
    public void setGroomingDays(Integer groomingDays) {
        this.groomingDays = groomingDays;
    }

    /**
     * Returns boardingNights.
     * @return the boardingNights
     */
    public Integer getBoardingNights() {
        return boardingNights;
    }

    /**
     * Sets boardingNights.
     * @param boardingNights the value to store
     */
    public void setBoardingNights(Integer boardingNights) {
        this.boardingNights = boardingNights;
    }

    /**
     * Returns quarantinePackage.
     * @return the quarantinePackage
     */
    public QuarantinePackage getQuarantinePackage() {
        return quarantinePackage;
    }

    /**
     * Sets quarantinePackage.
     * @param quarantinePackage the value to store
     */
    public void setQuarantinePackage(QuarantinePackage quarantinePackage) {
        this.quarantinePackage = quarantinePackage;
    }

    /**
     * Returns insurance.
     * @return the insurance
     */
    public InsurancePreference getInsurance() {
        return insurance;
    }

    /**
     * Sets insurance.
     * @param insurance the value to store
     */
    public void setInsurance(InsurancePreference insurance) {
        this.insurance = insurance;
    }

    /**
     * Returns escortNotes.
     * @return the escortNotes
     */
    public String getEscortNotes() {
        return escortNotes;
    }

    /**
     * Sets escortNotes.
     * @param escortNotes the value to store
     */
    public void setEscortNotes(String escortNotes) {
        this.escortNotes = escortNotes;
    }

    /**
     * Returns roadProviderName.
     * @return the roadProviderName
     */
    public String getRoadProviderName() {
        return roadProviderName;
    }

    /**
     * Sets roadProviderName.
     * @param roadProviderName the value to store
     */
    public void setRoadProviderName(String roadProviderName) {
        this.roadProviderName = roadProviderName;
    }

    /**
     * Returns roadProviderContact.
     * @return the roadProviderContact
     */
    public String getRoadProviderContact() {
        return roadProviderContact;
    }

    /**
     * Sets roadProviderContact.
     * @param roadProviderContact the value to store
     */
    public void setRoadProviderContact(String roadProviderContact) {
        this.roadProviderContact = roadProviderContact;
    }

    /**
     * Returns veterinaryProviderName.
     * @return the veterinaryProviderName
     */
    public String getVeterinaryProviderName() {
        return veterinaryProviderName;
    }

    /**
     * Sets veterinaryProviderName.
     * @param veterinaryProviderName the value to store
     */
    public void setVeterinaryProviderName(String veterinaryProviderName) {
        this.veterinaryProviderName = veterinaryProviderName;
    }

    /**
     * Returns veterinaryProviderContact.
     * @return the veterinaryProviderContact
     */
    public String getVeterinaryProviderContact() {
        return veterinaryProviderContact;
    }

    /**
     * Sets veterinaryProviderContact.
     * @param veterinaryProviderContact the value to store
     */
    public void setVeterinaryProviderContact(String veterinaryProviderContact) {
        this.veterinaryProviderContact = veterinaryProviderContact;
    }
}
