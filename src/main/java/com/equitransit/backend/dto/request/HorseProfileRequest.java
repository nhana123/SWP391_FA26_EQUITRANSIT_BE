/*
 * HorseProfileRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement customer-owned horse profiles.
 */
package com.equitransit.backend.dto.request;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.entity.HorseSex;


/** Full editable profile; nullable fields allow an incomplete draft. */
public class HorseProfileRequest {

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 50)
    @Pattern(regexp = "[\\x20-\\x7E]*")
    private String microchipId;

    @Size(max = 50)
    @Pattern(regexp = "[\\x20-\\x7E]*")
    private String passportNumber;

    @Size(max = 100)
    private String breed;

    @PastOrPresent
    private LocalDate dateOfBirth;

    private HorseSex sexCode;

    @Positive
    @Digits(integer = 6, fraction = 2)
    private BigDecimal weightKg;

    @Positive
    @Digits(integer = 4, fraction = 2)
    private BigDecimal heightCm;

    @Positive
    @Digits(integer = 4, fraction = 2)
    private BigDecimal bodyWidthCm;

    @PastOrPresent
    private OffsetDateTime measuredAt;

    @Size(max = 1000)
    private String specialCareNotes;

    @Size(max = 1000)
    private String handlingNotes;

    @Size(max = 1000)
    private String feedWaterRequirements;

    @Size(max = 1000)
    private String stallPreferences;

    @Size(max = 2000)
    private String veterinaryInstructions;

    @Size(max = 1000)
    private String veterinaryInstructionSource;


    /**
     * Returns name.
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets name.
     * @param name the value to store
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns microchipId.
     * @return the microchipId
     */
    public String getMicrochipId() {
        return microchipId;
    }

    /**
     * Sets microchipId.
     * @param microchipId the value to store
     */
    public void setMicrochipId(String microchipId) {
        this.microchipId = microchipId;
    }

    /**
     * Returns passportNumber.
     * @return the passportNumber
     */
    public String getPassportNumber() {
        return passportNumber;
    }

    /**
     * Sets passportNumber.
     * @param passportNumber the value to store
     */
    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    /**
     * Returns breed.
     * @return the breed
     */
    public String getBreed() {
        return breed;
    }

    /**
     * Sets breed.
     * @param breed the value to store
     */
    public void setBreed(String breed) {
        this.breed = breed;
    }

    /**
     * Returns dateOfBirth.
     * @return the dateOfBirth
     */
    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    /**
     * Sets dateOfBirth.
     * @param dateOfBirth the value to store
     */
    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    /**
     * Returns sexCode.
     * @return the sexCode
     */
    public HorseSex getSexCode() {
        return sexCode;
    }

    /**
     * Sets sexCode.
     * @param sexCode the value to store
     */
    public void setSexCode(HorseSex sexCode) {
        this.sexCode = sexCode;
    }

    /**
     * Returns weightKg.
     * @return the weightKg
     */
    public BigDecimal getWeightKg() {
        return weightKg;
    }

    /**
     * Sets weightKg.
     * @param weightKg the value to store
     */
    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    /**
     * Returns heightCm.
     * @return the heightCm
     */
    public BigDecimal getHeightCm() {
        return heightCm;
    }

    /**
     * Sets heightCm.
     * @param heightCm the value to store
     */
    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    /**
     * Returns bodyWidthCm.
     * @return the bodyWidthCm
     */
    public BigDecimal getBodyWidthCm() {
        return bodyWidthCm;
    }

    /**
     * Sets bodyWidthCm.
     * @param bodyWidthCm the value to store
     */
    public void setBodyWidthCm(BigDecimal bodyWidthCm) {
        this.bodyWidthCm = bodyWidthCm;
    }

    /**
     * Returns measuredAt.
     * @return the measuredAt
     */
    public OffsetDateTime getMeasuredAt() {
        return measuredAt;
    }

    /**
     * Sets measuredAt.
     * @param measuredAt the value to store
     */
    public void setMeasuredAt(OffsetDateTime measuredAt) {
        this.measuredAt = measuredAt;
    }

    /**
     * Returns specialCareNotes.
     * @return the specialCareNotes
     */
    public String getSpecialCareNotes() {
        return specialCareNotes;
    }

    /**
     * Sets specialCareNotes.
     * @param specialCareNotes the value to store
     */
    public void setSpecialCareNotes(String specialCareNotes) {
        this.specialCareNotes = specialCareNotes;
    }

    /**
     * Returns handlingNotes.
     * @return the handlingNotes
     */
    public String getHandlingNotes() {
        return handlingNotes;
    }

    /**
     * Sets handlingNotes.
     * @param handlingNotes the value to store
     */
    public void setHandlingNotes(String handlingNotes) {
        this.handlingNotes = handlingNotes;
    }

    /**
     * Returns feedWaterRequirements.
     * @return the feedWaterRequirements
     */
    public String getFeedWaterRequirements() {
        return feedWaterRequirements;
    }

    /**
     * Sets feedWaterRequirements.
     * @param feedWaterRequirements the value to store
     */
    public void setFeedWaterRequirements(String feedWaterRequirements) {
        this.feedWaterRequirements = feedWaterRequirements;
    }

    /**
     * Returns stallPreferences.
     * @return the stallPreferences
     */
    public String getStallPreferences() {
        return stallPreferences;
    }

    /**
     * Sets stallPreferences.
     * @param stallPreferences the value to store
     */
    public void setStallPreferences(String stallPreferences) {
        this.stallPreferences = stallPreferences;
    }

    /**
     * Returns veterinaryInstructions.
     * @return the veterinaryInstructions
     */
    public String getVeterinaryInstructions() {
        return veterinaryInstructions;
    }

    /**
     * Sets veterinaryInstructions.
     * @param veterinaryInstructions the value to store
     */
    public void setVeterinaryInstructions(String veterinaryInstructions) {
        this.veterinaryInstructions = veterinaryInstructions;
    }

    /**
     * Returns veterinaryInstructionSource.
     * @return the veterinaryInstructionSource
     */
    public String getVeterinaryInstructionSource() {
        return veterinaryInstructionSource;
    }

    /**
     * Sets veterinaryInstructionSource.
     * @param veterinaryInstructionSource the value to store
     */
    public void setVeterinaryInstructionSource(
            String veterinaryInstructionSource) {
        this.veterinaryInstructionSource = veterinaryInstructionSource;
    }
}
