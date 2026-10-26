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
    private final SubdivisionCatalog subdivisions;

    /** @param data catalog port @param subdivisions packaged region data */
    public LocationCatalogService(LocationCatalogRepository data,
            SubdivisionCatalog subdivisions) {
        this.data = data;
        this.subdivisions = subdivisions;
    }

    /** @return supported selector countries */
    public List<Country> countries() {
        return data.countries();
    }

    /** @param country ISO code @param type kind @return matching options */
    public List<Location> locations(String country, String type) {
        var rows = data.locations(country, type);
        return "CITY".equals(type) ? subdivisions.merge(country, rows) : rows;
    }

    /** @param country ISO country @param primaryOnly primary filter
     * @return active company hubs */
    public List<Location> hubs(String country, boolean primaryOnly) {
        return primaryOnly ? data.findPrimaryHubByCountry(country)
                .stream().toList() : data.hubs(country);
    }
}
