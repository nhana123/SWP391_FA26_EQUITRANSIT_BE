/*
 * OrderServicePreferences
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.dto.request;


import com.equitransit.backend.dto.ApiExamples;
import com.equitransit.backend.dto.ApiPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.equitransit.backend.json.DeclaredValueDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.validation.OptionalText;
import com.equitransit.backend.entity.ServiceArrangement;
import com.equitransit.backend.entity.AirStallPreference;
import com.equitransit.backend.entity.QuarantinePackage;
import com.equitransit.backend.entity.InsurancePreference;


/** Bounded typed intent; no prices, carrier fit or mandatory-day approval. */
@Schema(example = ApiExamples.SERVICES)
public class OrderServicePreferences {

    @Schema(
            example = "CUSTOMER",
            nullable = true)
    private ServiceArrangement roadArrangement;

    @Schema(
            example = "CUSTOMER",
            nullable = true)
    private ServiceArrangement veterinaryArrangement;

    @Schema(
            example = "WIDE",
            nullable = true)
    private AirStallPreference airStall;

    @Schema(
            example = "false",
            nullable = true)
    private Boolean enhancedCare;

    @Min(0)
    @Schema(
            example = "2",
            nullable = true,
            minimum = "0")
    private Integer groomingDays;

    @Min(0)
    @Schema(
            example = "1",
            nullable = true,
            minimum = "0")
    private Integer boardingNights;

    @Schema(
            example = "STANDARD",
            nullable = true)
    private QuarantinePackage quarantinePackage;

    @Schema(
            example = "EXISTING",
            nullable = true)
    private InsurancePreference insurance;

    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 16, fraction = 2)
    @JsonDeserialize(using = DeclaredValueDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(
            example = "50000.00",
            nullable = true,
            pattern = ApiPatterns.DECIMAL,
            type = "string",
            format = "decimal")
    private BigDecimal declaredValue;

    @Size(min = 1, max = 100)
    @Schema(
            example = "POL-2026-0147",
            nullable = true)
    @OptionalText
    private String existingPolicyReference;

    @Size(min = 1, max = 1000)
    @Schema(
            example = "Escort support requested.",
            nullable = true)
    @OptionalText
    private String escortNotes;

    @Size(min = 1, max = 200)
    @Schema(
            example = "Customer road provider",
            nullable = true)
    @OptionalText
    private String roadProviderName;

    @Size(min = 1, max = 500)
    @Pattern(regexp = ApiPatterns.PHONE)
    @Schema(
            example = "+84901234567",
            nullable = true,
            pattern = ApiPatterns.PHONE)
    @OptionalText
    private String roadProviderContact;

    @Size(min = 1, max = 200)
    @Schema(
            example = "Customer veterinary provider",
            nullable = true)
    @OptionalText
    private String veterinaryProviderName;

    @Size(min = 1, max = 500)
    @Pattern(regexp = ApiPatterns.PHONE)
    @Schema(
            example = "+84909876543",
            nullable = true,
            pattern = ApiPatterns.PHONE)
    @OptionalText
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
