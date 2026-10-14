/*
 * HorseProfileRequest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement customer-owned horse profiles.
 */
package com.equitransit.backend.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.validation.OptionalText;
import com.equitransit.backend.dto.ApiExamples;
import com.equitransit.backend.dto.ApiPatterns;
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
@Schema(example = ApiExamples.PROFILE)
public class HorseProfileRequest {

    @NotBlank
    @Size(min = 1, max = 150)
    @Schema(
            example = "Thunder Strike",
            nullable = true)
    private String name;

    @Size(min = 1, max = 50)
    @Pattern(regexp = ApiPatterns.MICROCHIP)
    @Schema(
            example = "985112003456789",
            nullable = true,
            pattern = ApiPatterns.MICROCHIP)
    @OptionalText
    private String microchipId;

    @Size(min = 1, max = 50)
    @Pattern(regexp = ApiPatterns.PASSPORT)
    @Schema(
            example = "FEI-10459921",
            nullable = true,
            pattern = ApiPatterns.PASSPORT)
    @OptionalText
    private String passportNumber;

    @Size(min = 1, max = 100)
    @Schema(
            example = "Thoroughbred",
            nullable = true)
    @OptionalText
    private String breed;

    @PastOrPresent
    @Schema(
            example = "2019-04-15",
            nullable = true,
            format = "date")
    private LocalDate dateOfBirth;

    @Schema(
            example = "GELDING",
            nullable = true)
    private HorseSex sexCode;

    @Positive
    @Digits(integer = 6, fraction = 2)
    @Schema(
            example = "520.00",
            nullable = true,
            minimum = "0",
            exclusiveMinimum = true)
    private BigDecimal weightKg;

    @Positive
    @Digits(integer = 4, fraction = 2)
    @Schema(
            example = "165.00",
            nullable = true,
            minimum = "0",
            exclusiveMinimum = true)
    private BigDecimal heightCm;

    @Positive
    @Digits(integer = 4, fraction = 2)
    @Schema(
            example = "80.00",
            nullable = true,
            minimum = "0",
            exclusiveMinimum = true)
    private BigDecimal bodyWidthCm;

    @PastOrPresent
    @Schema(
            example = "2026-10-13T08:00:00Z",
            nullable = true,
            format = "date-time")
    private OffsetDateTime measuredAt;

    @Size(min = 1, max = 1000)
    @Schema(
            example = "Calm handling required.",
            nullable = true)
    @OptionalText
    private String specialCareNotes;

    @Size(min = 1, max = 1000)
    @Schema(
            example = "Lead slowly.",
            nullable = true)
    @OptionalText
    private String handlingNotes;

    @Size(min = 1, max = 1000)
    @Schema(
            example = "Provide fresh water during stops.",
            nullable = true)
    @OptionalText
    private String feedWaterRequirements;

    @Size(min = 1, max = 1000)
    @Schema(
            example = "Wide stall requested.",
            nullable = true)
    @OptionalText
    private String stallPreferences;

    @Size(min = 1, max = 2000)
    @Schema(
            example = "Follow the supplied veterinary instructions.",
            nullable = true)
    @OptionalText
    private String veterinaryInstructions;

    @Size(min = 1, max = 1000)
    @Schema(
            example = "Owner-provided veterinary note.",
            nullable = true)
    @OptionalText
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
