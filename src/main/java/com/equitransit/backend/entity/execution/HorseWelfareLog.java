/*
 * HorseWelfareLog
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Persistence model for append-only horse welfare inspection logs.
 */
package com.equitransit.backend.entity.execution;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Represents an immutable, append-only welfare inspection log for a horse. */
public class HorseWelfareLog {

    private Long welfareLogId;
    private Long legId;
    private Long recordedBy;
    private OffsetDateTime checkTime;
    private String healthStatus;
    private BigDecimal temperatureCelsius;
    private BigDecimal waterIntakeLiters;
    private String behaviorNotes;
    private String photoFileKey;
    private BigDecimal feedAmountKg;
    private String careNotes;
    private String instructionFileKey;
    private Long supersedesLogId;

    /** Creates an uninitialized welfare log model. */
    public HorseWelfareLog() { }

    /** @return welfare log PK */
    public Long getWelfareLogId() {
        return welfareLogId;
    }

    /** @param welfareLogId welfare log PK */
    public void setWelfareLogId(Long welfareLogId) {
        this.welfareLogId = welfareLogId;
    }

    /** @return trip leg FK */
    public Long getLegId() {
        return legId;
    }

    /** @param legId trip leg FK */
    public void setLegId(Long legId) {
        this.legId = legId;
    }

    /** @return recording staff FK */
    public Long getRecordedBy() {
        return recordedBy;
    }

    /** @param recordedBy recording staff FK */
    public void setRecordedBy(Long recordedBy) {
        this.recordedBy = recordedBy;
    }

    /** @return inspection check time UTC */
    public OffsetDateTime getCheckTime() {
        return checkTime;
    }

    /** @param checkTime inspection check time UTC */
    public void setCheckTime(OffsetDateTime checkTime) {
        this.checkTime = checkTime;
    }

    /** @return health status code */
    public String getHealthStatus() {
        return healthStatus;
    }

    /** @param healthStatus health status code */
    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }

    /** @return rectal temperature in Celsius */
    public BigDecimal getTemperatureCelsius() {
        return temperatureCelsius;
    }

    /** @param temperatureCelsius rectal temperature in Celsius */
    public void setTemperatureCelsius(BigDecimal temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }

    /** @return observed water intake in liters */
    public BigDecimal getWaterIntakeLiters() {
        return waterIntakeLiters;
    }

    /** @param waterIntakeLiters observed water intake in liters */
    public void setWaterIntakeLiters(BigDecimal waterIntakeLiters) {
        this.waterIntakeLiters = waterIntakeLiters;
    }

    /** @return observed behavior or demeanor notes */
    public String getBehaviorNotes() {
        return behaviorNotes;
    }

    /** @param behaviorNotes observed behavior notes */
    public void setBehaviorNotes(String behaviorNotes) {
        this.behaviorNotes = behaviorNotes;
    }

    /** @return storage key of horse photo */
    public String getPhotoFileKey() {
        return photoFileKey;
    }

    /** @param photoFileKey storage key of horse photo */
    public void setPhotoFileKey(String photoFileKey) {
        this.photoFileKey = photoFileKey;
    }

    /** @return feed amount administered in kg */
    public BigDecimal getFeedAmountKg() {
        return feedAmountKg;
    }

    /** @param feedAmountKg feed amount administered in kg */
    public void setFeedAmountKg(BigDecimal feedAmountKg) {
        this.feedAmountKg = feedAmountKg;
    }

    /** @return special care or medicine notes */
    public String getCareNotes() {
        return careNotes;
    }

    /** @param careNotes special care or medicine notes */
    public void setCareNotes(String careNotes) {
        this.careNotes = careNotes;
    }

    /** @return storage key of veterinary instructions */
    public String getInstructionFileKey() {
        return instructionFileKey;
    }

    /** @param instructionFileKey storage key of instructions */
    public void setInstructionFileKey(String instructionFileKey) {
        this.instructionFileKey = instructionFileKey;
    }

    /** @return previous welfare log PK on the same leg superseded by this */
    public Long getSupersedesLogId() {
        return supersedesLogId;
    }

    /** @param supersedesLogId previous welfare log PK */
    public void setSupersedesLogId(Long supersedesLogId) {
        this.supersedesLogId = supersedesLogId;
    }
}
