/*
 * Horse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement customer-owned horse profiles.
 */
package com.equitransit.backend.entity.horse;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;


/** JDBC persistence model; SQL Server owns the binary rowversion. */
public class Horse {

    private Long horseId;
    private Long customerId;
    private String name;
    private String microchipId;
    private String passportNumber;
    private String breed;
    private LocalDate dateOfBirth;
    private HorseSex sexCode;
    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private BigDecimal bodyWidthCm;
    private OffsetDateTime measuredAt;
    private String specialCareNotes;
    private String handlingNotes;
    private String feedWaterRequirements;
    private String stallPreferences;
    private String veterinaryInstructions;
    private String veterinaryInstructionSource;
    private String recordVersion;

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
     * Returns customerId.
     * @return the customerId
     */
    public Long getCustomerId() {
        return customerId;
    }

    /**
     * Sets customerId.
     * @param customerId the value to store
     */
    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

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

    /**
     * Returns recordVersion.
     * @return the recordVersion
     */
    public String getRecordVersion() {
        return recordVersion;
    }

    /**
     * Sets recordVersion.
     * @param recordVersion the value to store
     */
    public void setRecordVersion(String recordVersion) {
        this.recordVersion = recordVersion;
    }
}
