/*
 * HorseWelfareService
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Append-only audit engine for live horse welfare assessments.
 */
package com.equitransit.backend.service.execution;

import com.equitransit.backend.dto.execution.HealthStatus;
import com.equitransit.backend.dto.execution.request.HorseWelfareLogCorrectionRequest;
import com.equitransit.backend.dto.execution.request.HorseWelfareLogCreateRequest;
import com.equitransit.backend.dto.execution.response.HorseWelfareLogResponse;
import com.equitransit.backend.dto.execution.response.WelfareAuditHistoryResponse;
import com.equitransit.backend.entity.execution.HorseWelfareLog;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.execution.HorseWelfareLogRepository;
import com.equitransit.backend.repository.execution.LegExecutionRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Append-only audit service for live horse vital checks and corrections. */
@Service
public class HorseWelfareService {

    private static final BigDecimal FEVER_THRESHOLD = new BigDecimal("38.50");

    private final HorseWelfareLogRepository welfare;
    private final LegExecutionRepository legs;
    private final CurrentActorService actors;
    private final Clock clock;

    /**
     * Creates horse welfare service.
     *
     * @param welfare welfare log repository
     * @param legs leg execution repository
     * @param actors current actor resolver
     * @param clock system UTC clock
     */
    public HorseWelfareService(HorseWelfareLogRepository welfare,
            LegExecutionRepository legs, CurrentActorService actors,
            Clock clock) {
        this.welfare = welfare;
        this.legs = legs;
        this.actors = actors;
        this.clock = clock;
    }

    /**
     * Records a new immutable horse welfare check.
     *
     * @param legId trip leg PK
     * @param req welfare check payload
     * @return recorded welfare response
     */
    @Transactional
    public HorseWelfareLogResponse recordWelfareLog(long legId,
            HorseWelfareLogCreateRequest req) {
        CurrentActor actor = actors.current();
        requireLeg(legId);
        authorizeWelfareRecorder(actor, legId);

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime checkTime = req.checkTime() != null
                ? req.checkTime() : now;
        validateCheckTime(checkTime, now);

        var entity = new HorseWelfareLog();
        entity.setLegId(legId);
        entity.setRecordedBy(actor.staffId());
        entity.setCheckTime(checkTime);
        entity.setHealthStatus(req.healthStatus().name());
        entity.setTemperatureCelsius(req.temperatureCelsius());
        entity.setWaterIntakeLiters(req.waterIntakeLiters());
        entity.setFeedAmountKg(req.feedAmountKg());
        entity.setBehaviorNotes(req.behaviorNotes());
        entity.setCareNotes(req.careNotes());
        entity.setPhotoFileKey(req.photoFileKey());
        entity.setInstructionFileKey(req.instructionFileKey());
        entity.setSupersedesLogId(null);

        long logId = welfare.save(entity);
        entity.setWelfareLogId(logId);

        return toResponse(entity);
    }

    /**
     * Records a superseding correction to an existing welfare log on same leg.
     *
     * @param legId trip leg PK
     * @param logId prior welfare log PK to supersede
     * @param req correction payload
     * @return new superseding welfare response
     */
    @Transactional
    public HorseWelfareLogResponse supersedeWelfareLog(long legId, long logId,
            HorseWelfareLogCorrectionRequest req) {
        CurrentActor actor = actors.current();
        requireLeg(legId);
        authorizeWelfareRecorder(actor, legId);

        var prior = welfare.findById(logId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND,
                        "WELFARE_LOG_NOT_FOUND",
                        "Welfare log #" + logId + " was not found."));

        if (prior.getLegId() != legId) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "SUPERSEDES_CROSS_LEG_VIOLATION",
                    "Cannot supersede a welfare log from a different "
                            + "transport leg.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime checkTime = req.checkTime() != null
                ? req.checkTime() : now;
        validateCheckTime(checkTime, now);

        var entity = new HorseWelfareLog();
        entity.setLegId(legId);
        entity.setRecordedBy(actor.staffId());
        entity.setCheckTime(checkTime);
        entity.setHealthStatus(req.healthStatus().name());
        entity.setTemperatureCelsius(req.temperatureCelsius());
        entity.setWaterIntakeLiters(req.waterIntakeLiters());
        entity.setFeedAmountKg(req.feedAmountKg());
        entity.setBehaviorNotes(req.behaviorNotes());
        entity.setCareNotes(req.careNotes());
        entity.setPhotoFileKey(req.photoFileKey());
        entity.setInstructionFileKey(req.instructionFileKey());
        entity.setSupersedesLogId(logId);

        long newId = welfare.save(entity);
        entity.setWelfareLogId(newId);

        return toResponse(entity);
    }

    /**
     * Retrieves active (non-superseded) welfare logs for a leg.
     *
     * @param legId trip leg PK
     * @return active welfare check responses
     */
    @Transactional(readOnly = true)
    public List<HorseWelfareLogResponse> getActiveWelfareLogs(long legId) {
        requireLeg(legId);
        return welfare.findActiveByLegId(legId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves complete audit history chain for a given welfare record.
     *
     * @param legId trip leg PK
     * @param logId target welfare log PK
     * @return audit history with active log and full historical chain
     */
    @Transactional(readOnly = true)
    public WelfareAuditHistoryResponse getWelfareAuditHistory(long legId,
            long logId) {
        requireLeg(legId);
        var target = welfare.findById(logId).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND,
                        "WELFARE_LOG_NOT_FOUND",
                        "Welfare log #" + logId + " was not found."));

        if (target.getLegId() != legId) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "SUPERSEDES_CROSS_LEG_VIOLATION",
                    "Log does not belong to the specified leg.");
        }

        var chain = welfare.findAuditChain(legId, logId);
        var responses = chain.stream().map(this::toResponse).toList();
        var activeEntity = chain.getLast();

        return new WelfareAuditHistoryResponse(logId, legId,
                toResponse(activeEntity), responses);
    }

    private void requireLeg(long legId) {
        legs.findLegById(legId).orElseThrow(() -> new OrderException(
                HttpStatus.NOT_FOUND, "LEG_NOT_FOUND",
                "Trip leg #" + legId + " was not found."));
    }

    private void authorizeWelfareRecorder(CurrentActor actor, long legId) {
        if ("DRIVER".equals(actor.roleCode()) && actor.staffId() != null) {
            if (legs.findDriverAssignment(legId, actor.staffId()).isEmpty()) {
                throw new AccessDeniedException(
                        "Driver is not assigned to this road leg.");
            }
            return;
        }
        if ("COORDINATOR".equals(actor.roleCode())
                || "MANAGER".equals(actor.roleCode())
                || "SPECIALIST".equals(actor.roleCode())) {
            return;
        }
        throw new AccessDeniedException(
                "Access denied for welfare logging on leg #" + legId + ".");
    }

    private void validateCheckTime(OffsetDateTime checkTime,
            OffsetDateTime now) {
        if (checkTime.isAfter(now.plusMinutes(5))) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "FUTURE_TIMESTAMP_NOT_PERMITTED",
                    "CheckTime cannot be in the future.");
        }
    }

    private HorseWelfareLogResponse toResponse(HorseWelfareLog w) {
        String staffName = legs.findStaffName(w.getRecordedBy());
        boolean isFever = w.getTemperatureCelsius() != null
                && w.getTemperatureCelsius().compareTo(FEVER_THRESHOLD) >= 0;
        boolean emergency = HealthStatus.CRITICAL.name().equalsIgnoreCase(
                w.getHealthStatus())
                || HealthStatus.MINOR_INJURY.name().equalsIgnoreCase(
                        w.getHealthStatus());
        String alert = emergency
                ? "Immediate veterinary attention required for status "
                        + w.getHealthStatus()
                : null;

        return new HorseWelfareLogResponse(
                w.getWelfareLogId(),
                w.getLegId(),
                w.getRecordedBy(),
                staffName,
                w.getCheckTime(),
                w.getHealthStatus(),
                w.getTemperatureCelsius(),
                w.getWaterIntakeLiters(),
                w.getFeedAmountKg(),
                w.getBehaviorNotes(),
                w.getCareNotes(),
                w.getPhotoFileKey(),
                w.getInstructionFileKey(),
                w.getSupersedesLogId(),
                isFever,
                emergency,
                alert);
    }
}
