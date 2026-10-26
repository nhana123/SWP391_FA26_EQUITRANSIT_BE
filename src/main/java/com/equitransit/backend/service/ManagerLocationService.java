package com.equitransit.backend.service;

import com.equitransit.backend.dto.location.LocationRequest;
import com.equitransit.backend.entity.LocationType;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.LocationCatalogRepository;
import com.equitransit.backend.repository.LocationCatalogRepository.Location;
import com.equitransit.backend.security.CurrentActorService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog administration with atomic primary hub changes. */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('MANAGER','COORDINATOR')")
public class ManagerLocationService {
    private final LocationCatalogRepository data;
    private final CurrentActorService actors;

    /** @param data catalog persistence @param actors live credentials */
    public ManagerLocationService(LocationCatalogRepository data,
            CurrentActorService actors) {
        this.data = data;
        this.actors = actors;
    }

    /** @param country country @param type kind @param inactive hidden rows
     * @param page page @param size limit @return locations */
    public List<Location> list(String country, String type, boolean inactive,
            int page, int size) {
        staff();
        return data.catalog(country, type, inactive, page, size);
    }

    /** @param id identity @return location including hidden rows */
    public Location get(int id) {
        staff();
        return data.find(id).orElseThrow(() -> new OrderException(
                HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND",
                "Location missing."));
    }

    /** @param id optional update identity @param request edit
     * @return saved row */
    @Transactional
    public Location save(Integer id, LocationRequest request) {
        staff();
        data.lock();
        Location old = id == null ? null : get(id);
        LocationRequest edit = normalize(request, old);
        try {
            if (edit.isPrimaryHub()) {
                data.demotePrimary(edit, id == null ? 0 : id);
            }
            int key;
            if (id == null) {
                key = data.insertLocation(edit);
            } else {
                data.updateLocation(id, edit);
                key = id;
            }
            return get(key);
        } catch (DuplicateKeyException exception) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "LOCATION_ALREADY_EXISTS", "Location already exists.");
        }
    }

    /** @param id row to hide without breaking historical references */
    @Transactional
    public void delete(int id) {
        staff();
        data.lock();
        get(id);
        data.deleteLocation(id);
    }

    private LocationRequest normalize(LocationRequest r, Location old) {
        String country = r.countryCode().toUpperCase(Locale.ROOT);
        if (!Set.of(Locale.getISOCountries()).contains(country)) {
            throw invalid("Invalid ISO country code.");
        }
        String name = clean(r.countryName());
        String canonical = data.countryName(country).orElse(null);
        if (canonical != null && name != null && !canonical.equals(name)) {
            throw invalid("Country name differs from the existing catalog.");
        }
        name = canonical == null ? name : canonical;
        if (name == null) {
            throw invalid("Country name is required for a new country.");
        }
        boolean primary = r.isPrimaryHub() == null
                ? old != null && old.isPrimaryHub() : r.isPrimaryHub();
        boolean active = r.isActive() == null
                ? old == null || old.isActive() : r.isActive();
        if (primary && (r.locationType() != LocationType.HUB || !active)) {
            throw invalid("A primary hub must be an active HUB.");
        }
        if ((r.latitude() == null) != (r.longitude() == null)) {
            throw invalid("Supply both latitude and longitude.");
        }
        String code = clean(r.locationCode());
        code = code == null ? null : code.toUpperCase(Locale.ROOT);
        if (r.locationType() == LocationType.AIRPORT && code != null
                && !code.matches("[A-Z]{3}")) {
            throw invalid("Airport code must contain three IATA letters.");
        }
        return new LocationRequest(country, name, r.locationType(),
                r.locationName().strip(), code, clean(r.address()),
                r.latitude(), r.longitude(), primary,
                r.domesticRatePerKm() != null ? r.domesticRatePerKm()
                        : old != null ? old.domesticRatePerKm() : null,
                active);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void staff() {
        var actor = actors.current();
        if (!List.of("MANAGER", "COORDINATOR").contains(actor.roleCode())
                || actor.staffId() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Location administration requires active staff.");
        }
    }

    private OrderException invalid(String message) {
        return new OrderException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_LOCATION", message);
    }
}
