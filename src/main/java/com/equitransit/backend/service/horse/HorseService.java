/*
 * HorseService
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Validate and maintain owner-scoped horse profiles.
 */
package com.equitransit.backend.service.horse;


import java.time.ZoneOffset;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.equitransit.backend.dto.horse.request.HorseProfileRequest;
import com.equitransit.backend.dto.horse.request.HorseUpdateRequest;
import com.equitransit.backend.dto.horse.response.HorsePageResponse;
import com.equitransit.backend.dto.horse.response.HorseResponse;
import com.equitransit.backend.entity.horse.Horse;
import com.equitransit.backend.exception.horse.HorseException;
import com.equitransit.backend.repository.horse.HorseRepository;
import com.equitransit.backend.security.CurrentActorService;


/** Changes only Horse master data, never submitted order snapshots. */
@Service
@Validated
@PreAuthorize("@domainAccessPolicy.isCustomer()")
public class HorseService {

    private final HorseRepository horses;
    private final CurrentActorService actors;

    /**
     * Creates the profile service.
     * @param horses the owner-scoped persistence repository
     * @param actors the trusted domain identity resolver
     */
    public HorseService(HorseRepository horses, CurrentActorService actors) {
        this.horses = horses;
        this.actors = actors;
    }

    /**
     * Lists only the authenticated customer's matching profiles.
     * @param page the zero-based page
     * @param size the bounded page size
     * @param query the optional name filter
     * @return an explicit pagination response
     */
    @Transactional(readOnly = true)
    public HorsePageResponse list(@Min(0) int page,
            @Min(1) @Max(100) int size, @Size(max = 150) String query) {
        long owner = actors.current().customerId();
        String search = query == null ? "" : query.trim();
        List<HorseResponse> items = horses.list(owner, page, size, search)
                .stream().map(this::response).toList();
        long total = horses.count(owner, search);

        return new HorsePageResponse(items, page, size, total,
                total / size + (total % size == 0 ? 0 : 1));
    }

    /**
     * Returns an owned profile without disclosing foreign horse existence.
     * @param id the horse key
     * @return the profile DTO
     */
    @Transactional(readOnly = true)
    public HorseResponse get(@Positive long id) {
        return response(owned(id, actors.current().customerId()));
    }

    /**
     * Captures booking measurements under a lock retained by the caller's tx.
     * @param id selected owned horse key
     * @param expected profile version acknowledged by the customer
     * @return locked current profile
     */
    @Transactional
    public HorseResponse lockSnapshot(@Positive long id, String expected) {
        HorseResponse horse = response(horses.lockOwned(id,
                actors.current().customerId()).orElseThrow(() ->
                new HorseException(HttpStatus.NOT_FOUND, "HORSE_NOT_FOUND",
                        "Horse profile was not found.")));

        if (!horse.recordVersion().equalsIgnoreCase(expected)) {
            throw new HorseException(HttpStatus.CONFLICT,
                    "HORSE_VERSION_CONFLICT",
                    "Horse measurements changed. Refresh the booking "
                + "revision.");
        }
        return horse;
    }
    /**
     * Deletes an owned horse if it has never been attached to an order.
     * @param id the positive horse key
     */
    @Transactional
    public void delete(@Positive long id) {
        long owner = actors.current().customerId();

        // Ensure horse exists and belongs to this customer (or throws 404).
        owned(id, owner);

        if (horses.hasOrders(id)) {
            throw new HorseException(HttpStatus.CONFLICT, "HORSE_IN_USE",
                    "Cannot delete a horse attached to an order.");
        }

        boolean deleted = horses.delete(id, owner);
        if (!deleted) {
            throw new HorseException(HttpStatus.NOT_FOUND, "HORSE_NOT_FOUND",
                    "Horse profile was not found.");
        }
    }
    /**
     * Creates a profile with server-derived ownership.
     * @param request the editable fields, never an owner or version
     * @return the persisted profile and generated version
     */
    @Transactional
    public HorseResponse create(@Valid HorseProfileRequest request) {
        long owner = actors.current().customerId();
        Horse horse = new Horse();
        long id;

        horse.setCustomerId(owner);
        apply(horse, request);
        try {
            id = horses.insert(horse);
        } catch (DataIntegrityViolationException error) {
            throw conflict();
        }
        return response(owned(id, owner));
    }

    /**
     * Replaces the editable fields if the last observed version matches.
     * @param id the owned horse key
     * @param request the replacement profile and expected version
     * @return the new persisted profile and version
     */
    @Transactional
    public HorseResponse update(@Positive long id,
            @Valid HorseUpdateRequest request) {
        long owner = actors.current().customerId();
        Horse horse = owned(id, owner);
        boolean changed;

        apply(horse, request);
        try {
            changed = horses.update(horse, request.getRecordVersion());
        } catch (DataIntegrityViolationException error) {
            throw conflict();
        }
        if (!changed) {
            throw new HorseException(HttpStatus.CONFLICT,
                    "HORSE_VERSION_CONFLICT",
                    "Profile changed. Reload it before saving again.");
        }
        return response(owned(id, owner));
    }

    private Horse owned(long id, long owner) {
        return horses.findOwned(id, owner).orElseThrow(() ->
                new HorseException(HttpStatus.NOT_FOUND, "HORSE_NOT_FOUND",
                        "Horse profile was not found."));
    }

    private HorseException conflict() {
        return new HorseException(HttpStatus.CONFLICT, "HORSE_CONFLICT",
                "The profile conflicts with an existing record.");
    }

    private void apply(Horse horse, HorseProfileRequest request) {
        horse.setName(request.getName().trim());
        horse.setMicrochipId(optional(request.getMicrochipId()));
        horse.setPassportNumber(optional(request.getPassportNumber()));
        horse.setBreed(optional(request.getBreed()));
        horse.setDateOfBirth(request.getDateOfBirth());
        horse.setSexCode(request.getSexCode());
        horse.setWeightKg(request.getWeightKg());
        horse.setHeightCm(request.getHeightCm());
        horse.setBodyWidthCm(request.getBodyWidthCm());
        horse.setMeasuredAt(request.getMeasuredAt() == null ? null
                : request.getMeasuredAt().withOffsetSameInstant(
                        ZoneOffset.UTC).withNano(0));
        horse.setSpecialCareNotes(optional(request.getSpecialCareNotes()));
        horse.setHandlingNotes(optional(request.getHandlingNotes()));
        horse.setFeedWaterRequirements(optional(
                request.getFeedWaterRequirements()));
        horse.setStallPreferences(optional(request.getStallPreferences()));
        horse.setVeterinaryInstructions(optional(
                request.getVeterinaryInstructions()));
        horse.setVeterinaryInstructionSource(optional(
                request.getVeterinaryInstructionSource()));
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private HorseResponse response(Horse horse) {
        boolean complete = horse.getWeightKg() != null
                && horse.getHeightCm() != null
                && horse.getBodyWidthCm() != null
                && horse.getMeasuredAt() != null;

        return new HorseResponse(horse.getHorseId(), horse.getName(),
                horse.getMicrochipId(), horse.getPassportNumber(),
                horse.getBreed(), horse.getDateOfBirth(), horse.getSexCode(),
                horse.getWeightKg(), horse.getHeightCm(),
                horse.getBodyWidthCm(), horse.getMeasuredAt(),
                horse.getSpecialCareNotes(), horse.getHandlingNotes(),
                horse.getFeedWaterRequirements(), horse.getStallPreferences(),
                horse.getVeterinaryInstructions(),
                horse.getVeterinaryInstructionSource(),
                horse.getRecordVersion(), complete);
    }

}
