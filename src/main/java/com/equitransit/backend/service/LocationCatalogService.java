package com.equitransit.backend.service;

import com.equitransit.backend.repository.LocationCatalogRepository;
import com.equitransit.backend.repository.LocationCatalogRepository.Country;
import com.equitransit.backend.repository.LocationCatalogRepository.Location;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public route lookup use cases; catalog maintenance remains internal. */
@Service
@Transactional(readOnly = true)
public class LocationCatalogService {
    private final LocationCatalogRepository data;

    /** @param data catalog port */
    public LocationCatalogService(LocationCatalogRepository data) {
        this.data = data;
    }

    /** @return supported selector countries */
    public List<Country> countries() {
        return data.countries();
    }

    /** @param country ISO code @param type kind @return matching options */
    public List<Location> locations(String country, String type) {
        return data.locations(country, type);
    }
}
